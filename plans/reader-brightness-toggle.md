# Reader: custom brightness toggle button

## Goal

Turn the reader's custom brightness filter on and off with two blind taps: tap the middle of the page to open the menu, then tap a button in the middle of the bottom bar. Today it takes: settings button → Filter tab → untick "Custom brightness".

The new button controls the existing global preference `ReaderPreferences.customBrightness`. The checkbox in the Filter tab reads the same preference, so the two stay in sync with no extra code. The brightness value (`customBrightnessValue`) is not touched; the button only switches the filter on or off.

## Decisions (approved)

1. **Position: the middle of the bottom bar** (third of five: Reading mode, Orientation, **Brightness**, Crop, Settings). The centre is the easiest spot to remember.
2. **Icon: moon (Material Symbols `dark_mode`).** Filled when the filter is on, outlined when it's off. This is Material's usual on/off pattern, like the existing Crop / Crop off pair.
3. **Feedback: an "On" / "Off" toast,** the same as the crop-borders button. It reuses the existing `MR.strings.on` / `MR.strings.off` strings.
4. **No new text strings.** The button's accessibility label reuses `MR.strings.pref_custom_brightness` ("Custom brightness"). Adding a string would mean editing `i18n/.../base/strings.xml`, which upstream changes constantly, so every update would risk a merge conflict.

## Changes

### 1. Icons: add `dark_mode` in two variants

- `icons/material-symbols/src/main/valkyrieResources/rounded/dark_mode.svg` (fill 0)
- `icons/material-symbols/src/main/valkyrieResources/roundedFilled/dark_mode.svg` (fill 1)

Source: Google Fonts → Material Symbols **Rounded**, weight 400, grade 0, optical size 24. Add the same licence comment header the existing files have (see `rounded/crop.svg`). The Valkyrie plugin generates `MaterialSymbols.Rounded.DarkMode` and `MaterialSymbols.RoundedFilled.DarkMode` at build time, so no Kotlin is written by hand.

These are new files, so they can't conflict with upstream unless upstream adds the same icon. In that case, a byte-identical file merges cleanly.

### 2. `ReaderBottomBar.kt` — the button

`app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderBottomBar.kt`

- New parameters after `onClickSettings`: `customBrightnessEnabled: Boolean` and `onClickCustomBrightness: () -> Unit`.
- A fifth `IconButton` between Orientation and Crop, mirroring the crop one: filled `DarkMode` when enabled, outlined when not, `contentDescription = stringResource(MR.strings.pref_custom_brightness)`.

### 3. `ReaderAppBars.kt` — pass-through

`app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt`

Add the same two parameters next to `cropEnabled` / `onClickCropBorder` (around line 74) and pass them to `ReaderBottomBar` (around line 173).

### 4. `ReaderActivity.kt` — wiring and the watcher fix

`app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`

**Wiring, in `AppBars()` (around line 447):**

```kotlin
val customBrightness by readerPreferences.customBrightness.collectAsState()
...
customBrightnessEnabled = customBrightness,
onClickCustomBrightness = {
    val enabled = readerPreferences.customBrightness.toggle()
    menuToggleToast?.cancel()
    menuToggleToast = toast(if (enabled) MR.strings.on else MR.strings.off)
},
```

The crop button calls `ReaderViewModel.toggleCropBorders()`, because whether it changes the paged or the webtoon setting depends on the reading mode. Brightness has no such branching, so the toggle stays in the Activity, which already holds `readerPreferences`. That keeps `ReaderViewModel.kt` out of the diff.

**Watcher fix, in `ReaderSettings.init` (around line 867):** replace

```kotlin
readerPreferences.customBrightness.changes()
    .onEach(::setCustomBrightness)
    .launchIn(lifecycleScope)
```

and the whole `setCustomBrightness()` function with:

```kotlin
readerPreferences.customBrightness.changes()
    .flatMapLatest { enabled ->
        if (enabled) {
            readerPreferences.customBrightnessValue.changes().sample(0.1.seconds)
        } else {
            flowOf(0)
        }
    }
    .onEach(::setCustomBrightnessValue)
    .launchIn(lifecycleScope)
```

`flatMapLatest` cancels the value watcher when the filter is switched off. That stops the watchers piling up and drops any slider value still waiting out the 0.1 s delay. The behaviour is otherwise the same: switching on applies the stored value, and switching off restores system brightness. `ReaderActivity` already imports `flatMapLatest`; add `flowOf` if it's missing.

Make this a separate commit from the button. It isn't specific to this fork, so it could be offered upstream on its own later.

## Verification

1. `./gradlew :app:spotlessApply` (ktlint formatting), then `./gradlew assembleRelease`.
2. `adb install -r app/build/outputs/apk/release/app-arm64-v8a-release.apk`. This keeps the app's data.
3. On the tablet:
   - With the filter on at -75: tap the middle of the page, then the middle button of the bottom bar. The screen goes back to normal brightness and the toast says "Off".
   - Tap again: dim again, toast "On", icon filled.
   - Open Settings → Filter tab: the "Custom brightness" checkbox matches the button state. Untick it there; the button's icon switches to outlined.
   - Toggle quickly 10 times, then drag the slider with the filter on: brightness follows the slider. Switch the filter off: it stays off.
   - Leave the reader, close the app and reopen a chapter: the filter state is remembered.
   - Check the bottom bar in both portrait and landscape: five buttons, evenly spaced.

## Merge-conflict exposure

| File | Risk | Why |
|---|---|---|
| `dark_mode.svg` × 2 | None | New files |
| `ReaderBottomBar.kt` | Low | Small file; upstream rarely touches it |
| `ReaderAppBars.kt` | Low–medium | Its parameter list changes when upstream adds reader buttons |
| `ReaderActivity.kt` | Medium | Large, busy file. The edits are in two small, separate spots |

Put a `// Personal fork:` comment on the fork-only lines in `ReaderActivity.kt` and `ReaderAppBars.kt`, like the existing ones in `app/build.gradle.kts`, so they're easy to spot when resolving a conflict.
