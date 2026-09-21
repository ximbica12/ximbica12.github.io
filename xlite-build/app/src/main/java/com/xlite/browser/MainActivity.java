package com.xlite.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String HOME = "https://x.com/home";
    private static final String LOGIN = "https://x.com/i/flow/login";
    private static final int STORAGE_PERMISSION = 42;
    private static final int FILE_CHOOSER = 43;

    private final Set<String> blocked = new HashSet<>(Arrays.asList(
            "ads-twitter.com",
            "analytics.twitter.com",
            "scribe.twitter.com",
            "static.ads-twitter.com",
            "ads-api.twitter.com",
            "doubleclick.net",
            "google-analytics.com",
            "googletagmanager.com",
            "googlesyndication.com",
            "adservice.google.com",
            "scorecardresearch.com"
    ));

    private FrameLayout root;
    private WebView web;
    private SharedPreferences prefs;
    private String defaultUa;
    private String cpftScript;
    private String pageScript;
    private String cpftSnapshot;
    private boolean documentStartInstalled;
    private boolean resumedOnce;
    private boolean backLongPressed;
    private float gestureStartX;
    private float gestureStartY;
    private long gestureStartTime;
    private int gestureEdge;

    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

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
        cpftSnapshot = prefs.getString("cpft_config_json", "{}");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configureWebView();
        setupEdgeGestures();
        installDocumentStartScripts();

        if (state != null) {
            web.restoreState(state);
        } else {
            String incoming = getIntent() != null ? getIntent().getDataString() : null;
            loadUrl(isHttp(incoming) ? incoming : HOME);
        }

        if (!prefs.getBoolean("v041_hint_shown", false)) {
            prefs.edit().putBoolean("v041_hint_shown", true).apply();
            Toast.makeText(this,
                    "Gestos: esquerda→direita = voltar • direita→esquerda = avançar • topo→baixo = menu",
                    Toast.LENGTH_LONG).show();
        }
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
        s.setOffscreenPreRaster(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);

        applyScale();

        defaultUa = s.getUserAgentString();
        applyUserAgent();

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        if (prefs.getBoolean("auto_translate", true)) {
            cm.setCookie("https://x.com", "lang=pt; Path=/; Secure");
            cm.setCookie("https://twitter.com", "lang=pt; Path=/; Secure");
            cm.flush();
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (!documentStartInstalled) injectFallbackScripts();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                if (prefs.getBoolean("adblock", true) && isBlocked(uri)) {
                    return emptyResponse();
                }

                if (isVideoManifest(uri)) {
                    WebResourceResponse capped = interceptAndCapHls(req);
                    if (capped != null) return capped;
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
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Toast.makeText(MainActivity.this,
                        "WebView reiniciado para recuperar memória",
                        Toast.LENGTH_SHORT).show();
                root.removeView(web);
                web.destroy();
                recreate();
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Não foi possível abrir o seletor de arquivos",
                            Toast.LENGTH_SHORT).show();
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
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                view.bringToFront();
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
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

    private void setupEdgeGestures() {
        web.setOnTouchListener((v, event) -> {
            final float density = getResources().getDisplayMetrics().density;
            final float edgeSize = 24f * density;
            final float topSize = 32f * density;
            final float horizontalThreshold = 96f * density;
            final float verticalThreshold = 112f * density;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gestureStartX = event.getX();
                    gestureStartY = event.getY();
                    gestureStartTime = System.currentTimeMillis();
                    gestureEdge = 0;

                    if (gestureStartX <= edgeSize) {
                        gestureEdge = 1;
                    } else if (gestureStartX >= web.getWidth() - edgeSize) {
                        gestureEdge = 2;
                    } else if (gestureStartY <= topSize) {
                        gestureEdge = 3;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (gestureEdge == 0) break;

                    float dx = event.getX() - gestureStartX;
                    float dy = event.getY() - gestureStartY;
                    long elapsed = System.currentTimeMillis() - gestureStartTime;
                    int edge = gestureEdge;
                    gestureEdge = 0;

                    if (elapsed > 900) break;

                    if (edge == 1 && dx >= horizontalThreshold
                            && Math.abs(dy) <= horizontalThreshold) {
                        handleBackGesture();
                    } else if (edge == 2 && dx <= -horizontalThreshold
                            && Math.abs(dy) <= horizontalThreshold) {
                        if (web.canGoForward()) {
                            web.goForward();
                            Toast.makeText(this, "Avançar", Toast.LENGTH_SHORT).show();
                        }
                    } else if (edge == 3 && dy >= verticalThreshold
                            && Math.abs(dx) <= verticalThreshold) {
                        showMenu();
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    gestureEdge = 0;
                    break;
            }

            // Não consome o toque: a página continua rolando/tocando normalmente.
            return false;
        });
    }

    private void handleBackGesture() {
        if (customView != null) {
            hideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
            Toast.makeText(this, "Voltar", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Sem página anterior", Toast.LENGTH_SHORT).show();
        }
    }

    private void installDocumentStartScripts() {
        cpftScript = readAsset("cpft-script.js");
        pageScript = readAsset("xlite-page.js");

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            documentStartInstalled = false;
            return;
        }

        try {
            Set<String> origins = new HashSet<>(Arrays.asList(
                    "https://x.com",
                    "https://*.x.com",
                    "https://twitter.com",
                    "https://*.twitter.com"
            ));

            String bootstrap = buildBootstrapScript();
            WebViewCompat.addDocumentStartJavaScript(web, bootstrap, origins);

            if (prefs.getBoolean("cpft_enabled", true) && cpftScript != null && !cpftScript.isEmpty()) {
                WebViewCompat.addDocumentStartJavaScript(
                        web,
                        "if(!window.__XLITE_CPFT_LOADED__){window.__XLITE_CPFT_LOADED__=true;" +
                                cpftScript + "}",
                        origins);
            }

            if (pageScript != null && !pageScript.isEmpty()) {
                WebViewCompat.addDocumentStartJavaScript(
                        web,
                        "if(!window.__XLITE_PAGE_LOADED__){window.__XLITE_PAGE_LOADED__=true;" +
                                pageScript + "}",
                        origins);
            }

            documentStartInstalled = true;
        } catch (Exception e) {
            documentStartInstalled = false;
        }
    }

    private String buildBootstrapScript() {
        String cpft = prefs.getString("cpft_config_json", "{}");
        if (cpft == null || cpft.trim().isEmpty()) cpft = "{}";

        try {
            JSONObject obj = new JSONObject(cpft);
            obj.put("bypassAgeVerification", false);
            if (!prefs.getBoolean("cpft_enabled", true)) obj.put("enabled", false);
            cpft = obj.toString();
        } catch (Exception e) {
            cpft = "{\"enabled\":" + prefs.getBoolean("cpft_enabled", true) +
                    ",\"bypassAgeVerification\":false}";
        }

        boolean translate = prefs.getBoolean("auto_translate", true);
        boolean lite = prefs.getBoolean("lite", true);

        return "window.__XLITE_CPFT_CONFIG__=" + cpft + ";" +
                "window.__XLITE={autoTranslate:" + translate + ",lite:" + lite + "};";
    }

    private void injectFallbackScripts() {
        StringBuilder js = new StringBuilder();
        js.append(buildBootstrapScript());

        if (prefs.getBoolean("cpft_enabled", true) && cpftScript != null && !cpftScript.isEmpty()) {
            js.append("if(!window.__XLITE_CPFT_LOADED__){window.__XLITE_CPFT_LOADED__=true;")
                    .append(cpftScript)
                    .append("}");
        }

        if (pageScript != null && !pageScript.isEmpty()) {
            js.append("if(!window.__XLITE_PAGE_LOADED__){window.__XLITE_PAGE_LOADED__=true;")
                    .append(pageScript)
                    .append("}");
        }

        web.evaluateJavascript(js.toString(), null);
    }

    private void applyScale() {
        int scale = prefs.getInt("j7_scale", 85);
        web.setInitialScale(scale);
    }

    private void applyUserAgent() {
        String ua = defaultUa == null ? "" : defaultUa;
        ua = ua.replace("; wv", "").replace("Version/4.0 ", "");
        web.getSettings().setUserAgentString(ua);
    }

    private void loadUrl(String url) {
        Map<String, String> headers = new HashMap<>();
        if (prefs.getBoolean("auto_translate", true)) {
            headers.put("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.7");
        }
        web.loadUrl(url, headers);
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                new ByteArrayInputStream(new byte[0]));
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

    private boolean isVideoManifest(Uri uri) {
        if (uri == null || uri.getHost() == null || uri.getPath() == null) return false;
        String host = uri.getHost().toLowerCase(Locale.US);
        String path = uri.getPath().toLowerCase(Locale.US);
        return host.endsWith("twimg.com") && path.contains(".m3u8");
    }

    private WebResourceResponse interceptAndCapHls(WebResourceRequest req) {
        HttpURLConnection con = null;
        try {
            URL url = new URL(req.getUrl().toString());
            con = (HttpURLConnection) url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);

            for (Map.Entry<String, String> h : req.getRequestHeaders().entrySet()) {
                if (h.getKey() == null) continue;
                if ("accept-encoding".equalsIgnoreCase(h.getKey())) continue;
                con.setRequestProperty(h.getKey(), h.getValue());
            }

            String cookie = CookieManager.getInstance().getCookie(req.getUrl().toString());
            if (cookie != null && !cookie.isEmpty()) {
                con.setRequestProperty("Cookie", cookie);
            }
            con.setRequestProperty("User-Agent", web.getSettings().getUserAgentString());

            int code = con.getResponseCode();
            if (code < 200 || code >= 300) return null;

            String body = readText(con.getInputStream());
            int max = prefs.getInt("video_max_height", 720);
            String capped = capHlsPlaylist(body, max);

            byte[] bytes = capped.getBytes(StandardCharsets.UTF_8);
            String mime = con.getContentType();
            if (mime == null || mime.isEmpty()) mime = "application/vnd.apple.mpegurl";
            int semicolon = mime.indexOf(';');
            if (semicolon > 0) mime = mime.substring(0, semicolon);

            WebResourceResponse response = new WebResourceResponse(
                    mime,
                    "UTF-8",
                    new ByteArrayInputStream(bytes));

            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> e : con.getHeaderFields().entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
                String key = e.getKey();
                if ("content-length".equalsIgnoreCase(key)
                        || "content-encoding".equalsIgnoreCase(key)) continue;
                headers.put(key, join(e.getValue(), ", "));
            }
            headers.put("Content-Length", String.valueOf(bytes.length));
            response.setResponseHeaders(headers);
            response.setStatusCodeAndReasonPhrase(200, "OK");
            return response;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private String capHlsPlaylist(String body, int maxQuality) {
        if (body == null || !body.contains("#EXT-X-STREAM-INF")) return body;

        String[] lines = body.replace("\r\n", "\n").split("\n");
        StringBuilder out = new StringBuilder();
        int totalVariants = 0;
        int keptVariants = 0;

        Pattern resolution = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith("#EXT-X-STREAM-INF")) {
                totalVariants++;
                boolean keep = true;

                Matcher m = resolution.matcher(line);
                if (m.find()) {
                    int w = Integer.parseInt(m.group(1));
                    int h = Integer.parseInt(m.group(2));
                    int tier = Math.min(w, h);
                    if (tier > maxQuality) keep = false;
                }

                String next = i + 1 < lines.length ? lines[i + 1] : "";
                if (keep) {
                    keptVariants++;
                    out.append(line).append('\n');
                    if (i + 1 < lines.length) {
                        out.append(next).append('\n');
                        i++;
                    }
                } else if (i + 1 < lines.length && !next.startsWith("#")) {
                    i++;
                }
                continue;
            }

            out.append(line).append('\n');
        }

        if (totalVariants > 0 && keptVariants == 0) return body;
        return out.toString();
    }

    private String readText(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = br.read(buf)) >= 0) sb.append(buf, 0, n);
        br.close();
        return sb.toString();
    }

    private String join(List<String> values, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(v);
        }
        return sb.toString();
    }

    private String readAsset(String name) {
        try {
            return readText(getAssets().open(name));
        } catch (Exception e) {
            return "";
        }
    }

    private void showMenu() {
        boolean ad = prefs.getBoolean("adblock", true);
        boolean lite = prefs.getBoolean("lite", true);
        boolean translate = prefs.getBoolean("auto_translate", true);
        boolean cpft = prefs.getBoolean("cpft_enabled", true);
        int scale = prefs.getInt("j7_scale", 85);
        int video = prefs.getInt("video_max_height", 720);

        String[] items = {
                "⌂  Início",
                "↻  Recarregar",
                "Escala J7: " + scale + "%",
                mark(translate) + "Tradução automática",
                "Vídeo máximo: " + video + "p",
                mark(cpft) + "Control Panel completo",
                "⚙  Opções completas do Control Panel",
                mark(ad) + "AdBlock",
                mark(lite) + "Modo leve / menos animações",
                "Reparar sessão do X",
                "Reset total da sessão",
                "Abrir página no navegador externo",
                "Sobre / WebView"
        };

        new AlertDialog.Builder(this)
                .setTitle("XLite for X v0.4.1")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            loadUrl(HOME);
                            break;
                        case 1:
                            web.reload();
                            break;
                        case 2:
                            showScaleDialog();
                            break;
                        case 3:
                            prefs.edit().putBoolean("auto_translate", !translate).apply();
                            recreate();
                            break;
                        case 4:
                            showVideoQualityDialog();
                            break;
                        case 5:
                            prefs.edit().putBoolean("cpft_enabled", !cpft).apply();
                            recreate();
                            break;
                        case 6:
                            startActivity(new Intent(this, SettingsActivity.class));
                            break;
                        case 7:
                            prefs.edit().putBoolean("adblock", !ad).apply();
                            web.reload();
                            break;
                        case 8:
                            prefs.edit().putBoolean("lite", !lite).apply();
                            recreate();
                            break;
                        case 9:
                            repairSession(false);
                            break;
                        case 10:
                            repairSession(true);
                            break;
                        case 11:
                            if (web.getUrl() != null) openExternal(Uri.parse(web.getUrl()));
                            break;
                        case 12:
                            showAbout();
                            break;
                    }
                })
                .show();
    }

    private void showScaleDialog() {
        final int[] values = {75, 80, 85, 90, 100};
        String[] labels = {
                "75% — máximo conteúdo",
                "80% — compacto",
                "85% — recomendado J7",
                "90%",
                "100% — normal"
        };
        int current = prefs.getInt("j7_scale", 85);
        int checked = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;

        new AlertDialog.Builder(this)
                .setTitle("Escala da página")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.edit().putInt("j7_scale", values[which]).apply();
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showVideoQualityDialog() {
        int current = prefs.getInt("video_max_height", 720);
        String[] labels = {
                "720p — recomendado para J7",
                "1080p — máximo",
        };

        new AlertDialog.Builder(this)
                .setTitle("Qualidade máxima de vídeo")
                .setSingleChoiceItems(labels, current == 1080 ? 1 : 0, (d, which) -> {
                    prefs.edit().putInt("video_max_height", which == 1 ? 1080 : 720).apply();
                    d.dismiss();
                    web.reload();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showAbout() {
        String webViewInfo = "desconhecido";
        try {
            PackageInfo p = WebViewCompat.getCurrentWebViewPackage(this);
            if (p != null) webViewInfo = p.packageName + " " + p.versionName;
        } catch (Exception ignored) {
        }

        new AlertDialog.Builder(this)
                .setTitle("XLite for X v0.4")
                .setMessage(
                        "Android 8+ • assinatura fixa desde a v0.4\n\n" +
                        "Gestos: borda esquerda → direita = voltar; borda direita → esquerda = avançar; do topo para baixo = menu.\n\n" +
                        "Control Panel for Twitter 4.24.1 completo integrado com injeção no início do documento quando o WebView suporta.\n\n" +
                        "Tradução automática usa o controle nativo de tradução do X.\n\n" +
                        "Vídeo: playlists HLS são filtradas para não anunciar variantes acima do limite escolhido.\n\n" +
                        "WebView: " + webViewInfo)
                .setPositiveButton("OK", null)
                .show();
    }

    private void repairSession(boolean full) {
        if (!full) {
            web.clearCache(true);
            web.evaluateJavascript(
                    "(function(){try{localStorage.clear();sessionStorage.clear();}catch(e){}" +
                            "setTimeout(function(){location.reload();},200);})();",
                    null);
            Toast.makeText(this, "Estado local do X reiniciado", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Reset total")
                .setMessage("Apaga cookies, armazenamento local e login deste app. " +
                        "Não altera a idade da conta nem ignora verificações do X.")
                .setPositiveButton("Resetar", (d, w) -> {
                    WebStorage.getInstance().deleteAllData();
                    web.clearCache(true);
                    web.clearHistory();
                    CookieManager.getInstance().removeAllCookies(ok -> {
                        CookieManager.getInstance().flush();
                        loadUrl(LOGIN);
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
            Toast.makeText(this, "Download iniciado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Falha no download", Toast.LENGTH_SHORT).show();
        } finally {
            pendingUrl = null;
        }
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Não há app para abrir este link", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideCustomView() {
        if (customView == null) return;

        root.removeView(customView);
        customView = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private String mark(boolean on) {
        return on ? "✓  " : "○  ";
    }

    private boolean isHttp(String s) {
        return s != null && (s.startsWith("https://") || s.startsWith("http://"));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backLongPressed = true;
            showMenu();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (backLongPressed) {
                backLongPressed = false;
                return true;
            }

            if (customView != null) {
                hideCustomView();
            } else if (web.canGoBack()) {
                web.goBack();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    @Deprecated
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enqueueDownload();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();

        if (resumedOnce) {
            String now = prefs.getString("cpft_config_json", "{}");
            if (now == null) now = "{}";
            if (!now.equals(cpftSnapshot)) {
                cpftSnapshot = now;
                recreate();
                return;
            }
        }
        resumedOnce = true;
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW && web != null) {
            web.clearCache(false);
            web.freeMemory();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.clearHistory();
            web.removeAllViews();
            web.destroy();
        }
        super.onDestroy();
    }
}
