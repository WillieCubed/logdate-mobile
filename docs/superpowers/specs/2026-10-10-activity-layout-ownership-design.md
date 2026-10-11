# Activity ownership and adaptive composition

Status: implemented and verified. Concrete changes and populated comparisons are ready for developer review before committing; rollout flags remain gated. See the implementation plan for acceptance evidence.

## Problem

The journal merge implementation confuses reusable workspace surfaces with ownership of the Home browsing experience. Its review is a long column of summaries, counts, consequence copy, and buttons. That is a rendering of the requirements, rather than a composition designed around the decision someone is making.

The navigation implementation has a related limitation: `HomeSceneStrategy` selects a source and detail from adjacent back-stack entries. When the source is `HomeRoute`, `ListDetailHomeScene` injects the detail into the whole Home screen. Route eligibility and available width are insufficient to establish whether that browsing context belongs beside the detail.

Settings currently replaces Home because it does not participate in that scene. However, no Settings-specific scene composes its category navigation and selected section. Rewind playback is already explicitly fullscreen; preserve that behavior.

## Product direction

An activity is a user task with its own visible context and navigation. It does not imply another Android Activity, window, or navigation controller.

The application navigation layer chooses the active activity. The activity owns its visible chrome and composition. Content components provide content and callbacks, and can occupy a pane or the whole available region. Back-stack presence does not imply simultaneous visibility.

Home remains mounted or has its state retained as appropriate, but its timeline, destination navigation, search, and creation controls are absent while a replacement activity is active. Back restores the previous Home destination, scroll, and selection.

## Activity compositions

| Activity | Compact presentation | Wider presentation | Home visible |
| --- | --- | --- | --- |
| Home browsing | Selected collection | Selected collection and a directly related preview where useful | Yes |
| Settings | Categories, then selected section | Settings category navigation beside the selected section | No |
| Journal reading | Journal content, then an opened item | Journal-specific context and content; an opened item can use a related list/detail composition | No unrelated Home content |
| Rewind playback | Immersive playback | Playback with its own related controls or story navigation | No |
| Journal merge: destination | Source context, contextual search, destination list | Bounded source prompt and destination list | No |
| Journal merge: review | Existing covers, inline outcome, concise consequence and actions | Bounded visual decision | No |

Journal reading may use a journals collection as related context when entered through that collection and both regions fit. It must not render the entire Home screen to obtain that list. Direct links render a complete journal activity without requiring an artificial Home entry. The exact collection-versus-journal-summary treatment must be evaluated in populated renders; this is not permission to add three narrow columns.

## Journal merge

Retain exactly two steps: choose destination and review. Selecting a destination opens review directly. There is still one irreversible confirmation, with the existing data and sync contracts unchanged.

### Components

- Source context: a wrapping “Merge … into…” prompt and one short instruction, without a redundant source illustration.
- Destination picker: activity-local search and a grid using the existing JournalCover, with counts below and the existing cover update date. Preserve loading, empty, no-results, and clear-search states.
- Identity comparison: the left visual area contains only the canonical JournalCover objects and their merge animation. No role labels, original-count badges, or destination control surround the covers.
- Selection: two full-width tappable journal containers use “Move contents from” and “Add them to” overlines above comparable titleMedium journal names. Their own bodySmall metadata uses complete item counts, separated within one wrapping line: the source count and items already in the other journal, and the destination’s current count and count when merged. There is no separate Items section or detached preservation sentence. Each container reopens the shared journal picker for its role, retaining the other selection; Back restores review, and each picker retains its own search and scroll. Pending merge recovery keeps its already committed source fixed. Cards disable while committing, wrap complete names at large text, and expose accessible action and count labels.
- Outcome: counts belong to the journal containers, with no separate Items section, arithmetic symbols, or proportional addition strip. Updated quantities and their explanation become visible before renewed confirmation.
- Decision surface: in a wide composition, the decision panel fills the safe height between 16dp top and bottom gutters, while the covers retain their natural visual bounds. Actions anchor to the panel bottom; short and large-text layouts remain scrollable.
- Consequences: a rounded secondaryContainer notice with an outlined Info icon sits immediately after the journal containers. Its message is “Your notes and media stay. The original journal will be removed. This can’t be undone.” Cancel/Merge form the bottom action region and share a row when measured labels and touch targets fit, stacking at narrow widths or large text. No additional confirmation is introduced.
- Feedback: visible, announced failure or changed-content feedback next to the decision; count changes still require renewed confirmation.

These are state-driven components with independent previews. They do not instantiate navigation, a global app bar, a workspace host, or their own outer Scaffold.

### Layout and interaction

The activity has no app-bar row or opaque header. A floating medium Material 3 Expressive tonal Back button is the only interactive overlay, using the existing medium square/pressed shape pattern. The area around it passes touches through, and content scrolls underneath. Initial top clearance belongs to scroll content padding, so it scrolls away. “Merge journals?” belongs to the decision surface; the picker supplies its source-specific prompt in content. It owns destination search; the Home-wide search and account target are absent. Destination search belongs with the destination selection component. Review has no search chrome.

On a phone, source context is concise and destination results receive most of the space. Review shows the actual journal covers, an inline outcome, and a concise consequence/action group. The short review places its action group immediately after the safety note. At large text sizes or when covers would be too narrow to read, the actual covers stack at a readable width. The full review scrolls in compact landscape and large text, keeping every action reachable.

Use a surfaceContainer activity canvas, a rounded surface collection for the picker, unframed canonical journal covers, and a separate rounded surface decision card. The activity is bounded to 1120dp. Wide measured bounds balance the books and decision at near-equal widths, with a bounded 560dp visual area and a matching taller decision card; the heading stays at titleLarge reading scale and the confirmation control retains its 56dp minimum height; tall phones give the books the remaining height above the decision. Compact layouts and large text scroll; stacked covers retain their natural height. Reuse Material 3 shapes and theme colors. The selected cover shares its canonical identity between picker and review, using the existing navigation shared transition scope. Two covers converge into the surviving book on confirmation; a fast local commit lets this short transition settle before navigation. All step and cover transitions honor reduced motion, and reopening an already completed operation navigates directly.

Choose placement from measured activity bounds. A separating hinge places the entire bounded flow on the larger physical side, without splitting a single decision across the fold. Keep keyboard focus order aligned with reading order, honor RTL, and preserve the existing search, selection, and scroll behavior across Back and resize.

## Navigation and ownership

Declare presentation intent at navigation registration: Home browsing, related detail, or replacement activity. Related details also identify the collection/context they can accompany. Avoid another growing list of route classes in a decorator.

The scene layer selects the visible composition from intent, current selection, and available bounds. It must not infer semantic relatedness solely from the previous route. Opening a replacement activity suspends the Home composition. Back restores the caller; successful merge removes obsolete source routes and opens the surviving journal activity as before.

Keep a single application back stack. Activity-specific compositions can render retained entries as panes, but must preserve entry lifecycle and ViewModel ownership. Do not add a nested NavDisplay merely to acquire a second Scaffold.

Separate reusable surface, spacing, inset, and hinge primitives from Home navigation and chrome. Existing `WorkspacePanel` and measurement helpers may remain useful, but using them does not enroll a route in Home. `HOME_WORKSPACE_V2` remains a presentation migration flag, not a substitute for activity ownership.

## Options considered

1. Adjust only the merge column and keep existing decorators: smaller change, but leaves semantic ownership ambiguous and does not address Settings or incorrect source/detail pairings.
2. **Recommended: explicit navigation presentation with activity-owned compositions and reusable content components.** Establishes a consistent boundary while retaining the existing app back stack, data workflow, and layout primitives.
3. Separate navigation hosts for each activity: adds duplicated lifecycle, restoration, deep-link, and Back behavior without being necessary for the required UI.

## Implementation order

1. Add failing navigation/composition tests proving that replacement activities hide Home and related details select only matching context. Preserve immersive Rewind behavior and deep-link restoration.
2. Introduce explicit presentation ownership and remove the implicit whole-Home source composition where it is inappropriate.
3. Recompose merge from the components above, preserving the existing ViewModel and use case. Add independent component and complete activity previews.
4. Compose Settings category and detail panes with retained selection and compact Back behavior.
5. Apply the same ownership rules to journal reading and Rewind; inspect actual populated compositions instead of assigning every detail the same layout.
6. Review populated phone/tablet comparisons before updating references. Verify compact/landscape, hinges, dark/dynamic colors, RTL, large text, keyboard, reduced motion, restoration, cancellation, and successful merge on emulators or Managed Devices. Run affected tests, lint, workspace contract, Android build, and server-client E2E where navigation or merge behavior changes.

## Acceptance

- Settings replaces Home and shows category/detail panes only when each is usable.
- Merge has distinct picker and review compositions, meaningful use of width, and no Home chrome.
- Journal and note panes contain related context; unrelated timeline content never survives merely because it is underneath on the stack.
- Rewind playback remains an immersive replacement activity.
- Resize and Back preserve selections and originating browsing state without duplicate hosts or ViewModels.
- Existing offline merge, stable operation identity, transactional persistence, redirects, and sync recovery remain intact.
- Visual acceptance judges hierarchy, context, and task clarity in addition to clipping and test success.
