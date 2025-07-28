# LogDate Development Guide

## Build Commands
- Build: `./gradlew :app:compose-main:assembleDebug`
- Run tests: `./gradlew test`
- Run single test: `./gradlew :module:test --tests "package.TestClass.testMethod"`
- Lint: `./gradlew lint`
- Documentation: `./gradlew dokkaHtmlMultiModule`
- Run Android app: `./gradlew :app:compose-main:installDebug`
- Run Desktop app: `./gradlew :app:compose-main:run`

## Test Coverage Commands  
- Generate coverage report: `./gradlew koverHtmlReport`
- Verify coverage meets threshold: `./gradlew koverVerify`
- Run tests with coverage: `./gradlew test koverHtmlReport`
- XML coverage report: `./gradlew koverXmlReport`

## Code Style Guidelines
- **Imports**: Order by kotlin.* > androidx.*/kotlinx.* > app.logdate.*. No wildcards. Avoid fully qualified package names unless there's a name conflict. Use the simple class name whenever possible.
- **Class Members**: Constants/companions > properties > init blocks > methods
- **Naming**: Classes/Interfaces: PascalCase, Functions/Properties: camelCase, Constants: UPPER_SNAKE_CASE
- **Error Handling**: Use try-catch with Napier logging, prefer nullable returns over exceptions
- **Documentation**: Use KDoc style (/** */) for public APIs with @param and @return tags
- **Architecture**: Follow clean architecture with UI, domain, and data layers
- **Data representation**: Use sealed result classes for fetching data/performing in the domain layer, prefer data classes for data models
- **State Management**: Use sealed classes/interfaces for UI state
- **Immutability**: Prefer data classes and immutable properties
- **Asynchronous**: Use coroutines and Flow for reactive programming
- **DI**: Use constructor-based dependency injection with Koin
- **Logging**: Use Napier for all logging - our multiplatform logging solution

## Logging Guidelines
- Use Napier for all logging across all platforms (Android, iOS, Desktop)
- Log levels: `Napier.v()` (verbose), `Napier.d()` (debug), `Napier.i()` (info), `Napier.w()` (warning), `Napier.e()` (error)
- Include meaningful context in log messages
- Use structured logging with exception details when available
- Example: `Napier.e("Failed to save data", exception)`

## Development Notes
- You can use grep and gradlew without asking for permission.
- Avoid redundant comments when making code changes. Don't add comments that simply restate what the code does.
- Keep modifications minimal and focused on addressing the issue at hand.

## KMP Guidelines
- For UUIDs, use kotlin.uuid.Uuid with Uuid.random() for generating new UUIDs (NOT Java's UUID class)
- For cross-platform serialization, use kotlinx.serialization
- For dates and times, use kotlinx.datetime.Instant