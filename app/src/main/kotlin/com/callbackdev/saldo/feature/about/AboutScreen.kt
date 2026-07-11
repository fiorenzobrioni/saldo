package com.callbackdev.saldo.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.BuildConfig
import com.callbackdev.saldo.R
import com.callbackdev.saldo.core.designsystem.theme.SaldoDimens

/**
 * About screen: the app identity (logo, name, version, tagline, author), the
 * license, and the open source libraries the app builds on. Everything is
 * static; the library list is hand-maintained alongside the version catalog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AppIdentity()
            Spacer(Modifier.height(28.dp))
            LicenseCard(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(SaldoDimens.cardSpacing))
            LibrariesCard(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The launcher artwork, name, version, tagline and author, centered as a hero block. */
@Composable
private fun AppIdentity(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_author),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The launcher icon rendered as the in-app logo. The adaptive-icon foreground
 * draws on a 108dp canvas of which the visible masked circle is the central
 * 72dp: scaling the image to size * 108/72 inside a clipped circle of the
 * launcher background color reproduces the icon exactly as the launcher shows it.
 */
@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(LOGO_SIZE)
            .clip(CircleShape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(LOGO_SIZE * LOGO_CANVAS_OVER_VISIBLE),
        )
    }
}

@Composable
private fun LicenseCard(modifier: Modifier = Modifier) {
    AboutCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.about_license_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_license_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibrariesCard(modifier: Modifier = Modifier) {
    val names = stringArrayResource(R.array.about_library_names)
    val roles = stringArrayResource(R.array.about_library_roles)
    AboutCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.about_libraries_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        names.forEachIndexed { index, name ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            LibraryRow(name = name, role = roles.getOrElse(index) { "" })
        }
    }
}

@Composable
private fun LibraryRow(name: String, role: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (role.isNotEmpty()) {
            Text(
                text = role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SaldoDimens.cardPadding,
                vertical = SaldoDimens.cardPaddingVertical,
            ),
        ) {
            content()
        }
    }
}

private val LOGO_SIZE = 96.dp

/** Adaptive-icon canvas (108dp) over the visible masked area (72dp). */
private const val LOGO_CANVAS_OVER_VISIBLE = 108f / 72f
