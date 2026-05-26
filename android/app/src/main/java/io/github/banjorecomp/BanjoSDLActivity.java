package io.github.banjorecomp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class BanjoSDLActivity extends SDLActivity {
    private static final String TAG = "BanjoSDLActivity";
    private static final int REQUEST_INSTALL_MODS = 1001;
    private static final String PROGRAM_ASSET_STAMP_FILE = ".program-assets-stamp";

    public static native void nativeSetAndroidSurfaceReady(boolean ready);
    public static native void nativeSetAppAudioActive(boolean active);

    private boolean activityResumed;
    private boolean windowFocused;
    private boolean appAudioActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File programDir = new File(getFilesDir(), "program");
        File appDataDir = new File(getFilesDir(), "data");

        try {
            extractProgramAssetsIfNeeded(programDir);
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy program assets", e);
        }

        super.onCreate(savedInstanceState);

        nativeSetenv("APP_PROGRAM_PATH", programDir.getAbsolutePath());
        nativeSetenv("APP_FOLDER_PATH", appDataDir.getAbsolutePath());
        nativeSetenv("RECOMP_AUTO_ROM_PATH", new File(programDir, "dev-roms/baserom.us.v10.z64").getAbsolutePath());
        Log.i(TAG, "APP_PROGRAM_PATH=" + programDir.getAbsolutePath());
        Log.i(TAG, "APP_FOLDER_PATH=" + appDataDir.getAbsolutePath());
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        updateAppAudioActive();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        updateAppAudioActive();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        windowFocused = hasFocus;
        updateAppAudioActive();
        super.onWindowFocusChanged(hasFocus);
    }

    private void updateAppAudioActive() {
        boolean active = activityResumed && windowFocused;
        if (active != appAudioActive) {
            appAudioActive = active;
            nativeSetAppAudioActive(active);
        }
    }

    public void openModFilePicker() {
        runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed"
            });

            try {
                startActivityForResult(intent, REQUEST_INSTALL_MODS);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No Android document picker is available for installing mods", e);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_INSTALL_MODS) {
            try {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<String> importedPaths = new ArrayList<>();

                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            copySelectedMod(data.getClipData().getItemAt(i).getUri(), importedPaths);
                        }
                    } else if (data.getData() != null) {
                        copySelectedMod(data.getData(), importedPaths);
                    }

                    if (!importedPaths.isEmpty()) {
                        nativeOnModsSelected(importedPaths.toArray(new String[0]));
                    }
                }
            } finally {
                // SDLActivity's normal onPause/onResume lifecycle handles native rendering state.
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void copySelectedMod(Uri uri, ArrayList<String> importedPaths) {
        String displayName = getDisplayName(uri);
        if (displayName == null || displayName.isEmpty()) {
            displayName = "selected-mod.zip";
        }

        File importDir = new File(getCacheDir(), "mod-imports");
        if (!importDir.exists() && !importDir.mkdirs()) {
            Log.e(TAG, "Failed to create mod import directory " + importDir.getAbsolutePath());
            return;
        }

        File destination = uniqueDestination(importDir, sanitizeFilename(displayName));
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(destination)) {
            if (in == null) {
                Log.e(TAG, "Unable to open selected mod URI " + uri);
                return;
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            importedPaths.add(destination.getAbsolutePath());
            Log.i(TAG, "Imported selected mod to " + destination.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to import selected mod " + uri, e);
        }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read display name for " + uri, e);
        }
        return uri.getLastPathSegment();
    }

    private String sanitizeFilename(String filename) {
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "selected-mod.zip" : sanitized;
    }

    private File uniqueDestination(File directory, String filename) {
        File candidate = new File(directory, filename);
        if (!candidate.exists()) {
            return candidate;
        }

        String stem = filename;
        String extension = "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            stem = filename.substring(0, dot);
            extension = filename.substring(dot);
        }

        for (int i = 1; ; i++) {
            candidate = new File(directory, stem + "-" + i + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
    }

    private static native void nativeOnModsSelected(String[] paths);

    private void extractProgramAssetsIfNeeded(File programDir) throws IOException {
        if (!assetTreeExists("program")) {
            Log.i(TAG, "No packaged program assets found; skipping asset extraction");
            return;
        }

        File stampFile = new File(programDir, PROGRAM_ASSET_STAMP_FILE);
        String expectedStamp = Long.toString(new File(getPackageCodePath()).lastModified());
        String currentStamp = readTextFile(stampFile);
        if (programDir.isDirectory() && expectedStamp.equals(currentStamp)) {
            Log.i(TAG, "Program assets are already current at " + programDir.getAbsolutePath());
            return;
        }

        deleteRecursively(programDir);
        copyAssetTree("program", programDir);
        writeTextFile(stampFile, expectedStamp);
        Log.i(TAG, "Copied program assets to " + programDir.getAbsolutePath());
    }

    private boolean assetTreeExists(String assetPath) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children != null && children.length > 0) {
            return true;
        }

        try (InputStream ignored = getAssets().open(assetPath)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String readTextFile(File file) throws IOException {
        if (!file.isFile()) {
            return null;
        }

        byte[] data = new byte[(int) file.length()];
        try (InputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
        }
        return new String(data, "UTF-8");
    }

    private void writeTextFile(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory " + parent);
        }

        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes("UTF-8"));
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (!file.delete()) {
            throw new IOException("Failed to delete " + file);
        }
    }

    private void copyAssetTree(String assetPath, File destination) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(assetPath, destination);
            return;
        }

        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Failed to create directory " + destination);
        }

        for (String child : children) {
            copyAssetTree(assetPath + "/" + child, new File(destination, child));
        }
    }

    private void copyAssetFile(String assetPath, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory " + parent);
        }

        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
