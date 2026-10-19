# Lexicons

This folder holds the [Lexicon](https://atproto.com/specs/lexicon) schemas that describe every kind
of record LogDate stores. Each file's path matches its id: `app/logdate/journal/entry.json` is
`app.logdate.journal.entry`.

- `app/logdate/` is LogDate's own schema family. These are drafts: nothing here is published yet.
- `community/` holds copies of the community schemas LogDate reuses, from
  [lexicon-community/lexicon](https://github.com/lexicon-community/lexicon) at commit
  `b4a2b19613105ede6b43e1330d3831842278b073`, under their MIT license (`community/LICENSE`).
  Don't edit them here; update the copy from upstream instead.
- `activity-streams.json` maps every LogDate record type to its Activity Streams 2.0 type, or to
  `null` when a record has no Activity Streams form.
- `published.json` lists the LogDate schemas that are published. Those can only change compatibly;
  the lint refuses anything else.

Check everything with:

```bash
./run lint:lexicons
```

That validates each file with the AT Protocol reference implementation, checks that every
reference resolves and every record type has an Activity Streams mapping, checks the space
declarations and permission sets, and validates the example records in
`tools/lexicon-check/examples/` against their schemas.

What each record is for, and where it's allowed to live, is in
[docs/reference/lexicons.md](../docs/reference/lexicons.md). How to write and change a schema is in
[docs/reference/lexicon-style.md](../docs/reference/lexicon-style.md).
