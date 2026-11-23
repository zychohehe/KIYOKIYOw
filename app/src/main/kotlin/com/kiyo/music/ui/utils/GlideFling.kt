// GlideFling.kt

package com.kiyo.music.ui.utils

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

// how long a flick keeps gliding: 1f is a plain exponential decay, lower glides further and settles slower
// (change this one number to make every list in the app feel snappier or floatier)
const val GlideFriction = 0.8f

// a fling that coasts a little longer than the stock one and eases out evenly instead of stopping abruptly;
// at the end of a list it hands the leftover velocity back, so the overscroll stretch still plays
private class GlideFlingBehavior(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var lastValue = 0f
        var remaining = initialVelocity
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            remaining = velocity
            // the list hit its end: stop gliding and let the overscroll effect take the rest
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return remaining
    }
}

/** the app's scrolling feel, for any lazy list, grid or scroll column: `flingBehavior = rememberGlideFling()` */
@Composable
fun rememberGlideFling(friction: Float = GlideFriction): FlingBehavior =
    remember(friction) {
        GlideFlingBehavior(exponentialDecay(frictionMultiplier = friction, absVelocityThreshold = 0.5f))
    }
