package com.alexbatalov.fallout2ce;

import android.content.ContentResolver;
import android.util.AtomicFile;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FileUtils {
    interface Progress { void onFile(String name); }

    static DocumentFile[] listFiles(ContentResolver resolver, DocumentFile directory) throws IOException {
        // DocumentFile.listFiles suppresses provider errors; detect incomplete listings.
        Uri uri = directory.getUri();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
            DocumentsContract.getDocumentId(uri));
        try (Cursor cursor = resolver.query(children,
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            if (cursor == null) throw new IOException("Cannot list folder: " + directory.getName());
            DocumentFile[] files = directory.listFiles();
            if (files.length != cursor.getCount()) throw new IOException("Folder changed or cannot be read: " + directory.getName());
            return files;
        }
    }

    static void copyRecursively(ContentResolver resolver, DocumentFile src, File dest,
            Progress progress) throws IOException {
        if (!src.isDirectory() || !src.canRead()) throw new IOException("Cannot read folder: " + src.getName());
        if (!dest.isDirectory() && !dest.mkdirs()) throw new IOException("Cannot create folder: " + dest);
        Set<String> names = new HashSet<>();
        for (DocumentFile child : listFiles(resolver, src)) {
            String name = child.getName();
            if (name == null || name.isEmpty() || name.equals(".") || name.equals("..")
                    || name.contains("/") || name.contains("\\") || name.equals(GameData.IMPORT_MARKER)) {
                throw new IOException("Invalid file name: " + name);
            }
            if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate file name: " + name);
            File existing = GameData.find(dest, name);
            File target = existing != null ? existing : new File(dest, name);
            if (child.isDirectory()) {
                if (name.equalsIgnoreCase("savegame") && target.isDirectory()) continue;
                copyRecursively(resolver, child, target, progress);
            } else if (child.isFile()) {
                // Preserve saves created on this device during a repair import.
                if (target.isFile() && target.getAbsolutePath().toLowerCase(Locale.ROOT)
                        .contains(File.separator + "savegame" + File.separator)) continue;
                progress.onFile(name);
                copyFile(resolver, child, target);
            } else {
                throw new IOException("Cannot read: " + name);
            }
        }
    }

    static void copyFile(ContentResolver resolver, DocumentFile src, File dest) throws IOException {
        AtomicFile file = new AtomicFile(dest);
        FileOutputStream output = null;
        try (InputStream input = resolver.openInputStream(src.getUri())) {
            if (input == null) throw new IOException("Cannot open: " + src.getName());
            output = file.startWrite();
            byte[] buffer = new byte[65536];
            long count = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                count += read;
            }
            long expected = src.length();
            if (expected > 0 && count != expected) throw new IOException("Incomplete file: " + src.getName());
            file.finishWrite(output);
        } catch (IOException | RuntimeException e) {
            if (output != null) file.failWrite(output);
            throw new IOException("Could not copy " + src.getName() + ": " + e.getMessage(), e);
        }
    }
}
