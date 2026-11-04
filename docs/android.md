# Android build and verification

## Import fix

A Windows `fallout2.cfg` can contain absolute archive paths such as `C:\Program Files\1C\Fallout2\critter.dat`, even when the file exists beside it. The old importer copied that configuration unchanged, ignored copy failures, and skipped archive checks whenever the configuration existed.

The launcher now checks both nonempty readable archives before creating the SDL activity. Imports accept mixed-case names, validate the selected folder, propagate provider/copy errors, check known source sizes, and use atomic per-file writes. An import marker blocks startup after failure or process death. Re-selecting the source folder retries the import. Existing savegame directories are skipped to avoid merging or overwriting saves.

Only Windows absolute paths with a known local equivalent are repaired: the two base DAT paths, vanilla `data` patch paths, and available music directories. Relative/custom mod paths and unrelated settings remain intact. The original configuration is backed up once. This repair also runs for existing complete installations.

## Build

- JDK 17
- Gradle 8.11.1 (wrapper), Android Gradle Plugin 8.9.2
- SDK 35 / Build Tools 35.0.0; target API 35, minimum API 21
- NDK 28.2.13676358, CMake 3.22.1
- SDL 2.32.10 on Android, with matching upstream Java sources (desktop versions unchanged). Local Java adjustments add Android 13+ USB receiver flags and annotate the existing API 26 dispatch classes. Lint permission exceptions are limited to unused/gated upstream Bluetooth and capture bridges.
- ARMv7, ARM64, x86, x86-64 in one universal APK

```sh
cd os/android
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease
```

For a signed release, provide ignored `os/android/release.keystore` and `release-keystore.properties` with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`. Keep this key for future updates; do not commit it. The fork's Android workflow reads signing material from GitHub Actions secrets. Its reports and APK are retained as workflow artifacts.

Verify the release:

```sh
apksigner verify --verbose os/android/app/build/outputs/apk/release/app-release.apk
zipalign -c -P 16 -v 4 os/android/app/build/outputs/apk/release/app-release.apk
python3 tools/check_android_apk.py os/android/app/build/outputs/apk/release/app-release.apk
```

The NDK and packaging versions support [16 KB pages](https://developer.android.com/guide/practices/page-sizes); the checks above verify binary and APK alignment, not gameplay on every device.

## Regression and device checks

Robolectric startup checks run against APIs 28 and 35. The API 35 replace-existing-file test runs on Linux CI; Windows skips that one case because its host filesystem lacks POSIX rename-over-existing semantics.

Unit tests cover missing, empty and mixed-case archives, interrupted-import markers, portable configuration paths, byte-for-byte copying, unknown source sizes, and truncated/unreadable sources without corrupting the destination.

Before calling a release device-verified, check on the target phone/tablet:

1. First launch opens the folder picker. Cancel exits cleanly.
2. Import the installed game folder with its original Windows configuration; reach the main menu.
3. An incorrect folder gives a retry dialog. A permission/storage failure does not launch the engine.
4. Kill the app during import, relaunch and retry; no partial game starts.
5. Background/foreground the import and rotate the device; no duplicate import or crash.
6. Start a new game, verify Russian text if supplied by the installation, music, touch controls, save/load and cold restart.
7. Check fullscreen layout on both phone and tablet and, where available, on a 16 KB emulator/device.

Commercial data stays local under ignored `/Fallout2/`. For the supplied installation, both DAT tables and every compressed entry were validated locally: 23,120 master entries and 7,120 critter entries. All six Windows paths in its configuration were successfully rewritten in a read-only probe. This is not a substitute for device gameplay testing.

## Stylus controls

An Android pen/eraser is identified by tool type (or by `SOURCE_STYLUS`) and sent through a separate absolute-pointer path. Finger input keeps the upstream trackpad controls; a Bluetooth mouse and the keyboard are not affected.

The pen drives the game cursor with the same mouse state stream a desktop mouse produces. It does not synthesize Android events at the OS level and needs no root access.

- **Hover** (`ACTION_HOVER_ENTER`/`MOVE`/`EXIT`): the game cursor follows the pen tip without pressing a button. Hovering over an object shows its default action and information exactly as on PC.
- **Tap** (`ACTION_DOWN`/`MOVE`/`UP`): the left button is pressed immediately at the pen position and released on lift, so a tap is one normal left click and a drag holds the left button.
- **Side button**: one physical press is exactly one right click. All Android channels are accepted: `BUTTON_STYLUS_PRIMARY`, `BUTTON_STYLUS_SECONDARY`, `BUTTON_SECONDARY`, `BUTTON_PRIMARY`/`BUTTON_TERTIARY` while hovering, `ACTION_BUTTON_PRESS`/`ACTION_BUTTON_RELEASE` even when `buttonState` is not reported, Android 14+ stylus key events (`KEYCODE_STYLUS_BUTTON_PRIMARY`/`SECONDARY`/`TERTIARY`, 308–310), and barrel buttons that some digitizers report as `ACTION_SCROLL` from the pen device. Right click cycles the original cursors: Movement → Command → Movement outside combat and Movement → Command → Targeting → Movement in combat. The engine still decides whether Targeting is allowed (weapons, combat, active item); the mod does not force it.
- **Hold** (about 250 ms, the engine's own `BUTTON_REPEAT_TIME`): the left button stays held, so Fallout's original action menu opens. Moving the pen up or down while still touching highlights the original menu entries; lifting the pen executes the selected action. There is no extra synthetic click after the menu closes.
- **Context menus** in the inventory, trading and other screens use the same mouse sequence and keep working.
- **Palm rejection**: while a pen stroke is active, finger pointers are ignored. The pen is never blocked permanently: cancellation, focus loss, backgrounding and surface destruction release the button, and finger input resumes immediately afterwards.
- **Absolute positioning**: `MotionEvent` coordinates are normalised by the current surface size, so letterboxing, the internal Fallout resolution and both orientations are handled by the existing rendering transform.

Side-button priority is deterministic. While the pen is in hover range, every edge is a right click. If the edge arrives in the first 200 ms of contact, it still becomes a right click and the young left stroke is cancelled: the native side releases left and presses right in the same engine update, so the engine handles the right click before it can act on the released left button. After that window the engine's repeat timer may own the stroke (the action menu), so the edge is ignored to protect the menu. Without this rule a right click during a held left button could execute an unrelated action in the newly selected cursor mode.

Some pens do not expose hover or a side button to Android at all, and some digitizers translate the button to `BUTTON_SECONDARY` instead of `BUTTON_STYLUS_PRIMARY`; the code supports both cases, but the hardware cannot be emulated. The arrow/hex cursor in game always follows the pen when absolute positioning is available.

For diagnosing a specific pen, `adb logcat -s SDL` prints pen down/up/cancel, button/scroll events, and stylus key events with their source, tool type, button state and device id. One line per press is expected; no line at all means Android does not deliver the button to the app.


### Pen input configuration

There is no input settings screen in the app. Defaults are defined in `StylusConfig` and are the recommended values:

| Key | Default | Effect |
| --- | --- | --- |
| `stylus_enabled` | `true` | Master switch for the pen path. When off, the pen falls back to the finger/trackpad controls. |
| `stylus_absolute_positioning` | `true` | Absolute cursor positioning. When off, the pen uses the finger/trackpad path. |
| `stylus_side_button_right_click` | `true` | Maps the side button to a right click. |
| `stylus_hover_enabled` | `true` | Follows the pen in hover range. |
| `stylus_palm_rejection` | `true` | Ignores finger pointers during a pen stroke. |

The values are read once from the `stylus` `SharedPreferences` file with these defaults; a future settings screen can override them without touching the input code.

C++ regression tests cover press/hold/release ordering, the action-menu hold, hover, cancellation, drag and side-button edges. Robolectric tests exercise absolute coordinates, side-button deduplication, palm routing and backward compatibility.

Physical pen behavior still needs checking on the target tablets; see [the manual stylus checklist](stylus-manual-test.md).

