package com.ximbica.tubelite;

import android.accounts.Account;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.Scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OAuth 2.0 authorization for the YouTube Data API.
 *
 * The Android OAuth client is registered in Google Cloud by package name +
 * signing-certificate SHA-1. It is intentionally not used as a client secret.
 * Access tokens are short lived; Google Play services silently refreshes them
 * by running authorize() again while the user's grant remains valid.
 */
public final class GoogleYouTubeAuth {
    public static final int REQUEST_AUTHORIZATION = 4107;
    public static final String YOUTUBE_READONLY =
            "https://www.googleapis.com/auth/youtube.readonly";

    private static final String PREFS = "google_youtube_oauth";
    private static final String KEY_CURRENT = "current_account";
    private static final String KEY_KNOWN = "known_accounts";

    public interface Listener {
        void onAuthorized(Session session);
        void onResolutionRequired(PendingIntent pendingIntent);
        void onSignedOut();
        void onError(String message);
    }

    public static final class Session {
        private final String accessToken;
        private final String accountName;
        private final String displayName;
        private final Account account;

        Session(String accessToken, String accountName, String displayName, Account account) {
            this.accessToken = accessToken;
            this.accountName = accountName;
            this.displayName = displayName;
            this.account = account;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getAccountName() {
            return accountName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Account getAccount() {
            return account;
        }
    }

    private final AuthorizationClient client;
    private final SharedPreferences prefs;
    private final Listener listener;
    private final List<Scope> scopes;

    private Session session;

    public GoogleYouTubeAuth(Activity activity, Listener listener) {
        this.client = Identity.getAuthorizationClient(activity);
        this.prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        this.listener = listener;
        this.scopes = Collections.singletonList(new Scope(YOUTUBE_READONLY));
    }

    /** Reacquires a fresh access token without showing UI when possible. */
    public void restoreSilently() {
        AuthorizationRequest.Builder builder = baseRequest();
        String current = getCurrentAccountName();
        if (current != null && !current.isEmpty()) {
            builder.setAccount(new Account(current, "com.google"));
        }
        perform(builder.build(), false);
    }

    /** Always opens Google's account selector, allowing another account to be added/chosen. */
    public void chooseAccount() {
        AuthorizationRequest request = baseRequest()
                .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
                .build();
        perform(request, true);
    }

    /** Switches to an account previously authorized by this app. */
    public void switchAccount(String accountName) {
        if (accountName == null || accountName.trim().isEmpty()) {
            chooseAccount();
            return;
        }
        AuthorizationRequest request = baseRequest()
                .setAccount(new Account(accountName, "com.google"))
                .build();
        perform(request, true);
    }

    public void handleResolutionResult(Intent data) {
        if (data == null) {
            listener.onError("Login cancelado.");
            return;
        }
        try {
            AuthorizationResult result = client.getAuthorizationResultFromIntent(data);
            consume(result);
        } catch (Exception e) {
            listener.onError(messageOf(e));
        }
    }

    /** Revokes the YouTube read-only grant for the currently selected account. */
    public void revokeCurrent() {
        String current = getCurrentAccountName();
        if (current == null || current.isEmpty()) {
            clearCurrentLocal();
            listener.onSignedOut();
            return;
        }

        Account account = session != null && session.getAccount() != null
                ? session.getAccount()
                : new Account(current, "com.google");

        RevokeAccessRequest request = RevokeAccessRequest.builder()
                .setAccount(account)
                .setScopes(scopes)
                .build();

        client.revokeAccess(request)
                .addOnSuccessListener(unused -> {
                    removeKnownAccount(current);
                    session = null;
                    listener.onSignedOut();
                })
                .addOnFailureListener(e -> listener.onError(
                        "Falha ao desconectar: " + messageOf(e)));
    }

    public List<String> getKnownAccounts() {
        Set<String> saved = prefs.getStringSet(KEY_KNOWN, Collections.emptySet());
        List<String> result = new ArrayList<>(saved == null
                ? Collections.emptySet()
                : saved);
        Collections.sort(result);

        String current = getCurrentAccountName();
        if (current != null && result.remove(current)) {
            result.add(0, current);
        }
        return result;
    }

    public String getCurrentAccountName() {
        return prefs.getString(KEY_CURRENT, null);
    }

    private AuthorizationRequest.Builder baseRequest() {
        return AuthorizationRequest.builder().setRequestedScopes(scopes);
    }

    private void perform(AuthorizationRequest request, boolean interactive) {
        client.authorize(request)
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        PendingIntent pendingIntent = result.getPendingIntent();
                        if (interactive && pendingIntent != null) {
                            listener.onResolutionRequired(pendingIntent);
                        } else {
                            listener.onSignedOut();
                        }
                        return;
                    }
                    consume(result);
                })
                .addOnFailureListener(e -> listener.onError(messageOf(e)));
    }

    @SuppressWarnings("deprecation")
    private void consume(AuthorizationResult result) {
        String token = result.getAccessToken();
        if (token == null || token.isEmpty()) {
            listener.onError("O Google não retornou um token de acesso.");
            return;
        }

        GoogleSignInAccount google = result.toGoogleSignInAccount();
        Account account = google == null ? null : google.getAccount();
        String accountName = google == null ? null : google.getEmail();
        String displayName = google == null ? null : google.getDisplayName();

        if ((accountName == null || accountName.isEmpty()) && account != null) {
            accountName = account.name;
        }
        if (accountName == null || accountName.isEmpty()) {
            accountName = getCurrentAccountName();
        }
        if (account == null && accountName != null && !accountName.isEmpty()) {
            account = new Account(accountName, "com.google");
        }

        if (accountName != null && !accountName.isEmpty()) {
            rememberAccount(accountName);
        }

        session = new Session(token, accountName, displayName, account);
        listener.onAuthorized(session);
    }

    private void rememberAccount(String accountName) {
        Set<String> oldSet = prefs.getStringSet(KEY_KNOWN, Collections.emptySet());
        Set<String> copy = new HashSet<>(oldSet == null
                ? Collections.emptySet()
                : oldSet);
        copy.add(accountName);
        prefs.edit()
                .putString(KEY_CURRENT, accountName)
                .putStringSet(KEY_KNOWN, copy)
                .apply();
    }

    private void removeKnownAccount(String accountName) {
        Set<String> oldSet = prefs.getStringSet(KEY_KNOWN, Collections.emptySet());
        Set<String> copy = new HashSet<>(oldSet == null
                ? Collections.emptySet()
                : oldSet);
        copy.remove(accountName);

        SharedPreferences.Editor editor = prefs.edit().putStringSet(KEY_KNOWN, copy);
        if (accountName.equals(getCurrentAccountName())) {
            if (copy.isEmpty()) {
                editor.remove(KEY_CURRENT);
            } else {
                editor.putString(KEY_CURRENT, copy.iterator().next());
            }
        }
        editor.apply();
    }

    private void clearCurrentLocal() {
        prefs.edit().remove(KEY_CURRENT).apply();
        session = null;
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : message;
    }
}
