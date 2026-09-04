package com.letscode.geopincam.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.letscode.geopincam.R

/** One option in a [SettingsChoiceRow]. */
data class SettingsOption<T>(val value: T, val label: String)

/** A titled group of settings rows. */
@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = HORIZONTAL_PADDING,
                end = HORIZONTAL_PADDING,
                top = SECTION_TOP_PADDING,
                bottom = SECTION_BOTTOM_PADDING
            )
        )
        content()
    }
}

/** A row with a switch; the whole row toggles it. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MIN_ROW_HEIGHT)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title = title, subtitle = subtitle, enabled = enabled, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A row that opens a single choice dialog. */
@Composable
fun <T> SettingsChoiceRow(
    title: String,
    options: List<SettingsOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selected }?.label.orEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MIN_ROW_HEIGHT)
            .clickable(enabled = enabled, role = Role.Button) { dialogVisible = true }
            .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(
            title = title,
            subtitle = selectedLabel,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
    }

    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MIN_ROW_HEIGHT)
                                .selectable(
                                    selected = option.value == selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(option.value)
                                        dialogVisible = false
                                    }
                                )
                                .padding(vertical = OPTION_PADDING),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option.value == selected, onClick = null)
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = OPTION_TEXT_SPACING)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** A row with a slider, used for the stamp background opacity. */
@Composable
fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING)
    ) {
        RowText(title = title, subtitle = valueLabel, enabled = enabled)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}

/** A read-only row, for facts such as the version or the save location. */
@Composable
fun SettingsInfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MIN_ROW_HEIGHT)
            .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title = title, subtitle = value, enabled = true, modifier = Modifier.weight(1f))
    }
}

/** A row that navigates somewhere else. */
@Composable
fun SettingsNavigationRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MIN_ROW_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowText(title = title, subtitle = null, enabled = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RowText(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    }
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val HORIZONTAL_PADDING = 16.dp
private val VERTICAL_PADDING = 10.dp
private val SECTION_TOP_PADDING = 20.dp
private val SECTION_BOTTOM_PADDING = 4.dp
private val OPTION_PADDING = 4.dp
private val OPTION_TEXT_SPACING = 12.dp
private val MIN_ROW_HEIGHT = 56.dp
private const val DISABLED_ALPHA = 0.38f
