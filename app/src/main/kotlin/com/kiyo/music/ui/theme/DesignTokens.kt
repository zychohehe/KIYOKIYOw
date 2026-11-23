package com.kiyo.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One place for the app's design tokens, so screens stop inventing their own greys and gaps.
 * "Golden Hour" (Los Angeles) look: warm asphalt-black surfaces, a sunset coral accent, with pink,
 * gold and palm-teal as supporting colours and soft cream text.
 * [KiyoTheme] builds the Material colour scheme from these; new UI should read them from here.
 */
object Tokens {
    object Palette {
        val Background = Color(0xFF0F0D0E)      // warm asphalt black
        val BackgroundAlt = Color(0xFF171415)   // night-time boulevard
        val Surface = Color(0xFF211C1E)         // elevated, slightly warm
        val SurfaceHigh = Color(0xFF2C2629)
        val Outline = Color(0xFF3E3639)

        val Accent = Color(0xFFFF7A4D)          // sunset coral
        val OnAccent = Color(0xFF2B0F06)
        val AccentContainer = Color(0xFF4A2316)
        val OnAccentContainer = Color(0xFFFFDDD0)

        val SunsetPink = Color(0xFFFF4F8B)      // hot pink sky
        val OnSunsetPink = Color(0xFF3A0618)
        val SunsetPinkContainer = Color(0xFF51152F)
        val OnSunsetPinkContainer = Color(0xFFFFD9E4)
        val Gold = Color(0xFFFFC857)            // golden hour
        val PalmTeal = Color(0xFF3DDBC0)        // palm tree / ocean

        val TextPrimary = Color(0xFFFFF6F0)     // warm cream white
        val TextSecondary = Color(0xFFB5A9A4)   // warm soft grey
    }

    /** the LA sunset: gold into coral into pink. Use for highlights, never for big backgrounds. */
    object Gradients {
        val Sunset = Brush.linearGradient(
            listOf(Palette.Gold, Palette.Accent, Palette.SunsetPink)
        )
    }

    object Spacing {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 24.dp
        val Xxl = 32.dp
    }

    object Radius {
        val Small = 10.dp
        val Medium = 16.dp
        val Large = 28.dp
    }

    object Elevation {
        val None = 0.dp
        val Low = 2.dp
        val Medium = 6.dp
    }
}
