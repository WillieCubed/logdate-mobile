# Client Text

`UiText` is how UI state carries words to the screen without writing them in English.

A ViewModel, mapper, or shared model names a Compose string resource and its arguments:

```kotlin
copy(recoveryMessage = Res.string.history_change_not_saved.toUiText())
```

The composable that shows it turns it into words for the current locale:

```kotlin
Text(state.recoveryMessage.asString())
```

Because state holds the resource rather than the words, the text follows the device language, and
tests compare resources instead of English sentences.

Use `UiText.Raw` for text that is already final, such as a server message, a device name, or the
person's own content. Code that runs outside composition, such as a notification, a share sheet, or
a message sent to the watch, calls the suspending `resolve()` at that boundary.

Domain code that has no Compose dependency should return meaning, such as an enum or a failure
reason, and let the feature choose the words.
