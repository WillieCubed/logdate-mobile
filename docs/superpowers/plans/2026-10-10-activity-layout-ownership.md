# Activity Layout Ownership Implementation Plan

> Use superpowers:executing-plans with test-first changes. The developer explicitly requested complete implementation of the approved spec; proceed through implementation and acceptance without another planning permission gate.

**Goal:** Give Settings, journal reading, Rewind playback, and journal merge appropriate activity ownership and adaptive panes without preserving unrelated Home content.

**Architecture:** Navigation registrations declare presentation metadata. One back stack and its existing entry decorators retain lifecycle and saved state. Activity compositions use reusable surfaces and measured bounds independently of Home chrome. Merge keeps its domain workflow and two stages.

**Tech Stack:** Kotlin Multiplatform, Compose, Navigation 3, existing Koin and Room bindings.

**Spec:** `docs/superpowers/specs/2026-10-10-activity-layout-ownership-design.md`

## Global Constraints

- Rebase latest main first; preserve concurrent work and the existing managed detached worktree.
- One app back stack, no nested NavDisplay or second Home host.
- No physical Android devices; emulator or Managed Device acceptance only.
- Preserve all merge data/sync behavior, destination metadata, redirects, operation identity, and stale-preview confirmation.
- Two merge steps; one irreversible confirmation.
- Use measured bounds, useful minimum pane widths, hinge-safe placement, RTL order, and large-text fallback.
- Developer review before committing product changes; all checks must pass.

## Review Focus

- Home below a replacement route must retain state without rendering unrelated chrome or content.
- A direct-linked Settings detail must have category context on wide screens without synthetic entry/ViewModel duplication.
- Nested settings navigation and repeated category changes must preserve meaningful Back history and selected category.
- Journal reading from Home, explicit collections, or deep links must show only matching context.
- Large text and compact landscape must keep merge actions reachable without obscuring consequences.

## Tasks

### 1. Navigation ownership and reusable activity layout

Files: `client/ui/.../navigation/ActivityPresentation.kt`, `app/compose-main/.../navigation/scenes/RelatedContentSceneStrategy.kt`, `RelatedContentScene.kt`, `WorkspaceRouteDecorator.kt`, navigation registrations and corresponding desktop/common tests.

- [x] Write and observe failing behavior tests: replacement merge/Settings hides Home; unrelated source does not pair; explicit matching collection/detail does; Rewind stays fullscreen in both flag states.
- [x] Add `ActivityPresentation` metadata and `NavEntry.activityPresentation()` defaulting to replacement. Register presentation at feature entries rather than adding route class allowlists.
- [x] Replace implicit whole-Home injection with semantically related entry composition; preserve existing entry lifecycle decorators.
- [x] Add shared activity framing and measured pane composition independent of Home search/navigation.
- [x] Run app navigation and shared UI suites; inspect all failures.

### 2. Merge activity components and compositions

Files: `client/feature/journal/.../ui/merge/JournalMergeScreen.kt`, new adjacent components/layout files, journal desktop tests, screenshot scenes and references.

- [x] Add failing UI tests for no Home search, distinct wide regions, visible phone actions, large-text/landscape scrolling, keyboard/focus, failure feedback and preserved query after Back.
- [x] Split source context, picker, identity comparison, outcome, consequences, actions, and feedback into state-driven components.
- [x] Implement compact and wide picker/review compositions with activity-local search and one action region, preserving ViewModel/use-case contracts.
- [x] Add populated independent component and activity previews and render tests.
- [x] Run journal tests and inspect phone/tablet/posture/text/color comparisons before updating references.

### 3. Settings activity

Files: `client/feature/core/.../settings/navigation/*`, Settings overview/category components, app settings scene/composition tests.

- [x] Write failing tests for category/detail panes, compact Back, nested details, category selection and state restoration.
- [x] Register Settings root/detail ownership and compose a Settings-specific category pane beside the active section when both fit.
- [x] Make category selection visible and preserve nested section history; reuse existing sections and their state owners.
- [x] Ensure direct-linked sections have complete activity context without a second NavDisplay or duplicate entry ViewModels.
- [x] Run core/settings and app navigation suites; inspect populated Settings compositions.

### 4. Journal reading and Rewind integration

Files: journal navigation/detail components, Rewind entries, app navigation scenes/tests.

- [x] Test Home-to-journal replacement, journals collection pairing, journal-to-note pairing and incompatible-context rejection.
- [x] Use journal-specific context/content components in complete direct-linked and related reading layouts; preserve note actions and journal selection.
- [x] Register immersive Rewind ownership explicitly and verify Home absence and full playback bounds.
- [x] Verify Back/resize restores originating destination and state.

### 5. Full acceptance and review

- [x] Run affected module suites, root `ktlintCheck`, `detekt`, `checkHomeWorkspaceContract`, and `:app:android-main:assembleDebug`.
- [x] Run `:integration:server-client-e2e:test` and unchanged merge domain/data/sync regressions.
- [x] Inspect populated phone/tablet comparison sheets plus compact, landscape, book/tabletop, dark/dynamic color, RTL, 200% text, keyboard, reduced motion and restoration.
- [x] Run complete navigation and merge flow on safe emulators/Managed Devices, including failure and cancellation.
- [x] Independently review concrete changes and address findings with regressions; present developer review before commit.

## Execution ledger

- Latest origin/main fetched and the detached worktree successfully rebased before product changes. Only the approved design document was untracked.
- Ruling: explicit complete implementation authorization includes writing and executing this plan — no additional plan approval is requested.

- Activity metadata, independent framing, related reading scenes, Settings scenes, merge components, and populated desktop fixtures implemented.
- Full journal (86), shared UI (181), Library (34), and core/Settings (310) desktop suites passed before final independent-review fixes.
- Full domain (826), data (260), database (46), sync (547), server (839; three optional tests skipped), and server-client E2E (26; one optional large-archive test skipped) passed. PostgreSQL repository merge acceptance and the complete server-client suite also passed against a fresh disposable database; its server was stopped cleanly.
- Independent review identified and drove regressions for search ownership, specific journal context, visible stale-count feedback, per-visit merge saved-state construction, repeated source-pane selection, and folded search placement. All production findings were fixed and independently re-reviewed. The strict process-snapshot regression and the complete runtime acceptance now pass.

- Final affected desktop suites: journal 95, shared UI 185, Library 34, core/Settings 310, Rewind 8, and app/navigation 78; no failures. Actual Koin merge construction and detached process snapshot restoration preserve query, destination, and operation identity, while fresh visits start clean.
- Final root `ktlintCheck`, `detekt`, `checkHomeWorkspaceContract`, and Android debug build passed in `/tmp/logdate-activity-acceptance-final.log`.
- Full desktop catalog comparison reached one expected Rewind settings text-wrap difference; that image was inspected and refreshed. The final 78-test app run revalidated the changed Rewind settings family; all other catalog images had already matched in the full run.
- All 79 selected Android journal, merge, Settings component, and Settings book-content screenshots passed after inspection and reference updates. Layoutlib reports optional missing vendor fold-extension warnings, with zero render/test errors; native hinge behavior was independently tested on Managed Devices.
- Native merge, dynamic/dark palettes, 200% text, reduced motion, foldable ownership, and immersive Rewind passed on Pixel 9 Pro API 36 and Pixel Tablet API 35. The Settings category/nested-navigation/Back-restoration regression passed on both after correcting lazy-list and actionable-row test selectors. These checks preserve the previous Home destination and visible saved item.
- Populated deterministic and native comparison sheets are saved under `/Users/williecubed/.codex/visualizations/2026/10/10/01a1239a-a009-7f93-b713-d3e822eee949/`: `merge-comparison.png`, `reading-comparison.png`, `settings-comparison.png`, `android-merge-comparison.png`, `android-reading-settings-baseline-review.png`, `native-phone-comparison.png`, `native-tablet-comparison.png`, and `native-settings-restoration-comparison.png`.
- The presentation and merge rollout flags remain gated. No physical devices were used. Changes remain uncommitted for the required developer review.


## Visual revision after developer review

The merge picker and review reuse the existing JournalCover, including real cover images and the normal generated color when no image exists. Removed the separate monogram/illustration treatment and duplicate title summaries. The picker is a cover grid; compact one-column results stay small enough to identify the first destination without scrolling.

Review defaults to the From/Keep covers, Change destination, optional Details, one short consequence, and Merge/Cancel. Counts, overlap, and preserved metadata are collapsed under Details. Changed counts automatically open Details and require renewed confirmation. At large text sizes the actual covers stack at a readable width rather than breaking titles across narrow columns. The existing cover now has a single keyboard focus target that activates with Enter.

The actual covers converge into the surviving journal on confirmation. Fast commits wait only for this presentation transition; reduced motion and restored completion navigate immediately. Workflow, transaction, and sync contracts are unchanged. Feature flags remain gated, and changes remain uncommitted for developer review.

Superseded visual verification: 101 journal desktop tests and 15 Android screenshot comparisons passed for the prior compact layout. Its final Android build was interrupted after shared Gradle cache contention. The latest developer review calls for a roomier Material 3 composition and shared-element transitions; fresh validation is required.

### Material surfaces and motion revision

Use a surfaceContainer canvas, an extraLarge rounded working surface for the destination collection, a secondaryContainer stage for actual covers, and a separate rounded decision surface. Expand the bounded activity to 1120dp. Wide layouts place the dominant visual stage beside the decision; tall phones allocate remaining height to the stage above the decision. Compact and large-text layouts scroll. Keep counts and metadata collapsed.

Reuse canonical journal shared identity for the selected cover between picker and review, combining step visibility with parent navigation visibility. Use the existing application shared transition scope when hosted, and a standalone fallback only when absent. Reduced motion removes step, bounds, detail-expansion, and cover-convergence animation delays.


The fixed-height phone stage is used only when paired covers fit. Narrow safe regions scroll complete stacked covers instead of compressing them. The tilt leaves both titles readable and a visible gap inside the stage boundary.

Final verification: 103 journal desktop tests and 16 inspected Android screenshot comparisons passed. The real Room/navigation merge and visual acceptance tests passed on Pixel 9 Pro API 36 and Pixel Tablet API 35, including actual cover images, dynamic light/dark palettes, 200% text, and system reduced motion. Root ktlintCheck, detekt, checkHomeWorkspaceContract, the Android debug build, and the server-client E2E task completed successfully (the unchanged server-client task reused its passing result). Final runtime runs were sequential after a transient disk-space failure; the stale interrupted tablet report was preserved separately before regenerating it. The final tablet invocation also successfully collected both device reports.

Evidence: `merge-material-motion` in the task visualization directory. Logs: `/tmp/logdate-merge-material-all-tests.log`, `/tmp/logdate-merge-material-final.log`, `/tmp/logdate-merge-material-phone-final.log`, and `/tmp/logdate-merge-material-tablet-final.log`. No physical devices were used. Flags remain gated; changes remain uncommitted for developer review.


### Tablet balance revision

The journal covers now sit directly on the activity canvas, without a containing surface. Wide review gives the decision approximately half the available width, uses larger typography and controls, and bounds the visual area rather than stretching it to the screen height. Counts stay collapsed. Phone stacking, shared cover identity, reduced motion, and failure feedback remain part of acceptance. Portrait tablets place full-size books above the decision rather than squeezing them into two columns.

Verification: 104 journal desktop tests, 16 inspected Android screenshot comparisons, root ktlintCheck and detekt, checkHomeWorkspaceContract, and the Android debug build passed. The unchanged server-client E2E task reused its passing result. Both real Room/navigation and dynamic-color visual tests passed again on Pixel Tablet API 35 and Pixel 9 Pro API 36, including 200% text and reduced motion. Evidence is in `merge-balanced-tablet`; logs are `/tmp/logdate-merge-balanced-tests.log`, `/tmp/logdate-merge-balanced-android.log`, `/tmp/logdate-merge-balanced-tablet-runtime.log`, and `/tmp/logdate-merge-balanced-phone-runtime.log`. No physical devices were used; flags remain gated and work remains uncommitted.


### Inline summary and destination action

Removed the Details disclosure. The decision card now shows the unique item count, overlap when present, and retained journal metadata directly. Change destination sits with that information, before the consequence and confirmation actions. The canonical covers remain uncontained on the activity canvas. Short landscape top-aligns the books so both complete covers are visible; tall phones measure the decision and remaining book space together, keeping confirmation visible on the first layout without a corrective state update.

Verification: 107 journal desktop tests and 16 inspected Android screenshot comparisons passed. Regressions cover destination-action placement, direct information, keyboard ordering, complete landscape books, and first-layout confirmation visibility. Root ktlintCheck, detekt, checkHomeWorkspaceContract, and the Android debug build passed; the unchanged server-client E2E task reused its passing result. Logs: `/tmp/logdate-merge-inline-tests.log` and `/tmp/logdate-merge-inline-android-final.log`. Evidence: `merge-inline-summary` in the task visualization directory. Both complete Room/navigation merge and dynamic-color visual acceptance tests passed on Pixel Tablet API 35 and Pixel 9 Pro API 36 (2 tests per device), including 200% text and reduced motion. Native images are in `native-tablet` and `native-phone` under that evidence directory; runtime logs are `/tmp/logdate-merge-inline-tablet-runtime.log` and `/tmp/logdate-merge-inline-phone-runtime.log`. No physical devices were used. Flags remain gated and changes remain uncommitted for developer review.


### Visual metadata and journal-owned destination control

Change is now in the Keep journal header, with the accessible label Change destination. Each canonical cover has its original item count and a matching semantic color marker. The decision card uses a compact kept + added = total graphic and a proportional strip; only source items absent from the destination count as additions. Shared items and retained identity are quiet supporting metadata. Cancel and Merge share a row whenever their measured labels and touch targets fit. The quantity display also measures its labels and counts, switching to aligned rows in narrow containers or at large text. The activity retains its existing maximum width, hinge safety, shared transitions and merge animation.

The production layout passed all 111 journal desktop tests. New regressions cover journal-associated counts, overlap arithmetic, destination-action ownership, paired footer actions and 280dp containers at 200% text. Initial-layout confirmation, complete landscape books, keyboard order, changed counts and failure recovery remain covered. Final Android/runtime evidence is recorded below.


Android verification completed all 16 render comparisons, root ktlintCheck, detekt, checkHomeWorkspaceContract, assembleDebug and the unchanged server-client E2E task (UP-TO-DATE). The invocation was cancelled only after reaching a shared Managed Device lease wait: the global active-device counter reported three leases while this run requested one. No shared counters, locks, devices or unrelated processes were changed. Native acceptance and the final desktop capture assertions are rerunning with task-local `ANDROID_AVD_HOME=/tmp/logdate-merge-visual-metadata-avds`, reusing the installed SDK. Logs: `/tmp/logdate-merge-visual-metadata-android.log` and `/tmp/logdate-merge-visual-metadata-isolated-runtime.log`.

The isolated desktop rerun passed all 111 tests, including complete landscape footer bounds. The fresh tablet passed the real merge flow but its visual test correctly failed on a Pixel Launcher ANR; the screenshot and complete report are preserved in `merge-visual-metadata/cold-emulator-failure`. The fresh run was stopped and its inactive temporary AVDs removed. Native acceptance is retrying against the established managed images, one task at a time, with this invocation allowing four global leases so it can claim one slot despite the three pre-existing leases. No shared counters, locks or other processes were changed.


Final acceptance: established Pixel Tablet API 35 and Pixel 9 Pro API 36 each passed both real Room/navigation merge and strict native visual acceptance (2 tests per device, zero failures/errors/skips). Dynamic light/dark, real cover images, 200% text and system reduced motion were inspected in fresh captures. Tablet BUILD SUCCESSFUL in 4m11s; the final phone aggregate BUILD SUCCESSFUL in 2m52s also completed root ktlintCheck, detekt, checkHomeWorkspaceContract, Android debug build, render validation and the unchanged server-client E2E task. Journal desktop suite remains 111 passing tests; Android render matrix remains 16 passing comparisons. Evidence, comparison sheets, preserved failed attempt and validation notes are in `merge-visual-metadata`. Logs: `/tmp/logdate-merge-visual-metadata-tablet-final.log` and `/tmp/logdate-merge-visual-metadata-phone-final.log`. No physical devices were used; flags remain gated and work remains uncommitted for developer review.


### Display-only covers and normal-scale decision

Developer review removed the role labels, original item counts, and Change action from the cover display. The source and kept destination now belong to the decision card; Change sits with the actual destination title, and preserved metadata describes that named journal. The overlap note belongs inside the quantities surface. Removed the detached checkmark assurance. The wide decision card now matches the 560dp visual area, with the normal titleLarge heading and 56dp primary action rather than oversized typography. Consequences and inline actions form its bottom region; narrow, large-text and short-height containers continue to scroll and adapt.

Test-first verification failed the three revised layout/ownership regressions against the previous presentation. After implementation, all 111 journal desktop tests passed, including first-frame phone confirmation, 280dp at 200% text, keyboard traversal, failure recovery and complete landscape actions. Populated responsive renders were inspected in `merge-display-only/layout-comparison.png`. Android and Managed Device verification is recorded below.


### Immersive overlay and before/after review

The next review removes the “permanently” wording and moves the consequence directly below review metadata. Replaced arithmetic and the proportional addition strip with an Items group comparing source, kept journal, and resulting unique sizes. Shared content is counted once. The fixed Back row is gone: a floating 56dp Material 3 Expressive filled tonal button uses the existing medium square/pressed shape pattern; surrounding top space passes touches through. Initial clearance belongs inside scrolling content, so the content moves under this overlay. The cover area remains display-only.

The previous display-only revision passed all 111 desktop tests, 16 Android comparisons, root gates, debug build, and both native tests on tablet/phone (BUILD SUCCESSFUL in 3m29s). This subsequent revision first failed five new/updated regressions against that presentation, including a real top-edge swipe outside the Back button. After implementation all 113 desktop tests passed. Fresh final renders/build/runtime are recorded below; prior native artifacts are not evidence for this changed source.


Final immersive acceptance: all 113 journal desktop tests and 16 Android screenshot comparisons passed. The final aggregate Android invocation completed BUILD SUCCESSFUL in 6m4s, including root ktlintCheck, detekt, checkHomeWorkspaceContract, assembleDebug, unchanged server-client E2E task (UP-TO-DATE) and both real Room/navigation and visual acceptance tests on Pixel Tablet API35 and Pixel9Pro API36 (2 tests/device; no failures/errors/skips). Fresh native cover images, dynamic light/dark, 200% text and reduced motion were inspected. Evidence: `merge-immersive-review`, including responsive/native comparison sheets and `validation.md`. Logs: `/tmp/logdate-merge-immersive-final-tests.log` and `/tmp/logdate-merge-immersive-android.log`. No physical devices used. Flags remain gated and all changes remain uncommitted for developer review.


### Comparable journal identity rows

The developer identified cognitive load across typography, hierarchy, layout and spacing in the identity section. Replaced the mixed source sentence, separate Keep label, bold destination and “Keeps…” sentence with two comparable From/Keep rows. Both names use bodyLarge, the role column and name column align, and Change belongs to the destination row. The preservation note is quiet and follows the destination name alignment. At narrow widths or large text, each field stacks with the complete name width and its action below. This feature component lives in `JournalMergeSelection.kt`; covers, floating navigation, quantities, consequences and merge behavior retain their existing contracts.

A direct selection-component regression failed against the old presentation before implementation. It verifies comparable reading edges, distinct roles and a working destination action associated with its name. Fresh suite and native/render evidence follow below.

The stronger first-frame phone regression also caught the expanded identity section clipping the primary touch target against the bottom gutter. The measured phone composition now lets its cover stage shrink to 280dp, reserving room for the complete decision and actions; wide and large-text layouts keep their existing paths. After this correction all 114 journal desktop tests pass, with zero skipped tests. The refreshed responsive comparison was inspected, including the complete first-frame phone button, narrow/large-text scrolling, RTL and hinge-safe book/tabletop layouts. Android final acceptance is being rerun against this source.

### Editable From/Into containers with local metadata

The developer replaced the previous row presentation with tappable journal containers and clarified the action as moving contents From one journal Into another. Both containers open the existing picker for their own role. The other selection is retained, Back cancels selection changes, separate searches and scroll positions survive switching roles, and selected source/destination IDs survive restart. Completion also removes the active merge route when its original source differs from the newly chosen source. Recovery of an already committed merge permits changing only its destination.

A subsequent review removes the separate Items panel and detached preservation sentence. Small metadata belongs inside the corresponding choice: source count and overlap in From; current and resulting count in Into. The display-only covers, floating Back, offline transaction, and merge animation retain their contracts. The wide decision's minimum height follows the reduced content while keeping inline actions and comparable scale to the cover display.

Test-first source selection, picker cancellation and restart regressions failed before implementation; a navigation regression caught the obsolete activity after changing source. Component tests failed before adding whole-card actions and before moving counts into the choices. Final runtime verification follows below. An intermediate acceptance run completed desktop and render tests, then caught a LongMethod finding introduced in open(); restoration setup was extracted rather than suppressing the check.

The final metadata version passed 120 desktop tests and journal detekt. A further regression at 140% text caught From wrapping before the field layout switched: the role column now scales with font size. The updated-count scroll target groups the Into card with its live explanation, preserving immediate recovery at large text. Final acceptance below uses the source with both this correction and Back blocked during commit/completion animation.

Final card acceptance: all 121 journal desktop tests and 16 Android screenshot comparisons passed. The aggregate finished BUILD SUCCESSFUL in 4m22s, including root ktlintCheck, detekt, checkHomeWorkspaceContract, assembleDebug and the unchanged server-client E2E task (UP-TO-DATE). Pixel Tablet API35 and Pixel9Pro API36 each passed the real Room/navigation and native visual acceptance tests (2 tests/device, zero failures/errors/skips). The real flow exercises source-card selection and canceling destination selection back to review. Fresh native media, dynamic light/dark, 200% text, initial phone button bounds and system reduced motion were inspected; desktop comparison also covers compact, landscape, RTL and book/tabletop constraints. Evidence: `merge-journal-cards`, including native reports and comparison sheets, plus `/tmp/logdate-merge-card-metadata-runtime-final.log`. No physical devices, staging or commit. Flags remain gated for developer review.


### Full-height expressive merge review

Developer review requests a nearly full-height decision panel, action-oriented journal labels, properly contextual item counts, and an MD3 consequence notice. Wide review now measures its minimum decision height from the available safe region instead of a fixed 480dp. The canonical covers retain their bounded display area. Journal cards use “Move contents from” and “Add them to” overlines above titleMedium names, with a single wrapping metadata line and complete item units. Source overlap reads “items already in the other journal”; destination counts distinguish “items currently” and “items when merged.” Consequences immediately follow the choices in a rounded secondaryContainer surface with an outlined Info icon and explicit no-undo copy. Footer controls remain at the bottom and inline when they fit.

Test-first regressions failed on the old panel bounds, role labels and missing info treatment. Final verification: 121 journal desktop tests and 16 Android screenshot comparisons pass; root ktlintCheck, detekt, checkHomeWorkspaceContract, assembleDebug and the unchanged server-client E2E task (UP-TO-DATE) succeed. Pixel Tablet API35 and Pixel9Pro API36 each pass both Room/navigation merge and native visual acceptance tests, zero failures/errors/skips. Populated desktop and fresh native photo-cover comparisons were inspected across compact/landscape, portrait, dark/dynamic colors, RTL, 200% text, book/tabletop, reduced motion, and failure/changed-count recovery. Aggregate BUILD SUCCESSFUL in 2m53s; evidence in merge-expressive-review and /tmp/logdate-merge-expressive-runtime.log. No physical device, staging or commit.
