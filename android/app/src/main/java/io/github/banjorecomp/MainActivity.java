package io.github.banjorecomp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String TAG = "BanjoAndroidShell";

    private static native int nativeProbe();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String message;
        try {
            // Do not explicitly load SDL2 here. It is a DT_NEEDED dependency of
            // BanjoAndroidShell, and explicitly loading libSDL2.so can run SDL's
            // JNI_OnLoad before the matching SDL Java activity classes exist.
            System.loadLibrary("BanjoAndroidShell");
            int result = nativeProbe();
            message = "Banjo Android native shell loaded. Probe result: " + result;
        } catch (Throwable t) {
            Log.e(TAG, "Native shell load/probe failed", t);
            message = "Native shell load/probe failed:\n\n" + Log.getStackTraceString(t);
        }

        TextView text = new TextView(this);
        text.setText(message);
        text.setPadding(32, 32, 32, 32);
        setContentView(text);
    }
}
