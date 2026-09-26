package com.callbackdev.saldo.feature.onboarding

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import kotlinx.coroutines.launch

/**
 * The app icon rendered as an onboarding hero, plus its two variants: an
 * animated "the coin drops onto the ring" reveal for the welcome page, and a
 * corner-badge overlay that keeps the per-page meaning (a shield for privacy, a
 * bell for notifications) while the brand mark carries the identity.
 *
 * Unlike the launcher tile, onboarding drops the warm ground: the artwork sits
 * straight on the page. The launcher artwork lives on a 108dp canvas whose
 * masked area is the central 72dp, so overdrawing the image by 108/72 inside a
 * box of the target size crops to that window and fills it (the transparent
 * margins overflow harmlessly, and layout still measures the target size). The
 * welcome reveal stacks the two ic_app_icon_* layers, which share that canvas
 * (all three are written by tools/draw_launcher_icon.py), so at rest they
 * equal ic_launcher_foreground.
 */

/** Onboarding hero size: roughly double the old 120dp tile, now that it is bare. */
val ONBOARDING_APP_ICON_SIZE = 220.dp

/** 108dp canvas over the 72dp masked window: fills the box like the launcher. */
private const val ARTWORK_OVERDRAW = 108f / 72f

// Fixed accents for the corner badges, taken from the icon's two halves (the
// icon itself is fixed colour, not dynamic, so the badges match it rather than
// the Material scheme). Both clear the 3:1 non-text contrast bar against a
// white glyph (4.7:1 and 3.9:1).
private val SecurityBadgeColor = Color(0xFF12807D) // the income half's sea green
private val NotificationBadgeColor = Color(0xFFCB6653) // the expense half's brick

/** The bare app-icon artwork (no ground), drawn straight on the page. */
@Composable
internal fun AppIconArtwork(
    size: Dp = ONBOARDING_APP_ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * ARTWORK_OVERDRAW),
        )
    }
}

/**
 * Welcome hero: the ring turns into place, then the coin drops into its gap
 * and settles, assembling the full icon straight on the page. One-shot,
 * non-looping, and non-blocking (the CTA is always live). When the system
 * animation scale is 0 the finished icon is shown with no motion.
 */
@Composable
internal fun WelcomeAppIcon(
    size: Dp = ONBOARDING_APP_ICON_SIZE,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animate = remember { animationsEnabled(context) }

    // 0 to 1: the ring's arrival (a quarter turn, a slight grow, a fade in).
    val ring = remember { Animatable(if (animate) 0f else 1f) }
    // Vertical offset as a fraction of the hero height; -1.15 clears the artwork.
    val coin = remember { Animatable(if (animate) -1.15f else 0f) }

    if (animate) {
        LaunchedEffect(Unit) {
            launch { ring.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing)) }
            launch {
                coin.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = CoinStiffness),
                )
            }
        }
    }

    val artSize = size * ARTWORK_OVERDRAW
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_app_icon_ring),
            contentDescription = null,
            modifier = Modifier
                .requiredSize(artSize)
                .graphicsLayer {
                    val t = ring.value
                    alpha = t
                    rotationZ = (1f - t) * RING_START_ROTATION
                    val scale = RING_START_SCALE + (1f - RING_START_SCALE) * t
                    scaleX = scale
                    scaleY = scale
                },
        )
        // The coin arrives from off-screen, over the page itself (no ground).
        Image(
            painter = painterResource(R.drawable.ic_app_icon_coin),
            contentDescription = null,
            modifier = Modifier
                .requiredSize(artSize)
                .graphicsLayer { translationY = coin.value * size.toPx() },
        )
    }
}

private const val RING_START_ROTATION = -90f
private const val RING_START_SCALE = 0.85f
private const val CoinStiffness = 220f

/** App icon with a small round accent badge on the ring's top-right shoulder. */
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
    val chipSize = size * 0.23f
    Box(modifier = modifier.size(size)) {
        AppIconArtwork(size = size)
        // The bare artwork fills the box (108/72 crop), so its top-right corner
        // sits near the box corner; nudge the chip onto the ring's shoulder.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = -size * 0.02f, y = size * 0.08f)
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
