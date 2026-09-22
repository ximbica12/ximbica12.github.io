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
    'versionCode = 320101',
    s,
    "versionCode",
)
s = sub_once(
    r'versionName\s*=\s*"[^"]+"',
    'versionName = "32.1-j7.1"',
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

p.write_text(s, encoding="utf-8")

# Keep attribution visible inside the patched source.
notice = Path("upstream/TUBELITE_J7_MODIFICATIONS.md")
notice.write_text(
    "# TubeLite J7 modifications\n\n"
    "Based on LibreTube v32.1 (GPL-3.0-or-later).\n"
    "Changes: Android applicationId, display name, version metadata, and ABI targeting for Samsung Galaxy J7 Prime / armeabi-v7a.\n"
    "The upstream project and copyright notices remain intact.\n",
    encoding="utf-8",
)

print("TubeLite J7 patch applied successfully")
