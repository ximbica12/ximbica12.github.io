package com.ximbica.gptlewd.gecko;

import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.graphics.Color;
import android.os.Bundle;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

public final class MainActivity extends Activity {
    private static final String HOME = "https://chatgpt.com/";
    private static final String THEME_URI =
            "resource://android/assets/web_extensions/gptlewd/";
    private static final String THEME_ID =
            "gptlewd-j7-theme@ximbica.local";

    private static GeckoRuntime runtime;

    private GeckoSession session;
    private boolean canGoBack = false;
    private boolean visible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(11, 3, 8));
        getWindow().setNavigationBarColor(Color.rgb(11, 3, 8));
        setContentView(R.layout.activity_main);

        GeckoView view = findViewById(R.id.gecko_view);

        if (runtime == null) {
            GeckoRuntimeSettings runtimeSettings =
                    new GeckoRuntimeSettings.Builder()
                            // J7 Prime tuning: exact density requested by the device profile.
                            .displayDpiOverride(240)
                            // Prefer dark web UI before the extension CSS is painted.
                            .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
                            // ChatGPT does not need WebGL antialiasing; save Mali-T830 bandwidth.
                            .glMsaaLevel(0)
                            // Avoid compositor zoom jumps around the composer.
                            .inputAutoZoomEnabled(false)
                            .doubleTapZoomingEnabled(false)
                            // Keep Gecko's low-memory machinery enabled on a 3 GB device.
                            .lowMemoryDetection(true)
                            // The J7 theme forces system fonts, avoiding web-font download/render cost.
                            .webFontsEnabled(false)
                            // One lightweight built-in extension; no dedicated extension process.
                            .extensionsProcessEnabled(false)
                            // No developer logging in a performance build.
                            .consoleOutput(false)
                            .debugLogging(false)
                            .remoteDebuggingEnabled(false)
                            .build();

            runtime = GeckoRuntime.create(this, runtimeSettings);

            // Built-in extensions are the GeckoView-preferred mechanism for touching web content.
            // ensureBuiltIn() avoids reinstalling it on every startup if the version is unchanged.
            runtime.getWebExtensionController().ensureBuiltIn(THEME_URI, THEME_ID);
        }

        GeckoSessionSettings sessionSettings =
                new GeckoSessionSettings.Builder()
                        .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                        .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                        .displayMode(GeckoSessionSettings.DISPLAY_MODE_STANDALONE)
                        .allowJavascript(true)
                        .suspendMediaWhenInactive(true)
                        .build();

        session = new GeckoSession(sessionSettings);

        // GeckoView official quick-start workaround for the ContentDelegate lifecycle.
        session.setContentDelegate(new GeckoSession.ContentDelegate() {});

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession geckoSession, boolean value) {
                canGoBack = value;
            }
        });

        session.open(runtime);
        view.setSession(session);
        session.loadUri(HOME);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (session != null) {
            session.setActive(true);
            session.setFocused(true);
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        }
    }

    @Override
    protected void onPause() {
        if (session != null) {
            session.setFocused(false);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        visible = false;
        if (session != null) {
            // GeckoView documents that an inactive session has a significantly smaller footprint.
            session.setActive(false);
            session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
        }
        super.onStop();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        if (!visible && session != null &&
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            session.setFocused(false);
            session.setActive(false);
            session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
        }
    }

    @Override
    public void onBackPressed() {
        if (session != null && canGoBack) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
