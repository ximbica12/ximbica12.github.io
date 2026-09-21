package com.ximbica.xlite;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebRequestError;

public final class MainActivity extends Activity {
    private static final String HOME = "https://x.com/home";
    private static final long LOAD_TIMEOUT_MS = 20000L;

    private static GeckoRuntime runtime;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private GeckoView view;
    private GeckoSession session;
    private View statusPanel;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button retryButton;

    private boolean canGoBack;
    private String currentUrl = HOME;

    private final Runnable timeoutRunnable = () -> {
        if (statusPanel != null && statusPanel.getVisibility() == View.VISIBLE) {
            showError("O X demorou demais para responder. Verifique a conexão e toque em Recarregar.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        view = findViewById(R.id.gecko_view);
        statusPanel = findViewById(R.id.status_panel);
        statusText = findViewById(R.id.status_text);
        progressBar = findViewById(R.id.load_progress);
        retryButton = findViewById(R.id.retry_button);

        retryButton.setOnClickListener(v -> recreateSessionAndLoad(currentUrl));

        currentUrl = resolveInitialUrl(getIntent());
        ensureRuntime();
        createSession();
        load(currentUrl);
    }

    private void ensureRuntime() {
        if (runtime != null) {
            return;
        }

        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .build();

        runtime = GeckoRuntime.create(getApplicationContext(), settings);

        runtime.getWebExtensionController()
                .installBuiltIn("resource://android/assets/xlite/")
                .accept(
                        extension -> { },
                        error -> runOnUiThread(() ->
                                statusText.setText("X Lite iniciado. Otimizador interno indisponível; continuando sem ele."))
                );
    }

    private void createSession() {
        session = new GeckoSession();

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFirstContentfulPaint(GeckoSession geckoSession) {
                hideStatusPanel();
            }

            @Override
            public void onFirstComposite(GeckoSession geckoSession) {
                // The compositor is alive. Keep the status overlay until actual content paints.
            }

            @Override
            public void onCrash(GeckoSession geckoSession) {
                showError("O motor Gecko fechou inesperadamente. Toque em Recarregar para reconstruir a sessão.");
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession geckoSession, String url) {
                currentUrl = url != null ? url : currentUrl;
                showLoading("Carregando X…", 5);
            }

            @Override
            public void onProgressChange(GeckoSession geckoSession, int progress) {
                showLoading("Carregando X… " + progress + "%", progress);
            }

            @Override
            public void onPageStop(GeckoSession geckoSession, boolean success) {
                handler.removeCallbacks(timeoutRunnable);
                if (!success) {
                    showError("O X não conseguiu concluir o carregamento. Toque em Recarregar.");
                } else if (progressBar.getProgress() >= 95) {
                    handler.postDelayed(this::hideAfterSuccessfulStop, 500);
                }
            }

            private void hideAfterSuccessfulStop() {
                hideStatusPanel();
            }
        });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession geckoSession, boolean value) {
                canGoBack = value;
            }

            @Override
            public GeckoResult<String> onLoadError(
                    GeckoSession geckoSession,
                    String uri,
                    WebRequestError error) {
                runOnUiThread(() -> showError(
                        "Falha ao abrir o X (" + error.category + "/" + error.code + "). Toque em Recarregar."));
                return null;
            }
        });

        session.open(runtime);
        view.setSession(session);

        // Explicitly mark this tab/session as foreground. This avoids a compositor/JS
        // session being left inactive on older Android devices.
        session.setActive(true);
        runtime.getWebExtensionController().setTabActive(session, true);
    }

    private void load(String url) {
        showLoading("Iniciando X…", 0);
        handler.removeCallbacks(timeoutRunnable);
        handler.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS);

        session.setActive(true);
        runtime.getWebExtensionController().setTabActive(session, true);
        session.loadUri(url);
    }

    private void recreateSessionAndLoad(String url) {
        handler.removeCallbacks(timeoutRunnable);

        if (session != null) {
            try {
                runtime.getWebExtensionController().setTabActive(session, false);
                session.setActive(false);
                session.close();
            } catch (Exception ignored) {
            }
        }

        createSession();
        load(url == null ? HOME : url);
    }

    private String resolveInitialUrl(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri uri = intent.getData();
            String host = uri.getHost();
            if ("x.com".equalsIgnoreCase(host) || "twitter.com".equalsIgnoreCase(host)) {
                return uri.toString();
            }
        }
        return HOME;
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
        handler.removeCallbacks(timeoutRunnable);
        runOnUiThread(() -> {
            statusPanel.setVisibility(View.VISIBLE);
            statusText.setText(message);
            progressBar.setVisibility(View.GONE);
            retryButton.setVisibility(View.VISIBLE);
        });
    }

    private void hideStatusPanel() {
        handler.removeCallbacks(timeoutRunnable);
        runOnUiThread(() -> statusPanel.setVisibility(View.GONE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) {
            session.setActive(true);
            if (runtime != null) {
                runtime.getWebExtensionController().setTabActive(session, true);
            }
        }
    }

    @Override
    protected void onPause() {
        if (session != null) {
            if (runtime != null) {
                runtime.getWebExtensionController().setTabActive(session, false);
            }
            session.setActive(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (session != null && isFinishing()) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = resolveInitialUrl(intent);
        currentUrl = url;
        if (session != null) {
            load(url);
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
}
