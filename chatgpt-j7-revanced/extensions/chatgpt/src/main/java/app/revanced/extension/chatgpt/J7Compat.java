package app.revanced.extension.chatgpt;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

public final class J7Compat {
    private static final int STORAGE_REQUEST = 0x4A37;

    private J7Compat() {}

    public static void onMainActivityCreated(Activity activity) {
        if (activity == null) return;
        tuneWindow(activity);
        requestLegacyStorageIfNeeded(activity);
    }

    private static void requestLegacyStorageIfNeeded(Activity activity) {
        if (Build.VERSION.SDK_INT >= 29 || Build.VERSION.SDK_INT < 23) return;
        if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) return;

        activity.requestPermissions(
                new String[] {
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                STORAGE_REQUEST
        );
    }

    private static void tuneWindow(Activity activity) {
        final Window window = activity.getWindow();
        if (window == null) return;

        window.setWindowAnimations(0);
        window.setStatusBarColor(Color.rgb(18, 18, 18));
        window.setNavigationBarColor(Color.rgb(10, 10, 10));

        final View decor = window.getDecorView();
        if (decor != null) {
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }
}
