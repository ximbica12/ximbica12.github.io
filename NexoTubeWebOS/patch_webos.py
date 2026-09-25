#!/usr/bin/env python3
from pathlib import Path
import json
import struct
import zlib

ROOT = Path("upstream")

def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"patch anchor missing in {path}: {old[:80]}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

# Package/app identity: coexist with LG's official YouTube app.
pkg_path = ROOT / "package.json"
pkg = json.loads(pkg_path.read_text(encoding="utf-8"))
pkg["name"] = "nexotube-webos"
pkg["version"] = "0.1.0"
pkg["description"] = "NexoTube TV for LG webOS 3.x+"
pkg["repository"] = "github:ximbica12/ximbica12.github.io"
pkg_path.write_text(json.dumps(pkg, indent=2) + "\n", encoding="utf-8")

appinfo_path = ROOT / "assets/appinfo.json"
appinfo = json.loads(appinfo_path.read_text(encoding="utf-8"))
appinfo.update({
    "id": "com.ximbica.nexotube.webos",
    "vendor": "ximbica12",
    "title": "NexoTube TV",
    "iconColor": "#d71920",
    "checkUpdateOnLaunch": False,
    "supportQuickStart": True,
})
# Never claim the official YouTube DIAL target and never replace the stock app.
appinfo.pop("dialAppName", None)
appinfo_path.write_text(json.dumps(appinfo, indent=2) + "\n", encoding="utf-8")

# Chromium 38 / old webOS: prefer reduced animation work during initial launch.
replace_once(
    "src/utils.js",
    "ytURL.searchParams.append('env_forceFullAnimation', '1');",
    "ytURL.searchParams.append('env_forceFullAnimation', '0');"
)

# NexoTube defaults tuned for the 2017 webOS generation.
config_path = ROOT / "src/config.js"
config = config_path.read_text(encoding="utf-8")
config = config.replace(
    "['upgradeThumbnails', { default: false, desc: 'Upgrade thumbnail quality' }]",
    "['upgradeThumbnails', { default: true, desc: 'Melhorar qualidade das miniaturas' }]"
)
config = config.replace(
    "['removeShorts',\n    { default: false, desc: 'Remove Shorts from subscriptions' }\n  ]",
    "['removeShorts',\n    { default: false, desc: 'Manter Shorts no feed' }\n  ]"
)
config = config.replace("desc: 'Enable ad blocking'", "desc: 'Bloqueio de anúncios'")
config = config.replace("desc: 'Enable SponsorBlock'", "desc: 'Ativar SponsorBlock'")
config = config.replace("desc: 'Hide YouTube logo'", "desc: 'Ocultar logo do YouTube'")
config = config.replace("desc: 'Display time in UI'", "desc: 'Mostrar relógio'")
config = config.replace("desc: 'Force max resolution video playback'", "desc: 'Forçar resolução máxima'")
config = config.replace("desc: 'Remove end screens from video'", "desc: 'Remover telas finais'")
config = config.replace("desc: 'Bypass initial account selection on startup'", "desc: 'Entrar direto na última conta'")
config_path.write_text(config, encoding="utf-8")

# Rebrand settings overlay while keeping upstream legal/source attribution.
replace_once(
    "src/ui.js",
    "elmHeading.textContent = 'webOS YouTube Extended';",
    "elmHeading.textContent = 'NexoTube TV';"
)
replace_once(
    "src/ui.js",
    "'Press [GREEN] to open YTAF configuration screen'",
    "'NexoTube TV: botão VERDE abre as configurações'"
)

# Lightweight TV-specific CSS loaded after upstream fixes.
user_path = ROOT / "src/userScript.ts"
user = user_path.read_text(encoding="utf-8")
if "import './nexotube-tv.css';" not in user:
    user += "\nimport './nexotube-tv.css';\n"
user_path.write_text(user, encoding="utf-8")

css = r"""
/* NexoTube TV - modified fork UI layer, 2026.
 * Keep selectors conservative for the Chromium 38 based webOS 3.x WAM.
 */
.ytaf-ui-container {
  border-radius: 22px !important;
  background: rgba(18, 18, 20, 0.96) !important;
  box-shadow: 0 16px 60px rgba(0,0,0,.55) !important;
}
.ytaf-ui-container h1 {
  color: #ff3940 !important;
  letter-spacing: .5px;
}
.ytaf-notification-container .message {
  border-radius: 16px !important;
}
"""
(ROOT / "src/nexotube-tv.css").write_text(css.lstrip(), encoding="utf-8")

# Create our own simple PNG assets with no external dependency.
def png_rgba(path, width, height):
    bg = (15, 16, 20, 255)
    red = (215, 25, 32, 255)
    white = (255, 255, 255, 255)
    rows = []
    cx, cy = width // 2, height // 2
    rx, ry = int(width * 0.33), int(height * 0.27)
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            color = bg
            # Rounded-ish red tile.
            if abs(x-cx) <= rx and abs(y-cy) <= ry:
                color = red
            # White play triangle.
            tx = x - int(width * 0.45)
            ty = y - cy
            if 0 <= tx <= int(width * 0.25) and abs(ty) <= int(tx * 0.78):
                color = white
            row.extend(color)
        rows.append(bytes(row))
    raw = b"".join(rows)
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag+data) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n"
    data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9))
    data += chunk(b"IEND", b"")
    (ROOT / "assets" / path).write_bytes(data)

for name, size in [
    ("icon.png", 80),
    ("largeIcon.png", 130),
    ("mediumLargeIcon.png", 160),
    ("extraLargeIcon.png", 200),
    ("playIcon.png", 80),
    ("imageForRecents.png", 300),
    ("bgImage.png", 300),
]:
    png_rgba(name, size, size)

# Splash can remain small and be stretched by webOS.
png_rgba("splashBackground-v1.png", 320, 180)

notice = ROOT / "NEXOTUBE_TV_MODIFICATIONS.md"
notice.write_text(
    "# NexoTube TV modifications\n\n"
    "NexoTube TV 0.1-alpha is based on webosbrew/youtube-webos, GPL-3.0-only.\n"
    "The upstream copyright and GPL notices remain intact.\n"
    "Changes include a separate app ID, NexoTube branding, webOS 3.x performance defaults, "
    "Portuguese settings labels and replacement visual assets.\n",
    encoding="utf-8"
)

print("NexoTube TV webOS patch applied")
