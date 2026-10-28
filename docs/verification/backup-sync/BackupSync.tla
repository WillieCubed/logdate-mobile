----------------------------- MODULE BackupSync -----------------------------
EXTENDS Naturals, FiniteSets, TLC
CONSTANTS MaxGeneration, CellularAllowed, BuggyDraftEcho, BuggyCheckingTitle, ReconcileKnownCommit
VARIABLES owner, transfers, generation, acknowledged, committed, phase, active, waiters,
          network, charging, authenticated, keys, serverUp, notice,
          completed, total, falsePending, replayedAcknowledged, replayedCommitted
vars == <<owner, transfers, generation, acknowledged, committed, phase, active, waiters,
          network, charging, authenticated, keys, serverUp, notice,
          completed, total, falsePending, replayedAcknowledged, replayedCommitted>>
NetworkAllowed == network = "wifi" \/ (CellularAllowed /\ network = "cellular")
Ready == authenticated /\ keys /\ NetworkAllowed
Healthy == Ready /\ serverUp
Pending == generation > acknowledged \/ falsePending
Init == /\ generation = 0 /\ acknowledged = 0 /\ committed = 0
        /\ phase = "idle" /\ active = 0 /\ waiters = {1, 2} /\ owner = 0 /\ transfers = {}
        /\ network = "offline" /\ charging = FALSE
        /\ authenticated = FALSE /\ keys = FALSE /\ serverUp = FALSE
        /\ notice = "off" /\ completed = 0 /\ total = 0
        /\ falsePending = FALSE /\ replayedAcknowledged = FALSE /\ replayedCommitted = FALSE
Edit == /\ generation < MaxGeneration
        /\ generation' = generation + 1 /\ waiters' = waiters \cup {1, 2}
        /\ UNCHANGED <<owner, transfers, acknowledged, committed, phase, active, network, charging,
                       authenticated, keys, serverUp, notice, completed, total,
                       falsePending, replayedAcknowledged, replayedCommitted>>
Environment == /\ network' \in {"offline", "cellular", "wifi"}
               /\ charging' \in BOOLEAN /\ authenticated' \in BOOLEAN
               /\ keys' \in BOOLEAN /\ serverUp' \in BOOLEAN
               /\ UNCHANGED <<owner, transfers, generation, acknowledged, committed, phase, active,
                              waiters, notice, completed, total, falsePending,
                              replayedAcknowledged, replayedCommitted>>
\* Periodic work and concurrent manual/network triggers coalesce, not a second worker.
Wake == /\ waiters # {1, 2} /\ waiters' = waiters \cup {1, 2}
        /\ UNCHANGED <<owner, transfers, generation, acknowledged, committed, phase, active, network,
                       charging, authenticated, keys, serverUp, notice, completed,
                       total, falsePending, replayedAcknowledged, replayedCommitted>>
BeginCheck == /\ phase = "idle" /\ owner = 0 /\ waiters # {} /\ Ready
              /\ phase' = "checking"
              /\ \E worker \in waiters: owner' = worker /\ waiters' = waiters \ {worker}
              /\ active' = 0 /\ notice' = "off" /\ completed' = 0 /\ total' = 0
              /\ UNCHANGED <<transfers, generation, acknowledged, committed, network, charging,
                             authenticated, keys, serverUp, falsePending,
                             replayedAcknowledged, replayedCommitted>>
LongCheck == /\ phase = "checking" /\ notice = "off"
             /\ notice' = IF BuggyCheckingTitle THEN "backing" ELSE "checking"
             /\ UNCHANGED <<owner, transfers, generation, acknowledged, committed, phase, active,
                            waiters, network, charging, authenticated, keys,
                            serverUp, completed, total, falsePending,
                            replayedAcknowledged, replayedCommitted>>
\* Authenticated receipt lookup or exact rich-draft equality is metadata, not payload replay.
Receipt == /\ ReconcileKnownCommit /\ phase = "checking" /\ Healthy /\ committed > acknowledged
           /\ acknowledged' = committed /\ falsePending' = FALSE
           /\ UNCHANGED <<owner, transfers, generation, committed, phase, active, waiters, network,
                          charging, authenticated, keys, serverUp, notice,
                          completed, total, replayedAcknowledged, replayedCommitted>>
Upload == /\ phase = "checking" /\ Healthy /\ Pending
          /\ (committed <= acknowledged \/ ~ReconcileKnownCommit)
          /\ phase' = "uploading" /\ active' = generation
          /\ transfers' = transfers \cup {owner}
          /\ notice' = "backing" /\ total' = 1 /\ completed' = 0
          /\ replayedAcknowledged' = (replayedAcknowledged \/ generation <= acknowledged)
          /\ replayedCommitted' = (replayedCommitted \/ generation <= committed)
          /\ UNCHANGED <<owner, generation, acknowledged, committed, waiters, network,
                         charging, authenticated, keys, serverUp, falsePending>>
Commit == /\ phase = "uploading" /\ Healthy
          /\ committed' = active /\ phase' = "awaitingAck"
          /\ UNCHANGED <<owner, transfers, generation, acknowledged, active, waiters, network,
                         charging, authenticated, keys, serverUp, notice,
                         completed, total, falsePending,
                         replayedAcknowledged, replayedCommitted>>
Acknowledge == /\ phase = "awaitingAck" /\ Healthy
               /\ acknowledged' = active /\ completed' = 1
               /\ phase' = "idle" /\ active' = 0 /\ notice' = "off"
               /\ falsePending' = FALSE
               /\ owner' = 0 /\ transfers' = {}
               /\ UNCHANGED <<generation, committed, waiters, network, charging,
                              authenticated, keys, serverUp, total,
                              replayedAcknowledged, replayedCommitted>>
\* Failure/lost response preserves the durable server commit; backoff never deletes pending work.
Fail == /\ phase \in {"checking", "uploading", "awaitingAck"}
        /\ (~Healthy \/ phase = "awaitingAck")
        /\ phase' = "backoff" /\ active' = 0 /\ notice' = "off"
        /\ completed' = 0 /\ total' = 0 /\ waiters' = waiters \cup {1, 2}
        /\ owner' = 0 /\ transfers' = {}
               /\ UNCHANGED <<generation, acknowledged, committed, network, charging,
                       authenticated, keys, serverUp, falsePending,
                       replayedAcknowledged, replayedCommitted>>
BackoffExpires == /\ phase = "backoff" /\ phase' = "idle"
                  /\ UNCHANGED <<owner, transfers, generation, acknowledged, committed, active,
                                 waiters, network, charging, authenticated, keys,
                                 serverUp, notice, completed, total, falsePending,
                                 replayedAcknowledged, replayedCommitted>>
FinishCheck == /\ phase = "checking" /\ Healthy /\ ~Pending
               /\ committed <= acknowledged /\ phase' = "idle" /\ notice' = "off"
               /\ owner' = 0 /\ transfers' = {}
               /\ UNCHANGED <<generation, acknowledged, committed, active,
                              waiters, network, charging, authenticated, keys,
                              serverUp, completed, total, falsePending,
                              replayedAcknowledged, replayedCommitted>>
\* Restart/cancellation may interrupt transient work; generations and receipts survive.
Interrupt == /\ phase # "idle"
             /\ phase' = "idle" /\ active' = 0 /\ notice' = "off"
             /\ completed' = 0 /\ total' = 0 /\ waiters' = waiters \cup {1, 2}
             /\ owner' = 0 /\ transfers' = {}
               /\ UNCHANGED <<generation, acknowledged, committed, network, charging,
                            authenticated, keys, serverUp, falsePending,
                            replayedAcknowledged, replayedCommitted>>
\* Counterexample switch corresponding to DraftDownloader's old precision comparison.
Echo == /\ BuggyDraftEcho /\ phase = "checking" /\ generation = acknowledged
        /\ generation > 0 /\ ~falsePending /\ falsePending' = TRUE
        /\ UNCHANGED <<owner, transfers, generation, acknowledged, committed, phase, active, waiters,
                       network, charging, authenticated, keys, serverUp, notice,
                       completed, total, replayedAcknowledged, replayedCommitted>>
Next == Edit \/ Environment \/ Wake \/ BeginCheck \/ LongCheck \/ Receipt \/ Upload \/
        Commit \/ Acknowledge \/ Fail \/ BackoffExpires \/ FinishCheck \/ Interrupt \/ Echo
Spec == Init /\ [][Next]_vars
TypeOK == /\ generation \in 0..MaxGeneration /\ acknowledged \in 0..generation
          /\ committed \in acknowledged..generation /\ active \in 0..generation
          /\ phase \in {"idle", "checking", "uploading", "awaitingAck", "backoff"}
          /\ notice \in {"off", "checking", "backing"}
          /\ waiters \subseteq {1, 2} /\ owner \in 0..2 /\ transfers \subseteq {1, 2} /\ network \in {"offline", "cellular", "wifi"}
          /\ charging \in BOOLEAN /\ authenticated \in BOOLEAN /\ keys \in BOOLEAN
          /\ serverUp \in BOOLEAN /\ falsePending \in BOOLEAN
          /\ replayedAcknowledged \in BOOLEAN /\ replayedCommitted \in BOOLEAN
          /\ completed \in 0..total /\ total \in 0..1
NoAcknowledgedPayload == ~replayedAcknowledged
NoCommittedPayloadReplay == ~replayedCommitted
OneTransfer == /\ Cardinality(transfers) <= 1
               /\ ((phase \in {"uploading", "awaitingAck"}) <=> active > 0)
               /\ (active > 0 => transfers = {owner} /\ owner # 0)
               /\ ((phase \in {"idle", "backoff"}) <=> owner = 0)
TruthfulNotification == notice = "backing" =>
                        phase \in {"uploading", "awaitingAck"} /\ total > 0
\* SF completion excludes perpetual OS/process preemption; this is an explicit assumption, not an Android guarantee.
FairSpec == Spec /\ SF_vars(BeginCheck) /\ SF_vars(Receipt) /\ SF_vars(Upload) /\
            SF_vars(Commit) /\ SF_vars(Acknowledge) /\ SF_vars(BackoffExpires) /\ WF_vars(Wake)
EventuallyBackedUp == (<>[]Healthy) => (generation > acknowledged ~> generation = acknowledged)
PluggedWifiBacksUp == (<>[](network = "wifi" /\ charging /\ authenticated /\ keys /\ serverUp)) =>
                     (generation > acknowledged ~> generation = acknowledged)
=============================================================================
