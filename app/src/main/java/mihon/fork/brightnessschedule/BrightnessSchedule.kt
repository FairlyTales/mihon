package mihon.fork.brightnessschedule

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Turns the reader's custom brightness filter on at From and off at Until. Only the clock passing those times acts; the
 * filter's current state is never read, so a manual toggle holds until the next event.
 */
@Inject
@SingleIn(AppScope::class)
class BrightnessSchedule(
    val preferences: BrightnessSchedulePreferences,
    private val readerPreferences: ReaderPreferences,
) {

    /**
     * Applies the latest event since the last run. Missed events are caught up, which inside the reader is
     * indistinguishable from switching on time.
     */
    fun run(now: ZonedDateTime = ZonedDateTime.now()) {
        if (!preferences.enabled.get()) return
        val lastRun = preferences.lastRun.get()
        preferences.lastRun.set(now.toInstant().toEpochMilli())
        if (lastRun == 0L) return

        latestEvent(
            lastRun = Instant.ofEpochMilli(lastRun),
            now = now,
            from = preferences.from.get().toLocalTime(),
            until = preferences.until.get().toLocalTime(),
        )?.let(readerPreferences.customBrightness::set)
    }

    /** Call when the schedule is switched on or a time changes: events before now are ignored. */
    fun restart() {
        preferences.lastRun.set(System.currentTimeMillis())
    }

    /** Ticks every minute rather than sleeping until the next event, so clock, time zone and DST changes are seen. */
    suspend fun runWhileVisible() {
        while (true) {
            run()
            val now = ZonedDateTime.now()
            delay(Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).toMillis())
        }
    }
}

/** The filter state of the latest event in (lastRun, now], or null if none passed or From equals Until. */
internal fun latestEvent(lastRun: Instant, now: ZonedDateTime, from: LocalTime, until: LocalTime): Boolean? {
    if (from == until) return null
    val lastFrom = lastOccurrence(from, now).takeIf { it.toInstant() > lastRun }
    val lastUntil = lastOccurrence(until, now).takeIf { it.toInstant() > lastRun }
    return when {
        lastFrom != null && lastUntil != null -> lastFrom.isAfter(lastUntil)
        lastFrom != null -> true
        lastUntil != null -> false
        else -> null
    }
}

/** ZonedDateTime.of moves a time inside a DST gap forward instead of failing. */
private fun lastOccurrence(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
    val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
    return if (today.isAfter(now)) ZonedDateTime.of(now.toLocalDate().minusDays(1), time, now.zone) else today
}

internal fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
