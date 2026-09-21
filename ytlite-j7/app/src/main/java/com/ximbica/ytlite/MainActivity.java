package com.ximbica.ytlite;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

public class MainActivity extends Activity {
    private static final String HOME =
            "https://m.youtube.com/?persist_app=1&app=m&hl=pt-BR&gl=BR";
    private static GeckoRuntime runtime;

    private GeckoSession session;
    private GeckoView geckoView;
    private ProgressBar progress;
    private boolean canGoBack = false;
    private boolean extensionsStarted = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        geckoView = new GeckoView(this);
        root.addView(geckoView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(2));
        root.addView(progress, pp);

        setContentView(root);

        if (runtime == null) {
            ContentBlocking.Settings blocking = new ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                    .build();

            GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                    .aboutConfigEnabled(false)
                    .remoteDebuggingEnabled(false)
                    .consoleOutput(false)
                    .contentBlocking(blocking)
                    .build();

            runtime = GeckoRuntime.create(getApplicationContext(), runtimeSettings);
        }

        installExtensionsOnce();

        GeckoSessionSettings sessionSettings = new GeckoSessionSettings.Builder()
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .usePrivateMode(false)
                .useTrackingProtection(true)
                .suspendMediaWhenInactive(true)
                .build();

        session = new GeckoSession(sessionSettings);

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession s, boolean value) {
                canGoBack = value;
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession s, LoadRequest request) {
                Uri uri = Uri.parse(request.uri);
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }
                if ("intent".equals(scheme)) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY);
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                if (uri != null && !uri.isEmpty()) {
                    s.loadUri(uri);
                }
                return GeckoResult.fromValue(null);
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession s, String url) {
                progress.setProgress(8);
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onProgressChange(GeckoSession s, int value) {
                progress.setProgress(value);
            }

            @Override
            public void onPageStop(GeckoSession s, boolean success) {
                progress.setProgress(100);
                progress.setVisibility(View.GONE);
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFullScreen(GeckoSession s, boolean fullScreen) {
                if (fullScreen) {
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                } else {
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
        });

        session.open(runtime);
        geckoView.setSession(session);

        String start = HOME;
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String incoming = intent.getData().toString();
            if (incoming.startsWith("https://youtu.be/")) {
                String id = intent.getData().getLastPathSegment();
                if (id != null && !id.isEmpty()) {
                    start = "https://m.youtube.com/watch?v=" + id + "&hl=pt-BR&gl=BR&cc_lang_pref=pt&cc_load_policy=1";
                }
            } else if (incoming.contains("youtube.com")) {
                start = incoming;
            }
        }

        if (start.contains("youtube.com") && !start.contains("hl=")) {
            start += (start.contains("?") ? "&" : "?") + "hl=pt-BR&gl=BR&cc_lang_pref=pt&cc_load_policy=1";
        }
        session.loadUri(start);
    }

    private synchronized void installExtensionsOnce() {
        if (extensionsStarted) return;
        extensionsStarted = true;

        ensureExtension(
                "resource://android/assets/extensions/ublock/",
                "uBlock0@raymondhill.net");
        ensureExtension(
                "resource://android/assets/extensions/sponsorblock/",
                "sponsorBlocker@ajay.app");
        ensureExtension(
                "resource://android/assets/extensions/ytlite/",
                "ytlite-j7@ximbica.local");
    }

    private void ensureExtension(String uri, String id) {
        runtime.getWebExtensionController()
                .ensureBuiltIn(uri, id)
                .accept(
                        extension -> { },
                        error -> { });
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
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (session != null && intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.startsWith("https://")) {
                session.loadUri(url);
            }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
