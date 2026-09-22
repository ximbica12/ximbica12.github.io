package com.ximbica.tubelite;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
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
    private TextView accountButton;
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
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.SURFACE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(10), dp(12), dp(8));
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        TextView brandMark = new TextView(this);
        brandMark.setText("▶");
        brandMark.setTextColor(Ui.PRIMARY);
        brandMark.setTextSize(17);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setBackground(Ui.round(this, Ui.PRIMARY_CONTAINER, 12));
        top.addView(brandMark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setPadding(dp(10), 0, 0, 0);
        top.addView(titleWrap, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView brand = new TextView(this);
        brand.setText("TubeLite");
        brand.setTextColor(Ui.TEXT);
        brand.setTextSize(20);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleWrap.addView(brand);

        TextView subtitle = new TextView(this);
        subtitle.setText("leve • Android 8");
        subtitle.setTextColor(0xFF938F99);
        subtitle.setTextSize(10.5f);
        titleWrap.addView(subtitle);

        accountButton = new TextView(this);
        accountButton.setText("Entrar");
        accountButton.setTextColor(Ui.ON_PRIMARY_CONTAINER);
        accountButton.setTextSize(12);
        accountButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        accountButton.setGravity(Gravity.CENTER);
        accountButton.setPadding(dp(13), 0, dp(13), 0);
        accountButton.setBackground(Ui.round(this, Ui.PRIMARY_CONTAINER, 20));
        top.addView(accountButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        accountButton.setOnClickListener(v -> showAccountMenu());

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(12), dp(2), dp(12), dp(8));
        root.addView(searchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Pesquisar no YouTube");
        searchBox.setHintTextColor(0xFF938F99);
        searchBox.setTextColor(Ui.TEXT);
        searchBox.setTextSize(14);
        searchBox.setBackground(Ui.roundStroke(this, Ui.SURFACE_2, 24, Ui.OUTLINE, 1));
        searchBox.setPadding(dp(16), 0, dp(14), 0);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setSelectAllOnFocus(false);
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, dp(48), 1f));

        ImageButton searchButton = new ImageButton(this);
        searchButton.setImageResource(R.drawable.ic_search);
        searchButton.setScaleType(ImageView.ScaleType.CENTER);
        searchButton.setPadding(dp(11), dp(11), dp(11), dp(11));
        searchButton.setBackground(Ui.circle(Ui.PRIMARY_CONTAINER));
        searchButton.setContentDescription("Pesquisar");
        LinearLayout.LayoutParams searchButtonLp =
                new LinearLayout.LayoutParams(dp(48), dp(48));
        searchButtonLp.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchButton, searchButtonLp);

        playerPanel = new LinearLayout(this);
        playerPanel.setOrientation(LinearLayout.VERTICAL);
        playerPanel.setVisibility(View.GONE);
        playerPanel.setBackgroundColor(Ui.SURFACE);
        root.addView(playerPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setKeepScreenOn(true);
        playerPanel.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(215)));

        nowTitle = new TextView(this);
        nowTitle.setTextColor(Ui.TEXT);
        nowTitle.setTextSize(16);
        nowTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitle.setPadding(dp(14), dp(10), dp(14), 0);
        nowTitle.setMaxLines(2);
        playerPanel.addView(nowTitle);

        nowChannel = new TextView(this);
        nowChannel.setTextColor(Ui.MUTED);
        nowChannel.setTextSize(12);
        nowChannel.setPadding(dp(14), dp(4), dp(14), dp(10));
        playerPanel.addView(nowChannel);

        status = new TextView(this);
        status.setTextColor(Ui.MUTED);
        status.setTextSize(11.5f);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), 0, dp(12), 0);
        status.setBackground(Ui.round(this, Ui.SURFACE_2, 13));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        statusLp.setMargins(dp(12), dp(2), dp(12), dp(3));
        root.addView(status, statusLp);

        recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);
        recycler.setItemAnimator(null);
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recycler.setBackgroundColor(Ui.BG);
        recycler.setPadding(0, dp(2), 0, dp(4));
        adapter = new VideoAdapter(item -> playVideo(item.getUrl()));
        recycler.setAdapter(adapter);
        root.addView(recycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setBackgroundColor(Ui.SURFACE);
        bottomNav.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        bottomNav.addView(makeNavButton(
                R.drawable.ic_home,
                "Início",
                true,
                () -> {
                    hideKeyboard();
                    loadHome();
                }
        ), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        bottomNav.addView(makeNavButton(
                R.drawable.ic_search,
                "Pesquisar",
                false,
                () -> {
                    searchBox.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT);
                }
        ), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        bottomNav.addView(makeNavButton(
                R.drawable.ic_account,
                "Conta",
                false,
                this::showAccountMenu
        ), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        searchButton.setOnClickListener(v -> runSearch());
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                hideKeyboard();
                runSearch();
                return true;
            }
            return false;
        });
    }

    private LinearLayout makeNavButton(int iconRes, String label, boolean selected, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), dp(3), dp(6), dp(3));

        LinearLayout iconPill = new LinearLayout(this);
        iconPill.setGravity(Gravity.CENTER);
        iconPill.setBackground(selected
                ? Ui.round(this, Ui.PRIMARY_CONTAINER, 20)
                : Ui.round(this, Ui.SURFACE, 20));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconPill.addView(icon, new LinearLayout.LayoutParams(dp(21), dp(21)));
        item.addView(iconPill, new LinearLayout.LayoutParams(dp(56), dp(31)));

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(selected ? Ui.PRIMARY : Ui.MUTED);
        text.setTextSize(10.5f);
        text.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMargins(0, dp(2), 0, 0);
        item.addView(text, textLp);

        item.setOnClickListener(v -> action.run());
        return item;
    }

    private void hideKeyboard() {
        View current = getCurrentFocus();
        if (current == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(current.getWindowToken(), 0);
        current.clearFocus();
    }

    private void updateMicroGStatus() {
        boolean revancedMicroG = packageExists("app.revanced.android.gms");
        boolean standardGms = packageExists("com.google.android.gms");
        String statusText = revancedMicroG
                ? "ReVanced GmsCore detectado"
                : standardGms
                ? "Google Play Services detectado"
                : "Google Play Services não detectado";
        accountButton.setContentDescription("Conta • " + statusText);
        accountButton.setOnLongClickListener(v -> {
            Toast.makeText(this, statusText, Toast.LENGTH_SHORT).show();
            return true;
        });
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
                status.setText("Login cancelado/bloqueado. Se apareceu erro 403, libere esta conta em Público-alvo > Usuários de teste no Google Cloud.");
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
