package com.alexbatalov.fallout2ce;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsdl.app.SDLSurface;
import org.libsdl.app.StylusConfig;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class StylusSurfaceTest {
    private static final int STYLUS_TOUCH_DEVICE_ID = -1398036812;
    private static final int STYLUS_HOVER_DEVICE_ID = -1398036811;
    private static final int STYLUS_BUTTON_DEVICE_ID = -1398036810;

    private static final int BUTTON_PRIMARY = 1 << 0;
    private static final int BUTTON_SECONDARY = 1 << 1;
    private static final int BUTTON_STYLUS_PRIMARY = 1 << 5;

    private static final int ACTION_BUTTON_PRESS = 11;
    private static final int ACTION_BUTTON_RELEASE = 12;
    private static final int KEYCODE_STYLUS_BUTTON_PRIMARY = 308;

    static final class StylusRecord {
        final int deviceId;
        final int pointerId;
        final int action;
        final float x;
        final float y;
        final float pressure;

        StylusRecord(int deviceId, int pointerId, int action, float x, float y, float pressure) {
            this.deviceId = deviceId;
            this.pointerId = pointerId;
            this.action = action;
            this.x = x;
            this.y = y;
            this.pressure = pressure;
        }
    }

    static class Surface extends SDLSurface {
        final List<StylusRecord> events = new ArrayList<>();
        final List<Integer> fingerActions = new ArrayList<>();

        Surface(Context context) {
            super(context);
            mWidth = 1000;
            mHeight = 500;
        }

        @Override protected void onStylusTouch(int deviceId, int pointerId, int action,
                                               float x, float y, float pressure) {
            events.add(new StylusRecord(deviceId, pointerId, action, x, y, pressure));
        }

        @Override public boolean onTouch(View v, MotionEvent event) {
            fingerActions.add(event.getActionMasked());
            return true;
        }

        int count(int deviceId, int action) {
            int result = 0;
            for (StylusRecord event : events) {
                if (event.deviceId == deviceId && event.action == action) ++result;
            }
            return result;
        }

        void configure(StylusConfig config) {
            setStylusConfig(config);
        }
    }

    private static MotionEvent make(int action, int[] ids, int[] tools,
                                    float[] xs, float[] ys, int buttonState, int source) {
        return make(7, action, ids, tools, xs, ys, buttonState, source);
    }

    private static MotionEvent make(int deviceId, int action, int[] ids, int[] tools,
                                    float[] xs, float[] ys, int buttonState, int source) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; ++i) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = ids[i];
            props[i].toolType = tools[i];
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xs[i];
            coords[i].y = ys[i];
            coords[i].pressure = 1;
        }
        return MotionEvent.obtain(0, 10, action, ids.length, props, coords, 0, buttonState,
                1, 1, deviceId, 0, source, 0);
    }

    private static MotionEvent pen(int action, float x, float y) {
        return pen(action, x, y, 0);
    }

    private static MotionEvent pen(int action, float x, float y, int buttonState) {
        return make(action, new int[]{0}, new int[]{MotionEvent.TOOL_TYPE_STYLUS},
                new float[]{x}, new float[]{y}, buttonState, InputDevice.SOURCE_STYLUS);
    }

    private static MotionEvent hover(int action, float x, float y) {
        return pen(action, x, y);
    }

    private static void send(Surface surface, MotionEvent event) {
        try { assertTrue(surface.dispatchTouchEvent(event)); }
        finally { event.recycle(); }
    }

    private static void sendGeneric(Surface surface, MotionEvent event) {
        try { assertTrue(surface.dispatchGenericMotionEvent(event)); }
        finally { event.recycle(); }
    }

    @Test public void penDownPressesImmediatelyAtAbsoluteCoordinates() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));
        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));

        assertEquals(2, surface.events.size());
        StylusRecord down = surface.events.get(0);
        assertEquals(STYLUS_TOUCH_DEVICE_ID, down.deviceId);
        assertEquals(MotionEvent.ACTION_DOWN, down.action);
        assertEquals(.25f, down.x, .0001f);
        assertEquals(.4f, down.y, .0001f);
        assertEquals(MotionEvent.ACTION_UP, surface.events.get(1).action);
        assertEquals(0, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
    }

    @Test public void holdKeepsLeftButtonWithoutExtraClicks() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));
        for (int i = 0; i < 30; ++i) {
            send(surface, pen(MotionEvent.ACTION_MOVE, 250, 200));
        }

        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_DOWN));
        assertEquals(30, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_MOVE));
        assertEquals(0, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
        assertEquals(0, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));
        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
    }

    @Test public void hoverMovesPointerWithoutPressing() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        sendGeneric(surface, hover(MotionEvent.ACTION_HOVER_ENTER, 250, 200));
        sendGeneric(surface, hover(MotionEvent.ACTION_HOVER_MOVE, 400, 100));
        sendGeneric(surface, hover(MotionEvent.ACTION_HOVER_EXIT, 400, 100));

        assertEquals(0, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_DOWN));
        assertEquals(0, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
        assertEquals(2, surface.count(STYLUS_HOVER_DEVICE_ID, MotionEvent.ACTION_MOVE));
        assertEquals(1, surface.count(STYLUS_HOVER_DEVICE_ID, MotionEvent.ACTION_UP));

        StylusRecord move = surface.events.get(1);
        assertEquals(STYLUS_HOVER_DEVICE_ID, move.deviceId);
        assertEquals(.4f, move.x, .0001f);
        assertEquals(.2f, move.y, .0001f);
    }

    @Test public void sideButtonIsExactlyOneRightClickPerPhysicalEdge() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 250, 200, BUTTON_STYLUS_PRIMARY));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_UP));

        // Holding the button and the matching release must not click again.
        sendGeneric(surface, pen(MotionEvent.ACTION_HOVER_MOVE, 250, 200, BUTTON_STYLUS_PRIMARY));
        sendGeneric(surface, pen(ACTION_BUTTON_RELEASE, 250, 200, 0));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        // A new press after release works again.
        ShadowSystemClock.advanceBy(Duration.ofMillis(200));
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 300, 210, BUTTON_STYLUS_PRIMARY));
        assertEquals(2, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        StylusRecord click = surface.events.get(0);
        assertEquals(STYLUS_BUTTON_DEVICE_ID, click.deviceId);
        assertEquals(.25f, click.x, .0001f);
        assertEquals(.4f, click.y, .0001f);
    }

    @Test public void sideButtonTranslatedToSecondaryAlsoClicks() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 100, 50, BUTTON_SECONDARY));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
    }

    @Test public void duplicateDeliveryDoesNotDoubleClick() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 250, 200, BUTTON_STYLUS_PRIMARY));
        // The same physical edge delivered once more through another channel.
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 250, 200, BUTTON_STYLUS_PRIMARY));
        send(surface, pen(ACTION_BUTTON_PRESS, 250, 200, BUTTON_STYLUS_PRIMARY));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
    }

    @Test public void sideButtonDuringYoungContactCancelsStrokeAndClicks() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 250, 200, BUTTON_STYLUS_PRIMARY));

        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
        // The contact stroke is cancelled (negative pressure) and never clicks.
        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
        assertEquals(-1f, surface.events.get(surface.events.size() - 1).pressure, 0);

        // The real pen-up must not create another left click.
        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));
        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
    }

    @Test public void sideButtonAfterGraceKeepsTheStroke() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(300));
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));
        ShadowSystemClock.advanceBy(Duration.ofMillis(300));
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 250, 200, BUTTON_STYLUS_PRIMARY));

        assertEquals(0, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
        assertEquals(0, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));

        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));
        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
    }

    @Test public void stylusButtonKeyProducesOneRightClick() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        assertTrue(surface.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KEYCODE_STYLUS_BUTTON_PRIMARY)));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        // Key repeat must not click again.
        assertTrue(surface.dispatchKeyEvent(new KeyEvent(0, 10, KeyEvent.ACTION_DOWN,
                KEYCODE_STYLUS_BUTTON_PRIMARY, 1)));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        assertTrue(surface.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KEYCODE_STYLUS_BUTTON_PRIMARY)));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        ShadowSystemClock.advanceBy(Duration.ofMillis(300));
        assertTrue(surface.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KEYCODE_STYLUS_BUTTON_PRIMARY)));
        assertEquals(2, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
    }

    @Test public void pensReportingButtonAsScrollStillClickOncePerBurst() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        // Identify the pen device first (device id 7 in `make`).
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));
        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));

        sendGeneric(surface, make(MotionEvent.ACTION_SCROLL,
                new int[]{0}, new int[]{MotionEvent.TOOL_TYPE_MOUSE},
                new float[]{250}, new float[]{200}, 0, InputDevice.SOURCE_MOUSE));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        // The rest of the scroll burst is the same physical press.
        sendGeneric(surface, make(MotionEvent.ACTION_SCROLL,
                new int[]{0}, new int[]{MotionEvent.TOOL_TYPE_MOUSE},
                new float[]{250}, new float[]{200}, 0, InputDevice.SOURCE_MOUSE));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));

        ShadowSystemClock.advanceBy(Duration.ofMillis(300));
        sendGeneric(surface, make(MotionEvent.ACTION_SCROLL,
                new int[]{0}, new int[]{MotionEvent.TOOL_TYPE_MOUSE},
                new float[]{250}, new float[]{200}, 0, InputDevice.SOURCE_MOUSE));
        assertEquals(2, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
    }

    @Test public void buttonPressWithoutReportedStateStillClicks() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 250, 200, 0));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
    }

    @Test public void primaryButtonOnHoverClicksButPrimaryOnTouchDoesNot() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        // A tip touch must never be mistaken for the barrel button.
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200, BUTTON_PRIMARY));
        assertEquals(0, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));

        // While hovering, some pens map the barrel button to BUTTON_PRIMARY.
        sendGeneric(surface, pen(MotionEvent.ACTION_HOVER_ENTER, 250, 200, BUTTON_PRIMARY));
        assertEquals(1, surface.count(STYLUS_BUTTON_DEVICE_ID, MotionEvent.ACTION_DOWN));
    }

    @Test public void cancellationAndFocusLossAreMarkedRatherThanClicked() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));
        send(surface, pen(MotionEvent.ACTION_CANCEL, 250, 200));
        assertEquals(-1f, surface.events.get(1).pressure, 0);

        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));
        surface.onWindowFocusChanged(false);
        assertEquals(4, surface.events.size());
        assertEquals(MotionEvent.ACTION_UP, surface.events.get(3).action);
        assertEquals(-1f, surface.events.get(3).pressure, 0);
    }

    @Test public void palmDoesNotInterruptPenStroke() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));

        MotionEvent palmDown = make(
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{0, 1}, new int[]{MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER},
                new float[]{250, 300}, new float[]{200, 300}, 0, InputDevice.SOURCE_STYLUS);
        try { assertTrue(surface.dispatchTouchEvent(palmDown)); }
        finally { palmDown.recycle(); }

        MotionEvent palmUp = make(
                MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{0, 1}, new int[]{MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER},
                new float[]{250, 300}, new float[]{200, 300}, 0, InputDevice.SOURCE_STYLUS);
        try { assertTrue(surface.dispatchTouchEvent(palmUp)); }
        finally { palmUp.recycle(); }

        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));
        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_DOWN));
        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
        assertEquals(0, surface.fingerActions.size());
    }

    @Test public void palmDownBeforePenDoesNotBlockPenMovement() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        MotionEvent palmDown = make(MotionEvent.ACTION_DOWN,
                new int[]{0}, new int[]{MotionEvent.TOOL_TYPE_FINGER},
                new float[]{500}, new float[]{400}, 0, InputDevice.SOURCE_TOUCHSCREEN);
        try { surface.dispatchTouchEvent(palmDown); }
        finally { palmDown.recycle(); }

        MotionEvent penDown = make(
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{0, 1}, new int[]{MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS},
                new float[]{500, 250}, new float[]{400, 200}, 0, InputDevice.SOURCE_STYLUS);
        try { assertTrue(surface.dispatchTouchEvent(penDown)); }
        finally { penDown.recycle(); }

        MotionEvent move = make(MotionEvent.ACTION_MOVE,
                new int[]{0, 1}, new int[]{MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS},
                new float[]{510, 300}, new float[]{410, 250}, 0, InputDevice.SOURCE_STYLUS);
        try { assertTrue(surface.dispatchTouchEvent(move)); }
        finally { move.recycle(); }

        StylusRecord last = surface.events.get(surface.events.size() - 1);
        assertEquals(MotionEvent.ACTION_MOVE, last.action);
        assertEquals(.3f, last.x, .0001f);
        assertEquals(.5f, last.y, .0001f);
    }

    @Test public void mouseMoveDuringPenStrokeDoesNotCancelIt() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));

        // Pointer ids are per event: this mouse uses another device and id 0.
        MotionEvent mouseMove = make(8, MotionEvent.ACTION_MOVE,
                new int[]{0}, new int[]{MotionEvent.TOOL_TYPE_MOUSE},
                new float[]{400}, new float[]{300}, 0, InputDevice.SOURCE_MOUSE);
        try { surface.dispatchGenericMotionEvent(mouseMove); }
        finally { mouseMove.recycle(); }

        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_DOWN));
        assertEquals(0, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
        assertEquals(1, surface.events.size());

        send(surface, pen(MotionEvent.ACTION_UP, 250, 200));
        assertEquals(1, surface.count(STYLUS_TOUCH_DEVICE_ID, MotionEvent.ACTION_UP));
    }

    @Test public void fingerAndMouseInputKeepUpstreamRouting() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        MotionEvent finger = make(MotionEvent.ACTION_DOWN,
                new int[]{0}, new int[]{MotionEvent.TOOL_TYPE_FINGER},
                new float[]{250}, new float[]{200}, 0, InputDevice.SOURCE_TOUCHSCREEN);
        try { assertTrue(surface.dispatchTouchEvent(finger)); }
        finally { finger.recycle(); }

        assertEquals(1, surface.fingerActions.size());
        assertEquals(MotionEvent.ACTION_DOWN, (int)surface.fingerActions.get(0));
        assertEquals(0, surface.events.size());
    }

    @Test public void disabledPenFallsBackToTouchRouting() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        surface.configure(new StylusConfig(false, true, true, true, true));
        send(surface, pen(MotionEvent.ACTION_DOWN, 250, 200));

        assertEquals(1, surface.fingerActions.size());
        assertEquals(0, surface.events.size());
    }

    @Test public void disabledHoverAndSideButtonDoNothing() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        surface.configure(new StylusConfig(true, true, false, false, true));
        sendGeneric(surface, hover(MotionEvent.ACTION_HOVER_ENTER, 250, 200));
        sendGeneric(surface, pen(ACTION_BUTTON_PRESS, 250, 200, BUTTON_STYLUS_PRIMARY));

        assertEquals(0, surface.events.size());
    }
}

