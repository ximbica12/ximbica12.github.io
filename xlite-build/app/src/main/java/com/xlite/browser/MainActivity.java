package com.xlite.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String HOME = "https://x.com/home";
    private static final String LOGIN = "https://x.com/i/flow/login";
    private static final int STORAGE_PERMISSION = 42;

    private final Set<String> blocked = new HashSet<>(Arrays.asList(
            "ads-twitter.com",
            "analytics.twitter.com",
            "scribe.twitter.com",
            "static.ads-twitter.com",
            "ads-api.twitter.com",
            "doubleclick.net",
            "google-analytics.com",
            "googletagmanager.com",
            "scorecardresearch.com",
            "googlesyndication.com",
            "adservice.google.com"
    ));

    private WebView web;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private String defaultUa;
    private String cpftScript;
    private String pendingUrl;
    private String pendingUa;
    private String pendingDisposition;
    private String pendingMime;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        prefs = getSharedPreferences("xlite", MODE_PRIVATE);
        buildUi();
        configureWebView();

        if (state != null) {
            web.restoreState(state);
        } else {
            String incoming = getIntent() != null ? getIntent().getDataString() : null;
            web.loadUrl(isHttp(incoming) ? incoming : HOME);
        }
    }

    private void buildUi() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.BLACK);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        column.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        column.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(Color.rgb(8, 8, 8));

        addButton(bar, "‹", v -> {
            if (web.canGoBack()) web.goBack();
        });
        addButton(bar, "⌂", v -> web.loadUrl(HOME));
        addButton(bar, "↻", v -> web.reload());
        addButton(bar, "⋮", v -> showMenu());

        column.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        setContentView(column);
    }

    private void addButton(LinearLayout bar, String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(21);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        bar.addView(b, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        applyScale();
        defaultUa = s.getUserAgentString();
        applyUserAgent();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, prefs.getBoolean("third_party_cookies", true));

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (prefs.getBoolean("adblock", true) && isBlocked(req.getUrl())) {
                    return new WebResourceResponse(
                            "text/plain", "UTF-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, req);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    if (isXHost(u.getHost())) return false;
                    openExternal(u);
                    return true;
                }
                openExternal(u);
                return true;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                injectEnhancements();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectEnhancements();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int n) {
                progress.setProgress(n);
                progress.setVisibility(n >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        long contentLength) {
                pendingUrl = url;
                pendingUa = userAgent;
                pendingDisposition = contentDisposition;
                pendingMime = mimeType;
                if (android.os.Build.VERSION.SDK_INT <= 28 &&
                        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            STORAGE_PERMISSION);
                } else {
                    enqueueDownload();
                }
            }
        });
    }

    private void applyScale() {
        int scale = prefs.getInt("j7_scale", 85);
        web.setInitialScale(scale);
    }

    private void injectEnhancements() {
        injectJ7Viewport();
        if (prefs.getBoolean("lite", true)) injectLite();
        if (prefs.getBoolean("cpft_enabled", true)) injectControlPanel();
    }

    private void injectJ7Viewport() {
        int pct = prefs.getInt("j7_scale", 85);
        double scale = pct / 100.0;
        String js =
                "(function(){" +
                "var m=document.querySelector('meta[name=viewport]');" +
                "if(!m){m=document.createElement('meta');m.name='viewport';document.head&&document.head.appendChild(m);}" +
                "if(m)m.setAttribute('content','width=device-width,initial-scale=" + scale +
                ",minimum-scale=0.65,maximum-scale=3,user-scalable=yes,viewport-fit=cover');" +
                "})();";
        web.evaluateJavascript(js, null);
    }

    private void injectLite() {
        String js =
                "(function(){" +
                "if(document.getElementById('xlite-css'))return;" +
                "var s=document.createElement('style');s.id='xlite-css';" +
                "s.textContent='*,*::before,*::after{animation-duration:.001s!important;" +
                "animation-iteration-count:1!important;transition-duration:.001s!important;" +
                "scroll-behavior:auto!important} [style*=backdrop-filter]{backdrop-filter:none!important;" +
                "-webkit-backdrop-filter:none!important}';" +
                "document.documentElement.appendChild(s);" +
                "})();";
        web.evaluateJavascript(js, null);
    }

    private void injectControlPanel() {
        if (cpftScript == null) cpftScript = readAsset("cpft-script.js");
        if (cpftScript == null || cpftScript.length() == 0) return;

        String config = controlPanelConfigJson();
        String bootstrap =
                "(function(){" +
                "if(window.__XLITE_CPFT_INJECTED__)return;" +
                "window.__XLITE_CPFT_INJECTED__=true;" +
                "var old=document.getElementById('cpftSettings');if(old)old.remove();" +
                "var s=document.createElement('script');s.type='text/json';s.id='cpftSettings';" +
                "s.textContent=" + quoteJs(config) + ";" +
                "document.documentElement.appendChild(s);" +
                "})();";

        web.evaluateJavascript(bootstrap, v -> web.evaluateJavascript(cpftScript, null));
    }

    private String controlPanelConfigJson() {
        return "{" +
                "\"enabled\":" + prefs.getBoolean("cpft_enabled", true) + "," +
                "\"bypassAgeVerification\":false," +
                "\"hideForYouTimeline\":" + prefs.getBoolean("cpft_hide_for_you", true) + "," +
                "\"hideGrokNav\":" + prefs.getBoolean("cpft_hide_grok", true) + "," +
                "\"hideWhoToFollowEtc\":" + prefs.getBoolean("cpft_hide_suggestions", true) + "," +
                "\"hideWhatsHappening\":" + prefs.getBoolean("cpft_hide_whats_happening", true) + "," +
                "\"hideTwitterBlueUpsells\":" + prefs.getBoolean("cpft_hide_upsells", true) + "," +
                "\"alwaysUseLatestTweets\":" + prefs.getBoolean("cpft_latest", true) + "," +
                "\"preventNextVideoAutoplay\":" + prefs.getBoolean("cpft_stop_next_video", true) + "," +
                "\"hideViews\":" + prefs.getBoolean("cpft_hide_views", false) + "," +
                "\"replaceLogo\":" + prefs.getBoolean("cpft_twitter_logo", false) +
                "}";
    }

    private String quoteJs(String text) {
        return "'" + text
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "'";
    }

    private String readAsset(String name) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    getAssets().open(name), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void applyUserAgent() {
        String ua = defaultUa == null ? "" : defaultUa;
        if (prefs.getBoolean("compat", true)) {
            ua = ua.replace("; wv", "").replace("Version/4.0 ", "");
        }
        web.getSettings().setUserAgentString(ua);
    }

    private boolean isBlocked(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(Locale.US);
        for (String d : blocked) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    private boolean isXHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return host.equals("x.com") || host.endsWith(".x.com")
                || host.equals("twitter.com") || host.endsWith(".twitter.com")
                || host.equals("t.co") || host.endsWith(".t.co")
                || host.equals("twimg.com") || host.endsWith(".twimg.com");
    }

    private void showMenu() {
        boolean ad = prefs.getBoolean("adblock", true);
        boolean lite = prefs.getBoolean("lite", true);
        boolean compat = prefs.getBoolean("compat", true);
        boolean cpft = prefs.getBoolean("cpft_enabled", true);
        int scale = prefs.getInt("j7_scale", 85);

        String[] items = {
                mark(ad) + "AdBlock",
                mark(lite) + "Modo leve",
                mark(compat) + "Compatibilidade do X",
                mark(cpft) + "Control Panel for Twitter 4.24.1",
                "Escala J7: " + scale + "%",
                "Opções do Control Panel",
                "Reparar sessão/verificação",
                "Reset total da sessão",
                "Abrir no navegador externo",
                "Sobre"
        };

        new AlertDialog.Builder(this)
                .setTitle("XLite for X v0.3")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            toggle("adblock", true); reloadClean(); break;
                        case 1:
                            toggle("lite", true); reloadClean(); break;
                        case 2:
                            toggle("compat", true); applyUserAgent(); reloadClean(); break;
                        case 3:
                            toggle("cpft_enabled", true); reloadClean(); break;
                        case 4:
                            showScaleDialog(); break;
                        case 5:
                            showControlPanelMenu(); break;
                        case 6:
                            repairSession(false); break;
                        case 7:
                            repairSession(true); break;
                        case 8:
                            if (web.getUrl() != null) openExternal(Uri.parse(web.getUrl()));
                            break;
                        case 9:
                            showAbout(); break;
                    }
                })
                .show();
    }

    private void showScaleDialog() {
        final int[] values = {75, 80, 85, 90, 100};
        String[] labels = {"75% — mais conteúdo", "80%", "85% — recomendado J7",
                "90%", "100% — tamanho normal"};
        int current = prefs.getInt("j7_scale", 85);
        int checked = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;

        new AlertDialog.Builder(this)
                .setTitle("Escala de interface")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.edit().putInt("j7_scale", values[which]).apply();
                    applyScale();
                    d.dismiss();
                    reloadClean();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showControlPanelMenu() {
        final String[] keys = {
                "cpft_hide_for_you",
                "cpft_hide_grok",
                "cpft_hide_suggestions",
                "cpft_hide_whats_happening",
                "cpft_hide_upsells",
                "cpft_latest",
                "cpft_stop_next_video",
                "cpft_hide_views",
                "cpft_twitter_logo"
        };
        String[] labels = {
                "Ocultar aba Para você",
                "Ocultar Grok",
                "Ocultar sugestões / Quem seguir",
                "Ocultar O que está acontecendo",
                "Ocultar upsells do Premium",
                "Usar timeline mais recente",
                "Impedir próximo vídeo automático",
                "Ocultar contagem de visualizações",
                "Restaurar pássaro do Twitter"
        };
        boolean[] defaults = {true, true, true, true, true, true, true, false, false};
        boolean[] checked = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            checked[i] = prefs.getBoolean(keys[i], defaults[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle("Control Panel for Twitter")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) ->
                        prefs.edit().putBoolean(keys[which], isChecked).apply())
                .setPositiveButton("Aplicar", (d, w) -> reloadClean())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("XLite for X v0.3")
                .setMessage("Android 8+ • J7 compact mode\n\n" +
                        "Control Panel for Twitter 4.24.1 integrado a partir do projeto " +
                        "de Jonny Buchanan / soitis.dev, licença MIT.\n\n" +
                        "A opção upstream de contornar verificação etária é mantida desativada.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void reloadClean() {
        web.evaluateJavascript("window.__XLITE_CPFT_INJECTED__=false;", null);
        web.reload();
    }

    private void repairSession(boolean full) {
        if (!full) {
            prefs.edit().putBoolean("compat", true).apply();
            applyUserAgent();
            web.clearCache(true);
            String js = "(function(){try{localStorage.clear();sessionStorage.clear();}catch(e){}" +
                    "setTimeout(function(){location.reload();},200);})();";
            web.evaluateJavascript(js, null);
            toast("Estado local reiniciado");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Reset total")
                .setMessage("Apaga cookies deste app e exige novo login. Não altera a idade da conta nem ignora verificações do X.")
                .setPositiveButton("Resetar", (d, w) -> {
                    WebStorage.getInstance().deleteAllData();
                    web.clearCache(true);
                    web.clearHistory();
                    CookieManager.getInstance().removeAllCookies(ok -> {
                        CookieManager.getInstance().flush();
                        web.loadUrl(LOGIN);
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enqueueDownload() {
        if (pendingUrl == null) return;
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(pendingUrl));
            r.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (pendingUa != null) r.addRequestHeader("User-Agent", pendingUa);

            String cookies = CookieManager.getInstance().getCookie(pendingUrl);
            if (cookies != null) r.addRequestHeader("Cookie", cookies);

            String name = android.webkit.URLUtil.guessFileName(
                    pendingUrl, pendingDisposition, pendingMime);
            r.setTitle(name);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);

            DownloadManager dm =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            dm.enqueue(r);
            toast("Download iniciado");
        } catch (Exception e) {
            toast("Falha no download");
        } finally {
            pendingUrl = null;
        }
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("Não há app para abrir este link");
        }
    }

    private void toggle(String key, boolean def) {
        prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply();
    }

    private String mark(boolean on) {
        return on ? "✓  " : "○  ";
    }

    private boolean isHttp(String s) {
        return s != null && (s.startsWith("https://") || s.startsWith("http://"));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueDownload();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
