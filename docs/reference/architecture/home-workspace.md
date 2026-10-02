# Adaptive Home workspace

`HOME_WORKSPACE_V2` defaults to false. The flag changes presentation only. Data, recording preferences, route keys, and ViewModel ownership remain unchanged.

Home destinations compose independently responsive panels. Feature code supplies content, actions, and selection; `client/ui/workspace` owns the viewport, navigation, surfaces, insets, gutters, fold regions, and panel placement. Direct links and nested details use `WorkspaceRouteFrame`; a hosted panel passes through without acquiring a second shell.

| Primitive | Responsibility |
| --- | --- |
| WorkspaceScaffold | Home navigation, destination header, global status, creation placement, safe insets |
| AdaptiveWorkspaceLayout | Fit panels within actual content bounds and physical hinge-safe regions |
| WorkspacePanel | Surface, content color, attachment shape, actual panel-local bounds |
| PanelHeader | Context, actions, large-text toolbar wrapping |
| PanelGroup | A meaningful subordinate content or selected-item group |
| WorkspaceSupportingSheet | One mounted visual focus with peek, browsing, expanded, and explicit focused content |
| WorkspacePlaybackLayout | Deliberate immersive visual content with adapting controls |
| WorkspaceRouteFrame | Safe framing for nested details and direct navigation outside Home |
| WorkspaceAccountAction | One accessible account target, quiet background-status marker, and on-demand account menu |

Canvas and navigation use `surfaceContainer`. Working panels use `surface/onSurface`. Groups use `surfaceContainerLow`, raised controls use `surfaceContainerHigh`, and selected items use `secondaryContainer/onSecondaryContainer`. Focus does not recolor a panel. Placement determines exposed corners. Ordinary text and journeys do not need containers.

Panel padding and expanded gutters are 16dp, section spacing is 24dp, related spacing is 8dp, and contained-group padding is 12dp. Browse/inspector panels prefer 320dp within 280–360dp; reading focus needs 320dp and constrains text to 720dp. Visual focus needs 360dp and receives remaining space. Insets and navigation are deducted before fitting. RTL changes logical order without mirroring physical hinge coordinates.

Feature roots use supplied local panel bounds for grid columns, toolbars, metadata, and media layout. They must not independently query global windows, split hinges, choose workspace surface colors/shapes, or add outer Scaffold/Surface wrappers.

Locations maintains a single native Maps instance while supporting content changes extent. Observed journey fragments remain separate across gaps. Initial camera fitting uses the identity of the loaded snapshot; passive data updates do not refit. Deliberate selection waits for map readiness. A too-short workspace offers visible controls to enter focused history. Detail Back closes context before changing sheet extent. Native attribution remains visible through map content padding. Missing map configuration/provider failure leaves historical browsing usable.

The supporting composition requires more than fitting the 360dp visual and 280dp browsing minima. Without a separating hinge, side-by-side composition also requires the map to be at least 1.6 times the supporting width and at least 0.85 times its own height. Otherwise the map spans the workspace and the transforming supporting sheet provides browsing below it. These rules use available content bounds after shell offsets; features do not select a layout from device labels. Separating hinges retain the shared safe-region resolver.

Journal grids scale their minimum cover width with local panel width, within 132–260dp, rather than filling large panels with small phone-sized covers. Rewind retains its snapping story-card browsing and annual action; its cover size responds to local panel bounds. Shared framing must not replace a destination's established interaction model with a generic list.

Rewind's individuality comes from each story's artwork, accent, title, quote, dates, and available metadata. Separation is subtle: ordinary covers use 1–2dp elevation, with static separation under reduced motion. Do not add simulated page stacks, perspective, or cover scaling. Photo-free covers use a flowing typographic layout instead of leaving their title at the foot of a large empty colour field. Keep the next-story affordance between covers and preserve intrinsic height for large text.

Run `checkHomeWorkspaceContract` after changing a migrated adapter/root. The Kotlin PSI scope lives in `config/workspace/home-roots.txt`; narrow exceptions name one function, one rule, and a reviewed reason in `home-allowlist.txt`. Extend shared primitives for new visual behavior. Existing immersive Rewind media clipping is an explicit exception.

The populated screenshot catalog in `screenshots/workspace/HomeWorkspaceScreenshots.kt` renders all destinations and immediate details through the shared shell at matching sizes. Inspect phone, compact, landscape, tablet portrait/landscape, book, tabletop, dark, 200% text, RTL, and reduced-motion scenes. Preview map geometry proves layout only. Use Gradle Managed Devices for real Maps and interaction acceptance; never use connected-test tasks or physical devices by default.

Do not enable the flag until the native-map and complete runtime/visual acceptance matrix passes. A missing key or skipped native test is an acceptance gap, not successful map verification.

Workspace search uses `WorkspaceAppBar` and `WorkspaceSearchBar` outside the panels. Search aligns with the leading content edge on phones and wider workspaces; the account action stays at the trailing edge. Navigation and the accessible pane title identify the destination without a competing header label. The default action opens unified search. `WorkspaceSearchScope` lets Your places use that same header field for its existing bounded place-name filter; disposal releases the scope and returning restores the saved query. Collection filters remain panel content; never add another destination search bar inside a collection or viewer. Omit redundant headings such as “Your days,” “Your latest story,” and “Previous stories”; retain meaningful dates and individual story titles.

Navigation uses a 240dp expanded sidebar from 1200dp in standard posture, a rail at intermediate widths, and bottom navigation on phones and in tabletop posture. Book posture keeps compact navigation so it does not consume the hinge-safe regions. Timeline's standalone reading collection has a 560dp maximum; alongside an opened day it uses the shared 280–360dp browse constraints. The composition enforces maximum widths, including safe hinge regions, rather than merely constraining text inside an oversized surface. Additional focus and inspector regions remain selection-driven.

`WorkspaceSectionSwitch` provides a compact dropdown selector inside the supporting panel header. `WorkspaceSupportingSheet` integrates the selector, contextual actions, and accessible expansion controls through shared header slots. Do not allocate a separate day/place mode row above the map or stretch these choices into full-width tabs. `WorkspacePanel(containment = PanelContainment.Collection)` supplies bounds without another painted surface when items already provide their own containers. Journals, Library, Rewind, and the expanded Your places collection use it. Locations > Your day, reading panels, and map-overlaid phone sheets retain their working surface. `workspaceControlBackdrop` gives contained controls no extra background at rest; sticky controls gain the raised surface only while content overlaps them.

The shared header contains search and one account action. Backup work uses a small non-interactive account marker and accessible state description; no independent sync button or permanent backup banner occupies the header. The account menu keeps backup status, relevant recovery actions, journaling streak, and settings reachable. Healthy idle states show no marker. Streak is not app-bar chrome.

The shared app bar always keeps search and account actions in one row, including compact screens at 200% text. Search hints use one line with ellipsis when space is limited; account actions retain their touch target. Do not introduce a large-text or narrow-screen branch that moves account actions below search.

The Home destination is named Places. Its existing location-history route keys and deep links remain stable.
