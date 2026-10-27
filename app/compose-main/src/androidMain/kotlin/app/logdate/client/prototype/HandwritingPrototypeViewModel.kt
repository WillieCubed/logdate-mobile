package app.logdate.client.prototype

import android.app.Application
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.feature.editor.ui.handwriting.HandwritingPage
import app.logdate.feature.editor.ui.handwriting.HandwritingPracticeEntry
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PracticePageStatus { READY, CHANGED, SAVING, SAVED, SAVED_EARLIER, SAVE_ERROR }

/** Keeps an isolated practice page through rotation and explicit disk saves through process death. */
class HandwritingPrototypeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val file = AtomicFile(File(application.filesDir, "handwriting-entry-practice.json"))
    private val legacyFile = AtomicFile(File(application.filesDir, "handwriting-practice.json"))
    var page by mutableStateOf<HandwritingPage?>(null)
        private set
    var text by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf(PracticePageStatus.READY)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    private var saving = false

    init {
        viewModelScope.launch {
            try {
                val entry =
                    withContext(Dispatchers.IO) {
                        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                            HandwritingPracticeEntry.decode(file.openRead().bufferedReader().use { it.readText() })
                        } else if (legacyFile.baseFile.exists() || File(legacyFile.baseFile.path + ".bak").exists()) {
                            HandwritingPracticeEntry(
                                page = HandwritingPage.decode(legacyFile.openRead().bufferedReader().use { it.readText() }),
                            )
                        } else {
                            HandwritingPracticeEntry()
                        }
                    }
                page = entry.page
                text = entry.text
                status = PracticePageStatus.READY
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Could not read handwriting practice page", e)
                loadFailed = true
            }
        }
    }

    fun edit(updated: HandwritingPage) {
        page = updated
        status = PracticePageStatus.CHANGED
    }

    fun editText(updated: String) {
        text = updated
        status = PracticePageStatus.CHANGED
    }

    fun save(pageSnapshot: HandwritingPage) {
        val snapshot = HandwritingPracticeEntry(pageSnapshot, text)
        if (saving) return
        saving = true
        status = PracticePageStatus.SAVING
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = file.startWrite()
                    try {
                        stream.write(snapshot.encode().toByteArray(Charsets.UTF_8))
                        file.finishWrite(stream)
                    } catch (e: Exception) {
                        file.failWrite(stream)
                        throw e
                    }
                }
                status =
                    if (snapshot.page == page && snapshot.text == text) {
                        PracticePageStatus.SAVED
                    } else {
                        PracticePageStatus.SAVED_EARLIER
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Could not save handwriting practice page", e)
                status = PracticePageStatus.SAVE_ERROR
            } finally {
                saving = false
            }
        }
    }
}
