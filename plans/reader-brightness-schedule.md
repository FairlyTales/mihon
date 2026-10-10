# Reader: custom brightness schedule

## Goal

Switch the reader's custom brightness filter on at a set evening time and off at a set morning time, so the morning black screen doesn't happen in the first place. The toggle button from `reader-brightness-toggle.md` stays for manual control.

## Behaviour

1. **Location:** Settings → Reader, as a new group with three rows: an on/off switch, **From** and **Until**.
2. **At From**, the schedule turns the custom brightness filter **on**. **At Until**, it turns it **off**. It only flips `ReaderPreferences.customBrightness`; the brightness value (the slider) is not touched.
3. **Clock only.** The schedule acts only when the clock passes From or Until. It never reads or reacts to the filter's current state. A manual toggle between two events stays in effect until the next event.
4. **Missed events are caught up (approved).** The filter only matters inside the reader, so the schedule runs when the reader opens or comes back to the foreground, and once a minute while it's open. On each run it looks at the events that passed since its last run and applies the most recent one. Seen from inside the reader, this is the same as switching at the exact minute, even if the tablet was asleep or the app closed.
5. **Turning the schedule on, or changing a time, does nothing immediately (approved).** Only the next From or Until acts. Events that "passed" before the change are ignored.
6. **From and Until may straddle midnight** (e.g. 22:00 → 07:00), or not (e.g. 13:00 → 15:00). There is no notion of a "window", only two daily events, so both cases work the same way.
7. **From equal to Until:** the two events would fire at the same moment with opposite effects, so the schedule does nothing. The switch's subtitle says so.

### Example: From 22:00, Until 07:00

| When | What happens | Filter |
|---|---|---|
| 21:30 | Reading | off |
| 22:00 | Still reading; the next once-a-minute run applies From | **on** |
| 22:40 | You switch it off with the bottom-bar button | off |
| 23:10 | Close the reader, tablet sleeps | off |
| 07:00 | Until passes while the tablet sleeps | (off) |
| 08:30 | You open the reader. Since its last run (23:10), Until passed; nothing newer did | **off** |
| 13:00 | Dark room: you switch it on with the button. No event until 22:00, so it stays on | on |

If instead you had left the filter on at 23:10, the 08:30 run would turn it off, which is the fix for the morning black screen.

## Design

Everything lives in one fork-owned package, `mihon.fork.brightnessschedule`, at `app/src/main/java/mihon/fork/brightnessschedule/`. Upstream files only gain an import and one call each.

**Why a package and not a Gradle module:** the feature needs classes that live inside the `app` module (`ReaderPreferences`, the settings screen's `Preference` types and the app's dependency graph). A separate module can't depend on `app` without a circular dependency. Merge conflicts happen per file, so new files in their own package give the same protection a separate module would.

### Files

| File | Contents |
|---|---|
| `BrightnessSchedulePreferences.kt` | The schedule's own settings (table below). Gets the app's `PreferenceStore` through Metro injection (`@Inject`), so `ReaderPreferences.kt` is not edited |
| `BrightnessSchedule.kt` | `latestEvent` (pure function), `run(now)` and the once-a-minute loop |
| `BrightnessScheduleBindings.kt` | `@ContributesTo(AppScope::class) interface BrightnessScheduleBindings { val brightnessSchedule: BrightnessSchedule }`. Metro merges it into the app's graph, so `AppGraph.kt` is not edited. Reached via `context.metroGraph<BrightnessScheduleBindings>()`, the same lookup `appGraph` uses |
| `BrightnessScheduleReader.kt` | `fun ComponentActivity.attachBrightnessSchedule()`. Starts the loop with `repeatOnLifecycle(STARTED)` |
| `BrightnessScheduleSettings.kt` | `@Composable fun brightnessScheduleGroup(): Preference.PreferenceGroup` with the switch and the From/Until rows, plus the time-picker dialog |
| `app/src/main/res/values/strings_brightness_schedule.xml` | The English labels. Resources must live under `res/`, but it's a new file of its own |
| `app/src/test/java/mihon/fork/brightnessschedule/BrightnessScheduleTest.kt` | Unit tests for `latestEvent` |

### Edits to upstream files

| File | Edit |
|---|---|
| `ReaderActivity.kt` | One import, plus `attachBrightnessSchedule()` in `onCreate` |
| `SettingsReaderScreen.kt` | One import, plus `brightnessScheduleGroup(),` in the list after the Display group |

Both lines get the `// Personal fork:` marker. `ReaderPreferences.kt` and `AppGraph.kt` are not touched.

### Settings stored

Keys are prefixed `fork_brightness_schedule_` so they can't clash with upstream keys:

| Preference | Type | Default | Notes |
|---|---|---|---|
| `enabled` | Boolean | `false` | The schedule's on/off switch |
| `from` | Int | `1320` (22:00) | Minutes after midnight |
| `until` | Int | `420` (07:00) | Minutes after midnight |
| `lastRun` | Long | `0` | Time of the schedule's last run, in epoch milliseconds. Uses `Preference.appStateKey`, so it is **not** included in backups |

`lastRun` must stay out of backups. A run time copied from another install would make the first run catch up on an event that never happened on this device.

The filter itself is still `ReaderPreferences.customBrightness`, read through the injected `ReaderPreferences`.

### The run

```kotlin
/** Returns true/false for the event to apply, or null when no event passed in (lastRun, now]. */
fun latestEvent(lastRun: Instant, now: ZonedDateTime, from: LocalTime, until: LocalTime): Boolean?
```

- For each of From and Until, find its most recent occurrence at or before `now`: today at that time, or yesterday if today's is still ahead. Use `ZonedDateTime.of(date, time, zone)`, which shifts a time that falls in a daylight-saving gap forward instead of failing.
- An occurrence counts only if it is after `lastRun`.
- If both count, the later one wins.
- If From equals Until, return null.

Each event's most recent occurrence is always within the last 24 hours. So when the reader hasn't been opened for days, both events count and the later one wins, with no need to loop over days.

`run(now)`:

1. If the schedule is off, return.
2. If `lastRun` is `0` (first run, or just restored from a backup), set it to `now` and return. This follows behaviour rule 5.
3. Otherwise, call `latestEvent`. If it returns a value, write it to `customBrightness`.
4. Set `lastRun = now`.

The existing watcher in `ReaderActivity` already reacts to `customBrightness` changes, so the screen dims or brightens on its own. No toast: a scheduled change is silent.

The loop calls `run` and then waits until the start of the next minute. It ticks every minute instead of sleeping until the next event. That way a manual clock change, a time-zone change while travelling or a daylight-saving jump is picked up within a minute.

`repeatOnLifecycle(STARTED)` starts the loop each time the reader becomes visible, so the first tick is the catch-up. It stops the loop when the reader goes to the background or the screen locks.

### Settings UI

- **Switch** "Custom brightness schedule":
  - Subtitle: "Turns custom brightness on at From and off at Until". When From equals Until, it says the schedule does nothing.
  - Its `onValueChanged` sets `lastRun = now` when switched on (behaviour rule 5).
- **"From"** and **"Until"** rows (`TextPreference`). Each shows its time as the subtitle, formatted with the system's 12/24-hour setting. Tapping a row opens a time-picker dialog: Material 3 `TimePicker` inside an `AlertDialog` with OK and Cancel, 24-hour mode taken from `DateFormat.is24HourFormat(context)`. Saving a time also sets `lastRun = now`.
- The From and Until rows stay visible while the schedule is off, so times can be set before switching it on.

Labels are English only, from `strings_brightness_schedule.xml`, read with Compose's `stringResource(R.string.…)`. A translation can be added later as `values-<lang>/strings_brightness_schedule.xml`.

## Verification

### Unit tests (`latestEvent`, JUnit 5 like `MigratorTest`)

- 22:00 → 07:00, last run 21:00, now 22:01 → **on**
- Same, last run 22:01, now 23:00 → nothing (a manual toggle at 22:30 survives)
- Same, last run 23:10, now 08:30 next day → **off**
- Same, last run three days ago, now 12:00 → **off** (Until 07:00 is the later event)
- Daytime window 13:00 → 15:00, last run 12:00, now 16:00 → **off** (both passed; Until is later)
- From equal to Until → nothing
- Spring-forward day with From at 02:30 (a time that doesn't exist that night) → fires once, at the shifted time

### On the tablet

1. Turn the schedule on with From two minutes from now. Open a chapter and wait. The screen dims within a minute of From.
2. Switch it off with the bottom-bar button. It stays off; the next minute's tick doesn't override it.
3. Set Until two minutes ahead, close the reader, wait past Until, reopen. The filter is off on open.
4. Turn the schedule on while already inside its window. Nothing changes until the next event.
5. Lock the screen across an event and unlock with the reader open. The event is applied on unlock.
6. Check the time-picker dialog in both 12-hour and 24-hour system settings.

Commit as one feature commit.
