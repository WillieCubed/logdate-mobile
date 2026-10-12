# Watch media streaming and prefetch (A-v2 D6)

This implements D6 of the recovered Wear companion plan. D6 is incomplete until the
phone server, watch transport, playback data source, cache, and prefetch worker are
bound and verified together. The redesign flag stays off until the complete
companion experience and populated runtime acceptance pass.

## Required behavior

- Keep watch recordings separate from downloaded phone media. StorageSpaceChecker
  supplies a shared download budget that preserves the recording reserve.
- Eviction invalidates running and queued downloads, including downloads without a
  completed file. Concurrent pulls cannot share temporary files or resurrect an
  evicted note. Preserve unrelated notes and recordings.
- Playing a managed window note must preserve its logical media reference and
  projection ownership. Hydration must not turn playback into a local note edit.
- Resolve only the current verified phone and exchange. Pin streams to the exact
  note version, byte length, and digest; reject mismatched replies and channels.
  Recheck the exchange and note before and after network work. Never fall back to
  arbitrary nearby phones or unscoped legacy downloads in negotiated v2 mode.
- MediaStreamClient opens byte ranges and resumes interrupted transfers at the
  received offset. Abort after 15 seconds without progress, including stalled
  opens/reads; close channels on cancellation, mismatch, failure, and success.
- PhoneMediaServer authenticates the source watch and exact exchange, checks the
  requested note version, and uses ChannelClient.sendFile with an offset. Never
  accept a remote path as a local file path. Split listener handlers to preserve
  the 500-line entrypoint limit.
- Media3 ChannelDataSource supports range reads and seeking. Local recordings and
  verified complete cache entries retain their existing playback behavior.
- WatchMediaPrefetchWorker uses charging and unmetered-network constraints, only
  current managed window audio, and the same budget/invalidation boundary as
  foreground playback. Stop cleanly when the exchange or flag changes.
- Integrate oversized window body transport (>90 KB) with the same exact-peer,
  byte-bound and digest validation; metadata-only snapshots cannot authorize
  destructive reconciliation when required bodies are unavailable.

## Execution order and evidence

1. Reproduce cache eviction, queued-pull, overlapping-pull and format-change races
   in real temporary files. Implement leases before disk-budget waits, serialize
   eviction with publication, and invalidate even when no complete file exists.
2. Test and implement the range client: offsets, reconnects, exact identity,
   truncated/oversized bytes, digest mismatch, 15-second inactivity, cancellation,
   and exchange changes. Keep endpoint contracts explicit for both platforms.
3. Implement the authenticated phone media server and watch ChannelClient adapter;
   add manifest dispatch and dependency bindings. Verify native graph and manifest.
4. Bind Media3 playback and preserve managed note references; prove that playing a
   managed note still permits later phone updates and window eviction, while local
   edits and pending captures survive. Protect stale requests and replaced media.
5. Bind constrained prefetch and large-body retrieval. Verify budget sharing,
   cancellation, resumption, and cleanup across foreground/background downloads.
6. Run affected suites, lint/static checks, phone/Wear builds and emulator-only
   runtime checks. Inspect populated playback UI. Publish focused verified commits
   to main and confirm internal-only phone and wear:internal publisher outcomes.

Paired emulator transport and populated visual acceptance are separate evidence
from unit tests. Physical-device commands remain forbidden; final hardware dogfood
is performed by the developer using the internal testing builds.
