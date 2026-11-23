package com.kiyo.music.ui.screens.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.kiyo.music.R
import com.kiyo.music.constants.DisableBlurKey
import com.kiyo.music.ui.screens.setup.onboarding.BlurCapability
import com.kiyo.music.ui.screens.setup.onboarding.CardInfo
import com.kiyo.music.ui.screens.setup.onboarding.DoneStep
import com.kiyo.music.ui.screens.setup.onboarding.HelloCardStep
import com.kiyo.music.ui.screens.setup.onboarding.Onb
import com.kiyo.music.utils.rememberPreference
import com.kiyo.music.viewmodels.OnboardingViewModel

// the onboarding is three screens: welcome, the card that says hello, and "kamu siap berangkat"
private enum class Step { Welcome, Hello, Done }

/** pages that play once and move on by themselves, back never returns to them */
private val AutoSteps = setOf(Step.Hello, Step.Done)

/** the onboarding flow: welcome, hello card, all set. no import screens any more */
@Composable
fun SetupWizardScreen(
    onComplete: (String, Uri?) -> Unit,
    onDrag: (Float) -> Unit,
    onDragRelease: () -> Unit,
) {
    val vm: OnboardingViewModel = hiltViewModel()

    // every run of onboarding starts clean
    LaunchedEffect(Unit) { vm.reset() }

    val history = remember { mutableStateListOf(Step.Welcome) }
    var forward by remember { mutableStateOf(true) }
    val step = history.last()

    fun go(next: Step, replaceCurrent: Boolean = false) {
        forward = true
        if (replaceCurrent && history.size > 1) history.removeAt(history.lastIndex)
        history.add(next)
    }

    fun back() {
        if (history.size <= 1 || step in AutoSteps) return
        forward = false
        history.removeAt(history.lastIndex)
    }

    var isLeavingWelcome by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // the blur check runs on the hello card; a phone that struggles gets blur turned off, and settings can turn it back on
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)

    // no username is asked for any more: the app's own name is used
    val defaultName = stringResource(R.string.app_name)
    val card = CardInfo(
        username = defaultName,
        photo = null,
        cardNumber = vm.cardNumber,
        joinedText = stringResource(R.string.profile_joined_at, vm.joinedDateText),
    )

    BackHandler { back() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Onb.Page),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val auto = targetState in AutoSteps || initialState in AutoSteps
                when {
                    auto -> (fadeIn(tween(420)) + scaleIn(tween(520), initialScale = 0.96f)) togetherWith fadeOut(tween(260))
                    forward -> slideInHorizontally(tween(380)) { it / 3 } + fadeIn(tween(320)) togetherWith
                        slideOutHorizontally(tween(380)) { -it / 4 } + fadeOut(tween(220))
                    else -> slideInHorizontally(tween(380)) { -it / 3 } + fadeIn(tween(320)) togetherWith
                        slideOutHorizontally(tween(380)) { it / 4 } + fadeOut(tween(220))
                }.using(SizeTransform(clip = false))
            },
            label = "onboardingStep",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                // the welcome page runs to the top of the sheet under the handle, a tap anywhere on it moves on
                Step.Welcome -> WelcomeStep(
                    isHiding = isLeavingWelcome,
                    onNext = {
                        // a second tap while she's waving goodbye would skip a page
                        if (!isLeavingWelcome) {
                            isLeavingWelcome = true
                            scope.launch {
                                delay(400)
                                go(Step.Hello)
                                isLeavingWelcome = false
                            }
                        }
                    },
                )

                Step.Hello -> HelloCardStep(
                    card = card,
                    onProbe = { probe ->
                        vm.onBlurProbe(probe)
                        if (vm.blur?.verdict == BlurCapability.Verdict.Laggy) onDisableBlurChange(true)
                    },
                    onFinished = { go(Step.Done, replaceCurrent = true) },
                )

                Step.Done -> DoneStep(
                    card = card,
                    onFinished = {
                        vm.saveCard()
                        vm.closeProvider()
                        onComplete(defaultName, null)
                    },
                )
            }
        }

        // the onboarding sheet's own handle; dragging it rubber-bands the whole sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            onDrag(amount)
                        },
                        onDragEnd = onDragRelease,
                        onDragCancel = onDragRelease,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f)),
            )
        }
    }
}
