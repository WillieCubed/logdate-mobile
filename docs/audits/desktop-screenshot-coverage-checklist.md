# Desktop Screenshot Coverage Checklist

This file is the source of truth for the requirement that the desktop screenshot test corpus must contain the complete user-facing screen inventory for the app.

## Completion Rule

Desktop screenshot testing is only complete when every user-reachable screen below has at least one deterministic desktop screenshot scenario in the universal host-tested screenshot lane, unless the screen is explicitly Android-only or otherwise platform-bound.

Allowed exception categories:

- Android-only platform UI that cannot be rendered meaningfully in the shared desktop host lane
- Flows whose visuals are fully represented by a smaller set of lower-level shared screen states and are explicitly marked as such below

## Status Legend

- `[done]` Covered in the universal shared screenshot lane and expected in desktop screenshot baselines
- `[android-only]` Covered by Android screenshot tests today but not yet migrated to the universal desktop lane
- `[todo]` Missing from the universal desktop screenshot lane
- `[n/a]` Not expected in the desktop lane because the UI is platform-specific

## App Shell And Entry

- `[todo]` Base route landing state
- `[todo]` Main app root loading state
- `[done]` Lock screen
- `[android-only]` App entry flow screenshots

## Onboarding

- `[done]` Onboarding start splash
- `[done]` Onboarding start landing
- `[done]` Personal intro name
- `[done]` Personal intro bio
- `[done]` Onboarding overview
- `[todo]` First entry / entry creation onboarding
- `[todo]` Backup sync onboarding
- `[done]` Memories import info
- `[done]` Memory selection populated
- `[done]` Memory selection empty
- `[done]` Memory selection loading
- `[done]` Memory selection error
- `[done]` Cloud account setup compact
- `[done]` Cloud account selected sign-in
- `[done]` Cloud account adaptive large-screen
- `[done]` Onboarding birthday
- `[done]` Onboarding recommendations
- `[done]` Recommendations saving
- `[done]` Onboarding day boundaries connected
- `[done]` Day boundaries permissions needed
- `[done]` Day boundaries checking
- `[done]` Onboarding location
- `[done]` Onboarding notifications
- `[done]` Notifications decision handled
- `[done]` Onboarding completion streak
- `[done]` Onboarding completion final
- `[done]` Onboarding welcome back
- `[todo]` Recovery phrase setup
- `[todo]` Recovery phrase entry

## Home And Timeline

- `[android-only]` Home tab route states
- `[todo]` Home screen canonical overview state
- `[todo]` Timeline route list state
- `[todo]` Timeline route selected-day detail state
- `[todo]` Timeline loading placeholder
- `[todo]` Timeline day detail panel populated
- `[todo]` Timeline detail empty placeholder
- `[todo]` Timeline locations map state
- `[todo]` Timeline notes list state
- `[todo]` Timeline people encountered state
- `[todo]` Timeline events section state
- `[todo]` Timeline TLDR section state
- `[todo]` New entry affordance / empty composer block state

## Location Timeline

- `[android-only]` Location flow route states
- `[android-only]` Location timeline component suites
- `[todo]` Location timeline screen canonical state

## Search

- `[done]` Search idle
- `[done]` Search searching
- `[done]` Search empty
- `[done]` Search results

## Editor

- `[android-only]` Editor route states
- `[android-only]` Editor component suites
- `[todo]` Note editor screen canonical state
- `[todo]` Immersive audio screen

## Journals

- `[android-only]` Journal route states
- `[android-only]` Note viewer route states
- `[android-only]` Journal overview route states
- `[android-only]` Journal component suites
- `[todo]` Journals overview screen canonical state
- `[todo]` No journals empty state
- `[todo]` Journal creation screen
- `[todo]` Journal detail screen
- `[todo]` Journal settings screen
- `[todo]` Note viewer screen
- `[todo]` Share journal screen

## Library

- `[android-only]` Library component suites
- `[todo]` Library overview screen canonical state
- `[todo]` Media detail screen

## Events And Calendar Sync

- `[todo]` Event detail screen
- `[todo]` Events calendar screen
- `[todo]` Calendar sync activity screen
- `[todo]` Calendar sync calendars screen
- `[todo]` Calendar sync settings screen
- `[todo]` Events settings screen

## Rewind

- `[android-only]` Rewind overview route states
- `[android-only]` Rewind story/detail route states
- `[android-only]` Rewind settings route states
- `[done]` Rewind cover card
- `[todo]` Rewind overview screen canonical state
- `[todo]` Rewind detail screen populated state
- `[todo]` Rewind loading screen
- `[todo]` Rewind error screen
- `[todo]` Past rewinds screen
- `[todo]` Rewind settings screen
- `[done]` Day boundary recovery

## Profile And Account

- `[android-only]` Profile route states
- `[android-only]` Cloud account flow route states
- `[todo]` Profile screen
- `[todo]` Cloud account onboarding screen
- `[done]` Cloud account welcome screen
- `[done]` Cloud account sign-in screen
- `[todo]` Display name setup screen
- `[todo]` Username setup screen
- `[todo]` Passkey account creation screen
- `[done]` Passkey account creation final screen
- `[todo]` Passkey authentication screen

## People

- `[todo]` People directory screen
- `[todo]` People inbox screen
- `[todo]` People settings screen
- `[todo]` Person detail screen

## Postcards

- `[todo]` Postcards collection screen
- `[todo]` Canvas editor screen
- `[todo]` Postcard viewer screen

## Settings

- `[android-only]` Settings overview route states
- `[android-only]` Devices route states
- `[android-only]` Settings/account component suites
- `[done]` Settings overview screen
- `[done]` Account settings screen
- `[done]` Privacy settings screen
- `[done]` Data settings screen
- `[done]` Devices screen
- `[n/a]` Android notification settings screen
- `[done]` Memories settings screen
- `[todo]` Voice notes settings screen
- `[todo]` Library settings screen
- `[todo]` Recommendation settings screen
- `[todo]` Streak settings screen
- `[todo]` Rewind settings screen in settings graph
- `[todo]` Timeline settings screen
- `[todo]` Sync settings screen
- `[todo]` Export settings screen
- `[todo]` Advanced settings screen
- `[todo]` Birthday settings screen
- `[todo]` Day boundary settings screen
- `[todo]` Clear data settings screen
- `[todo]` Reset settings screen
- `[todo]` Reset app settings screen
- `[todo]` Location settings screen
- `[todo]` Location advanced screen
- `[todo]` Location interval screen
- `[todo]` Location tracking options screen
- `[todo]` Watch settings screen
- `[todo]` Watch notification settings screen
- `[todo]` Watch sync settings screen
- `[todo]` Watch troubleshooting screen

## Export And Import

- `[android-only]` Export/import flow screenshots
- `[todo]` Export in-progress state in desktop lane
- `[todo]` Export success state in desktop lane
- `[todo]` Export failure state in desktop lane
- `[todo]` Restore in-progress state in desktop lane
- `[todo]` Restore success state in desktop lane
- `[todo]` Restore failure state in desktop lane

## Adaptive And Large-Screen Audits

- `[android-only]` Adaptive audit screenshots
- `[done]` Cloud account large-screen audit states
- `[done]` Memory selection large-screen audit states
- `[todo]` Large-screen audit states for the rest of the major route screens

## Migration Work

- `[todo]` Keep extending `client/screenshot-scenes` until every `[todo]` screen above is represented by at least one shared scene
- `[todo]` Keep `app/compose-main/src/desktopTest/reference` in sync with the full scene catalog
- `[todo]` Retire Android-only screenshot coverage where it becomes redundant with shared desktop coverage
- `[todo]` Preserve Android-only screenshot coverage for platform-bound visuals only
- `[todo]` Validate the full desktop screenshot corpus with host-only tasks
- `[todo]` Validate shared screenshot tests on desktop and iOS simulator with no connected-device tasks
