package app.logdate.client.domain.di

import okio.FileSystem

actual val domainFileSystem: FileSystem = FileSystem.SYSTEM
