package com.fizy.music.ui.screens.update

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import com.fizy.music.core.updater.clearDownloadedApks
import com.fizy.music.core.updater.getAutoUpdateCheckSetting
import com.fizy.music.core.updater.saveAutoUpdateCheckSetting
import com.fizy.music.core.updater.getUpdateNotificationsSetting
import com.fizy.music.core.updater.saveUpdateNotificationsSetting
import com.fizy.music.core.changelog.ChangelogScreen
import com.fizy.music.core.commitscreen.CommitScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.fizy.music.BuildConfig
import com.fizy.music.R
import com.fizy.music.core.updater.GithubRelease
import com.fizy.music.core.updater.UpdateChannel
import com.fizy.music.core.updater.UpdateState
import com.fizy.music.core.updater.canLeaveNightly
import com.fizy.music.core.updater.installedIsNightly
import com.fizy.music.core.updater.saveUpdateChannel
import com.fizy.music.core.updater.updateChannel
import com.fizy.music.core.updater.apkFileFor
import com.fizy.music.core.updater.downloadApk
import com.fizy.music.core.updater.formatBytes
import com.fizy.music.core.updater.getLatestRelease
import com.fizy.music.core.updater.getPreviousRelease
import com.fizy.music.core.updater.installApk
import com.fizy.music.core.updater.isDownloaded
import com.fizy.music.core.updater.isNewerThanInstalled
import com.fizy.music.core.updater.parseFullVersion
import com.fizy.music.ui.component.BetaBadge
import com.fizy.music.ui.component.PopupSheetState
import com.fizy.music.ui.component.pressBounce
import com.fizy.music.ui.player.menu.MenuRowSurface
import com.fizy.music.ui.player.menu.MenuSurface
import com.fizy.music.ui.screens.donate.showSupportSheet
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.fizy.music.ui.component.RemoteFocus

@Composable
fun rememberUpdateState(): androidx.compose.runtime.State<UpdateState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // asks again every time the app comes back to the front (github is only called once per 15 minutes, the rest comes from the shared copy),
    // so a release published while the app was in the background still shows up without a full restart
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            state.value = checkUpdate(updateChannel(context), force = false)
        }
    }
    return state
}

/** asks github (or the shared copy, unless force is set) whether a newer release is out on the channel */
private suspend fun checkUpdate(channel: UpdateChannel, force: Boolean): UpdateState =
    getLatestRelease(channel, force).fold(
        onSuccess = { release ->
            when {
                release == null -> UpdateState.UpToDate
                isNewerThanInstalled(release.version) -> UpdateState.Available(release)
                else -> UpdateState.UpToDate
            }
        },
        onFailure = { UpdateState.Failed(it.message ?: "Couldn't reach GitHub") },
    )

/** shows the app version sheet as a PopupSheet, see docs/popup-sheet.skill.md */
fun PopupSheetState.showUpdateSheet(updateState: UpdateState) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = stringResource(R.string.done), onClick = popup::dismiss) }) {
        UpdateSheetContent(initialState = updateState, popup = popup)
    }
}

private fun PopupSheetState.showUpdateDetailSheet(release: GithubRelease) {
    val popup = this
    popup.show {
        UpdateDetailContent(
            release = release,
            // pushed on top of the version sheet, so back just pops down to it again
            onBack = popup::dismiss,
            // locks this same sheet during a download, re-showing it locked would push a second copy with fresh state
            onLocked = popup::setLocked,
        )
    }
}

@Composable
internal fun SheetPrimaryButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.26f))
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    }
}

private val StableGreen = Color(0xFF9CF58A)
private const val StableLabel = "STABLE"

/** "8.1.0 build#1165" -> "8.1.0", "8.0.2 (1100)" -> "8.0.2" */
internal fun shortVersion(version: String): String = version.substringBefore(' ')

/** the system update screen: the same dark rounded cards as the rest of the app, with red icon tiles */
@Composable
private fun ColumnScope.UpdateSheetContent(
    initialState: UpdateState,
    popup: PopupSheetState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(updateChannel(context)) }
    var state by remember { mutableStateOf(initialState) }
    // the newest plain release, which decides whether a nightly may switch back
    var latestRelease by remember { mutableStateOf<GithubRelease?>(null) }
    var autoCheck by remember { mutableStateOf(getAutoUpdateCheckSetting(context)) }
    var notifications by remember { mutableStateOf(getUpdateNotificationsSetting(context)) }
    var clearResult by remember { mutableStateOf<Boolean?>(null) }

    suspend fun refresh(force: Boolean) {
        state = checkUpdate(channel, force)
        // a failed check keeps what it knew, so the rows don't vanish when offline
        getLatestRelease(UpdateChannel.Release).onSuccess { latestRelease = it }
    }
    // the shared copy answers instantly when the app already checked on launch, and again after a channel switch
    LaunchedEffect(channel) { refresh(force = false) }
    val checkAgain: () -> Unit = {
        if (state !is UpdateState.Checking) {
            state = UpdateState.Checking
            scope.launch { refresh(force = true) }
        }
    }
    fun switchTo(target: UpdateChannel) {
        saveUpdateChannel(context, target)
        state = UpdateState.Checking
        channel = target
    }

    val installed = shortVersion(BuildConfig.VERSION_NAME)
    val betaOn = channel == UpdateChannel.Nightly
    val canLeave = canLeaveNightly(latestRelease)
    val systemSubtitle = when (val shown = state) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Available -> "${stringResource(R.string.update_status_new_update)} ${shortVersion(shown.release.version)}"
        is UpdateState.Failed -> stringResource(R.string.update_status_failed)
    }
    val channelLabel = stringResource(
        if (channel == UpdateChannel.Nightly) R.string.update_channel_nightly else R.string.update_channel_release,
    )

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.update_settings_title),
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))

        SettingsSectionLabel(stringResource(R.string.app_updates_title))
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.SystemUpdate,
                title = stringResource(R.string.system_update),
                subtitle = systemSubtitle,
                onClick = {
                    val shown = state
                    if (shown is UpdateState.Available) popup.showUpdateDetailSheet(shown.release) else checkAgain()
                },
                trailing = { SettingsChevron() },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.version, installed),
                subtitle = stringResource(R.string.update_channel_flavor, channelLabel, BuildConfig.FLAVOR.uppercase()),
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.automatic_update_check),
                subtitle = stringResource(R.string.automatic_update_check_subtitle),
                trailing = {
                    SettingsSwitch(checked = autoCheck) {
                        autoCheck = it
                        saveAutoUpdateCheckSetting(context, it)
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.update_notifications),
                subtitle = stringResource(R.string.update_notifications_subtitle),
                trailing = {
                    SettingsSwitch(checked = notifications) {
                        notifications = it
                        saveUpdateNotificationsSetting(context, it)
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Science,
                title = stringResource(R.string.beta_updates),
                subtitle = if (!betaOn || canLeave) stringResource(R.string.beta_updates_subtitle)
                    else stringResource(R.string.channel_release_waits),
                trailing = {
                    SettingsSwitch(checked = betaOn, enabled = !betaOn || canLeave) { turnOn ->
                        if (turnOn) {
                            popup.showPathChangeSheet(toNightly = true) { switchTo(UpdateChannel.Nightly) }
                        } else if (installedIsNightly) {
                            popup.showPathChangeSheet(toNightly = false) { switchTo(UpdateChannel.Release) }
                        } else {
                            switchTo(UpdateChannel.Release)
                        }
                    }
                },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.DeleteOutline,
                title = stringResource(R.string.clear_downloaded_updates),
                subtitle = when (clearResult) {
                    true -> stringResource(R.string.clear_downloaded_updates_done)
                    false -> stringResource(R.string.update_status_failed)
                    null -> stringResource(R.string.clear_downloaded_updates_desc)
                },
                onClick = { clearResult = clearDownloadedApks(context) },
            )
        }

        Spacer(Modifier.height(24.dp))
        SettingsSectionLabel(stringResource(R.string.changelog))
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Schedule,
                title = stringResource(R.string.changelog),
                subtitle = stringResource(R.string.view_version_history),
                onClick = { popup.showChangelogPage() },
                trailing = { SettingsChevron() },
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.commits),
                subtitle = stringResource(R.string.browse_recent_changes),
                onClick = { popup.showCommitsPage() },
                trailing = { SettingsChevron() },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// the changelog and the commit list open as full pages on top of the settings, back pops them off
private fun PopupSheetState.showChangelogPage() {
    val popup = this
    popup.show(fullBleed = true) {
        ChangelogScreen(onBack = popup::dismiss, scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior())
    }
}

private fun PopupSheetState.showCommitsPage() {
    val popup = this
    popup.show(fullBleed = true) {
        CommitScreen(onBack = popup::dismiss, scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior())
    }
}

private val SettingsCardSurface = Color(0xFF1C1C1E)
private val SettingsRed = Color(0xFFE63946)
private val SettingsGrey = Color(0xFF8E8E93)
private val SettingsDividerColor = Color(0xFF2C2C2E)

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        color = SettingsGrey,
        modifier = Modifier.padding(start = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(SettingsCardSurface),
        content = content,
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 66.dp)
            .height(0.5.dp)
            .background(SettingsDividerColor),
    )
}

@Composable
private fun SettingsChevron() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = SettingsGrey,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val tappable = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(tappable)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(SettingsRed),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = Color.White,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = SettingsGrey,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** an iOS-style switch: red when on, a white knob that slides to the right */
@Composable
private fun SettingsSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(34.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (checked) SettingsRed else Color(0xFF39393D))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White))
    }
}

/** which channel this build is: nightly says so, newer than any release is beta, the rest is stable */
@Composable
private fun StatusRow(
    title: String,
    big: String,
    action: String? = null,
    onAction: () -> Unit = {},
    pulsing: Boolean = false,
) {
    val pulse = if (pulsing) {
        rememberInfiniteTransition(label = "statusPulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "statusPulseAlpha",
        )
    } else {
        null
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text(
                big,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.8).sp,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = pulse?.value ?: 1f },
            )
        }
        if (action != null) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                action,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .pressBounce(interaction)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

/** a row under actions: a light disc with the icon, then the label, dimmed ones can't be tapped */
@Composable
private fun ActionRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
    spinning: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val contentAlpha = if (enabled) 1f else 0.4f
    val spin = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(900, easing = LinearEasing))
            }
        } else {
            // finish the turn it's on instead of snapping back
            val rest = (spin.value / 360f).let { kotlin.math.ceil(it) * 360f }
            spin.animateTo(rest, spring(dampingRatio = 0.7f, stiffness = 300f))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface.copy(alpha = if (enabled) 1f else 0.6f))
            .clickable(enabled = enabled, interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha }
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFDADADA)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color(0xFF1C1C1E),
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = spin.value },
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            color = Color.White.copy(alpha = contentAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (hint != null) {
            Text(hint, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

const val UpdateHeadline = "Theres an update~!"

@Composable
private fun ColumnScope.UpdateDetailContent(
    release: GithubRelease,
    onBack: () -> Unit,
    onLocked: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableFloatStateOf(0f) }

    // a tv remote starts on Install, the back row at the top would close this the moment OK is pressed
    val installFocus = remember { FocusRequester() }
    val remote = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    LaunchedEffect(Unit) {
        if (remote && release.apkUrl != null) runCatching { installFocus.requestFocus() }
    }

    val startInstall: (java.io.File) -> Unit = { file ->
        if (!installApk(context, file)) {
            error = context.getString(R.string.update_allow_unknown_apps)
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onBack,
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = stringResource(R.string.update_back_desc),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_fizy_mark),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = release.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(16.dp)
            .animateContentSize()
    ) {
        Text(text = stringResource(R.string.update_changelog), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        ChangelogMarkdown(
            markdown = release.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strongColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = stringResource(R.string.update_to_be_installed),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .padding(14.dp)
    ) {
        AppIconImage(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = release.apkName ?: "fizy.apk",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${BuildConfig.APPLICATION_ID} · ${formatBytes(release.apkSizeBytes)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    error?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }

    Spacer(modifier = Modifier.height(18.dp))

    val animatedProgress by animateFloatAsState(
        targetValue = progress.floatValue,
        animationSpec = tween(durationMillis = 120),
        label = "downloadProgress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
            .focusRequester(installFocus)
            .clickable(
                // stays pressable while downloading, so a tv remote resting on it isn't thrown off the sheet
                enabled = release.apkUrl != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = {
                    if (downloading) return@clickable
                    error = null
                    if (isDownloaded(context, release)) {
                        startInstall(apkFileFor(context, release))
                    } else {
                        downloading = true
                        onLocked(true)
                        progress.floatValue = 0f
                        scope.launch {
                            val result = downloadApk(context, release) { progress.floatValue = it }
                            downloading = false
                            onLocked(false)
                            result.fold(
                                onSuccess = { startInstall(it) },
                                onFailure = { error = it.message ?: context.getString(R.string.update_download_failed) },
                            )
                        }
                    }
                },
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
        Text(
            text = when {
                downloading -> stringResource(R.string.update_downloading_progress, (animatedProgress * 100).toInt())
                release.apkUrl == null -> stringResource(R.string.update_no_apk)
                else -> stringResource(R.string.update_install_here)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MenuRowSurface)
            .clickable(
                enabled = !downloading,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = { context.openUrl(release.htmlUrl) },
            )
    ) {
        Text(text = stringResource(R.string.update_open_web), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    }
}

internal fun android.content.Context.openUrl(url: String) {
    runCatching {
        startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
internal fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(context) {
        runCatching {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(width = 192, height = 192)
                .asImageBitmap()
        }.getOrNull()
    }

    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier)
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(MenuSurface),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_fizy_mark),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
    }
}
