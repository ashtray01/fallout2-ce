package com.alexbatalov.fallout2ce;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libsdl.app.SDLSurface;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class StylusSurfaceTest {
    static class Surface extends SDLSurface {
        final List<float[]> events = new ArrayList<>();
        Surface(Context context) {
            super(context);
            mWidth = 1000;
            mHeight = 500;
        }
        @Override protected void onStylusTouch(int id, int action, float x, float y, float pressure) {
            events.add(new float[]{id, action, x, y, pressure});
        }
    }

    private MotionEvent event(int action, boolean palm) {
        int count = palm ? 2 : 1;
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; ++i) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = i == 0 ? MotionEvent.TOOL_TYPE_STYLUS : MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = 250;
            coords[i].y = 200;
            coords[i].pressure = 1;
        }
        return MotionEvent.obtain(0, 10, action, count, props, coords, 0, 0,
            1, 1, 7, 0, InputDevice.SOURCE_STYLUS, 0);
    }

    private void send(Surface surface, int action, boolean palm) {
        MotionEvent event = event(action, palm);
        try { assertTrue(surface.onTouch(surface, event)); }
        finally { event.recycle(); }
    }

    @Test public void penUsesAbsoluteNormalizedCoordinatesWithoutDuplicateTouch() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, MotionEvent.ACTION_DOWN, false);
        send(surface, MotionEvent.ACTION_UP, false);
        assertEquals(2, surface.events.size());
        assertArrayEquals(new float[]{0, MotionEvent.ACTION_DOWN, .25f, .4f, 1}, surface.events.get(0), .0001f);
        assertEquals(MotionEvent.ACTION_UP, (int)surface.events.get(1)[1]);
    }

    @Test public void cancellationAndFocusLossAreMarkedRatherThanClicked() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, MotionEvent.ACTION_DOWN, false);
        send(surface, MotionEvent.ACTION_CANCEL, false);
        assertEquals(-1f, surface.events.get(1)[4], 0);
        send(surface, MotionEvent.ACTION_DOWN, false);
        surface.onWindowFocusChanged(false);
        assertEquals(4, surface.events.size());
        assertEquals(-1f, surface.events.get(3)[4], 0);
    }

    @Test public void palmDoesNotCreateASecondGestureDuringPenStroke() {
        Surface surface = new Surface(RuntimeEnvironment.getApplication());
        send(surface, MotionEvent.ACTION_DOWN, false);
        send(surface, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), true);
        send(surface, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), true);
        send(surface, MotionEvent.ACTION_UP, false);
        assertEquals(2, surface.events.size());
    }
}
