package de.joelneumann.lojinha.ui.utils

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls the lazy list down by approximately 85% of its viewport height.
 */
suspend fun LazyListState.pageDown(animationDurationMillis: Int = 120) {
    val viewportHeight = layoutInfo.viewportSize.height
    val delta = if (viewportHeight > 0) viewportHeight * 0.85f else 500f
    animateScrollBy(delta, tween(durationMillis = animationDurationMillis, easing = LinearOutSlowInEasing))
}

/**
 * Scrolls the lazy list up by approximately 85% of its viewport height.
 */
suspend fun LazyListState.pageUp(animationDurationMillis: Int = 120) {
    val viewportHeight = layoutInfo.viewportSize.height
    val delta = if (viewportHeight > 0) viewportHeight * 0.85f else 500f
    animateScrollBy(-delta, tween(durationMillis = animationDurationMillis, easing = LinearOutSlowInEasing))
}

/**
 * Scrolls the scroll state down by approximately 85% of its viewport height.
 */
suspend fun ScrollState.pageDown(animationDurationMillis: Int = 120) {
    val viewportHeight = viewportSize
    val delta = if (viewportHeight > 0) viewportHeight * 0.85f else 500f
    animateScrollBy(delta, tween(durationMillis = animationDurationMillis, easing = LinearOutSlowInEasing))
}

/**
 * Scrolls the scroll state up by approximately 85% of its viewport height.
 */
suspend fun ScrollState.pageUp(animationDurationMillis: Int = 120) {
    val viewportHeight = viewportSize
    val delta = if (viewportHeight > 0) viewportHeight * 0.85f else 500f
    animateScrollBy(-delta, tween(durationMillis = animationDurationMillis, easing = LinearOutSlowInEasing))
}
