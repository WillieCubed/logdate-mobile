package app.logdate.server.openapi.schemadocs

internal const val EPOCH_MS = "Milliseconds since the Unix epoch, UTC."
internal const val DEVICE_ID = "Which device made the change. Any stable string; the apps use a per-install ID. Defaults to `unknown`."
internal const val SERVER_VERSION_ASSIGNED =
    "The version the server assigned to this write. Versions only ever increase per account; use them as `since` cursors."
internal const val SYNC_VERSION_IGNORED = "Ignored. The server assigns versions itself; kept for older clients."
internal const val CURSOR_LAST_TIMESTAMP =
    "The highest version in this page, or the `since` you sent if the page is empty. Send it as the next `since`. " +
        "Despite the name it is a version, not a clock time."
internal const val HAS_MORE =
    "`true` when more changes exist beyond this page; call again with `since` = `lastTimestamp`. `changes` and `deletions` " +
        "are cut to `limit` separately but share this one cursor, so when it is `true` a record with a lower version than " +
        "`lastTimestamp` can still be waiting in the list that was cut short. Use a `limit` large enough that pages are " +
        "rarely full, or re-page from your previous cursor rather than from `lastTimestamp` when it is `true`."
internal const val DELETION_VERSION =
    "The version the server assigned to the deletion. Deletions share one version sequence with changes, so compare it " +
        "with a record's `serverVersion` to tell whether the record was re-created afterwards."
internal const val IS_DELETED_ALWAYS_FALSE = "Always `false`. Deletions are delivered in the `deletions` list, not here."
internal const val ENCRYPTED_BY_APPS = "The LogDate apps send this encrypted; the server never reads it."
