package com.alexbatalov.fallout2ce;

import org.libsdl.app.SDLActivity;

public class MainActivity extends SDLActivity {
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Run libc exit handlers to release native game resources.
        System.exit(0);
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{ "fallout2-ce" };
    }
}
