# App lock and private windows

App lock protects restored journals as well as journals created on this device. Only successful
authentication or an explicitly disabled lock allows access. Missing enrollment, an unavailable
authenticator, and an unknown result keep the journal locked. Native security setup can make the
destination usable; returning from setup rechecks authentication without reopening canceled setup.

`LockableContent` keeps the underlying editor composed while preventing its placement, input, and
accessibility exposure. Keeping composition alive preserves unfinished edits during authentication.
Do not replace this with a painted overlay or discard the editor composition when the app locks.

Dialogs, menus, and bottom sheets own separate windows. They must use the shared components from
`app.logdate.ui.common`, which inherit the lock's visibility boundary. Import `AlertDialog`,
`DatePickerDialog`, `Dialog`, `DropdownMenu`, and `ModalBottomSheet`, and `TooltipBox` from that package instead of
calling the corresponding Compose components directly. The wrappers retain Material defaults.

```kotlin
import app.logdate.ui.common.AlertDialog

@Composable
fun ConfirmEntryExit(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text("Leave editor") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep editing") } },
        title = { Text("Leave this entry?") },
    )
}
```

Keep editable dialog state in the caller or its view model, outside the guarded window's content
lambda. Locking then hides the window without discarding the person's unfinished input. A modal
must never dismiss itself or confirm an operation merely because the app becomes locked.

The application startup repair gate runs before journal content is available and remains outside
this boundary. Any new exception needs a specific ownership explanation and privacy review.

Run `:client:ui:checkPrivateModalContract` when changing app windows. Lock acceptance includes a
populated editor and an already-open dialog: neither is visible or interactive while locked, both
retain their edits after unlock, and the unlock/setup action remains reachable at 200% text size.
