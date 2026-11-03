package com.alexbatalov.fallout2ce;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

public class GameDataTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private File archive(String name) throws Exception {
        File file = folder.newFile(name);
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(1); }
        return file;
    }

    @Test public void acceptsMixedCaseArchives() throws Exception {
        archive("MASTER.DAT");
        archive("Critter.Dat");
        assertTrue(GameData.isReady(folder.getRoot()));
    }

    @Test public void configDoesNotHideMissingArchive() throws Exception {
        archive("fallout2.cfg");
        archive("master.dat");
        assertFalse(GameData.isReady(folder.getRoot()));
    }

    @Test public void rejectsEmptyArchiveAndMissingStorage() throws Exception {
        archive("master.dat");
        folder.newFile("critter.dat");
        assertFalse(GameData.isReady(folder.getRoot()));
        assertFalse(GameData.isReady(null));
    }

    @Test public void interruptedImportIsNotReady() throws Exception {
        archive("master.dat");
        archive("critter.dat");
        File marker = folder.newFile(GameData.IMPORT_MARKER);
        assertFalse(GameData.isReady(folder.getRoot()));
        assertTrue(marker.delete());
        assertTrue(GameData.isReady(folder.getRoot()));
    }

    @Test public void directoryCannotMasqueradeAsArchive() throws Exception {
        archive("master.dat");
        folder.newFolder("critter.dat");
        assertFalse(GameData.hasArchives(folder.getRoot()));
    }

    @Test public void repairsWindowsPathsPreservingLanguageAndLineEndings() throws Exception {
        archive("MASTER.DAT");
        archive("CRITTER.DAT");
        folder.newFolder("DATA");
        String config = "[system]\r\nmaster_dat=C:\\Games\\Fallout2\\master.dat\r\n"
            + "critter_dat=D:\\critter.dat\r\nmaster_patches=C:\\Games\\Fallout2\\data\r\n"
            + "language=russian\r\n; critter_dat=D:\\ignored.dat\r\n";
        String fixed = GameData.portableConfig(config, folder.getRoot());
        assertEquals("[system]\r\nmaster_dat=MASTER.DAT\r\ncritter_dat=CRITTER.DAT\r\n"
            + "master_patches=DATA/\r\nlanguage=russian\r\n; critter_dat=D:\\ignored.dat\r\n", fixed);
        assertEquals(fixed, GameData.portableConfig(fixed, folder.getRoot()));
    }

    @Test public void preservesRelativeModPathsAndUnrelatedSettings() throws Exception {
        archive("master.dat");
        folder.newFolder("data");
        String config = "[system]\nmaster_dat=mods/custom.dat\ncritter_dat=/storage/custom.dat\n"
            + "master_patches=C:\\mod\\custom\n[other]\nmaster_dat=C:\\old.dat\n";
        assertEquals(config, GameData.portableConfig(config, folder.getRoot()));
    }

    @Test public void repairsUncPathsAndMusic() throws Exception {
        archive("critter.dat");
        folder.newFolder("DATA", "SOUND", "MUSIC");
        String config = "[SYSTEM]\ncritter_dat=\\\\pc\\games\\critter.dat\n"
            + "[sound]\nmusic_path1=D:\\Fallout2\\data\\sound\\music\\";
        assertEquals("[SYSTEM]\ncritter_dat=critter.dat\n[sound]\nmusic_path1=DATA/SOUND/MUSIC/",
            GameData.portableConfig(config, folder.getRoot()));
    }

    @Test public void doesNotInventMissingLocalPaths() {
        String config = "[system]\nmaster_dat=C:\\Fallout2\\master.dat\n[sound]\nmusic_path1=D:\\music\\";
        assertEquals(config, GameData.portableConfig(config, folder.getRoot()));
    }
}
