#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "LibreTube")
build = root / "app" / "build.gradle.kts"
text = build.read_text(encoding="utf-8")
text = text.replace('applicationId = "com.github.libretube"', 'applicationId = "com.ximbica.tubelite"')
text = text.replace('versionName = "32.1"', 'versionName = "0.4.0-libretube"')
text = text.replace('resValue("string", "app_name", "LibreTube")', 'resValue("string", "app_name", "TubeLite J7")')
text = text.replace('resValue("string", "app_name", "LibreTube Debug")', 'resValue("string", "app_name", "TubeLite J7 Dev")')
build.write_text(text, encoding="utf-8")

# Keep upstream namespace so generated R classes and imports stay intact.
# applicationId is separate and may safely use the TubeLite package.
manifest = root / "app" / "src" / "main" / "AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:largeHeap="true"', 'android:largeHeap="false"')
manifest.write_text(m, encoding="utf-8")

print("Patched LibreTube for TubeLite J7")
print("applicationId=com.ximbica.tubelite")
print("minSdk remains 26 (Android 8.0)")
