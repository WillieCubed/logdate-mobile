package app.logdate.client.sync.cloud

// Re-export shared sync models to avoid duplicate domain definitions.
typealias ContentUploadRequest = app.logdate.shared.model.sync.ContentUploadRequest
typealias ContentUploadResponse = app.logdate.shared.model.sync.ContentUploadResponse
typealias ContentUpdateRequest = app.logdate.shared.model.sync.ContentUpdateRequest
typealias ContentUpdateResponse = app.logdate.shared.model.sync.ContentUpdateResponse
typealias ContentChangesResponse = app.logdate.shared.model.sync.ContentChangesResponse
typealias ContentChange = app.logdate.shared.model.sync.ContentChange
typealias ContentDeletion = app.logdate.shared.model.sync.ContentDeletion

typealias JournalUploadRequest = app.logdate.shared.model.sync.JournalUploadRequest
typealias JournalUploadResponse = app.logdate.shared.model.sync.JournalUploadResponse
typealias JournalUpdateRequest = app.logdate.shared.model.sync.JournalUpdateRequest
typealias JournalUpdateResponse = app.logdate.shared.model.sync.JournalUpdateResponse
typealias JournalChangesResponse = app.logdate.shared.model.sync.JournalChangesResponse
typealias JournalChange = app.logdate.shared.model.sync.JournalChange
typealias JournalDeletion = app.logdate.shared.model.sync.JournalDeletion

typealias AssociationUploadRequest = app.logdate.shared.model.sync.AssociationUploadRequest
typealias Association = app.logdate.shared.model.sync.Association
typealias AssociationUploadResponse = app.logdate.shared.model.sync.AssociationUploadResponse
typealias AssociationChangesResponse = app.logdate.shared.model.sync.AssociationChangesResponse
typealias AssociationChange = app.logdate.shared.model.sync.AssociationChange
typealias AssociationDeletion = app.logdate.shared.model.sync.AssociationDeletion
typealias AssociationDeleteRequest = app.logdate.shared.model.sync.AssociationDeleteRequest
typealias AssociationDeleteItem = app.logdate.shared.model.sync.AssociationDeleteItem

typealias MediaUploadRequest = app.logdate.shared.model.sync.MediaUploadRequest
typealias MediaUploadResponse = app.logdate.shared.model.sync.MediaUploadResponse
typealias MediaDownloadResponse = app.logdate.shared.model.sync.MediaDownloadResponse
