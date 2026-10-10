package mihon.fork.brightnessschedule

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BrightnessScheduleTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val night = LocalTime.of(22, 0) to LocalTime.of(7, 0)

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(zone)

    private fun event(lastRun: String, now: String, window: Pair<LocalTime, LocalTime> = night): Boolean? =
        latestEvent(at(lastRun).toInstant(), at(now), window.first, window.second)

    @Test
    fun `From passing turns the filter on`() {
        assertEquals(true, event("2026-06-01T21:00", "2026-06-01T22:01"))
    }

    @Test
    fun `no event since the last run leaves a manual toggle alone`() {
        assertNull(event("2026-06-01T22:01", "2026-06-01T23:00"))
    }

    @Test
    fun `Until passed while asleep turns the filter off on open`() {
        assertEquals(false, event("2026-06-01T23:10", "2026-06-02T08:30"))
    }

    @Test
    fun `an event exactly at now counts`() {
        assertEquals(false, event("2026-06-02T06:00", "2026-06-02T07:00"))
    }

    @Test
    fun `after days away the later event wins`() {
        assertEquals(false, event("2026-05-29T12:00", "2026-06-01T12:00"))
        assertEquals(true, event("2026-05-29T12:00", "2026-06-01T23:00"))
    }

    @Test
    fun `daytime window with both events passed applies Until`() {
        val day = LocalTime.of(13, 0) to LocalTime.of(15, 0)
        assertEquals(false, event("2026-06-01T12:00", "2026-06-01T16:00", day))
    }

    @Test
    fun `equal times do nothing`() {
        val same = LocalTime.of(22, 0) to LocalTime.of(22, 0)
        assertNull(event("2026-06-01T21:00", "2026-06-01T23:00", same))
    }

    @Test
    fun `clock set back before the last run does nothing`() {
        assertNull(event("2026-06-01T23:00", "2026-06-01T22:30"))
    }

    @Test
    fun `From inside a DST gap fires once at the shifted time`() {
        // 2026-03-29 in Berlin jumps from 02:00 to 03:00, so 02:30 happens at 03:30.
        val gap = LocalTime.of(2, 30) to LocalTime.of(7, 0)
        assertNull(event("2026-03-29T01:00", "2026-03-29T03:29", gap))
        assertEquals(true, event("2026-03-29T01:00", "2026-03-29T03:31", gap))
        assertNull(event("2026-03-29T03:31", "2026-03-29T04:00", gap))
    }

    @Test
    fun `minutes convert to local time`() {
        assertEquals(LocalTime.of(22, 0), (22 * 60).toLocalTime())
    }
}
