# LogDate: A Home for Your Memories

This is a Kotlin Multiplatform project targeting Android, iOS, Desktop, and Server platforms. LogDate is a comprehensive journaling application that combines modern UI design with intelligent features and cross-platform synchronization.

## 🏗️ Architecture Overview

LogDate follows clean architecture principles with a modular, feature-based design:

```
LogDate Project
├── 📱 App Targets
│   ├── compose-main (Android, iOS, Desktop)
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
- [`:app:compose-main`](app/compose-main/README.md) - Main cross-platform application
- [`:app:wear`](app/wear/README.md) - Wear OS companion app

### Client Infrastructure
- [`:client:database`](client/database/README.md) - Local data persistence with Room
- [`:client:data`](client/data/README.md) - Data layer with repository pattern
- [`:client:networking`](client/networking/README.md) - HTTP client and network management
- [`:client:sync`](client/sync/README.md) - Cross-device data synchronization
- [`:client:intelligence`](client/intelligence/README.md) - AI and ML features
- [`:client:location`](client/location/README.md) - Location services and context
- [`:client:datastore`](client/datastore/README.md) - Preferences and settings storage

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

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/logdate.git
   cd logdate
   ```

2. **Configure API keys**
   Create a `local.properties` file in the project root:
   ```properties
   metaAppId=<your-meta-app-id>
   apiKeys.googleMaps=<your-google-maps-api-key>
   ```

3. **Firebase Setup**
   - Follow the [Firebase documentation](https://firebase.google.com/docs/android/setup)
   - Add your `google-services.json` to `app/compose-main/`

4. **Open in Android Studio**
   - Open the project in Android Studio
   - Let Gradle sync complete
   - Select your target platform and run

## 🏗️ Build Commands

From the project root, you can use these Gradle tasks:

### Building
```bash
# Build Android app
./gradlew :app:compose-main:assembleDebug

# Build and install Android app
./gradlew :app:compose-main:installDebug

# Run Desktop app
./gradlew :app:compose-main:run
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :client:database:test

# Run tests for specific class
./gradlew :client:domain:test --tests "GetJournalsUseCaseTest"
```

### Code Quality
```bash
# Run linting
./gradlew lint

# Generate documentation
./gradlew dokkaHtmlMultiModule
```

## 📖 Documentation

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
                          ┌─────────────────────┼─────────────────────┐
                          ▼                     ▼                     ▼
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