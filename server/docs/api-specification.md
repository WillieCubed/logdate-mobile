# LogDate Cloud API Specification

## Base URL
```
LogDate Cloud (Hosted): https://api.logdate.app/api/v1
Self-hosted instance: https://your-domain.com/api/v1
Development: http://localhost:8080/api/v1
```

> **Self-hosting**: LogDate Server is designed to be easily containerizable, allowing users to run their own instance with full data ownership and privacy control.

## Authentication
All API endpoints require authentication via JWT tokens in the Authorization header:
```
Authorization: Bearer <jwt_token>
```

## Response Format
All API responses follow this structure:
```json
{
  "success": true,
  "data": { /* response data */ },
  "message": "Optional message",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Error responses:
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": { /* optional error details */ }
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

## API Endpoints

### 1. Authentication & User Management

#### User Registration
```http
POST /auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123",
  "firstName": "John",
  "lastName": "Doe"
}
```

#### User Login
```http
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

#### Refresh Token
```http
POST /auth/refresh
Content-Type: application/json

{
  "refreshToken": "refresh_token_here"
}
```

#### Logout
```http
POST /auth/logout
Authorization: Bearer <jwt_token>
```

#### Password Reset Request
```http
POST /auth/forgot-password
Content-Type: application/json

{
  "email": "user@example.com"
}
```

#### Complete Password Reset
```http
POST /auth/reset-password
Content-Type: application/json

{
  "token": "reset_token_here",
  "newPassword": "newSecurePassword123"
}
```

#### Get User Profile
```http
GET /user/profile
Authorization: Bearer <jwt_token>
```

#### Update User Profile
```http
PUT /user/profile
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "newemail@example.com"
}
```

#### Update User Preferences
```http
PUT /user/preferences
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "birthday": "1990-01-01T00:00:00Z",
  "isOnboarded": true,
  "securityLevel": "BIOMETRIC",
  "favoriteNotes": ["note-id-1", "note-id-2"]
}
```

#### Delete User Account
```http
DELETE /user/account
Authorization: Bearer <jwt_token>
```

### 2. Journal Management

#### List User's Journals
```http
GET /journals?page=1&limit=20&sort=lastUpdated&order=desc
Authorization: Bearer <jwt_token>
```

#### Create New Journal
```http
POST /journals
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "title": "My Daily Journal",
  "description": "Personal thoughts and experiences",
  "isFavorited": false
}
```

#### Get Specific Journal
```http
GET /journals/{id}
Authorization: Bearer <jwt_token>
```

#### Update Journal
```http
PUT /journals/{id}
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "title": "Updated Journal Title",
  "description": "Updated description",
  "isFavorited": true
}
```

#### Delete Journal
```http
DELETE /journals/{id}
Authorization: Bearer <jwt_token>
```

#### Toggle Favorite Status
```http
POST /journals/{id}/favorite
Authorization: Bearer <jwt_token>
```

#### Get Notes in Journal
```http
GET /journals/{id}/notes?page=1&limit=20&sort=created&order=desc
Authorization: Bearer <jwt_token>
```

### 3. Notes & Content Management

#### List Text Notes
```http
GET /notes/text?page=1&limit=20&sort=created&order=desc
Authorization: Bearer <jwt_token>
```

#### Create Text Note
```http
POST /notes/text
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "content": "This is my note content",
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 100.0
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### Get Specific Text Note
```http
GET /notes/text/{id}
Authorization: Bearer <jwt_token>
```

#### Update Text Note
```http
PUT /notes/text/{id}
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "content": "Updated note content"
}
```

#### Delete Text Note
```http
DELETE /notes/text/{id}
Authorization: Bearer <jwt_token>
```

#### List Image Notes
```http
GET /notes/image?page=1&limit=20&sort=created&order=desc
Authorization: Bearer <jwt_token>
```

#### Create Image Note
```http
POST /notes/image
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "contentUri": "https://storage.googleapis.com/bucket/image.jpg",
  "caption": "Beautiful sunset",
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 100.0
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### Get Specific Image Note
```http
GET /notes/image/{id}
Authorization: Bearer <jwt_token>
```

#### Update Image Note
```http
PUT /notes/image/{id}
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "caption": "Updated caption"
}
```

#### Delete Image Note
```http
DELETE /notes/image/{id}
Authorization: Bearer <jwt_token>
```

#### List All Notes
```http
GET /notes?page=1&limit=20&sort=created&order=desc&type=all
Authorization: Bearer <jwt_token>
```

#### Search Notes
```http
GET /notes/search?q=sunset&page=1&limit=20
Authorization: Bearer <jwt_token>
```

#### Link Note to Journals
```http
POST /notes/{id}/journals
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "journalIds": ["journal-id-1", "journal-id-2"]
}
```

#### Unlink Note from Journal
```http
DELETE /notes/{id}/journals/{journalId}
Authorization: Bearer <jwt_token>
```

### 4. Draft Management

#### List User's Drafts
```http
GET /drafts?page=1&limit=20&sort=lastModifiedAt&order=desc
Authorization: Bearer <jwt_token>
```

#### Create/Save Draft
```http
POST /drafts
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "blocks": [
    {
      "type": "text",
      "id": "block-id-1",
      "timestamp": "2024-01-01T12:00:00Z",
      "content": "Draft text content",
      "locationLat": 37.7749,
      "locationLng": -122.4194,
      "altitude": 100.0
    }
  ],
  "selectedJournalIds": ["journal-id-1"]
}
```

#### Get Specific Draft
```http
GET /drafts/{id}
Authorization: Bearer <jwt_token>
```

#### Update Draft (Auto-save)
```http
PUT /drafts/{id}
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "blocks": [
    {
      "type": "text",
      "id": "block-id-1",
      "timestamp": "2024-01-01T12:00:00Z",
      "content": "Updated draft content",
      "locationLat": 37.7749,
      "locationLng": -122.4194,
      "altitude": 100.0
    }
  ],
  "selectedJournalIds": ["journal-id-1"]
}
```

#### Delete Draft
```http
DELETE /drafts/{id}
Authorization: Bearer <jwt_token>
```

#### Convert Draft to Notes
```http
POST /drafts/{id}/publish
Authorization: Bearer <jwt_token>
```

#### Get Recent Drafts
```http
GET /drafts/recent?limit=5
Authorization: Bearer <jwt_token>
```

### 5. Media Upload & Management

#### Get Signed Upload URL
```http
POST /media/upload
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "fileName": "image.jpg",
  "fileType": "image/jpeg",
  "fileSize": 1024000
}
```

#### Upload Image
```http
POST /media/images
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data

file: [binary data]
caption: "Image caption"
```

#### Upload Video
```http
POST /media/videos
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data

file: [binary data]
caption: "Video caption"
```

#### Upload Audio
```http
POST /media/audio
Authorization: Bearer <jwt_token>
Content-Type: multipart/form-data

file: [binary data]
```

#### Process/Transcode Media
```http
POST /media/process
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "mediaId": "media-id-here",
  "operations": ["resize", "compress", "thumbnail"]
}
```

#### Get Media Metadata
```http
GET /media/{id}
Authorization: Bearer <jwt_token>
```

#### Delete Media
```http
DELETE /media/{id}
Authorization: Bearer <jwt_token>
```

#### Get Signed Download URL
```http
GET /media/{id}/url
Authorization: Bearer <jwt_token>
```

#### Generate Thumbnails
```http
POST /media/{id}/thumbnails
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "sizes": ["150x150", "300x300", "600x600"]
}
```

#### Get Storage Usage Stats
```http
GET /media/usage
Authorization: Bearer <jwt_token>
```

### 6. Real-time Sync & Conflict Resolution

#### Get Sync Status
```http
GET /sync/status
Authorization: Bearer <jwt_token>
```

#### Push Local Changes
```http
POST /sync/changes
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "changes": [
    {
      "entityType": "journal",
      "entityId": "journal-id-1",
      "operation": "update",
      "timestamp": "2024-01-01T12:00:00Z",
      "data": { /* changed data */ }
    }
  ]
}
```

#### Get Changes Since Timestamp
```http
GET /sync/changes?since=2024-01-01T12:00:00Z&page=1&limit=50
Authorization: Bearer <jwt_token>
```

#### Submit Conflict Resolution
```http
POST /sync/resolve-conflict
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "conflictId": "conflict-id-here",
  "resolution": "take_local", // or "take_remote" or "merge"
  "mergedData": { /* optional merged data for manual resolution */ }
}
```

#### Get Pending Conflicts
```http
GET /sync/conflicts
Authorization: Bearer <jwt_token>
```

#### Get Last Sync Timestamp
```http
GET /sync/last-sync
Authorization: Bearer <jwt_token>
```

#### Force Full Sync
```http
POST /sync/full
Authorization: Bearer <jwt_token>
```

#### Sync Health Check
```http
GET /sync/health
Authorization: Bearer <jwt_token>
```

### 7. WebSocket Connections

#### Real-time Sync Connection
```
WS /sync/realtime
Authorization: Bearer <jwt_token>

// Message format:
{
  "type": "sync_update",
  "data": {
    "entityType": "journal",
    "entityId": "journal-id-1",
    "operation": "update",
    "timestamp": "2024-01-01T12:00:00Z"
  }
}
```

#### Collaborative Editing
```
WS /sync/collaborative/{draftId}
Authorization: Bearer <jwt_token>

// Message format:
{
  "type": "text_operation",
  "data": {
    "blockId": "block-id-1",
    "operation": "insert",
    "position": 10,
    "content": "new text",
    "timestamp": "2024-01-01T12:00:00Z"
  }
}
```

### 8. AI/Intelligence Services

#### Summarize Journal Entries
```http
POST /ai/summarize
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "journalIds": ["journal-id-1", "journal-id-2"],
  "dateRange": {
    "start": "2024-01-01T00:00:00Z",
    "end": "2024-01-31T23:59:59Z"
  },
  "summaryType": "weekly" // or "monthly", "custom"
}
```

#### Extract People from Content
```http
POST /ai/extract-people
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "content": "Had lunch with John Smith and Sarah Johnson today.",
  "contextNoteIds": ["note-id-1", "note-id-2"] // for better accuracy
}
```

#### Transcribe Audio to Text
```http
POST /ai/transcribe
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "audioUrl": "https://storage.googleapis.com/bucket/audio.mp3",
  "language": "en-US"
}
```

#### Analyze Content Sentiment
```http
POST /ai/analyze-sentiment
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "content": "I had an amazing day today! Everything went perfectly.",
  "noteIds": ["note-id-1", "note-id-2"] // for batch analysis
}
```

#### Suggest Tags for Content
```http
POST /ai/suggest-tags
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "content": "Went hiking in Yosemite National Park. Saw beautiful waterfalls.",
  "existingTags": ["nature", "hiking"] // user's existing tags for context
}
```

#### Generate Insights from Data
```http
POST /ai/insights
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "analysisType": "mood_trends", // or "activity_patterns", "social_connections"
  "dateRange": {
    "start": "2024-01-01T00:00:00Z",
    "end": "2024-01-31T23:59:59Z"
  }
}
```

#### Auto-complete Text
```http
POST /ai/complete-text
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "context": "Today I went to the",
  "maxLength": 50,
  "temperature": 0.7
}
```

#### Writing Suggestions
```http
POST /ai/improve-writing
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "content": "I went to store and buy some food for dinner tonight.",
  "improvementType": "grammar" // or "clarity", "style"
}
```

#### Generate Writing Prompts
```http
POST /ai/generate-prompts
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "category": "reflection", // or "gratitude", "goals", "memories"
  "mood": "contemplative",
  "count": 5
}
```

### 9. Device Management

#### List User's Devices
```http
GET /devices
Authorization: Bearer <jwt_token>
```

#### Register New Device
```http
POST /devices/register
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "label": "John's iPhone",
  "operatingSystem": "iOS",
  "version": "17.0",
  "model": "iPhone 15",
  "type": "mobile"
}
```

#### Get Device Details
```http
GET /devices/{id}
Authorization: Bearer <jwt_token>
```

#### Update Device Info
```http
PUT /devices/{id}
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "label": "Updated Device Name",
  "version": "17.1"
}
```

#### Unregister Device
```http
DELETE /devices/{id}
Authorization: Bearer <jwt_token>
```

#### Revoke Device Access
```http
POST /devices/{id}/revoke
Authorization: Bearer <jwt_token>
```

#### Get Current Device Info
```http
GET /devices/current
Authorization: Bearer <jwt_token>
```

### 10. Rewind/Memory Summaries

#### List User's Rewinds
```http
GET /rewinds?page=1&limit=20&sort=date&order=desc
Authorization: Bearer <jwt_token>
```

#### Get Specific Rewind
```http
GET /rewinds/{id}
Authorization: Bearer <jwt_token>
```

#### Generate New Rewind
```http
POST /rewinds/generate
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "dateRange": {
    "start": "2024-01-01T00:00:00Z",
    "end": "2024-01-07T23:59:59Z"
  },
  "type": "weekly", // or "monthly", "yearly"
  "includeImages": true,
  "includeMoodAnalysis": true
}
```

#### Delete Rewind
```http
DELETE /rewinds/{id}
Authorization: Bearer <jwt_token>
```

#### Get Current Week's Rewind
```http
GET /rewinds/current-week
Authorization: Bearer <jwt_token>
```

#### Get Rewinds for Specific Year
```http
GET /rewinds/year/{year}
Authorization: Bearer <jwt_token>
```

### 11. Timeline & Location

#### Get User's Timeline
```http
GET /timeline?date=2024-01-01&range=week&includeLocation=true
Authorization: Bearer <jwt_token>
```

#### Get Specific Day's Timeline
```http
GET /timeline/day/2024-01-01
Authorization: Bearer <jwt_token>
```

#### Log Location Data
```http
POST /location/logs
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "latitude": 37.7749,
  "longitude": -122.4194,
  "altitude": 100.0,
  "confidence": 0.95,
  "timestamp": "2024-01-01T12:00:00Z",
  "isGenuine": true
}
```

#### Get Location History
```http
GET /location/history?start=2024-01-01T00:00:00Z&end=2024-01-31T23:59:59Z
Authorization: Bearer <jwt_token>
```

#### Get User's Places
```http
GET /places
Authorization: Bearer <jwt_token>
```

#### Add New Place
```http
POST /places
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "name": "Home",
  "address": "123 Main St, San Francisco, CA",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "category": "residence"
}
```

## Error Codes

| Code | Description |
|------|-------------|
| `INVALID_CREDENTIALS` | Invalid email or password |
| `TOKEN_EXPIRED` | JWT token has expired |
| `INSUFFICIENT_PERMISSIONS` | User lacks required permissions |
| `RESOURCE_NOT_FOUND` | Requested resource doesn't exist |
| `VALIDATION_ERROR` | Request validation failed |
| `RATE_LIMIT_EXCEEDED` | Too many requests |
| `STORAGE_LIMIT_EXCEEDED` | User's storage quota exceeded |
| `SYNC_CONFLICT` | Data sync conflict detected |
| `MEDIA_PROCESSING_FAILED` | Media processing failed |
| `AI_SERVICE_UNAVAILABLE` | AI service temporarily unavailable |

## Rate Limiting

API endpoints are rate limited as follows:
- Authentication endpoints: 5 requests per minute
- General API endpoints: 100 requests per minute
- Media upload endpoints: 10 requests per minute
- AI/Intelligence endpoints: 20 requests per minute

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1704067200
```