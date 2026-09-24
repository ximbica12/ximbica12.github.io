# GPTLewd J7 Turbo — Alpha 2

Single-site ChatGPT client using GeckoView, tuned specifically for Galaxy J7 Prime / Android 8 / ARMv7 / 240 DPI.

## Alpha 2 architecture

- GeckoView 156 stable
- built-in WebExtension theme loaded at document_start
- no WebView and no browser chrome
- package: com.ximbica.gptlewd.gecko.a2 (parallel test package)
- minSdk 26 / ARMv7 only
- 240 DPI runtime override
- dark native + web color scheme
- WebGL MSAA disabled
- Gecko low-memory detection enabled
- web fonts disabled; system-font theme
- media suspended while the session is inactive
- session active/focused/priority follows Activity lifecycle
- long ChatGPT turns use CSS content-visibility
- expensive backdrop blur and giant shadows removed from the themed UI
- launcher icon generated from the user-provided art

The Alpha 1 is intentionally left untouched so the known-good logged-in build stays available during the Alpha 2 test.
