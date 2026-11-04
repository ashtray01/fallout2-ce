package org.libsdl.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Fallout pen input configuration.
 *
 * The Android app has no input settings screen, so all values default to the
 * behavior recommended for a tablet with an active pen. The same keys can be
 * provided through {@link SharedPreferences} (a future settings screen or a
 * device-management policy) without changing the defaults here.
 */
public final class StylusConfig {
    public static final String PREFERENCES_NAME = "stylus";

    public static final String KEY_ENABLED = "stylus_enabled";
    public static final String KEY_ABSOLUTE_POSITIONING = "stylus_absolute_positioning";
    public static final String KEY_SIDE_BUTTON_RIGHT_CLICK = "stylus_side_button_right_click";
    public static final String KEY_HOVER = "stylus_hover_enabled";
    public static final String KEY_PALM_REJECTION = "stylus_palm_rejection";

    public static final StylusConfig DEFAULT = new StylusConfig(true, true, true, true, true);

    public final boolean enabled;
    public final boolean absolutePositioning;
    public final boolean sideButtonRightClick;
    public final boolean hover;
    public final boolean palmRejection;

    public StylusConfig(boolean enabled,
                        boolean absolutePositioning,
                        boolean sideButtonRightClick,
                        boolean hover,
                        boolean palmRejection) {
        this.enabled = enabled;
        this.absolutePositioning = absolutePositioning;
        this.sideButtonRightClick = sideButtonRightClick;
        this.hover = hover;
        this.palmRejection = palmRejection;
    }

    /** True when the pen drives the game cursor directly. */
    public boolean interceptPen() {
        return enabled && absolutePositioning;
    }

    public static StylusConfig load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return new StylusConfig(
                preferences.getBoolean(KEY_ENABLED, DEFAULT.enabled),
                preferences.getBoolean(KEY_ABSOLUTE_POSITIONING, DEFAULT.absolutePositioning),
                preferences.getBoolean(KEY_SIDE_BUTTON_RIGHT_CLICK, DEFAULT.sideButtonRightClick),
                preferences.getBoolean(KEY_HOVER, DEFAULT.hover),
                preferences.getBoolean(KEY_PALM_REJECTION, DEFAULT.palmRejection));
    }
}
