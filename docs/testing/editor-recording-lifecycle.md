# Editor recording interruption acceptance

Run on a new private output directory:

```sh
bash tests/e2e/managed-editor-interruption-acceptance.sh /tmp/logdate-editor-interruption
```

The script uses Gradle Managed Devices only and never clears application data. It
builds and hashes the app and probe APKs, retains test sources and source hashes,
and requires five passing instrumentation cases without skips:

- A separate, unshipped driver starts a real recording and sends SIGKILL to its
  process after AAC frames reach disk. It opens the app normally, finds the draft
  through the editor's Drafts menu, and compares all complete captured AAC frame
  payloads with the recovered MPEG-4 audio. Reopening must not start capture.
- Actual edge-back cancellation preserves text. Committing edge-back opens exit
  confirmation; dismissing it preserves text. Explicit Discard deletes its draft,
  and three subsequent Add entry visits open with empty editable content.
- Explicit Discard of a recording stops capture, deletes its synthetic draft,
  and allows Add entry to reopen without starting another recording.
- The existing recording lifecycle cases exercise background/foreground, activity
  recreation, system notification return, Finish and Save while retaining another
  editor's input, and Back followed by Save Draft.

These are focused API 34 regressions, not a device matrix. Abrupt death is distinct
from activity recreation and the completed-file cold-start fixture. A platform
that cannot salvage complete AAC frames fails acceptance; the test does not treat
an empty or unplayable recording as a successful save.

The lifecycle tests inject Android touch input. The notification test also opens
the system shade and taps the notification with UiAutomator. Screenshots and XML
results remain in the supplied output directory for inspection.
