# Dependency Injection with Koin

This document outlines LogDate's approach to dependency injection using Koin, covering architecture patterns, module organization, and platform-specific implementations using Kotlin Multiplatform's expect/actual declarations.

## Overview

LogDate uses Koin for dependency injection across all platforms (Android, iOS, Desktop) with a hierarchical, modular architecture that leverages Kotlin Multiplatform's expect/actual pattern for clean platform separation and strict dependency inversion principles.

## Architecture Principles

### 1. Expect/Actual Pattern for Platform Abstraction

We use Kotlin Multiplatform's `expect`/`actual` declarations for platform-specific DI modules:

```kotlin
// commonMain - Define the interface
expect val dataModule: Module

// androidMain - Android implementation with real services
actual val dataModule: Module = module {
    factory<RemoteJournalDataSource> { FirebaseRemoteJournalDataSource() }
    single<LocationHistoryRepository> { OfflineFirstLocationHistoryRepository(get()) }
}

// desktopMain - Desktop implementation with stubs  
actual val dataModule: Module = module {
    factory<RemoteJournalDataSource> { StubJournalDataSource }
    single<LocationHistoryRepository> { StubLocationHistoryRepository() }
}
```

### 2. Strict Dependency Inversion

**All feature modules and use cases MUST depend only on interfaces, never on concrete implementations.** This ensures proper separation of concerns and testability.

```kotlin
// ✅ CORRECT - Features depend on interfaces
viewModel { 
    EntryEditorViewModel(
        fetchTodayNotes = get(),                    // Use case interface
        addNoteUseCase = get(),                     // Use case interface  
        getCurrentUserJournals = get(),             // Use case interface
        journalContentRepository = get(),           // Repository interface
        updateEntryDraft = get()                    // Use case interface
    )
}

// ❌ WRONG - Never inject concrete implementations
viewModel {
    EntryEditorViewModel(
        offlineFirstJournalRepository = get(),      // Concrete implementation
        firebaseDataSource = get()                  // Concrete implementation
    )
}
```

### 3. Layered Module Organization

Our DI structure reflects clean architecture layers with strict dependency flow:

```
Feature Layer (ViewModels, UI)
    ↓ (depends on interfaces only)
Domain Layer (Use Cases, Business Logic)  
    ↓ (depends on repository interfaces)
Data Layer (Repository Implementations)
    ↓ (depends on data source interfaces)
Infrastructure Layer (Concrete Implementations)
```

## Module Structure

### App Module (`/app/compose-main/src/*/di/AppModule.kt`)

**Common Module Definition**
```kotlin
expect val appModule: Module

internal val defaultModules: Set<Module> = setOf(
    coreFeatureModule,
    onboardingFeatureModule,
    editorFeatureModule,
    timelineFeatureModule,
    rewindFeatureModule,
    journalsFeatureModule,
    locationTimelineModule,
)
```

### Feature Modules - Dependency Inversion Requirements

Feature modules define ViewModels and UI-layer dependencies. **Critical: Features must only inject interfaces.**

**Editor Feature Module Example**
```kotlin
val editorFeatureModule: Module = module {
    includes(domainModule)  // Provides use case interfaces
    includes(mediaModule)   // Provides media service interfaces

    viewModel { 
        EntryEditorViewModel(
            // ✅ All dependencies are interfaces/use cases
            fetchTodayNotes = get(),                    // Use case
            addNoteUseCase = get(),                     // Use case
            getCurrentUserJournals = get(),             // Use case
            observeLocationUseCase = get(),             // Use case
            journalContentRepository = get(),           // Repository interface
            updateEntryDraft = get(),                   // Use case
            createEntryDraftUseCase = get(),            // Use case
            deleteEntryDraftUseCase = get(),            // Use case
            fetchEntryDraftUseCase = get(),             // Use case
            fetchMostRecentDraftUseCase = get(),        // Use case
            getAllDraftsUseCase = get()                 // Use case
        )
    }
}
```

**Core Feature Module - Platform-Specific Abstractions**

*Android Implementation:*
```kotlin
actual val coreFeatureModule: Module = module {
    includes(domainModule)  // Provides business logic interfaces
    
    // Platform-specific implementations bound to interfaces
    single<BiometricGatekeeper> { AndroidBiometricGatekeeper() }
    single<ExportLauncher> { AndroidExportLauncher(get()) }
    
    // ViewModels depend only on use cases and interfaces
    viewModel { AppViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get()) }  // Injects use case interface
}
```

*Desktop Implementation:*
```kotlin
actual val coreFeatureModule: Module = module {
    includes(domainModule)
    
    // Stub implementations for desktop
    single<BiometricGatekeeper> { StubBiometricGatekeeper() }
    single<ExportLauncher> { StubExportLauncher() }
    
    viewModel { AppViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel() }  // No location dependency on desktop
}
```

### Domain Module - Pure Business Logic

Domain layer provides use cases that depend only on repository interfaces:

```kotlin
val domainModule: Module = module {
    includes(intelligenceModule)  // Infrastructure services
    includes(locationModule)      // Infrastructure services
    
    // ✅ Use cases depend only on repository interfaces
    factory { AddNoteUseCase(get(), get(), get(), get()) }  // Repository interfaces
    factory { RemoveNoteUseCase(get()) }                    // Repository interface
    factory { GetTimelineUseCase(get(), get()) }            // Repository interfaces
    factory { SummarizeJournalEntriesUseCase(get(), get()) }// Repository interfaces
    
    // Account management
    factory { CreatePasskeyAccountUseCase(get()) }          // Repository interface
    factory { GetCurrentAccountUseCase(get()) }             // Repository interface
    
    // Draft management
    factory { CreateEntryDraftUseCase(get()) }              // Repository interface
    factory { UpdateEntryDraftUseCase(get()) }              // Repository interface
    factory { FetchMostRecentDraftUseCase(get()) }          // Repository interface
    
    // Location services
    factory { GetLocationHistoryUseCase(get()) }            // Repository interface
    single { LocationRetryWorker(get()) }                   // Service interface
}
```

### Data Modules - Implementation Layer

Data modules provide repository implementations. **This is where concrete implementations are bound to interfaces.**

**Android Implementation (Real Services)**
```kotlin
actual val dataModule: Module = module {
    includes(databaseModule, deviceInstanceModule, datastoreModule, syncModule)
    
    // ✅ Bind concrete implementations to interfaces
    factory<RemoteJournalDataSource> { FirebaseRemoteJournalDataSource() }
    single<JournalRepository> { OfflineFirstJournalRepository(get(), get(), get()) }
    single<LocationHistoryRepository> { OfflineFirstLocationHistoryRepository(get()) }
    single<EntryDraftRepository> { OfflineFirstEntryDraftRepository(get(), get()) }
    factory<LocalEntryDraftStore> { AndroidLocalEntryDraftStore(get()) }
    
    // Networking and account services
    single { PasskeyApiClient(httpClient, get(), get()) }
    single<PasskeyAccountRepository> { DefaultPasskeyAccountRepository(get(), get(), get(), get(), get()) }
}
```

**Desktop Implementation (Stub Services)**
```kotlin
actual val dataModule: Module = module {
    includes(deviceInstanceModule, datastoreModule, databaseModule)
    
    // ✅ Bind stub implementations to same interfaces
    factory<RemoteJournalDataSource> { StubJournalDataSource }
    single<JournalRepository> { OfflineFirstJournalRepository(get(), get(), get()) }
    single<LocationHistoryRepository> { OfflineFirstLocationHistoryRepository(get()) }
    single<EntryDraftRepository> { OfflineFirstEntryDraftRepository(get(), get()) }
    factory<LocalEntryDraftStore> { DesktopLocalEntryDraftStore() }
    
    // User services (stubs)
    single<UserDeviceRepository> { StubUserDeviceRepository }
}
```

## Dependency Inversion Best Practices

### 1. Interface-Only Dependencies in Features

**✅ CORRECT Pattern:**
```kotlin
// Features inject use cases and repository interfaces
viewModel { 
    JournalDetailViewModel(
        getJournal = get(),                    // Use case
        updateJournal = get(),                 // Use case
        journalRepository = get(),             // Repository interface
        deleteJournalUseCase = get()           // Use case
    )
}

// Use cases inject repository interfaces
factory { 
    AddNoteUseCase(
        journalNotesRepository = get(),        // Repository interface
        locationRepository = get(),            // Repository interface
        mediaRepository = get(),               // Repository interface
        intelligenceService = get()            // Service interface
    )
}
```

**❌ WRONG Pattern:**
```kotlin
// Never inject concrete implementations in features
viewModel {
    JournalDetailViewModel(
        offlineFirstJournalRepository = get(),     // Concrete implementation
        firebaseDataSource = get(),                // Concrete implementation
        roomDatabase = get()                       // Concrete implementation
    )
}
```

### 2. Repository Interface Definition

Define clear repository interfaces that use cases depend on:

```kotlin
// Domain layer - Interface definition
interface JournalRepository {
    suspend fun getJournals(): List<Journal>
    suspend fun createJournal(journal: Journal): String
    suspend fun updateJournal(journal: Journal)
    suspend fun deleteJournal(id: String)
}

// Use case depends on interface
class CreateJournalUseCase(
    private val journalRepository: JournalRepository  // Interface dependency
) {
    suspend operator fun invoke(name: String): Result<String> {
        return try {
            val journal = Journal(name = name)
            val id = journalRepository.createJournal(journal)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Data layer - Implementation bound to interface
single<JournalRepository> { OfflineFirstJournalRepository(get(), get(), get()) }
```

### 3. Service Interface Abstraction

Platform-specific services must be abstracted behind interfaces:

```kotlin
// Common interface
interface ExportLauncher {
    suspend fun exportData(data: ByteArray, filename: String): Result<Unit>
}

// Platform implementations
class AndroidExportLauncher(
    private val context: Context
) : ExportLauncher {
    override suspend fun exportData(data: ByteArray, filename: String): Result<Unit> {
        // Android-specific export logic
    }
}

class StubExportLauncher : ExportLauncher {
    override suspend fun exportData(data: ByteArray, filename: String): Result<Unit> {
        // Stub implementation for desktop
        return Result.success(Unit)
    }
}

// DI binding
single<ExportLauncher> { AndroidExportLauncher(get()) }  // Android
single<ExportLauncher> { StubExportLauncher() }          // Desktop
```

## DI Initialization

### Android
```kotlin
class LogDateApplication : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
        initializeKoin()
    }
}

internal fun Application.initializeKoin() {
    startKoin {
        androidLogger()
        androidContext(this@initializeKoin)
        workManagerFactory()
        modules(appModule)
    }
}
```

### Desktop
```kotlin
fun main() = application {
    KoinApplication(application = {
        modules(appModule)
    }) {
        Napier.base(DebugAntilog())
        LogDateApplication(rememberApplicationState())
    }
}
```

## Common Patterns

### Scope Management

**`single`** - For stateful services and repositories
```kotlin
single<JournalRepository> { OfflineFirstJournalRepository(get(), get(), get()) }
single<LocationRetryWorker> { LocationRetryWorker(get()) }
```

**`factory`** - For stateless objects and use cases
```kotlin
factory { AddNoteUseCase(get(), get(), get(), get()) }
factory<RemoteJournalDataSource> { FirebaseRemoteJournalDataSource() }
```

**`viewModel`** - Platform-specific ViewModel scoping
```kotlin
viewModel { JournalDetailViewModel(get(), get()) }
```

### Module Composition with `includes()`

```kotlin
val featureModule: Module = module {
    includes(domainModule)  // Provides use case interfaces
    includes(mediaModule)   // Provides media service interfaces
    
    // Feature-specific bindings
    viewModel { /* ViewModels with interface dependencies */ }
}
```

## Testing Support

### Interface-Based Testing

The dependency inversion pattern makes testing straightforward:

```kotlin
val testModule: Module = module {
    // Replace implementations with test doubles
    single<JournalRepository> { FakeJournalRepository() }
    single<LocationRepository> { FakeLocationRepository() }
    factory<RemoteJournalDataSource> { FakeRemoteDataSource() }
}

@Test
fun `test use case with fake repository`() {
    val koinApp = koinApplication {
        modules(testModule)
    }
    
    val useCase = koinApp.koin.get<AddNoteUseCase>()
    // Test with fake repository
}
```

## Architecture Violations to Avoid

### ❌ Direct Implementation Dependencies
```kotlin
// NEVER do this in feature modules
viewModel {
    EditorViewModel(
        offlineFirstRepository = get(),        // Concrete implementation
        firebaseDataSource = get(),            // Concrete implementation
        roomDatabase = get()                   // Concrete implementation
    )
}
```

### ❌ Bypassing Use Cases
```kotlin
// NEVER inject repositories directly into ViewModels
viewModel {
    HomeViewModel(
        journalRepository = get(),             // Should use use cases instead
        locationRepository = get()             // Should use use cases instead
    )
}
```

### ❌ Platform Conditional Logic
```kotlin
// NEVER use conditional injection - use expect/actual instead
single<LocationProvider> {
    if (Platform.isAndroid) AndroidLocationProvider(get()) 
    else StubLocationProvider
}
```

### ✅ Correct Architecture
```kotlin
// Features depend on use cases
viewModel {
    HomeViewModel(
        getTodayNotes = get(),                 // Use case
        hasNotesForToday = get(),              // Use case
        getCurrentJournals = get()             // Use case
    )
}

// Use cases depend on repository interfaces
factory {
    GetTodayNotesUseCase(
        notesRepository = get(),               // Repository interface
        dateProvider = get()                   // Service interface
    )
}

// Repositories bound to interfaces in data layer
single<NotesRepository> { OfflineFirstNotesRepository(get(), get()) }
```

## Best Practices Summary

1. **Features must only depend on use cases and service interfaces**
2. **Use cases must only depend on repository and service interfaces**  
3. **Repository implementations are bound in the data layer**
4. **Use expect/actual for platform-specific implementations**
5. **Never inject concrete implementations in feature or domain layers**
6. **Always define clear interfaces for cross-layer dependencies**
7. **Use factory scope for stateless use cases**
8. **Use single scope for stateful repositories and services**

This architecture ensures proper separation of concerns, excellent testability, and clean platform abstraction while maintaining strict dependency inversion principles.