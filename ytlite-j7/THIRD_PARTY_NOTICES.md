# Third-party components

YT Lite J7 v0.2.0 uses:

- Mozilla GeckoView 153.0.20260730155536.
- uBlock Origin 1.75.0 by Raymond Hill and contributors (GPL-3.0).
- SponsorBlock 6.1.7 by Ajay Ramachandran and contributors (GPL-3.0).
- Font Awesome Free 7.3.1 square-youtube SVG icon by Fonticons, Inc. (CC BY 4.0).

The GitHub Actions build downloads the official Firefox packages for uBlock Origin
and SponsorBlock and bundles them as built-in GeckoView WebExtensions.

The YT Lite J7 Shield is a separate local WebExtension. It provides an independent
high-confidence network block layer, early cosmetic hiding, YouTube ad-data pruning,
H.264 preference, and playback fallback logic. It does not claim to be Brave's
adblock-rust engine; Brave's engine is a Rust library and would require a dedicated
native/WASM integration path rather than pretending a URL blacklist is the same thing.
