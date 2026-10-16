package com.callbackdev.saldo.feature.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.callbackdev.saldo.R
import kotlin.math.roundToInt

/*
 * The parts the widget's settings screen is built from (16 Oct 2026): the
 * widget at the top, then every question on a rounded group under its own
 * heading, radio rows for one answer, switch rows with the sentence that says
 * what they do. Measured to the same numbers as the widget settings of the
 * rest of the developer's apps, so a reader who has configured one has
 * configured them all; deliberately not the app's own `SettingsGroup`
 * (a bordered card on a grey canvas), which is the settings screen's grammar
 * and not the widget's.
 */

/** A group's title: the section's name above its group. */
@Composable
internal fun ConfigHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** A sentence under a heading or a group, for what a row alone cannot say. */
@Composable
internal fun ConfigNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    )
}

/** Rows that belong together, on one rounded ground. */
@Composable
internal fun ConfigGroup(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = ConfigGroupShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(ConfigGroupShape),
    ) {
        Column { content() }
    }
}

internal val ConfigGroupCorner = 24.dp
internal val ConfigGroupShape = RoundedCornerShape(ConfigGroupCorner)

@Composable
internal fun ConfigDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DividerAlpha),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

private const val DividerAlpha = 0.6f

/** One answer among several: the radio, the words, and what the answer looks like. */
@Composable
internal fun ConfigChoiceRow(
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onPick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        trailing?.invoke()
    }
}

/** One question's answers on one group: radio rows, no hairlines (one question). */
@Composable
internal fun <T> ConfigChoices(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    ConfigGroup {
        options.forEach { (value, label) ->
            ConfigChoiceRow(label = label, selected = value == selected, onPick = { onPick(value) })
        }
    }
}

/** A toggle with the sentence that says what it does; the whole row is the target. */
@Composable
internal fun ConfigSwitchRow(label: String, note: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onToggle, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * How solid the card is: the name and the value on one line, the slider under
 * them, in steps of 5%. The value is said in words at the two ends.
 */
@Composable
internal fun OpacityRow(pct: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.widget_config_opacity),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when (pct) {
                    FullOpacity -> stringResource(R.string.widget_opacity_full)
                    0 -> stringResource(R.string.widget_opacity_transparent)
                    else -> stringResource(R.string.widget_opacity_percent, pct)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = pct.toFloat(),
            onValueChange = { raw -> onChange((raw / OpacityStep).roundToInt() * OpacityStep) },
            onValueChangeFinished = onDone,
            valueRange = 0f..FullOpacity.toFloat(),
            steps = FullOpacity / OpacityStep - 1,
        )
    }
}

/**
 * The one control that undoes the others: outlined, in the error colour, at
 * the foot, above "Done".
 */
@Composable
internal fun ConfigResetButton(onReset: () -> Unit) {
    OutlinedButton(
        onClick = onReset,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp),
    ) {
        Text(stringResource(R.string.widget_config_reset))
    }
}

/**
 * "Done", full width at the foot: the choices are saved as they are made, so
 * this only closes the door.
 */
@Composable
internal fun ConfigDoneButton(onDone: () -> Unit) {
    Button(
        onClick = onDone,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Text(stringResource(R.string.widget_config_done))
    }
}
