package com.github.libretube.ui.activities

import android.accounts.Account
import android.app.PendingIntent
import android.content.IntentSender
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.ui.base.BaseActivity
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Optional Google/YouTube bridge for TubeLite.
 *
 * LibreTube remains independent from Google. This screen only uses Google's
 * official OAuth authorization to read the selected user's YouTube
 * subscriptions, then imports those channel IDs through LibreTube's existing
 * SubscriptionHelper. Piped/LibreTube Sync accounts keep working independently.
 */
class GoogleAccountActivity : BaseActivity() {
    companion object {
        private const val YOUTUBE_READONLY =
            "https://www.googleapis.com/auth/youtube.readonly"
        private const val PREFS = "tubelite_google_youtube"
        private const val KEY_ACCOUNT = "account_email"
    }

    private lateinit var client: AuthorizationClient
    private val http = OkHttpClient()
    private val scopes = listOf(Scope(YOUTUBE_READONLY))

    private lateinit var accountLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var syncButton: MaterialButton
    private lateinit var signOutButton: MaterialButton

    private var accessToken: String? = null

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            showOAuthBlockedHint()
            return@registerForActivityResult
        }

        runCatching {
            client.getAuthorizationResultFromIntent(result.data)
        }.onSuccess(::consumeAuthorization)
            .onFailure {
                setStatus("Falha ao concluir o login: ${it.message ?: it.javaClass.simpleName}")
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = Identity.getAuthorizationClient(this)
        buildUi()
        updateAccountUi()
        restoreAuthorizationSilently()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "Conta Google / YouTube"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)

        val description = TextView(this).apply {
            text = "Conecte qualquer conta Google autorizada e importe as inscrições dessa conta para o LibreTube. O login Piped/LibreTube Sync continua separado."
            textSize = 14f
            alpha = 0.78f
            setPadding(0, dp(8), 0, dp(20))
        }
        root.addView(description)

        accountLabel = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(14))
        }
        root.addView(accountLabel)

        val loginButton = MaterialButton(this).apply {
            text = "Entrar / trocar conta Google"
            isAllCaps = false
            setOnClickListener { authorize(selectAccount = true) }
        }
        root.addView(
            loginButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        syncButton = MaterialButton(this).apply {
            text = "Sincronizar inscrições do YouTube"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { syncSubscriptionsWithFreshToken() }
        }
        val syncLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        root.addView(syncButton, syncLp)

        signOutButton = MaterialButton(this).apply {
            text = "Desconectar conta Google"
            isAllCaps = false
            isEnabled = false
            setOnClickListener { revokeCurrentAccount() }
        }
        val signOutLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        root.addView(signOutButton, signOutLp)

        statusLabel = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.START
            setPadding(0, dp(18), 0, 0)
            alpha = 0.86f
        }
        root.addView(statusLabel)
    }

    private fun updateAccountUi() {
        val email = savedAccount()
        if (email.isNullOrBlank()) {
            accountLabel.text = "Nenhuma conta Google conectada"
            syncButton.isEnabled = false
            signOutButton.isEnabled = false
        } else {
            accountLabel.text = email
            signOutButton.isEnabled = true
        }
    }

    private fun authorize(selectAccount: Boolean) {
        setStatus(if (selectAccount) "Abrindo seletor de contas…" else "Renovando autorização…")

        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(scopes)

        val saved = savedAccount()
        if (selectAccount) {
            builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        } else if (!saved.isNullOrBlank()) {
            builder.setAccount(Account(saved, "com.google"))
        }

        client.authorize(builder.build())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent: PendingIntent? = result.pendingIntent
                    if (pendingIntent == null) {
                        setStatus("O Google pediu confirmação, mas não retornou a tela de autorização.")
                    } else {
                        launchResolution(pendingIntent)
                    }
                } else {
                    consumeAuthorization(result)
                }
            }
            .addOnFailureListener {
                setStatus("OAuth Google: ${it.message ?: it.javaClass.simpleName}")
            }
    }

    private fun restoreAuthorizationSilently() {
        if (savedAccount().isNullOrBlank()) {
            setStatus("Use “Entrar / trocar conta Google” para conectar uma conta.")
            return
        }
        authorize(selectAccount = false)
    }

    @Suppress("DEPRECATION")
    private fun consumeAuthorization(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            setStatus("O Google não retornou um token de acesso.")
            return
        }

        val googleAccount = result.toGoogleSignInAccount()
        val email = googleAccount?.email ?: googleAccount?.account?.name ?: savedAccount()

        if (!email.isNullOrBlank()) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_ACCOUNT, email)
                .apply()
        }

        accessToken = token
        updateAccountUi()
        syncButton.isEnabled = true
        setStatus("Conta autorizada. Agora você pode sincronizar as inscrições.")
    }

    private fun launchResolution(pendingIntent: PendingIntent) {
        try {
            authLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        } catch (e: IntentSender.SendIntentException) {
            setStatus("Não foi possível abrir o login Google: ${e.message}")
        }
    }

    private fun syncSubscriptionsWithFreshToken() {
        val current = accessToken
        if (current.isNullOrBlank()) {
            authorize(selectAccount = false)
            setStatus("Renovando autorização. Depois toque novamente em sincronizar.")
            return
        }

        syncButton.isEnabled = false
        setStatus("Lendo inscrições da conta do YouTube…")

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val channelIds = fetchYoutubeSubscriptionIds(current)
                if (channelIds.isEmpty()) {
                    return@runCatching 0
                }
                SubscriptionHelper.importSubscriptions(channelIds)
                channelIds.size
            }.onSuccess { count ->
                withContext(Dispatchers.Main) {
                    syncButton.isEnabled = true
                    setStatus(
                        if (count == 0) "Nenhuma inscrição encontrada nessa conta."
                        else "Sincronização concluída: $count canais processados."
                    )
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    syncButton.isEnabled = true
                    setStatus("Falha ao sincronizar: ${it.message ?: it.javaClass.simpleName}")
                }
            }
        }
    }

    private fun fetchYoutubeSubscriptionIds(token: String): List<String> {
        val ids = LinkedHashSet<String>()
        var pageToken: String? = null

        do {
            val url = buildString {
                append("https://www.googleapis.com/youtube/v3/subscriptions")
                append("?part=snippet&mine=true&maxResults=50")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(Uri.encode(pageToken))
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(
                        "YouTube Data API HTTP ${response.code}: " + body.take(240)
                    )
                }

                val root = JSONObject(body)
                val items = root.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val snippet = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                        val resourceId = snippet.optJSONObject("resourceId") ?: continue
                        val channelId = resourceId.optString("channelId")
                        if (channelId.isNotBlank()) ids += channelId
                    }
                }
                pageToken = root.optString("nextPageToken").ifBlank { null }
            }
        } while (pageToken != null)

        return ids.toList()
    }

    private fun revokeCurrentAccount() {
        val email = savedAccount()
        if (email.isNullOrBlank()) {
            clearLocalAccount()
            return
        }

        val request = RevokeAccessRequest.builder()
            .setAccount(Account(email, "com.google"))
            .setScopes(scopes)
            .build()

        setStatus("Desconectando…")
        client.revokeAccess(request)
            .addOnSuccessListener {
                clearLocalAccount()
                setStatus("Conta Google desconectada.")
            }
            .addOnFailureListener {
                setStatus("Falha ao desconectar: ${it.message ?: it.javaClass.simpleName}")
            }
    }

    private fun clearLocalAccount() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
        accessToken = null
        updateAccountUi()
    }

    private fun savedAccount(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ACCOUNT, null)

    private fun showOAuthBlockedHint() {
        setStatus(
            "Login bloqueado/cancelado. Se apareceu Erro 403 access_denied, " +
                "adicione esta Conta Google em Google Cloud → Plataforma de autenticação " +
                "do Google → Público-alvo → Usuários de teste, ou publique/verifique o app."
        )
    }

    private fun setStatus(message: String) {
        statusLabel.text = message
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
