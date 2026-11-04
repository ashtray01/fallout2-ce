package org.libsdl.app;


import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;


/**
    SDLSurface. This is what we draw on, so we need to know when it's created
    in order to do anything useful.

    Because of this, that's where we set up the SDL thread
*/
public class SDLSurface extends SurfaceView implements SurfaceHolder.Callback,
    View.OnKeyListener, View.OnTouchListener, SensorEventListener  {

    // Fallout: pen input bypasses the finger-trackpad recognizer and drives the
    // game cursor as an absolute mouse pointer. The three synthetic SDL touch
    // channels must match src/stylus.h.
    private static final int STYLUS_TOUCH_DEVICE_ID = -1398036812;
    private static final int STYLUS_HOVER_DEVICE_ID = -1398036811;
    private static final int STYLUS_BUTTON_DEVICE_ID = -1398036810;

    // MotionEvent stylus button symbols appeared after the minimum SDK. The
    // numeric values are stable and are inlined by the compiler.
    private static final int BUTTON_PRIMARY_VALUE = 1 << 0;
    private static final int BUTTON_SECONDARY_VALUE = 1 << 1;
    private static final int BUTTON_TERTIARY_VALUE = 1 << 2;
    private static final int BUTTON_STYLUS_PRIMARY_VALUE = 1 << 5;
    private static final int BUTTON_STYLUS_SECONDARY_VALUE = 1 << 6;
    private static final int STYLUS_BUTTON_MASK =
            BUTTON_STYLUS_PRIMARY_VALUE | BUTTON_STYLUS_SECONDARY_VALUE | BUTTON_SECONDARY_VALUE;
    // While hovering, a barrel button may be mapped to the primary or tertiary
    // mouse button. The tip alone does not produce hover, so this is safe.
    private static final int STYLUS_HOVER_BUTTON_MASK =
            STYLUS_BUTTON_MASK | BUTTON_PRIMARY_VALUE | BUTTON_TERTIARY_VALUE;
    private static final int ACTION_BUTTON_PRESS_VALUE = 11;
    private static final int ACTION_BUTTON_RELEASE_VALUE = 12;

    // Android 14+ Bluetooth pens report barrel buttons as key events.
    private static final int KEYCODE_STYLUS_BUTTON_PRIMARY_VALUE = 308;
    private static final int KEYCODE_STYLUS_BUTTON_SECONDARY_VALUE = 309;
    private static final int KEYCODE_STYLUS_BUTTON_TERTIARY_VALUE = 310;

    // A press right after contact may cancel the young left stroke. The engine
    // opens its action menu after 250 ms, so staying below that value guarantees
    // the stroke cannot turn into a menu selection.
    private static final long SIDE_BUTTON_CONTACT_GRACE_MS = 200;
    // One physical press can be reported by several Android channels at once.
    private static final long SIDE_BUTTON_DEDUP_MS = 120;
    // A scroll-based barrel button produces a burst; one burst is one press.
    private static final long PEN_SCROLL_RELEASE_MS = 250;

    private StylusConfig mStylusConfig = StylusConfig.DEFAULT;
    private int stylusPointer = -1;
    private int stylusDeviceId = -1;
    private float stylusX = 0.5f;
    private float stylusY = 0.5f;
    private long stylusContactUptime;
    private long lastSideButtonClickUptime = -SIDE_BUTTON_DEDUP_MS;
    private long lastPenScrollUptime = -PEN_SCROLL_RELEASE_MS;
    private boolean stylusHoverActive;
    private boolean stylusSideButtonDown;

    // Sensors
    protected SensorManager mSensorManager;
    protected Display mDisplay;

    // Keep track of the surface size to normalize touch events
    protected float mWidth, mHeight;

    // Is SurfaceView ready for rendering
    public boolean mIsSurfaceReady;

    // Startup
    public SDLSurface(Context context) {
        super(context);
        getHolder().addCallback(this);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setOnKeyListener(this);
        setOnTouchListener(this);

        mDisplay = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mStylusConfig = StylusConfig.load(context);

        setOnGenericMotionListener(SDLActivity.getMotionListener());

        // Some arbitrary defaults to avoid a potential division by zero
        mWidth = 1.0f;
        mHeight = 1.0f;

        mIsSurfaceReady = false;
    }

    public void handlePause() {
        cancelStylus();
        enableSensor(Sensor.TYPE_ACCELEROMETER, false);
    }

    public void handleResume() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setOnKeyListener(this);
        setOnTouchListener(this);
        enableSensor(Sensor.TYPE_ACCELEROMETER, true);
    }

    public Surface getNativeSurface() {
        return getHolder().getSurface();
    }

    // Called when we have a valid drawing surface
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v("SDL", "surfaceCreated()");
        SDLActivity.onNativeSurfaceCreated();
    }

    // Called when we lose the surface
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        cancelStylus();
        Log.v("SDL", "surfaceDestroyed()");

        // Transition to pause, if needed
        SDLActivity.mNextNativeState = SDLActivity.NativeState.PAUSED;
        SDLActivity.handleNativeState();

        mIsSurfaceReady = false;
        SDLActivity.onNativeSurfaceDestroyed();
    }

    // Called when the surface is resized
    @Override
    public void surfaceChanged(SurfaceHolder holder,
                               int format, int width, int height) {
        Log.v("SDL", "surfaceChanged()");

        if (SDLActivity.mSingleton == null) {
            return;
        }

        mWidth = width;
        mHeight = height;
        int nDeviceWidth = width;
        int nDeviceHeight = height;
        try
        {
            if (Build.VERSION.SDK_INT >= 17 /* Android 4.2 (JELLY_BEAN_MR1) */) {
                DisplayMetrics realMetrics = new DisplayMetrics();
                mDisplay.getRealMetrics( realMetrics );
                nDeviceWidth = realMetrics.widthPixels;
                nDeviceHeight = realMetrics.heightPixels;
            }
        } catch(Exception ignored) {
        }

        synchronized(SDLActivity.getContext()) {
            // In case we're waiting on a size change after going fullscreen, send a notification.
            SDLActivity.getContext().notifyAll();
        }

        Log.v("SDL", "Window size: " + width + "x" + height);
        Log.v("SDL", "Device size: " + nDeviceWidth + "x" + nDeviceHeight);
        SDLActivity.nativeSetScreenResolution(width, height, nDeviceWidth, nDeviceHeight, mDisplay.getRefreshRate());
        SDLActivity.onNativeResize();

        // Prevent a screen distortion glitch,
        // for instance when the device is in Landscape and a Portrait App is resumed.
        boolean skip = false;
        int requestedOrientation = SDLActivity.mSingleton.getRequestedOrientation();

        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT) {
            if (mWidth > mHeight) {
               skip = true;
            }
        } else if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            if (mWidth < mHeight) {
               skip = true;
            }
        }

        // Special Patch for Square Resolution: Black Berry Passport
        if (skip) {
           double min = Math.min(mWidth, mHeight);
           double max = Math.max(mWidth, mHeight);

           if (max / min < 1.20) {
              Log.v("SDL", "Don't skip on such aspect-ratio. Could be a square resolution.");
              skip = false;
           }
        }

        // Don't skip in MultiWindow.
        if (skip) {
            if (Build.VERSION.SDK_INT >= 24 /* Android 7.0 (N) */) {
                if (SDLActivity.mSingleton.isInMultiWindowMode()) {
                    Log.v("SDL", "Don't skip in Multi-Window");
                    skip = false;
                }
            }
        }

        if (skip) {
           Log.v("SDL", "Skip .. Surface is not ready.");
           mIsSurfaceReady = false;
           return;
        }

        /* If the surface has been previously destroyed by onNativeSurfaceDestroyed, recreate it here */
        SDLActivity.onNativeSurfaceChanged();

        /* Surface is ready */
        mIsSurfaceReady = true;

        SDLActivity.mNextNativeState = SDLActivity.NativeState.RESUMED;
        SDLActivity.handleNativeState();
    }

    // Key events
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return SDLActivity.handleKeyEvent(v, keyCode, event, null);
    }

    // Touch events
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (handleStylus(event)) return true;
        /* Ref: http://developer.android.com/training/gestures/multi.html */
        int touchDevId = event.getDeviceId();
        final int pointerCount = event.getPointerCount();
        int action = event.getActionMasked();
        int pointerFingerId;
        int i = -1;
        float x,y,p;

        /*
         * Prevent id to be -1, since it's used in SDL internal for synthetic events
         * Appears when using Android emulator, eg:
         *  adb shell input mouse tap 100 100
         *  adb shell input touchscreen tap 100 100
         */
        if (touchDevId < 0) {
            touchDevId -= 1;
        }

        // 12290 = Samsung DeX mode desktop mouse
        // 12290 = 0x3002 = 0x2002 | 0x1002 = SOURCE_MOUSE | SOURCE_TOUCHSCREEN
        // 0x2   = SOURCE_CLASS_POINTER
        if (event.getSource() == InputDevice.SOURCE_MOUSE || event.getSource() == (InputDevice.SOURCE_MOUSE | InputDevice.SOURCE_TOUCHSCREEN)) {
            int mouseButton = 1;
            try {
                Object object = event.getClass().getMethod("getButtonState").invoke(event);
                if (object != null) {
                    mouseButton = (Integer) object;
                }
            } catch(Exception ignored) {
            }

            // We need to check if we're in relative mouse mode and get the axis offset rather than the x/y values
            // if we are.  We'll leverage our existing mouse motion listener
            SDLGenericMotionListener_API12 motionListener = SDLActivity.getMotionListener();
            x = motionListener.getEventX(event);
            y = motionListener.getEventY(event);

            SDLActivity.onNativeMouse(mouseButton, action, x, y, motionListener.inRelativeMode());
        } else {
            switch(action) {
                case MotionEvent.ACTION_MOVE:
                    for (i = 0; i < pointerCount; i++) {
                        pointerFingerId = event.getPointerId(i);
                        x = event.getX(i) / mWidth;
                        y = event.getY(i) / mHeight;
                        p = event.getPressure(i);
                        if (p > 1.0f) {
                            // may be larger than 1.0f on some devices
                            // see the documentation of getPressure(i)
                            p = 1.0f;
                        }
                        SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_DOWN:
                    // Primary pointer up/down, the index is always zero
                    i = 0;
                    /* fallthrough */
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_POINTER_DOWN:
                    // Non primary pointer up/down
                    if (i == -1) {
                        i = event.getActionIndex();
                    }

                    pointerFingerId = event.getPointerId(i);
                    x = event.getX(i) / mWidth;
                    y = event.getY(i) / mHeight;
                    p = event.getPressure(i);
                    if (p > 1.0f) {
                        // may be larger than 1.0f on some devices
                        // see the documentation of getPressure(i)
                        p = 1.0f;
                    }
                    SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
                    break;

                case MotionEvent.ACTION_CANCEL:
                    for (i = 0; i < pointerCount; i++) {
                        pointerFingerId = event.getPointerId(i);
                        x = event.getX(i) / mWidth;
                        y = event.getY(i) / mHeight;
                        p = event.getPressure(i);
                        if (p > 1.0f) {
                            // may be larger than 1.0f on some devices
                            // see the documentation of getPressure(i)
                            p = 1.0f;
                        }
                        SDLActivity.onNativeTouch(touchDevId, pointerFingerId, MotionEvent.ACTION_UP, x, y, p);
                    }
                    break;

                default:
                    break;
            }
        }

        return true;
   }

    // Stylus events
    //
    // Android may deliver the same pen MotionEvent through the touch, generic
    // motion or hover dispatch paths depending on the device and API level.
    // All three are routed to one handler; the side button uses edge detection,
    // so one physical press cannot produce two right clicks.
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (handleStylus(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (handleStylus(event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        if (handleStylus(event)) {
            return true;
        }
        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleStylusButtonKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isPenPointer(MotionEvent event, int pointerIndex) {
        if (pointerIndex < 0 || pointerIndex >= event.getPointerCount()) {
            return false;
        }
        int toolType = event.getToolType(pointerIndex);
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
            stylusDeviceId = event.getDeviceId();
            return true;
        }
        if (toolType == MotionEvent.TOOL_TYPE_FINGER) {
            // Some digitizers merge palm pointers into a stylus-sourced event,
            // so a finger pointer must never become the pen.
            return false;
        }
        if ((event.getSource() & InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS) {
            stylusDeviceId = event.getDeviceId();
            return true;
        }
        // Some digitizers label the barrel button, hover or scroll of an already
        // seen pen as a mouse. The device id ties those events back to the pen;
        // fingers and real mice keep their own device ids.
        return stylusDeviceId != -1 && event.getDeviceId() == stylusDeviceId;
    }

    private int findPenPointerIndex(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); ++i) {
            if (isPenPointer(event, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean handleStylus(MotionEvent event) {
        if (!mStylusConfig.interceptPen()) {
            return false;
        }

        final int action = event.getActionMasked();
        final int index = event.getActionIndex();
        final boolean strokeActive = stylusPointer != -1;

        // ACTION_MOVE reports every pointer and does not identify the pen, so
        // its tool type cannot be taken from `getActionIndex()`. Pointer
        // down/up actions, on the other hand, always refer to the pointer that
        // actually changed.
        final boolean pen;
        int positionIndex = index;
        if (action == MotionEvent.ACTION_MOVE) {
            int tracked = strokeActive ? event.findPointerIndex(stylusPointer) : -1;
            if (tracked >= 0 && !isPenPointer(event, tracked)) {
                // Pointer ids are per event: a mouse or palm can reuse the id.
                tracked = -1;
            }
            if (tracked < 0 && !strokeActive) {
                tracked = findPenPointerIndex(event);
            }
            pen = tracked >= 0;
            if (tracked >= 0) {
                positionIndex = tracked;
            }
        } else {
            pen = isPenPointer(event, index);
        }

        if (pen && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
                || action == ACTION_BUTTON_PRESS_VALUE || action == ACTION_BUTTON_RELEASE_VALUE
                || action == MotionEvent.ACTION_SCROLL)) {
            Log.i("SDL", "stylus action=" + action + " source=" + event.getSource()
                    + " tool=" + event.getToolType(index) + " buttons=" + event.getButtonState()
                    + " device=" + event.getDeviceId());
        }

        if (!pen) {
            if (!strokeActive) {
                return false; // Fingers, mice and keyboards keep upstream routing.
            }
            // While the pen touches the screen, finger pointers are the palm.
            // A real mouse must stay usable, so only finger-like tools are
            // rejected and pen input is never blocked forever.
            int toolType = event.getToolType(index);
            boolean fingerLike = toolType == MotionEvent.TOOL_TYPE_FINGER
                    || toolType == MotionEvent.TOOL_TYPE_UNKNOWN;
            return mStylusConfig.palmRejection && fingerLike;
        }

        if (action == MotionEvent.ACTION_SCROLL) {
            // Some barrel buttons are reported as a scroll wheel. A real mouse
            // wheel has a different device id and still scrolls the game.
            long now = SystemClock.uptimeMillis();
            if (now - lastPenScrollUptime > PEN_SCROLL_RELEASE_MS) {
                sideButtonPressed(event.getX(index), event.getY(index));
            }
            lastPenScrollUptime = now;
            return true;
        }

        if (!strokeActive
                && action != MotionEvent.ACTION_DOWN
                && action != MotionEvent.ACTION_POINTER_DOWN
                && action != ACTION_BUTTON_PRESS_VALUE
                && action != ACTION_BUTTON_RELEASE_VALUE) {
            // No active stroke: hover moves the absolute cursor only.
            return handleStylusHover(event, action, positionIndex);
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (stylusPointer != -1) {
                    // A lost pen-up must not leave the left button stuck.
                    cancelStylus();
                }
                stylusPointer = event.getPointerId(index);
                stylusX = event.getX(index) / mWidth;
                stylusY = event.getY(index) / mHeight;
                stylusContactUptime = SystemClock.uptimeMillis();
                stylusHoverActive = false;
                // Contact presses immediately. Holding the pen then feeds the
                // engine's repeat timer, which opens the original action menu.
                sendStylus(STYLUS_TOUCH_DEVICE_ID, stylusPointer, MotionEvent.ACTION_DOWN,
                        stylusX, stylusY, 1.0f);
                // Done after the contact so a button already down at touch is
                // combined with the active stroke by the native side.
                updateStylusSideButton(event, event.getX(index), event.getY(index));
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                int penIndex = event.findPointerIndex(stylusPointer);
                if (penIndex < 0) {
                    cancelStylus();
                    return true;
                }
                stylusX = event.getX(penIndex) / mWidth;
                stylusY = event.getY(penIndex) / mHeight;
                updateStylusSideButton(event, event.getX(penIndex), event.getY(penIndex));
                sendStylus(STYLUS_TOUCH_DEVICE_ID, stylusPointer, MotionEvent.ACTION_MOVE,
                        stylusX, stylusY, clampPressure(event.getPressure(penIndex)));
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (event.getPointerId(index) != stylusPointer) {
                    return true; // A lifted palm must not end the pen stroke.
                }
                stylusX = event.getX(index) / mWidth;
                stylusY = event.getY(index) / mHeight;
                updateStylusSideButton(event, event.getX(index), event.getY(index));
                sendStylus(STYLUS_TOUCH_DEVICE_ID, stylusPointer, MotionEvent.ACTION_UP,
                        stylusX, stylusY, 1.0f);
                stylusPointer = -1;
                return true;
            }

            case MotionEvent.ACTION_CANCEL:
                cancelStylus();
                return true;

            case ACTION_BUTTON_PRESS_VALUE:
            case ACTION_BUTTON_RELEASE_VALUE:
                // The action identifies the edge even on devices that do not
                // populate `getButtonState()` on button events.
                updateStylusSideButton(event, event.getX(index), event.getY(index));
                return true;

            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_HOVER_EXIT:
                return handleStylusHover(event, action, positionIndex);

            default:
                return true;
        }
    }

    private boolean handleStylusHover(MotionEvent event, int action, int index) {
        float x = event.getX(index);
        float y = event.getY(index);
        // Barrel-button edges can also arrive on hover events. This must run
        // even when hover cursor tracking is disabled.
        updateStylusSideButton(event, x, y);
        if (!mStylusConfig.hover) {
            return true; // Consume pen events without moving the cursor.
        }

        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            if (stylusHoverActive) {
                sendStylus(STYLUS_HOVER_DEVICE_ID, 0, MotionEvent.ACTION_UP,
                        x / mWidth, y / mHeight, 1.0f);
                stylusHoverActive = false;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            stylusX = x / mWidth;
            stylusY = y / mHeight;
            stylusHoverActive = true;
            sendStylus(STYLUS_HOVER_DEVICE_ID, 0, MotionEvent.ACTION_MOVE,
                    stylusX, stylusY, 1.0f);
        }
        return true;
    }

    private void updateStylusSideButton(MotionEvent event, float x, float y) {
        final int action = event.getActionMasked();
        boolean pressed;
        if (action == ACTION_BUTTON_PRESS_VALUE) {
            // The action itself identifies the edge; some devices leave
            // `getButtonState()` at 0 on ACTION_BUTTON_PRESS.
            pressed = true;
        } else if (action == ACTION_BUTTON_RELEASE_VALUE) {
            pressed = false;
        } else {
            int mask = STYLUS_BUTTON_MASK;
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                mask = STYLUS_HOVER_BUTTON_MASK;
            }
            pressed = (event.getButtonState() & mask) != 0;
        }

        if (pressed == stylusSideButtonDown) {
            // Duplicate delivery of one physical edge, or a held button.
            return;
        }
        stylusSideButtonDown = pressed;

        if (pressed) {
            sideButtonPressed(x, y);
        }
    }

    private boolean sideButtonPressed(float x, float y) {
        if (!mStylusConfig.sideButtonRightClick) {
            return false;
        }

        float normalizedX = x / mWidth;
        float normalizedY = y / mHeight;

        if (stylusPointer == -1) {
            return emitSideButtonClick(normalizedX, normalizedY);
        }

        long now = SystemClock.uptimeMillis();
        if (now - stylusContactUptime >= SIDE_BUTTON_CONTACT_GRACE_MS) {
            // The engine's repeat timer may already own the stroke (action
            // menu). Keep the stroke intact and ignore this edge.
            Log.i("SDL", "stylus button ignored: stroke is already held");
            return false;
        }

        int contactPointer = stylusPointer;
        if (!emitSideButtonClick(normalizedX, normalizedY)) {
            return false;
        }
        // The native side discards the stroke as part of the right click. This
        // cancel only cleans up the synthetic SDL finger and is a no-op there.
        stylusPointer = -1;
        sendStylus(STYLUS_TOUCH_DEVICE_ID, contactPointer, MotionEvent.ACTION_UP,
                normalizedX, normalizedY, -1.0f);
        return true;
    }

    private boolean emitSideButtonClick(float normalizedX, float normalizedY) {
        long now = SystemClock.uptimeMillis();
        if (now - lastSideButtonClickUptime < SIDE_BUTTON_DEDUP_MS) {
            return false;
        }
        lastSideButtonClickUptime = now;
        Log.i("SDL", "stylus right click at " + normalizedX + "," + normalizedY);
        // A complete press/release pair per edge: holding the physical button
        // cannot repeat the cursor switch.
        sendStylus(STYLUS_BUTTON_DEVICE_ID, 0, MotionEvent.ACTION_DOWN,
                normalizedX, normalizedY, 1.0f);
        sendStylus(STYLUS_BUTTON_DEVICE_ID, 0, MotionEvent.ACTION_UP,
                normalizedX, normalizedY, 1.0f);
        return true;
    }

    boolean handleStylusButtonKey(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        if (keyCode != KEYCODE_STYLUS_BUTTON_PRIMARY_VALUE
                && keyCode != KEYCODE_STYLUS_BUTTON_SECONDARY_VALUE
                && keyCode != KEYCODE_STYLUS_BUTTON_TERTIARY_VALUE) {
            return false;
        }

        Log.i("SDL", "stylus key=" + keyCode + " action=" + event.getAction()
                + " repeat=" + event.getRepeatCount() + " device=" + event.getDeviceId());

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            sideButtonPressed(stylusX * mWidth, stylusY * mHeight);
        }
        // Never forward barrel buttons to the game as keyboard input.
        return true;
    }

    private static float clampPressure(float pressure) {
        return pressure > 1.0f ? 1.0f : pressure;
    }

    private void sendStylus(int deviceId, int pointerId, int action, float x, float y, float pressure) {
        onStylusTouch(deviceId, pointerId, action, x, y, pressure);
    }

    protected void onStylusTouch(int deviceId, int pointerId, int action, float x, float y, float pressure) {
        SDLActivity.onNativeTouch(deviceId, pointerId, action, x, y, pressure);
    }

    private void cancelStylus() {
        if (stylusPointer != -1) {
            // Negative pressure marks cancellation; it never becomes a click.
            sendStylus(STYLUS_TOUCH_DEVICE_ID, stylusPointer, MotionEvent.ACTION_UP,
                    stylusX, stylusY, -1.0f);
            stylusPointer = -1;
        }
        if (stylusHoverActive) {
            sendStylus(STYLUS_HOVER_DEVICE_ID, 0, MotionEvent.ACTION_UP,
                    stylusX, stylusY, 1.0f);
            stylusHoverActive = false;
        }
        // The physical button state is unknown after cancellation. Releasing
        // it here means the next edge is treated as a new press.
        stylusSideButtonDown = false;
    }

    /** Overrides pen configuration; used by input tests and a future settings UI. */
    protected void setStylusConfig(StylusConfig config) {
        mStylusConfig = config;
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) cancelStylus();
    }

    // Sensor events
    public void enableSensor(int sensortype, boolean enabled) {
        // TODO: This uses getDefaultSensor - what if we have >1 accels?
        if (enabled) {
            mSensorManager.registerListener(this,
                            mSensorManager.getDefaultSensor(sensortype),
                            SensorManager.SENSOR_DELAY_GAME, null);
        } else {
            mSensorManager.unregisterListener(this,
                            mSensorManager.getDefaultSensor(sensortype));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            // Since we may have an orientation set, we won't receive onConfigurationChanged events.
            // We thus should check here.
            int newOrientation;

            float x, y;
            switch (mDisplay.getRotation()) {
                case Surface.ROTATION_90:
                    x = -event.values[1];
                    y = event.values[0];
                    newOrientation = SDLActivity.SDL_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    x = event.values[1];
                    y = -event.values[0];
                    newOrientation = SDLActivity.SDL_ORIENTATION_LANDSCAPE_FLIPPED;
                    break;
                case Surface.ROTATION_180:
                    x = -event.values[0];
                    y = -event.values[1];
                    newOrientation = SDLActivity.SDL_ORIENTATION_PORTRAIT_FLIPPED;
                    break;
                case Surface.ROTATION_0:
                default:
                    x = event.values[0];
                    y = event.values[1];
                    newOrientation = SDLActivity.SDL_ORIENTATION_PORTRAIT;
                    break;
            }

            if (newOrientation != SDLActivity.mCurrentOrientation) {
                SDLActivity.mCurrentOrientation = newOrientation;
                SDLActivity.onNativeOrientationChanged(newOrientation);
            }

            SDLActivity.onNativeAccel(-x / SensorManager.GRAVITY_EARTH,
                                      y / SensorManager.GRAVITY_EARTH,
                                      event.values[2] / SensorManager.GRAVITY_EARTH);


        }
    }

    // Captured pointer events for API 26.
    public boolean onCapturedPointerEvent(MotionEvent event)
    {
        int action = event.getActionMasked();

        float x, y;
        switch (action) {
            case MotionEvent.ACTION_SCROLL:
                x = event.getAxisValue(MotionEvent.AXIS_HSCROLL, 0);
                y = event.getAxisValue(MotionEvent.AXIS_VSCROLL, 0);
                SDLActivity.onNativeMouse(0, action, x, y, false);
                return true;

            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_MOVE:
                x = event.getX(0);
                y = event.getY(0);
                SDLActivity.onNativeMouse(0, action, x, y, true);
                return true;

            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:

                // Change our action value to what SDL's code expects.
                if (action == MotionEvent.ACTION_BUTTON_PRESS) {
                    action = MotionEvent.ACTION_DOWN;
                } else { /* MotionEvent.ACTION_BUTTON_RELEASE */
                    action = MotionEvent.ACTION_UP;
                }

                x = event.getX(0);
                y = event.getY(0);
                int button = event.getButtonState();

                SDLActivity.onNativeMouse(button, action, x, y, true);
                return true;
        }

        return false;
    }
}
