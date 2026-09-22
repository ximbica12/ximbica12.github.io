from pathlib import Path
import re

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
    'versionCode = 320102',
    s,
    "versionCode",
)
s = sub_once(
    r'versionName\s*=\s*"[^"]+"',
    'versionName = "32.1-j7.2"',
    s,
    "versionName",
)
s = sub_once(
    r'resValue\("string",\s*"app_name",\s*"LibreTube"\)',
    'resValue("string", "app_name", "TubeLite J7")',
    s,
    "app name",
)

needle = 'resValue("string", "app_name", "TubeLite J7")'
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
        helper_anchor + "import com.github.libretube.helpers.YouTubeDirectImport\n",
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
            Toast.makeText(
                requireContext(),
                R.string.youtube_direct_import_cancelled,
                Toast.LENGTH_LONG
            ).show()
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
        Toast.makeText(
            requireContext(),
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
        val accessToken = result.accessToken
        if (accessToken.isNullOrBlank()) {
            showYouTubeImportError(
                IllegalStateException("Google did not return an access token")
            )
            return
        }

        Toast.makeText(
            requireContext(),
            R.string.youtube_direct_import_working,
            Toast.LENGTH_LONG
        ).show()

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                YouTubeDirectImport.importAll(accessToken)
            }.onSuccess { imported ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        getString(
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
        Toast.makeText(
            requireContext(),
            getString(R.string.youtube_direct_import_failed, message),
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


# Keep attribution visible inside the patched source.
notice = Path("upstream/TUBELITE_J7_MODIFICATIONS.md")
notice.write_text(
    "# TubeLite J7 modifications\n\n"
    "Based on LibreTube v32.1 (GPL-3.0-or-later).\n"
    "Changes: Android applicationId, display name, version metadata, ARMv7 targeting, and direct YouTube OAuth library import for Samsung Galaxy J7 Prime.\n"
    "The upstream project and copyright notices remain intact.\n",
    encoding="utf-8",
)

print("TubeLite J7 patch applied successfully")
