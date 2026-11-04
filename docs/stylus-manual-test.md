# Manual stylus checklist (physical Android tablet)

Automated tests cannot verify how a particular digitizer reports hover, pressure or the side button. Run this checklist on every target tablet before calling a build device-verified.

Tested hardware for this release: none. The build is verified on Linux CI and with Robolectric only.

## Preparation

1. Install the APK from the release and import a legally owned Fallout 2 installation.
2. Start a new game and reach the map (outside combat).
3. Keep a Bluetooth mouse and a keyboard nearby for the compatibility checks.

## Pen positioning

- [ ] Hover the pen 1–2 cm above the screen: the game cursor follows the tip with no visible offset.
- [ ] Hover into the black letterbox area and back: the cursor stays under the pen and is clipped by the game area.
- [ ] Tap a ground tile: the character moves there after one click, with no cursor jump before the click.
- [ ] Rotate the tablet to the other landscape side (if the ROM/launcher allows it): the cursor stays aligned with the pen.
- [ ] Tap near all four screen edges and corners: the cursor reaches the edge without a systematic offset.

## Cursor switching

- [ ] Outside combat, press the side button once: the cursor changes Movement → Command.
- [ ] Press again: Command → Movement.
- [ ] In combat with a weapon drawn, press the side button twice: Movement → Command → Targeting.
- [ ] Press again: Targeting → Movement.
- [ ] Touch the screen and press the side button immediately (within ~200 ms): exactly one cursor switch, and the tap does not move the character or perform an action.
- [ ] Touch and hold for one second, then press the side button: the hold keeps its stroke/menu and no right click happens.
- [ ] Press and hold the side button for two seconds: exactly one cursor switch.
- [ ] If the button does nothing, capture `adb logcat -s SDL` while pressing it and attach the lines: pen events, button/scroll events and stylus key events (308–310) are logged with source, tool type, button state and device id. No line means Android does not deliver the button to the app.

## Action menu (hold)

- [ ] Select the Command cursor and hold the pen on an NPC: the original vertical action menu opens after about a quarter of a second.
- [ ] While still touching, move the pen up and down: the highlighted entry follows the pen.
- [ ] Lift the pen: the highlighted action is executed once.
- [ ] Hold on an item on the ground: Use / Look / Cancel entries appear and work.
- [ ] Hold on a container: Inventory and Use Skill entries appear and the inventory opens.
- [ ] Hold on the player character: the Rotate action appears and rotates the character.
- [ ] Hold on empty ground with the Command cursor: the menu does not open, and lifting does not trigger an unrelated action.
- [ ] Repeat the hold menu in the inventory (drag an item onto another item) and in trading.
- [ ] Cancel a hold with a system gesture (three-finger swipe / navigation gesture): no action is executed and the next tap works normally.

## Text and dialogs

- [ ] Talk to an NPC and choose dialog lines with the pen.
- [ ] Loot a corpse with the pen, drag items between the two inventory lists.
- [ ] Open the Pip-Boy with the pen, switch tabs and close it.

## Compatibility

- [ ] Finger input still uses the original trackpad controls (tap, two-finger right click, two-finger scroll).
- [ ] Rest the palm on the screen while writing/tapping with the pen: no extra clicks or character movement.
- [ ] Lift the pen and immediately tap with a finger: finger input works without restarting the app.
- [ ] Connect a Bluetooth mouse: cursor movement, left click and right click still work.
- [ ] Connect a keyboard: shortcuts and text input still work.
- [ ] Background the app during a pen stroke (Home), return: no button is stuck and no action is replayed.
- [ ] Lock/unlock the screen during a stroke: same as above.
- [ ] Enter and leave combat while using the pen; no forced Targeting cursor appears outside combat.
- [ ] Save, load and restart the app: no stylus state is persisted incorrectly.

## Report

Record the tablet model, pen model, Android version and the result of each section. If a device does not report hover or the side button to Android, note it explicitly: the app cannot emulate hardware that the digitizer does not expose.
