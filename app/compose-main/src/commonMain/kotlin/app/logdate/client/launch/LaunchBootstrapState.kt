package app.logdate.client.launch

/**
 * Ordered launch milestones used to control splash release during Android startup.
 */
enum class LaunchStage(
    val analyticsName: String,
    val label: String,
) {
    ApplicationStarted("application_started", "Application startup"),
    ActivityCreated("activity_created", "Activity created"),
    ComposeAttached("compose_attached", "Compose attached"),
    AppUiLoaded("app_ui_loaded", "App UI loaded"),
    DatabaseStateObserved("database_state_observed", "Database state observed"),
    InitialNavigationReady("initial_navigation_ready", "Initial navigation ready"),
}

data class LaunchStageSnapshot(
    val latestCompletedStage: LaunchStage = LaunchStage.ApplicationStarted,
    val hasLoadedAppUi: Boolean = false,
    val hasWatchdogExpired: Boolean = false,
)

sealed interface LaunchBootstrapState {
    val snapshot: LaunchStageSnapshot

    data class BlockingSplash(
        override val snapshot: LaunchStageSnapshot,
    ) : LaunchBootstrapState

    data class SplashReleased(
        override val snapshot: LaunchStageSnapshot,
    ) : LaunchBootstrapState

    data class Ready(
        override val snapshot: LaunchStageSnapshot,
    ) : LaunchBootstrapState
}

fun LaunchStageSnapshot.markCompleted(stage: LaunchStage): LaunchStageSnapshot =
    copy(
        latestCompletedStage =
            if (stage.ordinal > latestCompletedStage.ordinal) {
                stage
            } else {
                latestCompletedStage
            },
        hasLoadedAppUi = hasLoadedAppUi || stage == LaunchStage.AppUiLoaded,
    )

fun reduceLaunchBootstrapState(snapshot: LaunchStageSnapshot): LaunchBootstrapState =
    when {
        snapshot.hasLoadedAppUi -> LaunchBootstrapState.Ready(snapshot)
        snapshot.hasWatchdogExpired -> LaunchBootstrapState.SplashReleased(snapshot)
        else -> LaunchBootstrapState.BlockingSplash(snapshot)
    }
