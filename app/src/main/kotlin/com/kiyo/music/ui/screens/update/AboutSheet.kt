// about app, upstream credits and license sheets pushed from the version sheet or the kiyo page

package com.kiyo.music.ui.screens.update

import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiyo.music.BuildConfig
import com.kiyo.music.R
import com.kiyo.music.core.updater.TikTokHandle
import com.kiyo.music.core.updater.TikTokUrl
import com.kiyo.music.ui.component.PopupSheetState
import com.kiyo.music.ui.component.RemoteFocus
import com.kiyo.music.ui.component.pressBounce
import com.kiyo.music.ui.player.menu.MenuRowSurface
import com.kiyo.music.ui.screens.donate.showSupportSheet

/** the developer's name as the app shows it: just the name, no accounts or links behind it */
private const val DeveloperName = "zychohehe"

// the about sheet, pushed from the version sheet: what the app is, who made it, and where to follow along
fun PopupSheetState.showAboutSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = stringResource(R.string.done), onClick = popup::dismiss) }) {
        AboutSheetContent(popup = popup)
    }
}

// the banner at the top of the about sheet: a muted, looping clip that stands in for the old logo tile.
// it plays only while the app is on screen and frees its player the moment the sheet closes
@Composable
private fun AboutBannerVideo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            // no sound and no audio focus, so the banner never interrupts the music that is playing
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.about_banner}")))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, lifecycleState) {
        player.playWhenReady = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    }
    // the clip is 16:9, so the box is too and the picture fills it exactly; the surface color shows until the first frame
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.dp))
            .background(MenuRowSurface),
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ColumnScope.AboutSheetContent(popup: PopupSheetState) {
    val context = LocalContext.current
    val installed = shortVersion(BuildConfig.VERSION_NAME)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(18.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AboutBannerVideo(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Kiyo Music",
                fontSize = 32.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_version_label, installed),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.about_app_tagline),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.3).sp,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        Spacer(Modifier.height(26.dp))
        SectionHeader(stringResource(R.string.about_developer))
        Spacer(Modifier.height(8.dp))
        DeveloperCard(
            name = DeveloperName,
            role = stringResource(R.string.about_developer_role),
            onFollow = { context.openUrl(TikTokUrl) },
        )

        Spacer(Modifier.height(22.dp))
        SectionHeader(stringResource(R.string.about_actions))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AboutLinkRow(
                text = stringResource(R.string.about_support_me),
                detail = null,
                icon = null,
                onClick = { popup.showSupportSheet() },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun DeveloperCard(name: String, role: String, onFollow: () -> Unit) {
    // the whole card follows the developer on tiktok: tap the name, the face or the pill
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onFollow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.dev_zycho),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = role,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                text = "@$TikTokHandle",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.tiktok),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.about_follow_tiktok),
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AboutLinkRow(
    text: String,
    detail: String?,
    icon: Int?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}
