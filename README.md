# LogDate: A Home for Your Memories

This is a Kotlin Multiplatform project targeting Android, iOS, Desktop, and Server platforms. LogDate is a comprehensive journaling application that combines modern UI design with intelligent features and cross-platform synchronization.

## 🏗️ Architecture Overview

LogDate follows clean architecture principles with a modular, feature-based design:

```
LogDate Project
├── 📱 App Targets
│   ├── android-main (Android entry point)
│   ├── compose-main (shared Android/iOS/Desktop UI)
│   └── wear (Wear OS)
├── 🔧 Client Infrastructure
│   ├── data, database, networking
│   ├── sync, intelligence, location
│   └── ui, theme, util
├── 🎯 Client Features
│   ├── editor, timeline, journal
│   └── rewind, onboarding, core
├── 🌐 Shared Libraries
│   ├── model, config
│   └── activitypub
└── 🖥️ Server
    └── Ktor-based backend
```

## 🚀 Key Features

- **Cross-Platform**: Native apps for Android, iOS, Desktop, and Wear OS
- **Offline-First**: Full functionality without internet connection
- **AI-Powered**: Intelligent content analysis and insights
- **Block-Based Editor**: Flexible content creation with text, images, and media
- **Real-Time Sync**: Seamless synchronization across all devices
- **Privacy-Focused**: Local-first approach with optional cloud sync

## 📋 Module Documentation

Each module contains detailed documentation in its respective `README.md` file:

### App Modules
- [`:app:android-main`](app/android-main/) - Android application wrapper/entry point
- [`:app:compose-main`](app/compose-main/README.md) - Shared Compose Multiplatform app module (Android/iOS/Desktop)
- [`:app:wear`](app/wear/README.md) - Wear OS companion app with Material 3 UI

### Client Infrastructure
- [`:client:database`](client/database/README.md) - Local data persistence with Room
- [`:client:data`](client/data/README.md) - Data layer with repository pattern
- [`:client:networking`](client/networking/README.md) - HTTP client and network management
- [`:client:sync`](client/sync/README.md) - Cross-device data synchronization
- [`:client:intelligence`](client/intelligence/README.md) - AI and ML features
- [`:client:location`](client/location/README.md) - Location services and context
- [`:client:logdate-datastore`](client/datastore/README.md) - Preferences and settings storage

### Client Features
- [`:client:feature:editor`](client/feature/editor/README.md) - Entry creation and editing
- [`:client:feature:timeline`](client/feature/timeline/README.md) - Timeline browsing and search
- [`:client:feature:journal`](client/feature/journal/README.md) - Journal management
- [`:client:feature:rewind`](client/feature/rewind/README.md) - Memory recall features
- [`:client:feature:onboarding`](client/feature/onboarding/README.md) - User onboarding flows
- [`:client:feature:core`](client/feature/core/README.md) - Core app features

### Shared Libraries
- [`:shared:model`](shared/model/README.md) - Shared data models
- [`:shared:config`](shared/config/README.md) - Configuration and constants
- [`:shared:activitypub`](shared/activitypub/README.md) - ActivityPub protocol implementation

### Server
- [`:server`](server/README.md) - Ktor-based backend API

## 🛠️ Technology Stack

### Frontend
- **Kotlin Multiplatform**: Cross-platform business logic
- **Compose Multiplatform**: Modern UI framework
- **Room Database**: Local data persistence
- **Ktor Client**: HTTP networking
- **Koin**: Dependency injection
- **Napier**: Multiplatform logging solution

### Backend
- **Ktor Server**: Web framework
- **PostgreSQL**: Primary database
- **Firebase**: Analytics and cloud services
- **Docker**: Containerization

### AI & Intelligence
- **OpenAI API**: Content analysis and summarization
- **On-device ML**: Privacy-focused processing (planned)

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest stable)
- JDK 17 or higher
- Xcode (for iOS development)
- Docker and Docker Compose (for server development)
- Android Platform-Tools (`adb`) for device/debug workflows on Android.
  - macOS install: `brew install --cask android-platform-tools`
  - See `docs/environment/setup.md` for links to Android and Homebrew references.

### Quick Start with Docker

The fastest way to get the LogDate server running locally:

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/logdate.git
   cd logdate
   ```

2. **Set up local configuration**
   ```bash
   cp local.properties.example local.properties
   # Edit local.properties and fill in required values
   # See server/docs/environment-variables.md for details
   ```

3. **Start the development environment**
   ```bash
   # Quick start (recommended)
   ./scripts/dev-start.sh
   
   # Start with development tools
   ./scripts/dev-start.sh --with-tools
   
   # Or manually with docker-compose
   docker-compose up logdate-postgres logdate-redis -d
   ```

4. **Run the server**
   ```bash
   # Option 1: Run with Docker (full containerized setup)
   docker-compose --profile full-stack up
   
   # Option 2: Run locally (connect to containerized database)  
   export DATABASE_URL="jdbc:postgresql://localhost:15432/logdate"
   ./gradlew :server:run
   ```

5. **Access the application**
   - **Server API**: http://localhost:18080 (Docker) or http://localhost:8080 (local)
   - **Database**: localhost:15432 (logdate/logdate)
   - **Redis**: localhost:16379
   - **PgAdmin**: http://localhost:15050 (admin@logdate.app/admin)
   - **Redis Commander**: http://localhost:18081

### Manual Setup (Alternative)

If you prefer to set up dependencies manually:

1. **Configure API keys and environment**  
   See `docs/environment/setup.md` for required secrets (`OPENAI_API_KEY`, DB/Redis overrides, maps/meta keys) and how to supply them via `local.properties`, environment variables, or CI.

2. **Database Setup**
   Follow the [server database documentation](server/DATABASE.md) for PostgreSQL setup.

3. **Firebase Setup**
   - Follow the [Firebase documentation](https://firebase.google.com/docs/android/setup)
   - Drop the dev `google-services.json` into `app/android-main/`. For Release
     builds, place the production `google-services.json` in
     `app/android-main/src/release/` — the google-services plugin picks the
     release-specific source set automatically.
   - For iOS, see [`docs/reference/ios-build-setup.md`](docs/reference/ios-build-setup.md)
     for the bundle id and `GoogleService-Info-Release.plist` requirements.
   - For staging/production server and app configuration rules, see
     [`docs/runbook/staging-production-configuration.md`](docs/runbook/staging-production-configuration.md).

4. **Open in Android Studio**
   - Open the project in Android Studio
   - Let Gradle sync complete
   - Select your target platform and run

## 🏗️ Build Commands

From the project root, you can use these Gradle tasks:

### Server Development
```bash
# Start with Docker Compose (recommended)
docker-compose up postgres redis -d
./gradlew :server:run

# Full Docker environment
docker-compose --profile full-stack up

# Server only (manual database setup required)
./gradlew :server:run
```

### Client Applications
```bash
# Build Android app
./gradlew :app:android-main:assembleDebug

# Build and install Android app
./gradlew :app:android-main:installDebug

# Build a release app bundle
./gradlew :app:android-main:bundleRelease

# Build and install Wear OS app
./gradlew :app:wear:installDebug

# Run Desktop app
./gradlew :app:compose-main:run
```

### Testing
```bash
# Run all project tests
./gradlew test

# Run single test
./gradlew :module:test --tests "package.TestClass.testMethod"
```

For automated Play Console publishing, promotion-based production releases, and
Android release provenance/versioning, see
[`docs/reference/google-play-publishing.md`](docs/reference/google-play-publishing.md).

### Code Quality
```bash
# Run linting
./gradlew lint

# Generate documentation
./gradlew dokkaHtmlMultiModule
```

### Docker Commands

**Convenience Scripts (Recommended):**
```bash
# Start development environment
./scripts/dev-start.sh

# Start with development tools (PgAdmin, Redis Commander) 
./scripts/dev-start.sh --with-tools

# Check status of all services
./scripts/dev-status.sh

# View logs
./scripts/dev-logs.sh [service-name]

# Stop services (keeps data)
./scripts/dev-stop.sh

# Stop and remove all data
./scripts/dev-stop.sh --clean

# Reset environment (stop, clean, restart)
./scripts/dev-reset.sh
```

**Manual Docker Compose:**
```bash
# Development database only
docker-compose up logdate-postgres logdate-redis -d

# With development tools
docker-compose --profile tools up -d

# Production build
docker build --target production -t logdate-server .

# Clean up (removes containers and volumes)
docker-compose down -v
```

## 📖 Documentation

### Testing
Comprehensive testing guides for all test types:
- [Testing Overview](docs/testing/introduction.md) - Test pyramid and approach
- [Unit Tests](docs/testing/unit-tests.md) - Testing individual components
- [Integration Tests](docs/testing/integration-tests.md) - Database and API testing
- [UI Tests](docs/testing/ui-tests.md) - Compose component testing
- [Screenshot Tests](docs/testing/screenshot-tests.md) - Visual regression testing

### API Documentation
Generate comprehensive API documentation:
```bash
./gradlew dokkaHtmlMultiModule
```
Documentation will be available in the `build/dokka/` directory.

### Module Documentation
Each module contains detailed documentation covering:
- Architecture and design decisions
- Key components and responsibilities
- Usage patterns and examples
- TODOs and future plans

## 🏛️ Architecture Principles

### Clean Architecture
- **Separation of Concerns**: Clear boundaries between UI, domain, and data layers
- **Dependency Inversion**: High-level modules don't depend on low-level modules
- **Single Responsibility**: Each module has a single, well-defined purpose

### Offline-First
- **Local Data Priority**: All features work offline
- **Background Sync**: Automatic synchronization when online
- **Conflict Resolution**: Intelligent handling of data conflicts

### Privacy by Design
- **Local Processing**: Sensitive operations performed on-device
- **Minimal Data Collection**: Only collect necessary information
- **User Control**: Granular privacy settings and data management

### Platform-Specific Optimization
- **Native Feel**: Platform-appropriate UI and interactions
- **Performance**: Optimized for each platform's constraints
- **Integration**: Deep integration with platform services
- **Wear OS**: Material 3 Expressive design for optimal wearable experience

## 🔄 Data Flow

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│     UI      │◄──►│   Domain    │◄──►│    Data     │
│   Layer     │    │   Layer     │    │   Layer     │
└─────────────┘    └─────────────┘    └─────────────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  Compose    │    │ Use Cases   │    │ Repository  │
│   Views     │    │ & Entities  │    │    Impl     │
└─────────────┘    └─────────────┘    └─────────────┘
                                              │
                          ┌───────────────────┼───────────────────┐
                          ▼                   ▼                   ▼
                   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
                   │   Local     │    │   Remote    │    │    Cache    │
                   │  Database   │    │     API     │    │   Layer     │
                   └─────────────┘    └─────────────┘    └─────────────┘
```

## 🤝 Contributing

Please read each module's README for specific contribution guidelines. General principles:

1. Follow the existing code style and architecture patterns
2. Write comprehensive tests for new functionality
3. Update documentation for any API changes
4. Ensure all platforms build and tests pass

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- UI powered by [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- Backend services with [Ktor](https://ktor.io/)
- Local storage with [Room](https://developer.android.com/training/data-storage/room)
