@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.feature.widgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.logdate.client.media.MediaManager
import app.logdate.client.repository.user.UserStateRepository
import app.logdate.ui.theme.LogDateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.android.ext.android.inject
import kotlin.time.Clock

class NewEntryWidgetConfigActivity : SecureWidgetConfigActivity() {
    private val mediaManager: MediaManager by inject()
    private val userStateRepository: UserStateRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        lifecycleScope.launch {
            if (!unlockSetup(userStateRepository)) return@launch
            val instanceSettings = WidgetInstanceSettings(this@NewEntryWidgetConfigActivity)
            val initialShape = instanceSettings.photoShape(widgetId)
            setContent {
                LogDateTheme {
                    NewEntrySetup(mediaManager, initialShape) { shape ->
                        instanceSettings.saveUsePhotoPrompt(widgetId, true)
                        instanceSettings.savePhotoShape(widgetId, shape)
                        enqueueWidgetRefresh(this@NewEntryWidgetConfigActivity)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEntrySetup(
    mediaManager: MediaManager,
    initialShape: PhotoWidgetShape,
    onDone: (PhotoWidgetShape) -> Unit,
) {
    val context = LocalContext.current
    var shape by remember { mutableStateOf(initialShape) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var photoState by remember { mutableStateOf<OnThisDayWidgetState>(OnThisDayWidgetState.PhotoUnavailable) }
    val permissions = remember { imagePermissionsForWidget() }
    val hasAccess = hasWidgetImageAccess(context)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionRevision++
        }
    LaunchedEffect(permissionRevision) {
        photoState =
            if (hasWidgetImageAccess(context)) {
                runCatching {
                    val today =
                        Clock.System
                            .now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                    choosePhotoPrompt(mediaManager.getRecentImages(60).first(), today)
                        ?.let { OnThisDayWidgetState.PhotoPrompt(it.uri) }
                }.getOrNull() ?: OnThisDayWidgetState.PhotoUnavailable
            } else {
                OnThisDayWidgetState.PhotoUnavailable
            }
    }
    val previewState =
        (photoState as? OnThisDayWidgetState.PhotoPrompt)
            ?: OnThisDayWidgetState.PhotoPrompt("android.resource://${context.packageName}/drawable/widget_photo_sample")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Scaffold(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxHeight().fillMaxWidth(),
            topBar = { TopAppBar(title = { Text("New Entry shape") }) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    WidgetSetupPreview(
                        NewEntryWidget(previewState, shape),
                        Pair(previewState, shape),
                    )
                }
                Text("Shape", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val photoUri = previewState.photoUri
                    val photoBitmap = remember(photoUri) { photoUri?.let { loadScaledThumbnail(context, it)?.asImageBitmap() } }
                    val photoPainter = photoBitmap?.let(::BitmapPainter) ?: painterResource(R.drawable.widget_photo_sample)
                    val systemRadius = systemWidgetCornerRadiusDp(context)
                    for ((option, label) in listOf(
                        PhotoWidgetShape.SYSTEM to "System",
                        PhotoWidgetShape.SOFT to "Soft",
                        PhotoWidgetShape.CIRCLE to "Circle",
                    )) {
                        val previewShape =
                            when (option) {
                                PhotoWidgetShape.SYSTEM -> RoundedCornerShape(systemRadius.dp)
                                PhotoWidgetShape.SOFT -> RoundedCornerShape(maxOf(systemRadius, 48f).dp)
                                PhotoWidgetShape.CIRCLE -> CircleShape
                            }
                        WidgetSetupChoice(
                            selected = shape == option,
                            onClick = { shape = option },
                            modifier = Modifier.weight(1f).height(116.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                                Image(
                                    painter = photoPainter,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(if (option == PhotoWidgetShape.CIRCLE) 60.dp else 76.dp, 60.dp)
                                            .clip(previewShape),
                                )
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
                if (!hasAccess) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { launcher.launch(permissions) }) { Text("Allow photos") }
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onDone(shape) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

internal fun imagePermissionsForWidget(): Array<String> =
    when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun hasWidgetImageAccess(context: Context): Boolean =
    imagePermissionsForWidget().any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
