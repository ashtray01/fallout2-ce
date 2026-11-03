package com.alexbatalov.fallout2ce;

import android.content.Intent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ImportActivityTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private static File storage;

    public static class TestActivity extends ImportActivity {
        @Override public File getExternalFilesDir(String type) { return storage; }
    }

    private void write(String name, String value) throws Exception {
        Files.write(new File(folder.getRoot(), name).toPath(), value.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test public void missingArchiveOpensPickerEvenWithConfig() throws Exception {
        storage = folder.getRoot();
        write("fallout2.cfg", "[system]\n");
        write("master.dat", "data");
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class).create()) {
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,
                shadowOf(controller.get()).getNextStartedActivityForResult().intent.getAction());
        }
    }

    @Test public void repairsConfigBeforeStartingGame() throws Exception {
        storage = folder.getRoot();
        write("master.dat", "data");
        write("critter.dat", "data");
        String original = "[system]\ncritter_dat=C:\\Games\\Fallout2\\critter.dat\n";
        write("fallout2.cfg", original);
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class).create()) {
            assertEquals(MainActivity.class.getName(),
                shadowOf(controller.get()).getNextStartedActivity().getComponent().getClassName());
            assertEquals("[system]\ncritter_dat=critter.dat\n",
                new String(Files.readAllBytes(new File(storage, "fallout2.cfg").toPath()), StandardCharsets.ISO_8859_1));
            assertEquals(original,
                new String(Files.readAllBytes(new File(storage, "fallout2.cfg.before-android").toPath()), StandardCharsets.ISO_8859_1));
        }
    }

    @Test public void interruptedImportOpensPickerInsteadOfEngine() throws Exception {
        storage = folder.getRoot();
        write("master.dat", "data");
        write("critter.dat", "data");
        write(GameData.IMPORT_MARKER, "");
        try (org.robolectric.android.controller.ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class).create()) {
            assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,
                shadowOf(controller.get()).getNextStartedActivityForResult().intent.getAction());
            controller.get().onActivityResult(1, android.app.Activity.RESULT_OK, null);
            assertTrue(controller.get().isFinishing());
        }
    }
}
