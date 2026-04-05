package app.logdate.feature.onboarding.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

private const val ONBOARDING_FADE_DURATION_MILLIS = 220
private const val ONBOARDING_SLIDE_DURATION_MILLIS = 260

internal fun onboardingFadeTransition(): ContentTransform =
    fadeIn(
        animationSpec = tween(durationMillis = ONBOARDING_FADE_DURATION_MILLIS),
    ) togetherWith
        fadeOut(
            animationSpec = tween(durationMillis = ONBOARDING_FADE_DURATION_MILLIS),
        )

internal fun onboardingSlideTransition(forward: Boolean = true): ContentTransform =
    slideInHorizontally(
        initialOffsetX = { fullWidth ->
            if (forward) {
                fullWidth / 6
            } else {
                -fullWidth / 6
            }
        },
        animationSpec =
            tween(
                durationMillis = ONBOARDING_SLIDE_DURATION_MILLIS,
                easing = FastOutSlowInEasing,
            ),
    ) +
        fadeIn(
            animationSpec = tween(durationMillis = ONBOARDING_FADE_DURATION_MILLIS),
        ) togetherWith
        slideOutHorizontally(
            targetOffsetX = { fullWidth ->
                if (forward) {
                    -fullWidth / 6
                } else {
                    fullWidth / 6
                }
            },
            animationSpec =
                tween(
                    durationMillis = ONBOARDING_SLIDE_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
        ) +
        fadeOut(
            animationSpec = tween(durationMillis = ONBOARDING_FADE_DURATION_MILLIS),
        )
