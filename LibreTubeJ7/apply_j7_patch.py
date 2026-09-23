from pathlib import Path
import copy
import re
import xml.etree.ElementTree as ET

p = Path("upstream/app/build.gradle.kts")
s = p.read_text(encoding="utf-8")

def sub_once(pattern, replacement, text, label):
    new, n = re.subn(pattern, replacement, text, count=1)
    if n != 1:
        raise SystemExit(f"patch failed: {label} ({n} matches)")
    return new

s = sub_once(
    r'applicationId\s*=\s*"com\.github\.libretube"',
    'applicationId = "com.ximbica.tubelite"',
    s,
    "applicationId",
)
s = sub_once(
    r'versionCode\s*=\s*\d+',
    'versionCode = 320107',
    s,
    "versionCode",
)
s = sub_once(
    r'versionName\s*=\s*"[^"]+"',
    'versionName = "32.1-j7.7"',
    s,
    "versionName",
)
s = sub_once(
    r'resValue\("string",\s*"app_name",\s*"LibreTube"\)',
    'resValue("string", "app_name", "NexoTube")',
    s,
    "app name",
)

needle = 'resValue("string", "app_name", "NexoTube")'
if 'abiFilters += setOf("armeabi-v7a")' not in s:
    s = s.replace(
        needle,
        needle + '\n        ndk {\n            abiFilters += setOf("armeabi-v7a")\n        }',
        1,
    )

# OAuth authorization used only for the one-tap YouTube importer.
dep_anchor = '    implementation(libs.androidx.activity)'
if 'com.google.android.gms:play-services-auth:21.6.0' not in s:
    if dep_anchor not in s:
        raise SystemExit("patch failed: dependency anchor")
    s = s.replace(
        dep_anchor,
        dep_anchor + '\n    implementation("com.google.android.gms:play-services-auth:21.6.0")',
        1,
    )

p.write_text(s, encoding="utf-8")


# Android 8 language fix: LibreTube has both pt and pt-rBR, but newer strings
# are not always translated in the Brazilian resource set. Backfill only names
# missing from pt-rBR with the Portuguese translation before Android falls back
# to English.
pt_path = Path("upstream/app/src/main/res/values-pt/strings.xml")
ptbr_path = Path("upstream/app/src/main/res/values-pt-rBR/strings.xml")
if pt_path.exists() and ptbr_path.exists():
    pt_tree = ET.parse(pt_path)
    ptbr_tree = ET.parse(ptbr_path)
    pt_root = pt_tree.getroot()
    ptbr_root = ptbr_tree.getroot()
    existing_names = {
        node.attrib.get("name")
        for node in ptbr_root
        if node.attrib.get("name")
    }
    for node in pt_root:
        name = node.attrib.get("name")
        if name and name not in existing_names:
            ptbr_root.append(copy.deepcopy(node))
            existing_names.add(name)
    ET.indent(ptbr_tree, space="    ")
    ptbr_tree.write(ptbr_path, encoding="utf-8", xml_declaration=True)


# Add a direct Google -> LibreTube import action to the existing import/export screen.
settings_path = Path("upstream/app/src/main/java/com/github/libretube/ui/preferences/BackupRestoreSettings.kt")
settings = settings_path.read_text(encoding="utf-8")

import_anchor = "import android.content.Context\n"
extra_imports = (
    "import android.app.Activity\n"
    "import android.widget.Toast\n"
)
if "import android.app.Activity" not in settings:
    settings = settings.replace(import_anchor, extra_imports + import_anchor, 1)

activity_result_anchor = "import androidx.activity.result.contract.ActivityResultContracts\n"
if "import androidx.activity.result.IntentSenderRequest" not in settings:
    settings = settings.replace(
        activity_result_anchor,
        "import androidx.activity.result.IntentSenderRequest\n" + activity_result_anchor,
        1,
    )

helper_anchor = "import com.github.libretube.helpers.PreferenceHelper\n"
if "import com.github.libretube.helpers.YouTubeDirectImport" not in settings:
    settings = settings.replace(
        helper_anchor,
        helper_anchor
        + "import com.github.libretube.helpers.ProfileManager\n"
        + "import com.github.libretube.helpers.YouTubeDirectImport\n"
        + "import com.github.libretube.db.DatabaseHolder\n",
        1,
    )

google_import_anchor = "import com.google.android.material.dialog.MaterialAlertDialogBuilder\n"
google_imports = (
    "import com.google.android.gms.auth.api.identity.AuthorizationRequest\n"
    "import com.google.android.gms.auth.api.identity.AuthorizationResult\n"
    "import com.google.android.gms.auth.api.identity.Identity\n"
    "import com.google.android.gms.common.api.Scope\n"
)
if "import com.google.android.gms.auth.api.identity.Identity" not in settings:
    settings = settings.replace(
        google_import_anchor,
        google_imports + google_import_anchor,
        1,
    )

field_anchor = "    private var importFormat: ImportFormat = ImportFormat.NEWPIPE\n"
oauth_fields = r'''
    private val googleAuthClient by lazy(LazyThreadSafetyMode.NONE) {
        Identity.getAuthorizationClient(requireActivity())
    }

    private val youtubeAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK || activityResult.data == null) {
            context?.let { ctx ->
                Toast.makeText(
                    ctx,
                    R.string.youtube_direct_import_cancelled,
                    Toast.LENGTH_LONG
                ).show()
            }
            return@registerForActivityResult
        }

        runCatching {
            googleAuthClient.getAuthorizationResultFromIntent(activityResult.data!!)
        }.onSuccess(::handleYouTubeAuthorization)
            .onFailure { showYouTubeImportError(it) }
    }
'''
if "youtubeAuthLauncher" not in settings:
    settings = settings.replace(field_anchor, field_anchor + oauth_fields, 1)

prefs_anchor = '''        val importSubscriptions = findPreference<Preference>("import_subscriptions")
'''
direct_pref = '''        val directYouTubeImport = findPreference<Preference>("youtube_direct_import")
        directYouTubeImport?.setOnPreferenceClickListener {
            startDirectYouTubeImport()
            true
        }

'''
if 'findPreference<Preference>("youtube_direct_import")' not in settings:
    settings = settings.replace(prefs_anchor, direct_pref + prefs_anchor, 1)

companion_anchor = "    companion object {\n"
oauth_methods = r'''
    private fun startDirectYouTubeImport() {
        val ctx = context ?: return
        Toast.makeText(
            ctx,
            R.string.youtube_direct_import_start,
            Toast.LENGTH_LONG
        ).show()

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(Scope("https://www.googleapis.com/auth/youtube.readonly"))
            )
            .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            .build()

        googleAuthClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        showYouTubeImportError(
                            IllegalStateException("Google authorization resolution is missing")
                        )
                        return@addOnSuccessListener
                    }
                    youtubeAuthLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } else {
                    handleYouTubeAuthorization(result)
                }
            }
            .addOnFailureListener(::showYouTubeImportError)
    }

    private fun handleYouTubeAuthorization(result: AuthorizationResult) {
        val googleEmail = runCatching {
            @Suppress("DEPRECATION")
            result.toGoogleSignInAccount()?.email
        }.getOrNull()

        if (!googleEmail.isNullOrBlank()) {
            val targetProfile = ProfileManager.ensureProfileForGoogleAccount(googleEmail)
            if (targetProfile.id != ProfileManager.getActiveProfileId()) {
                DatabaseHolder.switchProfile(targetProfile.id)
                context?.applicationContext?.let {
                    PreferenceHelper.reloadAuthenticationPreferences(it)
                }
            }
        }

        val accessToken = result.accessToken
        if (accessToken.isNullOrBlank()) {
            showYouTubeImportError(
                IllegalStateException("Google did not return an access token")
            )
            return
        }

        context?.let { ctx ->
            Toast.makeText(
                ctx,
                R.string.youtube_direct_import_working,
                Toast.LENGTH_LONG
            ).show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                YouTubeDirectImport.importAll(accessToken)
            }.onSuccess { imported ->
                withContext(Dispatchers.Main) {
                    val ctx = context ?: return@withContext
                    Toast.makeText(
                        ctx,
                        ctx.getString(
                            R.string.youtube_direct_import_done,
                            imported.subscriptions,
                            imported.playlists,
                            imported.playlistVideos,
                            imported.likedVideos
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    showYouTubeImportError(error)
                }
            }
        }
    }

    private fun showYouTubeImportError(error: Throwable) {
        val message = error.localizedMessage
            ?: error::class.java.simpleName
            ?: "Unknown error"
        val ctx = context ?: return
        Toast.makeText(
            ctx,
            ctx.getString(R.string.youtube_direct_import_failed, message),
            Toast.LENGTH_LONG
        ).show()
    }

'''
if "private fun startDirectYouTubeImport()" not in settings:
    settings = settings.replace(companion_anchor, oauth_methods + companion_anchor, 1)

settings_path.write_text(settings, encoding="utf-8")

# Put the direct importer at the top of Import/Export, ahead of the legacy file routes.
xml_path = Path("upstream/app/src/main/res/xml/import_export_settings.xml")
xml = xml_path.read_text(encoding="utf-8")
screen_anchor = '''<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
'''
direct_xml = r'''

    <PreferenceCategory app:title="YouTube">

        <Preference
            android:icon="@drawable/ic_auth"
            android:summary="@string/youtube_direct_import_summary"
            app:key="youtube_direct_import"
            app:title="@string/youtube_direct_import" />

    </PreferenceCategory>
'''
if 'app:key="youtube_direct_import"' not in xml:
    xml = xml.replace(screen_anchor, screen_anchor + direct_xml, 1)
xml_path.write_text(xml, encoding="utf-8")



# NexoTube J7.7 top bar: app title + visible profile shortcut.
main_path = Path("upstream/app/src/main/java/com/github/libretube/ui/activities/MainActivity.kt")
main = main_path.read_text(encoding="utf-8")
if "import com.github.libretube.ui.dialogs.ProfileDialog" not in main:
    main = main.replace(
        "import com.github.libretube.ui.dialogs.ImportTempPlaylistDialog\n",
        "import com.github.libretube.ui.dialogs.ImportTempPlaylistDialog\n"
        "import com.github.libretube.ui.dialogs.ProfileDialog\n",
        1,
    )

main = main.replace(
    "binding.toolbar.title = ThemeHelper.getStyledAppName(this)",
    "binding.toolbar.title = getString(R.string.app_name)",
    1,
)

profile_handler_anchor = """    /**
     * Deselect all bottom bar items
     */
"""
profile_handler = """    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_profile) {
            if (!isFinishing && !isDestroyed) {
                ProfileDialog().show(supportFragmentManager, "profile_dialog")
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

"""
if "R.id.action_profile" not in main:
    if profile_handler_anchor not in main:
        raise SystemExit("patch failed: MainActivity profile handler anchor")
    main = main.replace(profile_handler_anchor, profile_handler + profile_handler_anchor, 1)

main_path.write_text(main, encoding="utf-8")


# Android 8/8.1 stability fix: never enter system PiP when leaving the app.
# On Oreo we pause immediately and disable the video track instead of moving the
# video Surface into a system overlay. Android 9+ keeps upstream PiP behavior.
player_path = Path("upstream/app/src/main/java/com/github/libretube/ui/fragments/PlayerFragment.kt")
player = player_path.read_text(encoding="utf-8")

if "import android.os.Build\n" not in player:
    player = player.replace(
        "import android.os.Bundle\n",
        "import android.os.Build\nimport android.os.Bundle\n",
        1,
    )

old_leave_hint = """    fun onUserLeaveHint() {
        if (shouldStartPiP()) {
            PictureInPictureCompat.enterPictureInPictureMode(requireActivity(), pipParams)
        }
    }
"""
new_leave_hint = """    fun onUserLeaveHint() {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.O_MR1) {
            // Samsung/low-memory Oreo safety path: do not create the system PiP
            // window. Pausing also prevents the video decoder/surface from staying
            // active behind the launcher, while keeping the position/session intact.
            if (::playerController.isInitialized) {
                playerController.pause()
                setAutoPlayCountdownEnabled(false)
                setVideoTrackTypeDisabled(true)
            }
            isEnteringPiPMode = false
            return
        }

        if (shouldStartPiP()) {
            PictureInPictureCompat.enterPictureInPictureMode(requireActivity(), pipParams)
        }
    }
"""
if old_leave_hint not in player:
    raise SystemExit("patch failed: PlayerFragment onUserLeaveHint anchor")
player = player.replace(old_leave_hint, new_leave_hint, 1)
player_path.write_text(player, encoding="utf-8")


# J7.5 playback reliability: explicit HTTP timeouts, bounded metadata extraction,
# smaller back-buffer, and retry-on-connection-failure. Upstream SABR capability
# and Shorts fixes are cherry-picked by the workflow before this patch runs.
player_helper_path = Path("upstream/app/src/main/java/com/github/libretube/helpers/PlayerHelper.kt")
player_helper = player_helper_path.read_text(encoding="utf-8")

if "import androidx.media3.datasource.DefaultHttpDataSource" not in player_helper:
    player_helper = player_helper.replace(
        "import androidx.media3.datasource.DefaultDataSource\n",
        "import androidx.media3.datasource.DefaultDataSource\n"
        "import androidx.media3.datasource.DefaultHttpDataSource\n",
        1,
    )

old_ds = "        val dataSourceFactory = DefaultDataSource.Factory(context)\n"
new_ds = """        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
"""
if old_ds not in player_helper:
    raise SystemExit("patch failed: PlayerHelper data source anchor")
player_helper = player_helper.replace(old_ds, new_ds, 1)
player_helper = player_helper.replace(
    ".setBackBuffer(1000 * 60 * 3, true)",
    ".setBackBuffer(30_000, false)",
    1,
)
player_helper_path.write_text(player_helper, encoding="utf-8")

sabr_path = Path("upstream/app/src/main/java/com/github/libretube/player/parser/SabrClient.kt")
sabr = sabr_path.read_text(encoding="utf-8")
if "import java.util.concurrent.TimeUnit" not in sabr:
    sabr = sabr.replace(
        "import java.time.Instant\n",
        "import java.time.Instant\nimport java.util.concurrent.TimeUnit\n",
        1,
    )

sabr_client_anchor = """    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
"""
sabr_client_replacement = """    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
"""
if sabr_client_anchor not in sabr:
    raise SystemExit("patch failed: SABR OkHttp anchor")
sabr = sabr.replace(sabr_client_anchor, sabr_client_replacement, 1)
sabr_path.write_text(sabr, encoding="utf-8")

online_path = Path("upstream/app/src/main/java/com/github/libretube/services/OnlinePlayerService.kt")
online = online_path.read_text(encoding="utf-8")
if "import kotlinx.coroutines.delay" not in online:
    online = online.replace(
        "import kotlinx.coroutines.cancelAndJoin\n",
        "import kotlinx.coroutines.cancelAndJoin\nimport kotlinx.coroutines.delay\n",
        1,
    )
if "import kotlinx.coroutines.withTimeoutOrNull" not in online:
    online = online.replace(
        "import kotlinx.coroutines.withContext\n",
        "import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.withTimeoutOrNull\n",
        1,
    )

old_fetch = """            streams = withContext(Dispatchers.IO) {
                try {
                    MediaServiceRepository.instance.getStreams(videoId).let {
                        DeArrowUtil.deArrowStreams(it, videoId)
                    }
                }  catch (e: Exception) {
                    Log.e(TAG(), e.stackTraceToString())
                    toastFromMainDispatcher(e.localizedMessage.orEmpty())
                    return@withContext null
                }
            } ?: return@launch
"""
new_fetch = """            streams = withContext(Dispatchers.IO) {
                var loaded: Streams? = null
                var lastError: Throwable? = null

                repeat(2) { attempt ->
                    loaded = withTimeoutOrNull(25_000L) {
                        runCatching {
                            MediaServiceRepository.instance.getStreams(videoId).let {
                                DeArrowUtil.deArrowStreams(it, videoId)
                            }
                        }.onFailure {
                            lastError = it
                            Log.e(TAG(), it.stackTraceToString())
                        }.getOrNull()
                    }

                    if (loaded != null) return@withContext loaded
                    if (attempt == 0) delay(700L)
                }

                toastFromMainDispatcher(
                    lastError?.localizedMessage ?: "Tempo limite ao carregar o vídeo"
                )
                null
            } ?: return@launch
"""
if old_fetch not in online:
    raise SystemExit("patch failed: OnlinePlayerService fetch anchor")
online = online.replace(old_fetch, new_fetch, 1)
online_path.write_text(online, encoding="utf-8")


# Keep attribution visible inside the patched source.
notice = Path("upstream/NEXOTUBE_MODIFICATIONS.md")
notice.write_text(
    "# NexoTube modifications\n\n"
    "Based on LibreTube v32.1 (GPL-3.0-or-later).\n"
    "Changes: Android applicationId, NexoTube branding, ARMv7 targeting, pt-BR fixes, direct Google library import, account ratings, lifecycle-safe isolated profiles, personalized regional Home/Shorts, automatic PT caption fallback, local Watch Later, playback timeout/SABR hardening, selected upstream crash fixes, and Oreo safeguards for Samsung Galaxy J7 Prime.\n"
    "The upstream project and copyright notices remain intact.\n",
    encoding="utf-8",
)

print("NexoTube J7.7 patch applied successfully")
