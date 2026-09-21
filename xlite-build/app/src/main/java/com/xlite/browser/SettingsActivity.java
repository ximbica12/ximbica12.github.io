package com.xlite.browser;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class SettingsActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(21, 32, 43));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        web.addJavascriptInterface(new ConfigBridge(), "AndroidConfig");
        web.setWebViewClient(new WebViewClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/cpft-options.html");
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
            return "{}";
        }
    }

    private final class ConfigBridge {
        @JavascriptInterface
        public String getEffectiveConfig() {
            try {
                JSONObject base = new JSONObject(readAsset("cpft-defaults.json"));
                String saved = getSharedPreferences("xlite", MODE_PRIVATE)
                        .getString("cpft_config_json", "{}");
                JSONObject user = new JSONObject(saved == null ? "{}" : saved);
                Iterator<String> keys = user.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    base.put(key, user.get(key));
                }
                base.put("bypassAgeVerification", false);
                return base.toString();
            } catch (Exception e) {
                return "{\"bypassAgeVerification\":false}";
            }
        }

        @JavascriptInterface
        public void saveConfig(String json) {
            try {
                JSONObject obj = new JSONObject(json == null ? "{}" : json);
                obj.put("bypassAgeVerification", false);
                getSharedPreferences("xlite", MODE_PRIVATE)
                        .edit()
                        .putString("cpft_config_json", obj.toString())
                        .apply();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AndroidConfig");
            web.destroy();
        }
        super.onDestroy();
    }
}
