package com.ximbica.gptlewd.web;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.net.URLConnection;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 5001;
    private static final int STORAGE_PERMISSION = 5002;
    private static final String HOME = "https://chatgpt.com/";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 0, 9));
        getWindow().setNavigationBarColor(Color.rgb(18, 0, 9));
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web);
        configureWebView();

        if (savedInstanceState == null) {
            webView.loadUrl(HOME);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(92);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Remove the classic "; wv" marker and present a normal Android Chrome UA.
        // This improves compatibility with modern web login pages on old Android WebView.
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 8.1.0; SM-G610M Build/M1AJQ) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 " +
            "Mobile Safari/537.36"
        );

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(Color.rgb(18, 0, 9));
        webView.setWebViewClient(new LewdWebViewClient());
        webView.setWebChromeClient(new LewdChromeClient());
        webView.setDownloadListener(new LewdDownloadListener());

        WebView.setWebContentsDebuggingEnabled(false);
    }

    private final class LewdWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            injectLewdTheme();
            CookieManager.getInstance().flush();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null) return false;
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                if (isChatOrAuthHost(host) || !request.hasGesture()) {
                    return false;
                }
                openExternal(uri);
                return true;
            }

            if ("intent".equalsIgnoreCase(scheme)) {
                try {
                    Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                    startActivity(intent);
                } catch (Exception ignored) {
                }
                return true;
            }

            return false;
        }
    }

    private boolean isChatOrAuthHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("chatgpt.com")
            || host.endsWith(".chatgpt.com")
            || host.equals("openai.com")
            || host.endsWith(".openai.com")
            || host.equals("accounts.google.com")
            || host.endsWith(".google.com")
            || host.endsWith(".microsoftonline.com")
            || host.endsWith(".live.com")
            || host.equals("appleid.apple.com")
            || host.endsWith(".auth0.com");
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            webView.loadUrl(uri.toString());
        }
    }

    private final class LewdChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView webView,
                                         ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(null);
            }
            fileCallback = filePathCallback;

            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception e) {
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
            }

            try {
                startActivityForResult(intent, FILE_CHOOSER);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                Toast.makeText(MainActivity.this, "Nenhum seletor de arquivos disponível", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    private final class LewdDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(String url, String userAgent,
                                    String contentDisposition, String mimetype,
                                    long contentLength) {
            if (android.os.Build.VERSION.SDK_INT <= 28
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION
                );
                Toast.makeText(MainActivity.this,
                    "Permita armazenamento e toque no download novamente",
                    Toast.LENGTH_LONG).show();
                return;
            }

            try {
                String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setTitle(fileName);
                req.setDescription("GPTLewd Web");
                req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );
                req.setAllowedOverMetered(true);
                req.setAllowedOverRoaming(true);

                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && !cookie.isEmpty()) {
                    req.addRequestHeader("Cookie", cookie);
                }
                req.addRequestHeader("User-Agent", webView.getSettings().getUserAgentString());

                if (mimetype != null && !mimetype.isEmpty()) {
                    req.setMimeType(mimetype);
                } else {
                    String guessed = URLConnection.guessContentTypeFromName(fileName);
                    if (guessed != null) req.setMimeType(guessed);
                }

                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(req);
                Toast.makeText(MainActivity.this, "Baixando: " + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this,
                    "Falha ao iniciar download: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void injectLewdTheme() {
        String css =
            ":root{" +
            "--gl-bg:#120009;--gl-panel:#210014;--gl-panel2:#310521;" +
            "--gl-hot:#ff2d8e;--gl-pink:#ff77b7;--gl-red:#ff315f;" +
            "--gl-purple:#c54cff;--gl-text:#fff2f8;--gl-muted:#caa7b9;" +
            "}" +
            "html,body{background:var(--gl-bg)!important;color:var(--gl-text)!important;}" +
            "body{background-image:" +
            "radial-gradient(circle at 20% -10%,rgba(255,45,142,.18),transparent 34%)," +
            "radial-gradient(circle at 110% 40%,rgba(197,76,255,.13),transparent 38%)!important;}" +
            "*{scrollbar-color:#7d174f #16000c!important;}" +
            "header,nav,aside,[class*='sidebar'],[class*='Sidebar']{" +
            "background-color:rgba(24,0,13,.96)!important;" +
            "border-color:rgba(255,45,142,.18)!important;}" +
            "button,[role='button']{border-radius:18px!important;" +
            "transition:none!important;}" +
            "button:hover,[role='button']:hover{" +
            "box-shadow:0 0 0 1px rgba(255,45,142,.45),0 0 14px rgba(255,45,142,.16)!important;}" +
            "textarea,[contenteditable='true'],input{" +
            "caret-color:var(--gl-hot)!important;}" +
            "form{border-radius:28px!important;" +
            "box-shadow:0 0 0 1px rgba(255,45,142,.22),0 8px 30px rgba(70,0,38,.20)!important;}" +
            "[data-message-author-role='user']{filter:saturate(1.12)!important;}" +
            "[data-message-author-role='user']>div{" +
            "border-radius:24px 24px 8px 24px!important;" +
            "background:linear-gradient(135deg,#4a0b31,#281020)!important;" +
            "border:1px solid rgba(255,119,183,.18)!important;}" +
            "a{color:#ff77b7!important;}" +
            "::selection{background:#ff2d8e!important;color:white!important;}" +
            "[class*='animate-'],[class*='transition-']{animation-duration:.01ms!important;" +
            "transition-duration:.01ms!important;}" +
            "@media(max-width:420px){" +
            "body{font-size:15px!important;} form{margin-left:4px!important;margin-right:4px!important;}" +
            "}" +
            "#gptlewd-badge{position:fixed;right:8px;bottom:7px;z-index:2147483646;" +
            "font:700 9px sans-serif;letter-spacing:.12em;color:#ffb0d0;" +
            "background:rgba(32,0,18,.72);border:1px solid rgba(255,45,142,.35);" +
            "border-radius:999px;padding:4px 7px;pointer-events:none;" +
            "box-shadow:0 0 12px rgba(255,45,142,.13)}";

        String js =
            "(function(){" +
            "var s=document.getElementById('gptlewd-style');" +
            "if(!s){s=document.createElement('style');s.id='gptlewd-style';document.head.appendChild(s);}" +
            "s.textContent=" + jsQuote(css) + ";" +
            "if(!document.getElementById('gptlewd-badge')){" +
            "var b=document.createElement('div');b.id='gptlewd-badge';b.textContent='18+  GPTLEWD';" +
            "document.documentElement.appendChild(b);" +
            "}" +
            "})();";

        webView.evaluateJavascript(js, null);
    }

    private String jsQuote(String value) {
        return "'" + value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", "\\n") + "'";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER) {
            if (fileCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
