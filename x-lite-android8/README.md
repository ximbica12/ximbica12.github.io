# X Lite Android 8

Dedicated X.com shell for Android 8 / API 26 using Mozilla GeckoView.

## Design
- Mozilla GeckoView instead of Android WebView.
- No in-app browser toolbar or address bar.
- Normal persistent Gecko profile/session, so X login cookies can survive app restarts.
- ARMv7-only package for Samsung Galaxy J7 Prime class hardware.
- Built-in GeckoView WebExtension for X-specific optimizations.
- Promoted placements are hidden when X marks them with placement tracking markup.
- Visible X "Translate post/Tweet" controls are activated automatically; this keeps translation on X's own UI/translation path.
- Video elements avoid unnecessary eager preloading where possible.
- Mobile width fixes reduce the need for manual zoom.

## Age/account behavior
This app does not forge age information and does not disable X account-level age controls. The compatibility approach is to keep a persistent normal session/cookie jar so an already-verified account does not lose its state because of a transient/incognito wrapper.

## Video quality
X selects video bitrate with adaptive streaming. The shell improves playback overhead but intentionally does not hook undocumented X media APIs, so fixed 720p cannot be guaranteed.

## Output
GitHub Actions builds:
- X-Lite-J7-Android8.apk
- X-Lite-J7-Android8.apks for SAI
