package com.ximbica.xlite;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.RenderProcessGoneDetail;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final String HOME = "https://x.com/home";
    private static final long LOAD_TIMEOUT_MS = 25000L;
    private static final long WATCHDOG_INTERVAL_MS = 30000L;
    private static final long WATCHDOG_TIMEOUT_MS = 12000L;
    private static final int FILE_CHOOSER_REQUEST = 4103;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout browserContainer;
    private FrameLayout fullscreenContainer;
    private View statusPanel;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button retryButton;

    private WebView webView;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> filePathCallback;

    private String currentUrl = HOME;
    private String optimizerScript = "";
    private boolean foreground;
    private int watchdogToken;
    private int watchdogPongToken;

    private final Runnable loadTimeoutRunnable = () -> {
        if (statusPanel != null && statusPanel.getVisibility() == View.VISIBLE) {
            showError("O X demorou demais para responder. Toque em Recarregar.");
        }
    };

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!foreground || webView == null || customView != null
                    || statusPanel.getVisibility() == View.VISIBLE) {
                scheduleWatchdog();
                return;
            }

            final WebView target = webView;
            final int token = ++watchdogToken;

            try {
                target.evaluateJavascript("1", value -> {
                    if (target == webView) {
                        watchdogPongToken = token;
                    }
                });
            } catch (Throwable ignored) {
            }

            handler.postDelayed(() -> {
                if (!foreground || target != webView) {
                    scheduleWatchdog();
                    return;
                }

                if (watchdogPongToken < token) {
                    rebuildWebView(currentUrl, "O renderer do X parou de responder. Reiniciando…");
                } else {
                    scheduleWatchdog();
                }
            }, WATCHDOG_TIMEOUT_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        browserContainer = findViewById(R.id.browser_container);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        statusPanel = findViewById(R.id.status_panel);
        statusText = findViewById(R.id.status_text);
        progressBar = findViewById(R.id.load_progress);
        retryButton = findViewById(R.id.retry_button);

        retryButton.setOnClickListener(v -> rebuildWebView(currentUrl, "Recarregando X…"));

        WebView.setWebContentsDebuggingEnabled(false);
        optimizerScript = readAsset("xlite-webview.js");

        currentUrl = resolveInitialUrl(getIntent());
        createWebView();
        load(currentUrl);
    }

    private void createWebView() {
        WebView w = new WebView(this);
        w.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        w.setBackgroundColor(0xFF000000);
        w.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = w.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(false);
        settings.setOffscreenPreRaster(false);

        String userAgent = settings.getUserAgentString();
        if (userAgent != null) {
            userAgent = userAgent.replace("; wv", "").replace(" Version/4.0", "");
            settings.setUserAgentString(userAgent);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(w, true);

        w.setWebViewClient(new XWebViewClient());
        w.setWebChromeClient(new XWebChromeClient());

        browserContainer.removeAllViews();
        browserContainer.addView(w);
        webView = w;
    }

    private void load(String url) {
        if (webView == null) {
            createWebView();
        }

        currentUrl = url == null ? HOME : url;
        showLoading("Iniciando X…", 0);
        handler.removeCallbacks(loadTimeoutRunnable);
        handler.postDelayed(loadTimeoutRunnable, LOAD_TIMEOUT_MS);
        webView.loadUrl(currentUrl);
    }

    private void rebuildWebView(String url, String reason) {
        showLoading(reason, 0);
        destroyWebView();
        createWebView();
        handler.postDelayed(() -> load(url == null ? HOME : url), 180L);
    }

    private void destroyWebView() {
        handler.removeCallbacks(loadTimeoutRunnable);
        handler.removeCallbacks(watchdogRunnable);

        WebView old = webView;
        webView = null;

        if (old != null) {
            try {
                old.stopLoading();
            } catch (Throwable ignored) {
            }

            try {
                browserContainer.removeView(old);
            } catch (Throwable ignored) {
            }

            try {
                old.destroy();
            } catch (Throwable ignored) {
            }
        }
    }

    private final class XWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            return handleNavigation(uri);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(Uri.parse(url));
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            currentUrl = url == null ? currentUrl : url;
            showLoading("Carregando X…", 5);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            hideStatusPanel();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            handler.removeCallbacks(loadTimeoutRunnable);
            currentUrl = url == null ? currentUrl : url;
            injectOptimizer(view);
            CookieManager.getInstance().flush();
            hideStatusPanel();
            scheduleWatchdog();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                showError("Falha ao abrir o X: " + error.getDescription() + ". Toque em Recarregar.");
            }
        }

        @Override
        public void onReceivedHttpError(
                WebView view,
                WebResourceRequest request,
                WebResourceResponse errorResponse) {
            if (request != null && request.isForMainFrame()
                    && errorResponse != null && errorResponse.getStatusCode() >= 400) {
                showError("O X respondeu com erro HTTP " + errorResponse.getStatusCode()
                        + ". Toque em Recarregar.");
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            final String reason = detail != null && detail.didCrash()
                    ? "O WebView do X fechou. Recuperando…"
                    : "O Android encerrou o renderer para liberar memória. Recuperando…";

            handler.post(() -> rebuildWebView(currentUrl, reason));
            return true;
        }
    }

    private final class XWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (statusPanel.getVisibility() == View.VISIBLE && newProgress < 100) {
                showLoading("Carregando X… " + newProgress + "%", Math.max(1, newProgress));
            }
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }

            MainActivity.this.filePathCallback = filePathCallback;

            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Throwable ignored) {
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }

            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                return false;
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }

            customView = view;
            customViewCallback = callback;

            fullscreenContainer.removeAllViews();
            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            fullscreenContainer.setVisibility(View.VISIBLE);
            browserContainer.setVisibility(View.GONE);
            statusPanel.setVisibility(View.GONE);

            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreenVideo();
        }
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
            if (isXHost(host) || "t.co".equalsIgnoreCase(host)) {
                return false;
            }

            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
            }
            return true;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
        }
        return true;
    }

    private boolean isXHost(String host) {
        if (host == null) {
            return false;
        }

        String h = host.toLowerCase();
        return h.equals("x.com")
                || h.endsWith(".x.com")
                || h.equals("twitter.com")
                || h.endsWith(".twitter.com");
    }

    private void injectOptimizer(WebView target) {
        if (optimizerScript == null || optimizerScript.isEmpty() || target != webView) {
            return;
        }

        try {
            target.evaluateJavascript(optimizerScript, null);
        } catch (Throwable ignored) {
        }
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String resolveInitialUrl(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri uri = intent.getData();
            if (isXHost(uri.getHost())) {
                return uri.toString();
            }
        }
        return HOME;
    }

    private void scheduleWatchdog() {
        handler.removeCallbacks(watchdogRunnable);
        if (foreground) {
            handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
        }
    }

    private void showLoading(String message, int progress) {
        runOnUiThread(() -> {
            statusPanel.setVisibility(View.VISIBLE);
            statusText.setText(message);
            retryButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(progress <= 0);
            if (progress > 0) {
                progressBar.setProgress(progress);
            }
        });
    }

    private void showError(String message) {
        handler.removeCallbacks(loadTimeoutRunnable);
        runOnUiThread(() -> {
            statusPanel.setVisibility(View.VISIBLE);
            statusText.setText(message);
            progressBar.setVisibility(View.GONE);
            retryButton.setVisibility(View.VISIBLE);
        });
    }

    private void hideStatusPanel() {
        handler.removeCallbacks(loadTimeoutRunnable);
        runOnUiThread(() -> statusPanel.setVisibility(View.GONE));
    }

    private void exitFullscreenVideo() {
        if (customView == null) {
            return;
        }

        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        browserContainer.setVisibility(View.VISIBLE);

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
        }

        customView = null;
        customViewCallback = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
        scheduleWatchdog();
    }

    @Override
    protected void onPause() {
        foreground = false;
        handler.removeCallbacks(watchdogRunnable);
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL && foreground && webView != null) {
            rebuildWebView(currentUrl, "Memória baixa. Reiniciando o X para evitar travamento…");
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (foreground && webView != null) {
            rebuildWebView(currentUrl, "Memória crítica. Recuperando o X…");
        }
    }

    @Override
    protected void onDestroy() {
        foreground = false;
        handler.removeCallbacksAndMessages(null);

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        exitFullscreenVideo();
        destroyWebView();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = resolveInitialUrl(intent);
        currentUrl = url;
        load(url);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreenVideo();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            ValueCallback<Uri[]> callback = filePathCallback;
            filePathCallback = null;

            if (callback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                callback.onReceiveValue(result);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
