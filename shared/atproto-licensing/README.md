# Apache 2.0 licensing for `studio.hypertext.atproto`

The Apache 2.0 license here applies **only** to the
`studio.hypertext.atproto:*` Kotlin Multiplatform library — the
`shared/atproto-*` modules in this repository plus the `shared/atproto-bom`
that pins their versions. The rest of the LogDate monorepo (the app
modules, server, and supporting code) is **not** under this license.

When the atproto modules are extracted into their own dedicated repository,
`LICENSE` and `NOTICE` move to that repo's root. They live here in a
namespaced subdirectory in the meantime so license-detection tools don't
infer that the entire monorepo is Apache-licensed.

The publishing convention plugin
(`build-logic/src/main/kotlin/app/logdate/AtprotoPublishedModulePlugin.kt`)
ships these files inside each published artifact's `META-INF/` so the
license travels with every consumer of the published library.
