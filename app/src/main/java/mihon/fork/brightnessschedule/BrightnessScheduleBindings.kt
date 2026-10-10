package mihon.fork.brightnessschedule

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import mihon.core.metro.metroGraph

/** Metro merges this into AppGraph, so the fork doesn't have to edit it. */
@ContributesTo(AppScope::class)
interface BrightnessScheduleBindings {
    val brightnessSchedule: BrightnessSchedule
}

internal val Context.brightnessSchedule: BrightnessSchedule
    get() = metroGraph<BrightnessScheduleBindings>().brightnessSchedule
