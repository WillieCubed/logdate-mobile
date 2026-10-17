# Official AT Protocol and Bluesky lexicons

Unmodified copies of published schemas, used only as test fixtures: the schema tools must parse,
check and generate code for every real-world schema, not just LogDate's own.

| Folder | Source | Commit |
|---|---|---|
| `bluesky-social-atproto/com/atproto` | [bluesky-social/atproto](https://github.com/bluesky-social/atproto) `lexicons/com/atproto` | `11ce369ebf05fa533a46080f1b42eded6fa5f71f` |
| `bluesky-social-bsky/app/bsky` | [bluesky-social/bsky](https://github.com/bluesky-social/bsky) `lexicons/app/bsky` | `6b84f6fe51b0769142f85d7f94fa400103b1b285` |

Two `com.atproto` schemas are left out, `identity.getRecommendedDidCredentials` and
`temp.revokeAccountCredentials`, because the repository's pre-commit check rejects file names
containing "Credentials". No other schema references them.

Both repositories are dual-licensed under MIT and Apache 2.0; their license files sit next to each
copy. Don't edit these files. To update them, replace each folder with the same path from a newer
commit and change the commit above.
