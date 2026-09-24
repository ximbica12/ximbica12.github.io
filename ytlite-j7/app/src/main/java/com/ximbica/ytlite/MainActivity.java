package com.ximbica.ytlite;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String HOME =
            "https://m.youtube.com/?persist_app=1&app=m&hl=pt-BR&gl=BR&cc_lang_pref=pt&cc_load_policy=1";

    private static final String UBO_ID = "uBlock0@raymondhill.net";
    private static final String SPONSOR_ID = "sponsorBlocker@ajay.app";
    private static final String YTLITE_ID = "ytlite-j7@ximbica.local";

    private static final Set<String> REQUIRED_EXTENSIONS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    UBO_ID, SPONSOR_ID, YTLITE_ID)));

    private static GeckoRuntime runtime;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> readyExtensions =
            Collections.synchronizedSet(new HashSet<>());

    private GeckoSession session;
    private GeckoView geckoView;
    private ProgressBar progress;
    private TextView blockerStatus;

    private boolean canGoBack = false;
    private boolean browserStarted = false;
    private boolean extensionsInstallStarted = false;
    private boolean startupQueued = false;
    private String pendingUrl = HOME;

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

        blockerStatus = new TextView(this);
        blockerStatus.setTextColor(Color.WHITE);
        blockerStatus.setTextSize(16f);
        blockerStatus.setGravity(Gravity.CENTER);
        blockerStatus.setPadding(dp(28), dp(28), dp(28), dp(28));
        blockerStatus.setText("Preparando bloqueio 0/3…");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(blockerStatus, statusParams);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(2));
        root.addView(progress, pp);

        setContentView(root);
        pendingUrl = resolveStartUrl(getIntent());

        if (runtime == null) {
            ContentBlocking.Settings blocking = new ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.DEFAULT)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .build();

            GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                    .aboutConfigEnabled(false)
                    .remoteDebuggingEnabled(false)
                    .consoleOutput(false)
                    .contentBlocking(blocking)
                    .build();

            runtime = GeckoRuntime.create(getApplicationContext(), runtimeSettings);
        }

        WebExtensionController controller = runtime.getWebExtensionController();
        controller.setAddonManagerDelegate(new WebExtensionController.AddonManagerDelegate() {
            @Override
            public void onReady(WebExtension extension) {
                markExtensionReady(extension);
            }
        });

        installExtensionsFailClosed();
    }

    private synchronized void installExtensionsFailClosed() {
        if (extensionsInstallStarted) return;
        extensionsInstallStarted = true;

        ensureExtension("resource://android/assets/extensions/ublock/", UBO_ID);
        ensureExtension("resource://android/assets/extensions/sponsorblock/", SPONSOR_ID);
        ensureExtension("resource://android/assets/extensions/ytlite/", YTLITE_ID);
    }

    private void ensureExtension(String uri, String id) {
        runtime.getWebExtensionController()
                .ensureBuiltIn(uri, id)
                .accept(
                        extension -> {
                            // GeckoView 121+ no longer guarantees install/ensure waits
                            // for extension startup. If metadata is already populated, this
                            // extension was already fully ready; otherwise onReady() will fire.
                            if (extension != null
                                    && extension.metaData != null
                                    && extension.metaData.baseUrl != null) {
                                markExtensionReady(extension);
                            }
                        },
                        error -> runOnUiThread(() -> {
                            blockerStatus.setText(
                                    "Falha ao iniciar o bloqueador.\n" +
                                    "O YouTube não será aberto sem proteção.");
                            progress.setVisibility(View.GONE);
                        }));
    }

    private void markExtensionReady(WebExtension extension) {
        if (extension == null || !REQUIRED_EXTENSIONS.contains(extension.id)) return;

        readyExtensions.add(extension.id);
        runOnUiThread(() -> {
            blockerStatus.setText(
                    "Preparando bloqueio " + readyExtensions.size() + "/3…");
            maybeStartBrowser();
        });
    }

    private synchronized void maybeStartBrowser() {
        if (browserStarted || startupQueued) return;
        if (!readyExtensions.containsAll(REQUIRED_EXTENSIONS)) return;

        startupQueued = true;
        blockerStatus.setText("Bloqueio ativo. Abrindo YouTube…");

        // Give uBO's background page a brief warm-up after GeckoView's onReady().
        // We prefer a short protected splash to ever showing an unfiltered first load.
        mainHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            startBrowser();
        }, 1400);
    }

    private synchronized void startBrowser() {
        if (browserStarted) return;
        browserStarted = true;

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
        blockerStatus.setVisibility(View.GONE);
        session.loadUri(pendingUrl);
    }

    private String resolveStartUrl(Intent intent) {
        String start = HOME;

        if (intent != null
                && Intent.ACTION_VIEW.equals(intent.getAction())
                && intent.getData() != null) {
            String incoming = intent.getData().toString();

            if (incoming.startsWith("https://youtu.be/")) {
                String id = intent.getData().getLastPathSegment();
                if (id != null && !id.isEmpty()) {
                    start = "https://m.youtube.com/watch?v=" + id;
                }
            } else if (incoming.contains("youtube.com")) {
                start = incoming;
            }
        }

        if (start.contains("youtube.com") && !start.contains("hl=")) {
            start += (start.contains("?") ? "&" : "?")
                    + "hl=pt-BR&gl=BR&cc_lang_pref=pt&cc_load_policy=1";
        }
        return start;
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
        pendingUrl = resolveStartUrl(intent);
        if (browserStarted && session != null) {
            session.loadUri(pendingUrl);
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
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
