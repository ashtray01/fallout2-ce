package com.alexbatalov.fallout2ce;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.AtomicFile;
import android.view.WindowManager;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Validates/imports data before SDL and the native engine are started. */
public class ImportActivity extends Activity {
    private static final int IMPORT_REQUEST_CODE = 1;
    private boolean importing;
    private boolean pickerPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.getBoolean("pickerPending")) {
            pickerPending = true;
            return;
        } // The system picker is still open.
        File directory = getExternalFilesDir(null);
        if (GameData.isReady(directory)) {
            try {
                repairConfig(directory);
                startMainActivity();
            } catch (IOException | RuntimeException e) {
                showError(e.getMessage());
            }
        } else {
            selectFolder();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("pickerPending", pickerPending);
    }

    private void selectFolder() {
        pickerPending = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, IMPORT_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode != IMPORT_REQUEST_CODE) return;
        pickerPending = false;
        if (resultCode != RESULT_OK || resultData == null || resultData.getData() == null) {
            finish();
            return;
        }
        Uri uri = resultData.getData();
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(this, uri);
            if (tree == null) throw new IOException("Cannot open selected folder");
            copyFiles(tree);
        } catch (IOException | RuntimeException e) {
            showError(e.getMessage());
        }
    }

    private void copyFiles(DocumentFile tree) {
        if (importing) return;
        importing = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setTitle(R.string.loading_dialog_title);
        dialog.setMessage(getString(R.string.loading_dialog_message));
        dialog.setCancelable(false);
        dialog.show();
        new Thread(() -> {
            String error = null;
            try {
                boolean master = false;
                boolean critter = false;
                for (DocumentFile child : FileUtils.listFiles(getContentResolver(), tree)) {
                    if (child.isFile() && child.canRead()) {
                        master |= "master.dat".equalsIgnoreCase(child.getName());
                        critter |= "critter.dat".equalsIgnoreCase(child.getName());
                    }
                }
                if (!master || !critter) throw new IOException(getString(R.string.import_missing_archives));
                File directory = getExternalFilesDir(null);
                if (directory == null) throw new IOException(getString(R.string.import_storage_unavailable));
                File marker = new File(directory, GameData.IMPORT_MARKER);
                if (!marker.exists() && !marker.createNewFile()) throw new IOException("Cannot create import marker");
                FileUtils.copyRecursively(getContentResolver(), tree, directory, name ->
                    runOnUiThread(() -> { if (!isDestroyed()) dialog.setMessage(getString(R.string.import_copying, name)); }));
                if (!GameData.hasArchives(directory)) throw new IOException(getString(R.string.import_missing_archives));
                repairConfig(directory);
                if (!marker.delete()) throw new IOException("Cannot complete import");
            } catch (IOException | RuntimeException e) {
                error = e.getMessage();
                if (error == null) error = e.getClass().getSimpleName();
            }
            final String message = error;
            runOnUiThread(() -> {
                importing = false;
                if (isDestroyed()) return;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                dialog.dismiss();
                if (message == null) startMainActivity();
                else showError(message);
            });
        }, "game-data-import").start();
    }

    private void repairConfig(File directory) throws IOException {
        File config = GameData.find(directory, "fallout2.cfg");
        if (config == null) return;
        AtomicFile file = new AtomicFile(config);
        // Byte-preserving charset: localized legacy configuration is not necessarily UTF-8.
        String original = new String(file.readFully(), StandardCharsets.ISO_8859_1);
        String repaired = GameData.portableConfig(original, directory);
        if (original.equals(repaired)) return;
        File backup = new File(directory, "fallout2.cfg.before-android");
        if (!backup.exists()) {
            try (FileOutputStream output = new FileOutputStream(backup)) {
                output.write(original.getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(repaired.getBytes(StandardCharsets.ISO_8859_1));
            file.finishWrite(output);
        } catch (IOException e) {
            if (output != null) file.failWrite(output);
            throw e;
        }
    }

    private void showError(String detail) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.import_error_title)
            .setMessage(getString(R.string.import_error_message, detail))
            .setPositiveButton(R.string.import_retry, (dialog, which) -> selectFolder())
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
            .setOnCancelListener(dialog -> finish())
            .show();
    }

    @Override
    public void onBackPressed() {
        if (!importing) super.onBackPressed();
    }

    private void startMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
