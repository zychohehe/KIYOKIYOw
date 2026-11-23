// the first onboarding page: the kiyo girl showing off her phone over drifting welcomes, a tap anywhere moves on

package com.kiyo.music.ui.screens.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kiyo.music.LocalAppContentObscured
import com.kiyo.music.R
import com.kiyo.music.ui.component.damageBoundary
import com.kiyo.music.ui.screens.setup.onboarding.OnbTitleTracking
import com.kiyo.music.ui.screens.setup.welcome.GirlArt
import com.kiyo.music.ui.screens.setup.welcome.GirlMotion
import com.kiyo.music.ui.screens.setup.welcome.WelcomeColors
import com.kiyo.music.ui.screens.setup.welcome.WelcomeDots
import com.kiyo.music.ui.screens.setup.welcome.WelcomeGirl
import com.kiyo.music.ui.screens.setup.welcome.WelcomeWords
import com.kiyo.music.ui.utils.AppMotion
import com.kiyo.music.ui.utils.HighRefreshWhile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min

private val GlowBlue = Color(0xFF6C8CA8)
private val GlowViolet = Color(0xFF8A6CFF)

@Composable
fun WelcomeStep(isHiding: Boolean, onNext: () -> Unit) {
    val clock = rememberWelcomeClock()
    val enter = remember { Animatable(0f) }
    // a soft spring instead of a fixed tween: it eases in and settles without a hard stop
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)) }
    // logo pops in first, the closing line types itself out last
    val haptic = LocalHapticFeedback.current
    val logoIn = remember { Animatable(0f) }
    val finale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        logoIn.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(1500)
        finale.animateTo(1f, tween(1500, easing = LinearEasing))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val leave by animateFloatAsState(
        targetValue = if (isHiding) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "welcomeLeave",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomeColors.Page)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.next),
                role = Role.Button,
                onClick = onNext,
            )
            // everything here moves every frame; bounded, it never makes the app behind the sheet repaint
            .damageBoundary(),
    ) {
        val layout = remember(maxWidth, maxHeight) { welcomeLayout(maxWidth, maxHeight) }
        val u = layout.unit
        // petunjuk "ketuk" berdenyut pelan
        val hintPulse by rememberInfiniteTransition(label = "welcomeHint").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "welcomeHintAlpha",
        )

        // a faint violet glow behind the title, so the name sits on light instead of flat black
        Box(
            modifier = Modifier
                .offset(x = layout.titleStart - (90f * u).dp, y = layout.titleTop - (50f * u).dp)
                .size((360f * u).dp, (300f * u).dp)
                .graphicsLayer { alpha = enter.value * (1f - leave) }
                .drawBehind {
                    // the glow drifts between steel blue and violet
                    val k = 0.5f + 0.5f * kotlin.math.sin(clock.seconds.floatValue * 0.6f)
                    val c = androidx.compose.ui.graphics.lerp(GlowBlue, GlowViolet, k)
                    drawRect(
                        Brush.radialGradient(listOf(c.copy(alpha = 0.18f), c.copy(alpha = 0.08f), Color.Transparent)),
                    )
                },
        )

        WelcomeDots(layout.dots)
        WelcomeWords(clock.seconds, layout.wordsSize, layout.wordsTop)

        WelcomeGirl(
            motion = clock.girl,
            seconds = clock.seconds,
            modifier = Modifier
                .offset(x = layout.girlLeft, y = layout.girlTop)
                .size(layout.girlWidth, layout.girlHeight)
                .graphicsLayer {
                    translationY = leave * 56.dp.toPx()
                    alpha = 1f - leave
                },
        )

        // the bottom of the art melts into the page under the closing line
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(layout.fadeHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to WelcomeColors.Page.copy(alpha = 0.86f),
                        1f to WelcomeColors.Page,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .padding(start = layout.titleStart, top = layout.titleTop)
                .graphicsLayer {
                    alpha = enter.value * (1f - leave)
                    translationY = (1f - enter.value) * 18.dp.toPx()
                },
        ) {
            // logo baru: lencana play + gelombang suara, muncul dengan pegas
            Box(
                modifier = Modifier
                    .padding(bottom = (14f * u).dp)
                    .size((72f * u).dp)
                    .graphicsLayer {
                        val k = 0.6f + 0.4f * logoIn.value
                        scaleX = k; scaleY = k; alpha = logoIn.value.coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(22f * u))
                    .background(Brush.linearGradient(listOf(Color(0xFF3A4A8C), Color(0xFF1B1F3B))))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22f * u)),
                contentAlignment = Alignment.Center,
            ) {
                com.kiyo.music.ui.screens.setup.welcome.KiyoLogoMark(
                    modifier = Modifier.size((50f * u).dp),
                    seconds = { clock.seconds.floatValue },
                )
            }
            // lencana kecil bergaya kaca di atas sapaan
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                    .padding(horizontal = (12f * u).dp, vertical = (5f * u).dp),
            ) {
                Text(
                    text = stringResource(R.string.welcome_to),
                    color = Color(0xFFE6E6E6),
                    fontSize = (15f * u).sp,
                    lineHeight = (18f * u).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
            }
            Spacer(Modifier.height((8f * u).dp))
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFE4E9F0), Color(0xFFA9BCD0)),
                    ),
                    fontSize = (64f * u).sp,
                    lineHeight = (66f * u).sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = OnbTitleTracking,
                ),
            )
            // garis aksen di bawah nama aplikasi
            Box(
                modifier = Modifier
                    .padding(top = (6f * u).dp)
                    .width((56f * u).dp)
                    .height((4f * u).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFA9BCD0), Color(0xFF6C8CA8)))),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = layout.titleStart, end = 24.dp, bottom = 30.dp)
                .graphicsLayer { alpha = enter.value * (1f - leave) },
        ) {
            val full = stringResource(R.string.welcome_finale)
            // only recompose when a new letter appears, not on every frame of the animation
            val shownChars by remember(full) { derivedStateOf { (full.length * finale.value).toInt() } }
            val startReady by remember { derivedStateOf { finale.value >= 0.95f } }
            Text(
                text = full.take(shownChars),
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFA9BCD0), Color(0xFF8FA6FF))),
                    fontSize = (28f * u).sp,
                    lineHeight = (32f * u).sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = OnbTitleTracking,
                ),
                modifier = Modifier.height((40f * u).dp),
            )
            // tombol mulai: muncul setelah teks selesai diketik
            Box(
                modifier = Modifier
                    .padding(vertical = (10f * u).dp)
                    .graphicsLayer {
                        val a = ((finale.value - 0.9f) / 0.1f).coerceIn(0f, 1f)
                        alpha = a
                        translationY = (1f - a) * 12.dp.toPx()
                    }
                    // a soft glow behind the start pill that breathes with the hint, so the way forward reads first
                    .drawBehind {
                        val r = size.width * 0.75f
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(GlowViolet.copy(alpha = 0.42f * hintPulse), Color.Transparent),
                                center = center,
                                radius = r,
                            ),
                            radius = r,
                            center = center,
                        )
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF4F5BD5), Color(0xFF8FA6FF))))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = startReady,
                        role = Role.Button,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNext()
                    }
                    .padding(horizontal = (28f * u).dp, vertical = (12f * u).dp),
            ) {
                Text(
                    text = stringResource(R.string.welcome_start),
                    color = Color.White,
                    fontSize = (17f * u).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                )
            }
            Text(
                text = stringResource(R.string.welcome_lets_listen),
                color = Color(0xFFD0D0D0),
                fontSize = (23f * u).sp,
                lineHeight = (27f * u).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = OnbTitleTracking,
            )
            Text(
                text = stringResource(R.string.welcome_tap_anywhere),
                color = Color(0xFF8C8C8C).copy(alpha = hintPulse),
                fontSize = (14f * u).sp,
                lineHeight = (18f * u).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// the page's one clock: each frame it moves the girl, then the rings and words read the same time
private class WelcomeClock {
    val seconds = mutableFloatStateOf(0f)
    val girl = GirlMotion()
    var running by mutableStateOf(false)
        private set

    suspend fun run() {
        running = true
        try {
            var last = -1L
            var t = seconds.floatValue
            while (true) {
                withFrameNanos { now ->
                    // a stall only counts as a short step, so nothing leaps forward after a hitch
                    if (last >= 0L) t += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    girl.update(t)
                    seconds.floatValue = t
                }
            }
        } finally {
            running = false
        }
    }
}

// runs only while someone can see it: not under the player, not in the background, not with animations off
@Composable
private fun rememberWelcomeClock(): WelcomeClock {
    val clock = remember { WelcomeClock() }
    val obscured = LocalAppContentObscured.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, obscured, lifecycle) {
        combine(snapshotFlow { !obscured.value && AppMotion.enabled }, lifecycle.currentStateFlow) { free, state ->
            free && state.isAtLeast(Lifecycle.State.STARTED)
        }
            .distinctUntilChanged()
            .collectLatest { visible -> if (visible) clock.run() }
    }
    // android 15+ drops draw-only animation to 60hz unless something asks, and this should glide at 120
    HighRefreshWhile { clock.running }
    return clock
}

// where everything sits, from one size unit so a tablet gets the same picture scaled up
private class WelcomeLayout(
    val unit: Float,
    val titleStart: Dp,
    val titleTop: Dp,
    val girlLeft: Dp,
    val girlTop: Dp,
    val girlWidth: Dp,
    val girlHeight: Dp,
    val wordsSize: Dp,
    val wordsTop: Dp,
    val dots: Dp,
    val fadeHeight: Dp,
)

// tall windows get the phone design; wide ones (tablets, foldables, landscape) put the girl on the right
private fun welcomeLayout(width: Dp, height: Dp): WelcomeLayout {
    val w = width.value
    val h = height.value
    val shape = GirlArt.FrameWidth / GirlArt.FrameHeight
    if (w / h < 0.9f) {
        val u = min(w / 393f, h / 820f).coerceIn(0.8f, 1.9f)
        val titleTop = max(64f, 76f * u)
        // big like a wallpaper: she spans the width and is cropped at the hips, under the title
        val girlTop = max(titleTop + 126f * u, h * 0.27f)
        val girlHeight = min(w * 1.08f / shape, (h - girlTop) * 1.12f)
        val girlWidth = girlHeight * shape
        return WelcomeLayout(
            unit = u,
            titleStart = max(24f, 36f * u).dp,
            titleTop = titleTop.dp,
            girlLeft = (w * 0.985f - girlWidth * GirlArt.FingertipsAcross).dp,
            girlTop = girlTop.dp,
            girlWidth = girlWidth.dp,
            girlHeight = girlHeight.dp,
            wordsSize = (128f * u).dp,
            wordsTop = (h * 0.235f).dp,
            dots = (17.5f * u).dp,
            fadeHeight = (h * 0.3f).dp,
        )
    }
    val u = min(w / 900f, h / 520f).coerceIn(0.7f, 1.8f)
    val girlWidth = min(h * 1.16f * shape, w * 0.62f)
    return WelcomeLayout(
        unit = u,
        titleStart = (48f * u).dp,
        titleTop = max(56f, h * 0.16f).dp,
        girlLeft = (w * 0.975f - girlWidth * GirlArt.FingertipsAcross).coerceAtLeast(w * 0.4f).dp,
        girlTop = max(8f, h * 0.02f).dp,
        girlWidth = girlWidth.dp,
        girlHeight = (girlWidth / shape).dp,
        wordsSize = (128f * u).dp,
        wordsTop = (h * 0.06f).dp,
        dots = (17.5f * u).dp,
        fadeHeight = (h * 0.26f).dp,
    )
}
