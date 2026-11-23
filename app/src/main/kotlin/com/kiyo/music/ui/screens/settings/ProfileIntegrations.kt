// ProfileIntegrations.kt
//
// The "Integrations" block on the profile page: the three services that used to live in a page of
// their own under settings. Discord (rich presence), YouTube Music (the account login + sync) and
// Backup. Each row opens that service's own page, and its sign-in is on that page.

package com.kiyo.music.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kiyo.music.R
import com.kiyo.music.ui.screens.settings.importsync.YouTubeSyncRoute
import com.kiyo.music.ui.screens.settings.integrations.backup.BackupRoute
import com.kiyo.music.ui.theme.InterFontFamily

// the youtube music logo is a red disc, this fills its corners so it sits as a tile like the others
private val YouTubeMusicRed = Color(0xFFFF0000)

// backup has no brand of its own, so its white cloud sits on a plain blue tile
private val BackupBlue = Color(0xFF2F7CF6)

private val TileShape = RoundedCornerShape(9.dp)
private val RowNameStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    color = Color.White,
)

/** the grey small heading over a block of the profile page ("Integrations", "Settings", ...) */
@Composable
internal fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp,
            color = Color(0xFF9A9A9A),
        ),
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
    )
}

/** Discord, YouTube Music and Backup as one tidy block, a hairline between the rows. */
@Composable
internal fun ProfileIntegrations(navController: NavController) {
    Column(Modifier.fillMaxWidth()) {
        ProfileSectionTitle(stringResource(R.string.integrations))
        IntegrationLine(
            name = stringResource(R.string.module_youtube_name),
            description = stringResource(R.string.module_youtube_desc),
            icon = R.drawable.provider_youtube_music,
            iconBackground = YouTubeMusicRed,
            onClick = { navController.navigate(YouTubeSyncRoute) },
        )
        IntegrationLine(
            name = stringResource(R.string.integration_backup_name),
            description = stringResource(R.string.integration_backup_desc),
            icon = R.drawable.backup,
            iconBackground = BackupBlue,
            iconPadding = 7.dp,
            onClick = { navController.navigate(BackupRoute) },
        )
    }
}

@Composable
private fun IntegrationLine(
    name: String,
    description: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    iconBackground: Color = Color.Transparent,
    iconPadding: Dp = 0.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .flatSection()
            .clickable(onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 2.dp, vertical = 12.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(TileShape)
                .background(iconBackground)
                .padding(iconPadding),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text = name, style = RowNameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
