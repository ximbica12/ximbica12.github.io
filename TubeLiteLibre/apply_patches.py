#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "LibreTube")
here = Path(__file__).resolve().parent

# ---- app identity + Android 8 tuning ----
build = root / "app" / "build.gradle.kts"
text = build.read_text(encoding="utf-8")
text = text.replace('applicationId = "com.github.libretube"', 'applicationId = "com.ximbica.tubelite"')
text = text.replace('versionName = "32.1"', 'versionName = "0.4.0-libretube"')
text = text.replace('resValue("string", "app_name", "LibreTube")', 'resValue("string", "app_name", "TubeLite J7")')
text = text.replace('resValue("string", "app_name", "LibreTube Debug")', 'resValue("string", "app_name", "TubeLite J7 Dev")')

material_dep = "    implementation(libs.material)\n"
google_dep = '    implementation("com.google.android.gms:play-services-auth:21.6.0")\n'
if google_dep not in text:
    text = text.replace(material_dep, material_dep + google_dep)
build.write_text(text, encoding="utf-8")

# Keep upstream namespace so generated R classes and imports stay intact.
manifest = root / "app" / "src" / "main" / "AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:largeHeap="true"', 'android:largeHeap="false"')

google_activity = """
        <activity
            android:name=".ui.activities.GoogleAccountActivity"
            android:exported="false"
            android:label="Conta Google / YouTube"
            android:screenOrientation="locked" />

"""
settings_marker = """        <activity
            android:name=".ui.activities.SettingsActivity" """
if "GoogleAccountActivity" not in m:
    m = m.replace(settings_marker, google_activity + settings_marker)
manifest.write_text(m, encoding="utf-8")

# ---- Google account screen overlay ----
overlay = here / "overlay" / "GoogleAccountActivity.kt"
target = root / "app" / "src" / "main" / "java" / "com" / "github" / "libretube" / "ui" / "activities" / "GoogleAccountActivity.kt"
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(overlay, target)

# ---- expose account screen in LibreTube's existing overflow menu ----
menu = root / "app" / "src" / "main" / "res" / "menu" / "action_bar.xml"
menu_text = menu.read_text(encoding="utf-8")
google_menu = """
    <item
        android:id="@+id/action_google_account"
        android:title="Conta Google / YouTube"
        app:showAsAction="never" />

"""
settings_item = """    <item
        android:id="@+id/action_settings" """
if "action_google_account" not in menu_text:
    menu_text = menu_text.replace(settings_item, google_menu + settings_item)
menu.write_text(menu_text, encoding="utf-8")

host = root / "app" / "src" / "main" / "java" / "com" / "github" / "libretube" / "ui" / "activities" / "AbstractPlayerHostActivity.kt"
host_text = host.read_text(encoding="utf-8")
google_case = """
            R.id.action_google_account -> {
                val googleAccountIntent = Intent(this, GoogleAccountActivity::class.java)
                startActivity(googleAccountIntent)
                true
            }

"""
settings_case = """            R.id.action_settings -> {"""
if "R.id.action_google_account" not in host_text:
    host_text = host_text.replace(settings_case, google_case + settings_case)
host.write_text(host_text, encoding="utf-8")

print("Patched LibreTube for TubeLite J7")
print("applicationId=com.ximbica.tubelite")
print("minSdk remains 26 (Android 8.0)")
print("Google OAuth + YouTube subscription sync overlay enabled")
