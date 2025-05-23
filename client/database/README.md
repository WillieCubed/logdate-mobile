# `:client:database`

**Local database implementation using Room with multiplatform support**

## Overview

Provides local data persistence using Room database with support for complex queries, migrations, and cross-platform compatibility. This module stores journals, notes, drafts, user data, and metadata locally with efficient querying and data integrity features.

## Current Database Schema Version

**Version 10** (see `schemas/` directory for migration history)

## Architecture

```
Database Module
├── LogDateDatabase (Room Database)
├── DAOs (Data Access Objects)
├── Entities (Room Entities)
├── Converters (Type Converters)
├── Migrations (Schema Migrations)
└── Platform-specific Implementations
```

## Key Components

### Database Core
- `LogDateDatabase.kt` - Main Room database definition
- `LogDateDatabaseConstructor.kt` - Database instantiation logic
- `DatabaseModule.kt` - Dependency injection configuration

### Data Access Objects (DAOs)
- `JournalDao.kt` - Journal CRUD operations
- `TextNoteDao.kt` - Text note operations
- `ImageNoteDao.kt` - Image note operations
- `JournalNotesDao.kt` - Journal-note relationship operations
- `UserDevicesDao.kt` - User device tracking
- `UserMediaDao.kt` - Media metadata operations
- `LocationHistoryDao.kt` - Location tracking data
- `JournalContentDao.kt` - Journal content operations
- `CachedRewindDao.kt` - Rewind data caching

### Entities
- `JournalEntity.kt` - Journal data structure
- `TextNoteEntity.kt` - Text note data structure
- `ImageNoteEntity.kt` - Image note data structure
- `JournalWithNotes.kt` - Journal with related notes
- `GenericNoteData.kt` - Common note properties
- `UserDeviceEntity.kt` - Device information
- `LocationLogEntity.kt` - Location history
- `MediaImageEntity.kt` - Media file metadata
- `JournalContentEntityLink.kt` - Content relationships

### Type Converters
- `TimestampConverter.kt` - DateTime to/from database conversion
- `UuidConverter.kt` - UUID to/from String conversion

### Migrations
- `AppDatabaseMigrations.kt` - Database schema migration definitions

## Platform Support

- **Android**: Native Room implementation
- **iOS**: Room with SQLite driver
- **JVM/Desktop**: Room with SQLite driver

## Dependencies

### Core Dependencies
- **Room Runtime**: Database framework
- **SQLite Bundled**: Embedded SQLite engine
- **Kotlinx DateTime**: Date/time handling
- **Koin**: Dependency injection
- **Napier**: Logging

### Build Dependencies
- **Room Compiler**: Code generation (KSP)
- **KSP**: Kotlin Symbol Processing

## Database Features

### Data Integrity
- Foreign key constraints
- NOT NULL constraints where appropriate
- Unique constraints for business rules
- Cascading deletes for related data

### Performance Optimizations
- Proper indexing on frequently queried columns
- Efficient query patterns
- Pagination support for large datasets
- Lazy loading for related entities

### Advanced Features
- Full-text search capabilities (planned)
- Complex relationship queries
- Aggregation and statistical queries
- Data archival and cleanup

## Schema Design

### Core Tables
- **journals** - User journals with metadata
- **text_notes** - Text-based journal entries
- **image_notes** - Image-based journal entries
- **journal_content_links** - Many-to-many relationships
- **user_devices** - Device registration and sync
- **location_history** - Location tracking data
- **media_images** - Media file metadata
- **cached_rewinds** - Precomputed rewind data

### Key Relationships
- One-to-many: Journal → Notes
- Many-to-many: Journals ↔ Content via link tables
- One-to-many: User → Devices
- One-to-many: User → Location History

## Usage Patterns

### Repository Pattern
```kotlin
// DAOs are injected into repository implementations
class OfflineFirstJournalRepository(
    private val journalDao: JournalDao,
    private val notesDao: JournalNotesDao
) : JournalRepository
```

### Query Optimization
```kotlin
// Efficient pagination
@Query("SELECT * FROM journals ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
suspend fun getJournalsPaginated(limit: Int, offset: Int): List<JournalEntity>
```

## Migration Strategy

### Versioning
- Each schema change increments the database version
- Migrations are provided for each version increment
- Backward compatibility is maintained where possible

### Migration Testing
- Automated migration tests verify data integrity
- Test migrations with production-like data volumes
- Rollback strategies for failed migrations

## Performance Considerations

### Indexing Strategy
- Primary keys on all entities
- Foreign key indexes for relationships
- Composite indexes for common query patterns
- Full-text search indexes (planned)

### Query Optimization
- Avoid N+1 query problems with joins
- Use `@Relation` for efficient related data loading
- Implement pagination for large result sets
- Cache frequently accessed data

## TODOs

### Core Features
- [ ] Implement full-text search indexing
- [ ] Add database backup/restore functionality
- [ ] Implement database encryption for sensitive data
- [ ] Add database performance monitoring
- [ ] Optimize query performance with additional indexes
- [ ] Implement database integrity checks
- [ ] Add database size management and archival
- [ ] Implement proper cascading deletes

### Advanced Features
- [ ] Add database compression for large entries
- [ ] Implement database partitioning for performance
- [ ] Add database analytics and metrics
- [ ] Implement database replication for redundancy
- [ ] Add database export/import functionality
- [ ] Implement database versioning for sync
- [ ] Add database maintenance routines
- [ ] Implement database security auditing

### Development & Testing
- [ ] Add comprehensive database testing
- [ ] Implement database migration testing automation
- [ ] Add database performance benchmarks
- [ ] Create database debugging tools
- [ ] Add database documentation generation
- [ ] Implement database mock implementations
- [ ] Add database integration tests
- [ ] Create database development utilities

## Security Considerations

- Sensitive data encryption at rest (planned)
- Proper SQL injection prevention via Room
- Access control through repository pattern
- Data sanitization for user inputs
- Audit trails for data modifications

## Maintenance

### Regular Tasks
- Monitor database size growth
- Analyze query performance
- Review and optimize slow queries
- Clean up orphaned data
- Archive old data based on retention policies