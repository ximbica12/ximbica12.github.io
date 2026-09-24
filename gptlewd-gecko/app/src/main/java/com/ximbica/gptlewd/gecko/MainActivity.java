package com.ximbica.gptlewd.gecko;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

public final class MainActivity extends Activity {
    private static final String HOME = "https://chatgpt.com/";
    private static GeckoRuntime runtime;

    private GeckoSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(16, 0, 7));
        getWindow().setNavigationBarColor(Color.rgb(16, 0, 7));
        setContentView(R.layout.activity_main);

        GeckoView view = findViewById(R.id.gecko_view);
        session = new GeckoSession();

        // Workaround recommended by GeckoView's official quick-start.
        session.setContentDelegate(new GeckoSession.ContentDelegate() {});

        if (runtime == null) {
            runtime = GeckoRuntime.create(this);
        }

        session.open(runtime);
        view.setSession(session);
        session.loadUri(HOME);
    }

    @Override
    public void onBackPressed() {
        if (session != null) {
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
