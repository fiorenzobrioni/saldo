package com.callbackdev.saldo.feature.onboarding

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.theme.AvatarShape
import kotlinx.coroutines.launch

/**
 * The app icon rendered as an onboarding hero, plus its two variants: an
 * animated "cards drop into the wallet" reveal for the welcome page, and a
 * corner-badge overlay that keeps the per-page meaning (a shield for privacy, a
 * bell for notifications) while the brand mark carries the identity.
 *
 * The launcher artwork lives on a 108dp canvas whose masked area is the central
 * 72dp, so overdrawing the image by 108/72 inside a clipped squircle reproduces
 * the icon exactly as the launcher shows it (same technique as the About logo).
 * The welcome reveal stacks the three ic_app_icon_* layers, which share that
 * canvas and group transform, so at rest they equal ic_launcher_foreground.
 */

/** Default onboarding size for the app-icon hero: a step up from the old badge. */
val ONBOARDING_APP_ICON_SIZE = 120.dp

/** 108dp canvas over the 72dp masked window: fills the tile like the launcher. */
private const val ARTWORK_OVERDRAW = 108f / 72f

// Fixed brand-palette accents for the corner badges (the icon itself is fixed
// colour, not dynamic, so the badges match it rather than the Material scheme).
// Both clear the 3:1 non-text contrast bar against a white glyph.
private val SecurityBadgeColor = Color(0xFF34A853) // brand green
private val NotificationBadgeColor = Color(0xFFEA4335) // brand red

/** Static app icon inside the app's squircle, on the launcher's white ground. */
@Composable
internal fun AppIconArtwork(
    size: Dp = ONBOARDING_APP_ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(AvatarShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(size * ARTWORK_OVERDRAW),
        )
    }
}

/**
 * Welcome hero: the two cards fall in from above the tile, one then the other,
 * and slot behind the wallet to assemble the full icon. One-shot, non-looping,
 * and non-blocking (the CTA is always live). When the system animation scale is
 * 0 the finished icon is shown with no motion.
 */
@Composable
internal fun WelcomeAppIcon(
    size: Dp = ONBOARDING_APP_ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animate = remember { animationsEnabled(context) }

    // Vertical offset as a fraction of the tile height; -1.15 clears the tile.
    val backCard = remember { Animatable(if (animate) -1.15f else 0f) }
    val frontCard = remember { Animatable(if (animate) -1.15f else 0f) }

    if (animate) {
        LaunchedEffect(Unit) {
            launch { backCard.animateTo(0f, tween(durationMillis = 460, easing = FastOutSlowInEasing)) }
            launch {
                frontCard.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 460, delayMillis = 170, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    val artSize = size * ARTWORK_OVERDRAW
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // White squircle ground.
        Box(
            Modifier
                .matchParentSize()
                .clip(AvatarShape)
                .background(Color.White),
        )
        // Cards travel unclipped so they read as arriving from off-tile.
        Image(
            painter = painterResource(R.drawable.ic_app_icon_card_back),
            contentDescription = null,
            modifier = Modifier
                .size(artSize)
                .graphicsLayer { translationY = backCard.value * size.toPx() },
        )
        Image(
            painter = painterResource(R.drawable.ic_app_icon_card_front),
            contentDescription = null,
            modifier = Modifier
                .size(artSize)
                .graphicsLayer { translationY = frontCard.value * size.toPx() },
        )
        // Wallet front, clipped to the tile, hides the cards' lower half at rest.
        Box(Modifier.matchParentSize().clip(AvatarShape), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_app_icon_wallet),
                contentDescription = null,
                modifier = Modifier.size(artSize),
            )
        }
    }
}

/** App icon with a small round accent badge on the top-right corner. */
@Composable
internal fun AppIconWithSecurityBadge(
    badge: ImageVector,
    size: Dp = ONBOARDING_APP_ICON_SIZE,
    modifier: Modifier = Modifier,
) = AppIconWithCornerBadge(badge, SecurityBadgeColor, size, modifier)

@Composable
internal fun AppIconWithNotificationBadge(
    badge: ImageVector,
    size: Dp = ONBOARDING_APP_ICON_SIZE,
    modifier: Modifier = Modifier,
) = AppIconWithCornerBadge(badge, NotificationBadgeColor, size, modifier)

@Composable
private fun AppIconWithCornerBadge(
    badge: ImageVector,
    badgeColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val chipSize = size * 0.34f
    Box(modifier = modifier.size(size)) {
        AppIconArtwork(size = size)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = chipSize * 0.30f, y = -chipSize * 0.30f)
                .size(chipSize)
                .clip(CircleShape)
                // A ring in the page ground colour lifts the chip off the icon.
                .background(MaterialTheme.colorScheme.background)
                .padding(chipSize * 0.10f)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = badge,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(chipSize * 0.56f),
            )
        }
    }
}

/** False only when the user has turned system animations off (scale 0). */
private fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) != 0f
