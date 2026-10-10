package app.logdate.client.domain.timeline

import app.logdate.client.repository.journals.isCapturedOnWatch

/** Whether every note behind this moment was captured on the watch; false for a moment without notes. */
fun Moment.capturedOnWatch(): Boolean = sourceNotes.isNotEmpty() && sourceNotes.all { it.isCapturedOnWatch }
