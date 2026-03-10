package app.logdate.server.logdate

import app.logdate.server.sync.SyncRepository

fun SyncRepository.asLogDateCollectionsRepository(): LogDateCollectionsRepository = SyncBackedLogDateCollectionsRepository(this)

fun SyncRepository.asLogDateMediaRepository(): LogDateMediaRepository = SyncBackedLogDateMediaRepository(this)

fun SyncRepository.asLogDateBackupRepository(): LogDateBackupRepository = SyncBackedLogDateBackupRepository(this)
