# LogDate Data Export Specification

This document outlines the data export functionality of LogDate, which allows users to export their journals, notes, drafts, and media in a structured format.

## Overview

The LogDate data export system creates a single `.zip` bundle containing all user data in a standardized format. This export can be used for backup purposes, data migration, or offline analysis.

## Export Structure

The export is structured as follows:

```
logdate-export-YYYY-MM-DD.zip
├── metadata.json
├── journals.json
├── notes.json
├── drafts.json
└── media/
    ├── YYYY/
    │   ├── YYYY-MM-DDTHH-MM-SS.sss+ZZZZ_[id].[extension]
    │   └── ...
    └── ...
```

## File Formats

### metadata.json

Contains metadata about the export:

```json
{
  "version": "1.0",
  "exportDate": "2025-06-02T14:30:00.000+0000",
  "userId": "user-uuid",
  "deviceId": "device-uuid",
  "appVersion": "1.2.3",
  "stats": {
    "journalCount": 10,
    "noteCount": 150,
    "draftCount": 5,
    "mediaCount": 43
  }
}
```

### journals.json

Contains all user journals with their metadata:

```json
{
  "journals": [
    {
      "id": "journal-uuid",
      "title": "My Travel Journal",
      "description": "Adventures from around the world",
      "createdAt": "2025-01-15T09:30:00.000+0000",
      "updatedAt": "2025-06-01T14:20:00.000+0000",
      "coverColor": "#3498db",
      "coverImagePath": "media/2025/2025-01-15T09-30-00.000+0000_cover_journal-uuid.jpg",
      "isArchived": false,
      "isShared": false,
      "tags": ["travel", "adventure"]
    },
    // Additional journals...
  ]
}
```

### notes.json

Contains all notes across all journals:

```json
{
  "notes": [
    {
      "id": "note-uuid",
      "journalId": "journal-uuid",
      "type": "text",
      "content": "Today I visited the Eiffel Tower...",
      "createdAt": "2025-03-10T15:45:00.000+0000",
      "updatedAt": "2025-03-10T16:20:00.000+0000",
      "location": {
        "latitude": 48.8584,
        "longitude": 2.2945,
        "placeName": "Eiffel Tower, Paris, France"
      },
      "tags": ["paris", "landmarks"],
      "people": ["person-uuid-1", "person-uuid-2"]
    },
    {
      "id": "note-uuid-2",
      "journalId": "journal-uuid",
      "type": "image",
      "caption": "Sunset at the Eiffel Tower",
      "mediaPath": "media/2025/2025-03-10T15-50-00.000+0000_note-uuid-2.jpg",
      "createdAt": "2025-03-10T15:50:00.000+0000",
      "updatedAt": "2025-03-10T15:50:00.000+0000",
      "location": {
        "latitude": 48.8584,
        "longitude": 2.2945,
        "placeName": "Eiffel Tower, Paris, France"
      },
      "tags": ["paris", "sunset"],
      "people": ["person-uuid-1"]
    },
    // Additional notes (text, image, voice, video)...
  ]
}
```

### drafts.json

Contains all draft entries that haven't been saved to a journal:

```json
{
  "drafts": [
    {
      "id": "draft-uuid",
      "journalId": "journal-uuid", // Optional, may be null
      "content": "Started writing about my day...",
      "createdAt": "2025-06-01T10:15:00.000+0000",
      "updatedAt": "2025-06-01T10:30:00.000+0000",
      "location": {
        "latitude": 40.7128,
        "longitude": -74.0060,
        "placeName": "New York, NY, USA"
      },
      "mediaReferences": [
        "media/2025/2025-06-01T10-20-00.000+0000_draft-img-1.jpg"
      ]
    },
    // Additional drafts...
  ]
}
```

## Media Organization

Media files are organized by year, with filenames that preserve chronological order:

- Stored in subdirectories by year (`YYYY/`)
- Filenames follow the pattern: `YYYY-MM-DDTHH-MM-SS.sss+ZZZZ_[id].[extension]` where:
  - `YYYY-MM-DDTHH-MM-SS.sss` is the ISO 8601 datetime
  - `+ZZZZ` is the UTC offset (e.g., +0000, -0700)
  - `[id]` is a unique identifier (typically the note UUID)
  - `[extension]` is the file type (jpg, png, mp3, m4a, mp4, etc.)

Examples:
- `media/2025/2025-03-10T15-50-00.000+0000_note-uuid-2.jpg`
- `media/2024/2024-12-25T08-15-30.000-0800_voice-note-uuid.m4a`

## Export Process

1. User initiates export from Settings > Data > Export
2. System compiles all journals, notes, and drafts into JSON files
3. Media files are copied and organized by year
4. All files are compressed into a single ZIP archive
5. User is prompted to save the export to their preferred location

## Import Process

The export format is designed to be importable back into LogDate or compatible applications:

1. User selects the ZIP file to import
2. System validates the file structure and data integrity
3. Data is imported while preserving relationships between journals, notes, and media
4. Conflict resolution is performed if duplicate IDs are detected

## Security Considerations

- The export package does not include user authentication credentials
- All exports are encrypted using AES-256 encryption when the user enables the "Encrypt Export" option
- When encryption is enabled, the user must create a password to secure the export

## Compatibility

This export format is compatible with LogDate version 1.0 and above. Future versions of the application will maintain backward compatibility with this export format.