package com.ximbica.tubelite;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final OkHttpClient apiHttp = new OkHttpClient();

    private RecyclerView recycler;
    private VideoAdapter adapter;
    private TextView status;
    private TextView nowTitle;
    private TextView nowChannel;
    private TextView microgBadge;
    private Button accountButton;
    private GoogleYouTubeAuth googleAuth;
    private EditText searchBox;
    private LinearLayout playerPanel;
    private PlayerView playerView;
    private ExoPlayer player;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NewPipe.init(
                new HttpDownloader(),
                new Localization("pt", "BR"),
                new ContentCountry("BR")
        );

        buildUi();
        updateMicroGStatus();
        initializeGoogleAuth();

        Uri deepLink = getIntent() == null ? null : getIntent().getData();
        if (deepLink != null && ("http".equals(deepLink.getScheme()) || "https".equals(deepLink.getScheme()))) {
            playVideo(deepLink.toString());
        } else {
            loadHome();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15, 15, 15));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(8));
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = new TextView(this);
        brand.setText("▶  TubeLite J7");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(19);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView adFree = badge("SEM ADS");
        top.addView(adFree);

        microgBadge = badge("microG ?");
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.setMargins(dp(8), 0, 0, 0);
        top.addView(microgBadge, badgeLp);

        accountButton = new Button(this);
        accountButton.setText("Entrar");
        accountButton.setAllCaps(false);
        accountButton.setTextSize(11);
        accountButton.setMinWidth(0);
        accountButton.setMinimumWidth(0);
        accountButton.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams accountLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        accountLp.setMargins(dp(6), 0, 0, 0);
        top.addView(accountButton, accountLp);
        accountButton.setOnClickListener(v -> showAccountMenu());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setPadding(dp(10), 0, dp(10), dp(8));
        root.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Pesquisar vídeos");
        searchBox.setHintTextColor(Color.rgb(145, 145, 145));
        searchBox.setTextColor(Color.WHITE);
        searchBox.setTextSize(15);
        searchBox.setBackgroundColor(Color.rgb(37, 37, 37));
        searchBox.setPadding(dp(12), 0, dp(12), 0);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button searchButton = new Button(this);
        searchButton.setText("Buscar");
        searchButton.setAllCaps(false);
        LinearLayout.LayoutParams searchButtonLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        searchButtonLp.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchButton, searchButtonLp);

        Button homeButton = new Button(this);
        homeButton.setText("Início");
        homeButton.setAllCaps(false);
        LinearLayout.LayoutParams homeLp =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        homeLp.setMargins(dp(6), 0, 0, 0);
        searchRow.addView(homeButton, homeLp);

        playerPanel = new LinearLayout(this);
        playerPanel.setOrientation(LinearLayout.VERTICAL);
        playerPanel.setVisibility(View.GONE);
        playerPanel.setBackgroundColor(Color.BLACK);
        root.addView(playerPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setKeepScreenOn(true);
        playerPanel.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        nowTitle = new TextView(this);
        nowTitle.setTextColor(Color.WHITE);
        nowTitle.setTextSize(16);
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setPadding(dp(12), dp(8), dp(12), 0);
        nowTitle.setMaxLines(2);
        playerPanel.addView(nowTitle);

        nowChannel = new TextView(this);
        nowChannel.setTextColor(Color.rgb(170, 170, 170));
        nowChannel.setTextSize(13);
        nowChannel.setPadding(dp(12), dp(4), dp(12), dp(8));
        playerPanel.addView(nowChannel);

        status = new TextView(this);
        status.setTextColor(Color.rgb(190, 190, 190));
        status.setTextSize(13);
        status.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(status);

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new VideoAdapter(item -> playVideo(item.getUrl()));
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        searchButton.setOnClickListener(v -> runSearch());
        homeButton.setOnClickListener(v -> loadHome());
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                runSearch();
                return true;
            }
            return false;
        });
    }

    private TextView badge(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(10);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(8), dp(5), dp(8), dp(5));
        t.setBackgroundColor(Color.rgb(70, 70, 70));
        return t;
    }

    private void updateMicroGStatus() {
        boolean revancedMicroG = packageExists("app.revanced.android.gms");
        boolean standardGms = packageExists("com.google.android.gms");

        if (revancedMicroG) {
            microgBadge.setText("microG ✓");
            microgBadge.setBackgroundColor(Color.rgb(30, 120, 70));
        } else if (standardGms) {
            microgBadge.setText("GMS ✓");
        } else {
            microgBadge.setText("microG —");
        }

        microgBadge.setOnClickListener(v -> Toast.makeText(
                this,
                revancedMicroG
                        ? "ReVanced GmsCore detectado. O login do TubeLite usa a autorização OAuth oficial quando Google Play Services compatível está disponível."
                        : standardGms
                        ? "Google Play Services detectado. Login OAuth disponível no botão de conta."
                        : "Google Play Services não detectado. O modo visitante continua funcionando normalmente.",
                Toast.LENGTH_LONG
        ).show());
    }

    private boolean packageExists(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void initializeGoogleAuth() {
        googleAuth = new GoogleYouTubeAuth(this, new GoogleYouTubeAuth.Listener() {
            @Override
            public void onAuthorized(GoogleYouTubeAuth.Session session) {
                String label = session.getDisplayName();
                if (label == null || label.trim().isEmpty()) {
                    label = session.getAccountName();
                }
                accountButton.setText("✓ " + compactAccountLabel(label));
                loadYoutubeProfile(session.getAccessToken());
            }

            @Override
            public void onResolutionRequired(PendingIntent pendingIntent) {
                launchGoogleAuthorization(pendingIntent);
            }

            @Override
            public void onSignedOut() {
                accountButton.setText("Entrar");
            }

            @Override
            public void onError(String message) {
                accountButton.setText("Entrar");
                status.setText("OAuth Google: " + message);
            }
        });

        googleAuth.restoreSilently();
    }

    private void showAccountMenu() {
        if (googleAuth == null) return;

        List<String> known = googleAuth.getKnownAccounts();
        String current = googleAuth.getCurrentAccountName();

        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add("+ Adicionar / trocar conta Google");
        actions.add(() -> googleAuth.chooseAccount());

        for (String account : known) {
            if (account.equals(current)) continue;
            labels.add("Usar " + account);
            actions.add(() -> googleAuth.switchAccount(account));
        }

        if (current != null && !current.isEmpty()) {
            labels.add("Desconectar " + current);
            actions.add(() -> googleAuth.revokeCurrent());
        }

        String title = current == null
                ? "Conta do YouTube"
                : "Conta atual: " + current;

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        actions.get(which).run();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void launchGoogleAuthorization(PendingIntent pendingIntent) {
        try {
            startIntentSenderForResult(
                    pendingIntent.getIntentSender(),
                    GoogleYouTubeAuth.REQUEST_AUTHORIZATION,
                    null,
                    0,
                    0,
                    0
            );
        } catch (IntentSender.SendIntentException e) {
            status.setText("Não foi possível abrir o login Google: " + e.getMessage());
        }
    }

    private void loadYoutubeProfile(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return;

        io.submit(() -> {
            Request request = new Request.Builder()
                    .url("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true&maxResults=1")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .build();

            try (Response response = apiHttp.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int code = response.code();
                    runOnUiThread(() -> status.setText(
                            "Google conectado, mas YouTube Data API respondeu HTTP " + code
                                    + ". Confira se a API v3 e o escopo youtube.readonly estão ativos."
                    ));
                    return;
                }

                String json = response.body() == null ? "{}" : response.body().string();
                JSONObject root = new JSONObject(json);
                JSONArray items = root.optJSONArray("items");
                String channelTitle = "";

                if (items != null && items.length() > 0) {
                    JSONObject snippet = items.optJSONObject(0) == null
                            ? null
                            : items.optJSONObject(0).optJSONObject("snippet");
                    if (snippet != null) {
                        channelTitle = snippet.optString("title", "");
                    }
                }

                final String title = channelTitle;
                runOnUiThread(() -> {
                    if (!title.isEmpty()) {
                        accountButton.setText("✓ " + compactAccountLabel(title));
                        status.setText("YouTube conectado • " + title);
                    } else {
                        status.setText("Conta Google autorizada para YouTube.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(
                        "Conta Google autorizada; falha ao consultar perfil: "
                                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                ));
            }
        });
    }

    private String compactAccountLabel(String value) {
        if (value == null || value.trim().isEmpty()) return "Conta";
        String clean = value.trim();
        return clean.length() <= 12 ? clean : clean.substring(0, 11) + "…";
    }

    private void runSearch() {
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) {
            loadHome();
            return;
        }

        status.setText("Buscando “" + q + "”…");
        io.submit(() -> {
            try {
                SearchInfo info = SearchInfo.getInfo(
                        ServiceList.YouTube,
                        ServiceList.YouTube.getSearchQHFactory().fromQuery(q)
                );
                List<StreamInfoItem> videos = onlyVideos(info.getRelatedItems());
                runOnUiThread(() -> {
                    adapter.setItems(videos);
                    status.setText(videos.size() + " resultados");
                    recycler.scrollToPosition(0);
                });
            } catch (Exception e) {
                showError("Falha na busca", e);
            }
        });
    }

    private void loadHome() {
        status.setText("Carregando destaques…");
        io.submit(() -> {
            try {
                KioskExtractor extractor = ServiceList.YouTube
                        .getKioskList()
                        .getDefaultKioskExtractor();
                extractor.fetchPage();
                KioskInfo info = KioskInfo.getInfo(extractor);
                List<StreamInfoItem> videos = new ArrayList<>(info.getRelatedItems());
                runOnUiThread(() -> {
                    adapter.setItems(videos);
                    status.setText("Destaques • " + videos.size() + " vídeos");
                    recycler.scrollToPosition(0);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Início indisponível. A pesquisa continua funcionando.");
                    adapter.setItems(new ArrayList<>());
                });
            }
        });
    }

    private List<StreamInfoItem> onlyVideos(List<InfoItem> input) {
        List<StreamInfoItem> out = new ArrayList<>();
        for (InfoItem item : input) {
            if (item instanceof StreamInfoItem) {
                out.add((StreamInfoItem) item);
            }
        }
        return out;
    }

    private void playVideo(String url) {
        status.setText("Abrindo vídeo…");
        io.submit(() -> {
            try {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
                String mediaUrl = choosePlaybackUrl(info);
                if (mediaUrl == null || mediaUrl.isEmpty()) {
                    throw new IllegalStateException("Nenhum stream reproduzível foi encontrado");
                }

                runOnUiThread(() -> {
                    ensurePlayer();
                    playerPanel.setVisibility(View.VISIBLE);
                    nowTitle.setText(info.getName());
                    String channel = info.getUploaderName() == null ? "" : info.getUploaderName();
                    String quality = selectedQuality(info, mediaUrl);
                    nowChannel.setText(channel + (quality.isEmpty() ? "" : "  •  " + quality));
                    player.setMediaItem(MediaItem.fromUri(mediaUrl));
                    player.prepare();
                    player.play();
                    status.setText("Reproduzindo sem camada de anúncios");
                });
            } catch (Exception e) {
                showError("Não foi possível abrir o vídeo", e);
            }
        });
    }

    private String choosePlaybackUrl(StreamInfo info) {
        VideoStream best = null;
        int bestScore = Integer.MIN_VALUE;

        for (VideoStream stream : info.getVideoStreams()) {
            if (!stream.isUrl()) continue;

            int height = parseHeight(stream.getResolution());
            boolean mp4 = stream.getFormat() == MediaFormat.MPEG_4;

            int score;
            if (height > 0 && height <= 720) {
                score = height * 10;
            } else if (height > 720) {
                score = 100 - height;
            } else {
                score = 1;
            }
            if (mp4) score += 5;

            if (score > bestScore) {
                bestScore = score;
                best = stream;
            }
        }

        if (best != null) {
            return best.getContent();
        }

        String hls = info.getHlsUrl();
        if (hls != null && !hls.isEmpty()) {
            return hls;
        }

        return null;
    }

    private String selectedQuality(StreamInfo info, String url) {
        for (VideoStream stream : info.getVideoStreams()) {
            if (url.equals(stream.getContent())) {
                String res = stream.getResolution();
                return (res == null || res.isEmpty()) ? "auto" : res;
            }
        }
        return url.equals(info.getHlsUrl()) ? "HLS auto" : "";
    }

    private int parseHeight(String resolution) {
        if (resolution == null) return -1;
        String lower = resolution.toLowerCase(Locale.US);
        int p = lower.indexOf('p');
        if (p <= 0) return -1;
        int start = p - 1;
        while (start >= 0 && Character.isDigit(lower.charAt(start))) start--;
        try {
            return Integer.parseInt(lower.substring(start + 1, p));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void ensurePlayer() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
        }
    }

    private void showError(String prefix, Exception e) {
        String raw = e.getMessage();
        String msg = raw == null ? e.getClass().getSimpleName() : raw;
        if (msg.contains("Sign in to confirm") || msg.contains("LOGIN_REQUIRED")) {
            msg = "O YouTube bloqueou temporariamente o acesso anônimo desta rede/IP.";
        }
        final String finalMsg = prefix + ": " + msg;
        runOnUiThread(() -> status.setText(finalMsg));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GoogleYouTubeAuth.REQUEST_AUTHORIZATION) {
            if (resultCode == RESULT_OK && data != null && googleAuth != null) {
                googleAuth.handleResolutionResult(data);
            } else {
                status.setText("Login Google cancelado.");
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null) playVideo(data.toString());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
