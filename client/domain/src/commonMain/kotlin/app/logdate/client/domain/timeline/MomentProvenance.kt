package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.CaptureActivity
import app.logdate.client.repository.journals.isCapturedOnWatch

/** Whether every note behind this moment was captured on the watch; false for a moment without notes. */
fun Moment.capturedOnWatch(): Boolean = sourceNotes.isNotEmpty() && sourceNotes.all { it.isCapturedOnWatch }

/** What the person was doing during this moment: the activity every note behind it records, or null. */
fun Moment.capturedDuring(): CaptureActivity? = sourceNotes.map { it.capturedDuring }.distinct().singleOrNull()
