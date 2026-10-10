# Refactor: move the brightness toggle into its own fork package

## Goal

Shrink the toggle button's footprint in upstream files to one import and one line, the same as the brightness schedule. Behaviour stays the same: a moon button in the middle of the reader's bottom bar switches `ReaderPreferences.customBrightness`, filled when on, with an "On"/"Off" toast.

## Today (commit `b32aa6130`)

| Upstream file | Fork lines | Why they exist |
|---|---|---|
| `ReaderBottomBar.kt` | 16 | Two new parameters plus the `IconButton` |
| `ReaderAppBars.kt` | 5 | Passes the two parameters through; no logic |
| `ReaderActivity.kt` | 10 | Reads the preference, toggles it, shows the toast |
| `icons/material-symbols/…/dark_mode.svg` ×2 | new files | Moon icons, generated into `MaterialSymbols` |

The parameter edits in `ReaderAppBars` and `ReaderBottomBar` are the likeliest conflicts, because upstream changes those parameter lists whenever it adds or removes a reader button.

## After

New package `app/src/main/java/mihon/fork/brightnesstoggle/`:

| File | Contents |
|---|---|
| `BrightnessToggleButton.kt` | `@Composable fun BrightnessToggleButton()`. Gets `ReaderPreferences` itself via `LocalContext.current.appGraph.readerPreferences`, the same way the settings screens do. Reads the state with `collectAsState`, and on click calls `toggle()` and shows the toast |
| `BrightnessToggleIcons.kt` | The two moon `ImageVector`s, copied from the Kotlin Valkyrie generated out of the SVGs, with the Apache licence note |

Upstream file changes:

| File | Change |
|---|---|
| `ReaderBottomBar.kt` | One import, plus `BrightnessToggleButton()` between the Orientation and Crop buttons, marked `// Personal fork:` |
| `ReaderAppBars.kt` | Back to upstream |
| `ReaderActivity.kt` | Button lines and the `toggle` import removed. The watcher fix (`0a463bb05`) and the schedule hook stay |
| `icons/material-symbols/…/dark_mode.svg` ×2 | Deleted; the icons now live in the fork package |

### Why no Metro binding this time

The button only needs `ReaderPreferences`, and `AppGraph` already exposes that (`AppGraph.kt:86`). A `@ContributesTo` interface would add a file to reach something that's already reachable. The schedule needed one because its own `BrightnessSchedule` class isn't on `AppGraph`.

### Behaviour differences

- **Toast:** the button keeps its own toast handle instead of sharing the activity's `menuToggleToast`. Tapping Crop and then the moon quickly can show both toasts briefly instead of replacing one with the other. Nothing else changes.
- **None otherwise:** same preference, icon, position and label.

### Why copy the icons instead of keeping the SVGs

SVGs in upstream's icon folder can only conflict if upstream adds its own `dark_mode.svg`, but that's plausible, and its header would likely differ from ours. As Kotlin in the fork package, the icons are outside upstream's folders entirely. The cost is ~40 lines of path data that never change.

## Not touched

- **The watcher fix** in `ReaderActivity.kt` stays as its own commit. It fixes upstream code, so the cleanest way to drop it from the fork is to send it upstream as a pull request. After that's merged, a rebase removes it.
- **The schedule** is unchanged. It writes the same preference, so the button's icon follows scheduled changes as it does now.

## Verification

1. `./gradlew :app:spotlessCheck assembleRelease`.
2. `git diff upstream/main --stat -- app/src/main/java/eu icons/`. Only `ReaderBottomBar.kt` (+2), `SettingsReaderScreen.kt` (+2) and `ReaderActivity.kt` (watcher fix plus the schedule hook) should be listed.
3. Install, then on the tablet:
   - The moon button sits in the middle of the bottom bar.
   - Tapping it switches the filter and shows "On"/"Off".
   - The icon follows the Filter-tab checkbox and scheduled switches.

One commit: "Move the brightness toggle into its own fork package".
