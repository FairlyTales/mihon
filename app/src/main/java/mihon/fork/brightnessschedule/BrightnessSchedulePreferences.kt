package mihon.fork.brightnessschedule

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class BrightnessSchedulePreferences(
    preferenceStore: PreferenceStore,
) {
    val enabled: Preference<Boolean> = preferenceStore.getBoolean("fork_brightness_schedule_enabled", false)

    /** Minutes after midnight. */
    val from: Preference<Int> = preferenceStore.getInt("fork_brightness_schedule_from", 22 * 60)

    /** Minutes after midnight. */
    val until: Preference<Int> = preferenceStore.getInt("fork_brightness_schedule_until", 7 * 60)

    /**
     * Epoch millis of the last run; 0 means none yet. App state so a backup restored on another device can't make the
     * first run catch up on an event that never happened there.
     */
    val lastRun: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("fork_brightness_schedule_last_run"),
        0L,
    )
}
