package com.alexbatalov.fallout2ce;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class FileUtilsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private DocumentFile source(File file, long length) {
        DocumentFile document = mock(DocumentFile.class);
        when(document.getUri()).thenReturn(Uri.fromFile(file));
        when(document.getName()).thenReturn(file.getName());
        when(document.length()).thenReturn(length);
        return document;
    }

    private ContentResolver resolver() {
        return RuntimeEnvironment.getApplication().getContentResolver();
    }

    @Test public void copiesArchiveBytes() throws Exception {
        File src = folder.newFile("source.dat");
        byte[] bytes = new byte[200000];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        Files.write(src.toPath(), bytes);
        File dest = new File(folder.getRoot(), "critter.dat");
        FileUtils.copyFile(resolver(), source(src, bytes.length), dest);
        assertArrayEquals(bytes, Files.readAllBytes(dest.toPath()));
    }

    @Test
    @Config(sdk = 28)
    public void replacesExistingArchive() throws Exception {
        File src = folder.newFile("source.dat");
        Files.write(src.toPath(), new byte[]{1, 2, 3});
        File dest = folder.newFile("critter.dat");
        Files.write(dest.toPath(), new byte[]{9});
        FileUtils.copyFile(resolver(), source(src, 3), dest);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(dest.toPath()));
    }

    @Test public void truncatedStreamRestoresPreviousArchive() throws Exception {
        File src = folder.newFile("source.dat");
        Files.write(src.toPath(), new byte[]{1, 2});
        File dest = folder.newFile("critter.dat");
        byte[] original = new byte[]{8, 9, 10};
        Files.write(dest.toPath(), original);
        assertThrows(java.io.IOException.class,
            () -> FileUtils.copyFile(resolver(), source(src, 100), dest));
        assertArrayEquals(original, Files.readAllBytes(dest.toPath()));
    }

    @Test public void failedNewCopyDoesNotLeavePartialArchive() throws Exception {
        File src = folder.newFile("source.dat");
        Files.write(src.toPath(), new byte[]{1});
        File dest = new File(folder.getRoot(), "critter.dat");
        assertThrows(java.io.IOException.class,
            () -> FileUtils.copyFile(resolver(), source(src, 100), dest));
        assertFalse(dest.exists());
    }

    @Test public void missingSourceDoesNotOverwriteDestination() throws Exception {
        File src = new File(folder.getRoot(), "missing.dat");
        File dest = folder.newFile("critter.dat");
        Files.write(dest.toPath(), new byte[]{5});
        assertThrows(java.io.IOException.class,
            () -> FileUtils.copyFile(resolver(), source(src, 100), dest));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(dest.toPath()));
    }

    @Test public void supportsProviderWithUnknownLength() throws Exception {
        File src = folder.newFile("source.dat");
        Files.write(src.toPath(), new byte[]{1, 2});
        File dest = new File(folder.getRoot(), "critter.dat");
        FileUtils.copyFile(resolver(), source(src, 0), dest);
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(dest.toPath()));
    }
}
