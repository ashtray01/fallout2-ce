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

An Android pen/eraser is identified by tool type and sent through a separate absolute-pointer path. Finger input keeps the upstream trackpad controls.

- Tap: place the cursor at the pen position and issue one left click.
- Hold still for 500 ms: issue one right click, with no extra left click on release.
- Move at least 8 game pixels while touching: hold left to drag from the original position; lift to release.
- Two taps remain two left clicks; a right-click mapping does not delay single taps.
- Cancellation/focus loss releases input without turning the interrupted stroke into a tap. Palm events during a pen stroke are ignored.

C++ regression tests preserve each press/release edge even when a full tap arrives between frames, and coalesce high-rate motion. Android tests exercise pen coordinates, cancellation and palm routing. Physical pen behavior still needs checking on the target Honor devices.
