package com.kiyo.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kiyo.music.R
import com.kiyo.music.constants.AccountImageUrlKey
import com.kiyo.music.constants.ProfilePicUriKey
import com.kiyo.music.utils.rememberPreference

/**
 * The profile avatar for every page but Home (Home draws its own in its header). It reads the same
 * two sources Home does: the photo picked in the profile, else the signed-in account's photo.
 */
@Composable
fun ProfileAvatarButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (picked, _) = rememberPreference(ProfilePicUriKey, "")
    val (account, _) = rememberPreference(AccountImageUrlKey, "")
    val image = picked.takeIf { it.isNotBlank() }?.let { if (it.contains("://")) it else "file://$it" }
        ?: account.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                placeholder = painterResource(R.drawable.person),
                error = painterResource(R.drawable.person),
                contentDescription = stringResource(R.string.profile_label),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = stringResource(R.string.profile_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}


/**
 * Pages that draw something of their own in the top-right corner (a collapsing search circle, their own avatar)
 * call [HideGlobalProfileAvatar], and the app-wide avatar steps aside while any of them is on screen. A count,
 * so two pages alive at once during a slide don't switch it back on early.
 */
object ProfileAvatarGate {
    var owners by mutableIntStateOf(0)
        internal set
    val suppressed: Boolean get() = owners > 0
}

@Composable
fun HideGlobalProfileAvatar() {
    DisposableEffect(Unit) {
        ProfileAvatarGate.owners += 1
        onDispose { ProfileAvatarGate.owners = (ProfileAvatarGate.owners - 1).coerceAtLeast(0) }
    }
}
