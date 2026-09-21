package com.ximbica.xlite;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public final class MainActivity extends Activity {
    private static final String HOME = "https://x.com/home";
    private static GeckoRuntime runtime;

    private GeckoSession session;
    private boolean canGoBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        GeckoView view = findViewById(R.id.gecko_view);

        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(false)
                    .consoleOutput(false)
                    .build();
            runtime = GeckoRuntime.create(this, settings);

            runtime.getWebExtensionController()
                    .installBuiltIn("resource://android/assets/xlite/")
                    .accept(extension -> { }, error -> { });
        }

        session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() { });
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(GeckoSession geckoSession, boolean value) {
                canGoBack = value;
            }
        });

        session.open(runtime);
        view.setSession(session);

        String initialUrl = HOME;
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            Uri uri = intent.getData();
            String host = uri.getHost();
            if ("x.com".equalsIgnoreCase(host) || "twitter.com".equalsIgnoreCase(host)) {
                initialUrl = uri.toString();
            }
        }
        session.loadUri(initialUrl);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null && session != null) {
            Uri uri = intent.getData();
            String host = uri.getHost();
            if ("x.com".equalsIgnoreCase(host) || "twitter.com".equalsIgnoreCase(host)) {
                session.loadUri(uri.toString());
            }
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
