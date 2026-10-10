package mihon.fork.brightnessschedule

import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource as androidStringResource
import tachiyomi.core.common.preference.Preference as PreferenceData

@Composable
fun brightnessScheduleGroup(): Preference.PreferenceGroup {
    val context = LocalContext.current
    val schedule = remember { context.brightnessSchedule }
    val preferences = schedule.preferences
    val from by preferences.from.collectAsState()
    val until by preferences.until.collectAsState()

    val is24Hour = DateFormat.is24HourFormat(context)
    val formatter = remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a") }

    var editing by remember { mutableStateOf<PreferenceData<Int>?>(null) }
    editing?.let { preference ->
        TimeDialog(
            minutes = preference.get(),
            is24Hour = is24Hour,
            onConfirm = {
                preference.set(it)
                schedule.restart()
                editing = null
            },
            onDismissRequest = { editing = null },
        )
    }

    return Preference.PreferenceGroup(
        title = androidStringResource(R.string.fork_brightness_schedule_group),
        preferenceItems = listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.enabled,
                title = androidStringResource(R.string.fork_brightness_schedule),
                subtitle = androidStringResource(
                    if (from == until) {
                        R.string.fork_brightness_schedule_same_times
                    } else {
                        R.string.fork_brightness_schedule_summary
                    },
                ),
                onValueChanged = { enabled ->
                    if (enabled) schedule.restart()
                    true
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = androidStringResource(R.string.fork_brightness_schedule_from),
                subtitle = from.toLocalTime().format(formatter),
                onClick = { editing = preferences.from },
            ),
            Preference.PreferenceItem.TextPreference(
                title = androidStringResource(R.string.fork_brightness_schedule_until),
                subtitle = until.toLocalTime().format(formatter),
                onClick = { editing = preferences.until },
            ),
        ),
    )
}

@Composable
private fun TimeDialog(
    minutes: Int,
    is24Hour: Boolean,
    onConfirm: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = minutes / 60,
        initialMinute = minutes % 60,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        // The horizontal layout a landscape tablet would pick doesn't fit an AlertDialog's width.
        text = { TimePicker(state = state, layoutType = TimePickerLayoutType.Vertical) },
    )
}
