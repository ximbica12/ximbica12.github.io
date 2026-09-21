package com.xlite.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
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
import org.mozilla.geckoview.WebResponse;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String HOME = "https://x.com/home";
    private static final String CPFT_ID = "{5cce4ab5-3d47-41b9-af5e-8203eea05245}";
    private static final String HELPER_ID = "xlite-helper@xlite.local";
    private static final int FILE_PICKER = 77;

    private static GeckoRuntime runtime;

    private GeckoView geckoView;
    private GeckoSession session;
    private SharedPreferences prefs;
    private TextView handle;
    private WebExtension controlPanel;
    private WebExtension helperExtension;
    private boolean canGoBack;
    private boolean canGoForward;

    private GeckoSession.PromptDelegate.FilePrompt pendingFilePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> pendingFileResult;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        prefs = getSharedPreferences("xlite", MODE_PRIVATE);
        createRuntimeIfNeeded();
        buildUi();
        createSession();

        String incoming = getIntent() != null ? getIntent().getDataString() : null;
        String initial = isHttp(incoming) ? incoming : HOME;
        installExtensionsAndLoad(initial);

        if (!prefs.getBoolean("v05_hint", false)) {
            prefs.edit().putBoolean("v05_hint", true).apply();
            Toast.makeText(this,
                    "XLite v0.5: toque no pequeno ⋮ lateral para navegar e abrir as opções",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void createRuntimeIfNeeded() {
        if (runtime != null) {
            runtime.attachTo(getApplicationContext());
            return;
        }

        ContentBlocking.Settings blocking = new ContentBlocking.Settings.Builder()
                .antiTracking(
                        ContentBlocking.AntiTracking.AD
                                | ContentBlocking.AntiTracking.ANALYTIC
                                | ContentBlocking.AntiTracking.CRYPTOMINING
                                | ContentBlocking.AntiTracking.FINGERPRINTING)
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_ALL)
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .build();

        GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(false)
                .consoleOutput(false)
                .remoteDebuggingEnabled(false)
                .automaticFontSizeAdjustment(false)
                .fontInflation(false)
                .fontSizeFactor(prefs.getBoolean("compact_text", true) ? 0.92f : 1.0f)
                .forceUserScalableEnabled(true)
                .lowMemoryDetection(true)
                .locales(new String[]{"pt-BR", "pt", "en-US"})
                .contentBlocking(blocking)
                .build();

        runtime = GeckoRuntime.create(getApplicationContext(), settings);
        runtime.warmUp();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        geckoView = new GeckoView(this);
        geckoView.setViewBackend(GeckoView.BACKEND_SURFACE_VIEW);
        geckoView.coverUntilFirstPaint(Color.BLACK);
        root.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        handle = new TextView(this);
        handle.setText("⋮");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(22f);
        handle.setGravity(Gravity.CENTER);
        handle.setAlpha(0.38f);
        handle.setContentDescription("Menu XLite");

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD9161616);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x553F3F3F);
        handle.setBackground(bg);
        handle.setOnClickListener(v -> {
            handle.animate().alpha(0.95f).setDuration(100).start();
            showMenu();
        });

        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(27), dp(46));
        hp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        hp.setMarginEnd(dp(2));
        root.addView(handle, hp);

        setContentView(root);

        handle.postDelayed(() ->
                handle.animate().alpha(0.30f).setDuration(300).start(), 1600);
    }

    private void createSession() {
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .allowJavascript(true)
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .displayMode(GeckoSessionSettings.DISPLAY_MODE_STANDALONE)
                .useTrackingProtection(true)
                .suspendMediaWhenInactive(true)
                .build();

        session = new GeckoSession(settings);

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession s, boolean value) {
                canGoBack = value;
            }

            @Override
            public void onCanGoForward(GeckoSession s, boolean value) {
                canGoForward = value;
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                    GeckoSession s, LoadRequest request) {
                String uri = request.uri;
                if (uri == null) return null;

                Uri parsed;
                try {
                    parsed = Uri.parse(uri);
                } catch (Exception e) {
                    return null;
                }

                String scheme = parsed.getScheme();
                if ("moz-extension".equalsIgnoreCase(scheme)
                        || "about".equalsIgnoreCase(scheme)
                        || "data".equalsIgnoreCase(scheme)) {
                    return null;
                }

                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    String host = parsed.getHost();
                    if (isXHost(host)) return null;

                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, parsed));
                        return GeckoResult.deny();
                    } catch (Exception ignored) {
                        return null;
                    }
                }

                return null;
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                try {
                    if (uri != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                    }
                } catch (Exception ignored) {
                }
                return null;
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onFullScreen(GeckoSession s, boolean fullScreen) {
                handle.setVisibility(fullScreen ? View.GONE : View.VISIBLE);
                if (fullScreen) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                }
            }

            @Override
            public void onCrash(GeckoSession s) {
                Toast.makeText(MainActivity.this,
                        "O motor reiniciou após uma falha. Reabrindo o X.",
                        Toast.LENGTH_SHORT).show();
                recoverSession();
            }

            @Override
            public void onKill(GeckoSession s) {
                Toast.makeText(MainActivity.this,
                        "Memória liberada pelo Android. Reabrindo o X.",
                        Toast.LENGTH_SHORT).show();
                recoverSession();
            }

            @Override
            public void onExternalResponse(GeckoSession s, WebResponse response) {
                if (response.requestExternalApp) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(response.uri)));
                    } catch (Exception ignored) {
                    }
                } else {
                    Toast.makeText(MainActivity.this,
                            "Download detectado. Abra o link no navegador externo se precisar salvar.",
                            Toast.LENGTH_SHORT).show();
                    try {
                        if (response.body != null) response.body.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onFilePrompt(
                    GeckoSession s, FilePrompt prompt) {
                if (pendingFileResult != null) return GeckoResult.fromValue(prompt.dismiss());

                pendingFilePrompt = prompt;
                pendingFileResult = new GeckoResult<>();

                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType(prompt.mimeTypes != null && prompt.mimeTypes.length == 1
                        ? prompt.mimeTypes[0] : "*/*");
                if (prompt.mimeTypes != null && prompt.mimeTypes.length > 1) {
                    pick.putExtra(Intent.EXTRA_MIME_TYPES, prompt.mimeTypes);
                }
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        prompt.type == FilePrompt.Type.MULTIPLE);

                try {
                    startActivityForResult(pick, FILE_PICKER);
                    return pendingFileResult;
                } catch (Exception e) {
                    GeckoResult<PromptResponse> result = pendingFileResult;
                    pendingFilePrompt = null;
                    pendingFileResult = null;
                    result.complete(prompt.dismiss());
                    return result;
                }
            }
        });

        session.open(runtime);
        geckoView.setSession(session);
        runtime.getWebExtensionController().setTabActive(session, true);
    }

    private void installExtensionsAndLoad(String initial) {
        WebExtensionController controller = runtime.getWebExtensionController();

        controller.ensureBuiltIn(
                "resource://android/assets/cpft/", CPFT_ID)
                .accept(ext -> {
                    controlPanel = ext;
                    installHelperThenLoad(initial);
                }, error -> installHelperThenLoad(initial));
    }

    private void installHelperThenLoad(String initial) {
        runtime.getWebExtensionController().ensureBuiltIn(
                "resource://android/assets/xlite-helper/", HELPER_ID)
                .accept(ext -> {
                    helperExtension = ext;
                    attachHelperMessaging();
                    session.loadUri(initial);
                }, error -> session.loadUri(initial));
    }

    private void attachHelperMessaging() {
        if (helperExtension == null) return;

        session.getWebExtensionController().setMessageDelegate(
                helperExtension,
                new WebExtension.MessageDelegate() {
                    @Override
                    public GeckoResult<Object> onMessage(
                            String nativeApp, Object message, WebExtension.MessageSender sender) {
                        JSONObject reply = new JSONObject();
                        try {
                            reply.put("videoMax", prefs.getInt("video_max_height", 720));
                            reply.put("autoTranslate", prefs.getBoolean("auto_translate", true));
                        } catch (Exception ignored) {
                        }
                        return GeckoResult.fromValue(reply);
                    }
                },
                "xlite");
    }

    private void showMenu() {
        int video = prefs.getInt("video_max_height", 720);
        boolean translate = prefs.getBoolean("auto_translate", true);
        boolean compact = prefs.getBoolean("compact_text", true);

        String[] items = {
                (canGoBack ? "←  Voltar" : "←  Voltar — indisponível"),
                (canGoForward ? "→  Avançar" : "→  Avançar — indisponível"),
                "⌂  Início",
                "↻  Recarregar",
                "⚙  Control Panel for Twitter — opções completas",
                (translate ? "✓  " : "○  ") + "Tradução automática dos posts",
                "▶  Vídeo máximo: " + video + "p",
                (compact ? "✓  " : "○  ") + "Texto compacto para J7",
                "🌐  Abrir página fora do XLite",
                "ⓘ  Sobre"
        };

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("XLite for X v0.5")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            if (canGoBack) session.goBack();
                            break;
                        case 1:
                            if (canGoForward) session.goForward();
                            break;
                        case 2:
                            session.loadUri(HOME);
                            break;
                        case 3:
                            session.reload();
                            break;
                        case 4:
                            openControlPanelOptions();
                            break;
                        case 5:
                            prefs.edit().putBoolean("auto_translate", !translate).apply();
                            session.reload();
                            break;
                        case 6:
                            showVideoQualityDialog();
                            break;
                        case 7:
                            prefs.edit().putBoolean("compact_text", !compact).apply();
                            runtime.getSettings().setFontSizeFactor(!compact ? 0.92f : 1.0f);
                            session.reload();
                            break;
                        case 8:
                            openExternalCurrentPage();
                            break;
                        case 9:
                            showAbout();
                            break;
                    }
                })
                .create();

        dialog.setOnDismissListener(d ->
                handle.postDelayed(() ->
                        handle.animate().alpha(0.30f).setDuration(250).start(), 500));
        dialog.show();
    }

    private void openControlPanelOptions() {
        if (controlPanel != null
                && controlPanel.metaData != null
                && controlPanel.metaData.optionsPageUrl != null) {
            session.loadUri(controlPanel.metaData.optionsPageUrl);
        } else {
            Toast.makeText(this,
                    "O Control Panel ainda está inicializando.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showVideoQualityDialog() {
        int current = prefs.getInt("video_max_height", 720);
        String[] choices = {"720p — recomendado no J7", "1080p — máximo"};

        new AlertDialog.Builder(this)
                .setTitle("Limite de vídeo")
                .setSingleChoiceItems(choices, current == 1080 ? 1 : 0, (d, which) -> {
                    prefs.edit().putInt("video_max_height", which == 1 ? 1080 : 720).apply();
                    d.dismiss();
                    session.reload();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAbout() {
        String cpftVersion = controlPanel != null && controlPanel.metaData != null
                ? controlPanel.metaData.version : "carregando";

        new AlertDialog.Builder(this)
                .setTitle("XLite for X v0.5")
                .setMessage(
                        "Motor: Mozilla GeckoView 156 estável (ARMv7)\n" +
                        "Renderização: SurfaceView\n" +
                        "Control Panel for Twitter: " + cpftVersion + " como WebExtension real\n\n" +
                        "Sem barra fixa e sem gestos de navegação. O pequeno ⋮ lateral apenas sobrepõe a página e não reduz a área do X.\n\n" +
                        "Proteção nativa contra trackers de anúncios, analytics, fingerprinting e cryptomining.\n\n" +
                        "A opção de contornar verificação etária permanece desativada.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void openExternalCurrentPage() {
        // GeckoView does not expose synchronous current URL; HOME is a safe fallback.
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(HOME)));
        } catch (Exception ignored) {
        }
    }

    private void recoverSession() {
        runOnUiThread(() -> {
            try {
                geckoView.releaseSession();
            } catch (Exception ignored) {
            }
            createSession();
            installExtensionsAndLoad(HOME);
        });
    }

    private boolean isXHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return host.equals("x.com") || host.endsWith(".x.com")
                || host.equals("twitter.com") || host.endsWith(".twitter.com")
                || host.equals("t.co") || host.endsWith(".t.co")
                || host.equals("twimg.com") || host.endsWith(".twimg.com");
    }

    private boolean isHttp(String value) {
        return value != null
                && (value.startsWith("https://") || value.startsWith("http://"));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_PICKER && pendingFileResult != null && pendingFilePrompt != null) {
            GeckoSession.PromptDelegate.PromptResponse response;

            if (resultCode != RESULT_OK || data == null) {
                response = pendingFilePrompt.dismiss();
            } else {
                ClipData clips = data.getClipData();
                if (clips != null && clips.getItemCount() > 0) {
                    Uri[] uris = new Uri[clips.getItemCount()];
                    for (int i = 0; i < clips.getItemCount(); i++) {
                        uris[i] = clips.getItemAt(i).getUri();
                    }
                    response = pendingFilePrompt.confirm(getApplicationContext(), uris);
                } else if (data.getData() != null) {
                    response = pendingFilePrompt.confirm(
                            getApplicationContext(), data.getData());
                } else {
                    response = pendingFilePrompt.dismiss();
                }
            }

            pendingFileResult.complete(response);
            pendingFileResult = null;
            pendingFilePrompt = null;
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (canGoBack) {
            session.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) session.setActive(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session != null) session.setActive(true);
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            runtime.getWebExtensionController().setTabActive(session, false);
            try {
                geckoView.releaseSession();
                session.close();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
