package com.ximbica.gptlewd.gecko;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public final class MainActivity extends Activity {
    private static final String HOME = "https://chatgpt.com/";
    private static GeckoRuntime runtime;

    private GeckoView geckoView;
    private GeckoSession session;
    private ProgressBar progress;
    private boolean canGoBack = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(16, 0, 7));
        getWindow().setNavigationBarColor(Color.rgb(16, 0, 7));
        setContentView(R.layout.activity_main);

        geckoView = findViewById(R.id.gecko_view);
        progress = findViewById(R.id.progress);

        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .remoteDebuggingEnabled(false)
                    .consoleOutput(false)
                    .build();
            runtime = GeckoRuntime.create(this, settings);
        }

        session = new GeckoSession();
        session.setContentDelegate(new GeckoSession.ContentDelegate() {});

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(@NonNull GeckoSession session, @NonNull String url) {
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(8);
            }

            @Override
            public void onProgressChange(@NonNull GeckoSession session, int value) {
                progress.setProgress(value);
                if (value >= 100) {
                    progress.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPageStop(@NonNull GeckoSession session, boolean success) {
                progress.setVisibility(View.GONE);
                if (!success) {
                    Toast.makeText(MainActivity.this, "Falha ao carregar a página", Toast.LENGTH_SHORT).show();
                }
            }
        });

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onCanGoBack(@NonNull GeckoSession session, boolean value) {
                canGoBack = value;
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(
                    @NonNull GeckoSession session,
                    @NonNull LoadRequest request) {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }
        });

        session.open(runtime);
        geckoView.setSession(session);
        session.loadUri(HOME);
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
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }
}
