(() => {
  "use strict";

  const page = window.wrappedJSObject || window;

  // ---------- Codec policy: H.264/AVC first ----------
  // Exynos 7870 handles AVC much better than VP9/AV1 on this device.
  try {
    const reject = type => /(?:vp8|vp9|vp09|av01|av1)/i.test(String(type || ""));

    if (page.MediaSource &&
        typeof page.MediaSource.isTypeSupported === "function" &&
        typeof exportFunction === "function") {
      const originalMse = page.MediaSource.isTypeSupported;
      page.MediaSource.isTypeSupported = exportFunction(function(type) {
        if (reject(type)) return false;
        try { return originalMse.call(page.MediaSource, type); }
        catch (_) { return true; }
      }, page.MediaSource);
    }

    if (page.HTMLMediaElement &&
        page.HTMLMediaElement.prototype &&
        typeof page.HTMLMediaElement.prototype.canPlayType === "function" &&
        typeof exportFunction === "function") {
      const proto = page.HTMLMediaElement.prototype;
      const originalCanPlayType = proto.canPlayType;
      proto.canPlayType = exportFunction(function(type) {
        if (reject(type)) return "";
        try { return originalCanPlayType.call(this, type); }
        catch (_) { return "maybe"; }
      }, proto);
    }
  } catch (_) {}

  // ---------- Page-data ad pruning ----------
  // This is intentionally narrow: only known ad payload keys/renderers are removed.
  const AD_KEYS = new Set([
    "adPlacements",
    "playerAds",
    "adSlots",
    "adBreakHeartbeatParams",
    "adBreakParams",
    "adBreakServiceParams",
    "ssapConfig",
    "daiConfig",
    "adClientParams"
  ]);

  const AD_RENDERER_KEYS = new Set([
    "adSlotRenderer",
    "displayAdRenderer",
    "promotedVideoRenderer",
    "promotedSparklesWebRenderer",
    "promotedSparklesTextSearchRenderer",
    "compactPromotedVideoRenderer",
    "compactPromotedItemRenderer",
    "searchPyvRenderer",
    "videoAdRenderer",
    "instreamVideoAdRenderer",
    "carouselAdRenderer",
    "inFeedAdLayoutRenderer"
  ]);

  function isAdItem(item) {
    if (!item || typeof item !== "object" || Array.isArray(item)) return false;
    try {
      for (const key of AD_RENDERER_KEYS) {
        if (key in item) return true;
      }
      const rw = item.command && item.command.reelWatchEndpoint;
      if (rw && rw.adClientParams && rw.adClientParams.isAd) return true;
    } catch (_) {}
    return false;
  }

  function pruneAds(node, depth) {
    depth = depth || 0;
    if (!node || typeof node !== "object" || depth > 32) return node;

    try {
      if (Array.isArray(node)) {
        for (let i = node.length - 1; i >= 0; i--) {
          if (isAdItem(node[i])) node.splice(i, 1);
          else pruneAds(node[i], depth + 1);
        }
        return node;
      }

      for (const key of Object.keys(node)) {
        if (AD_KEYS.has(key) || AD_RENDERER_KEYS.has(key)) {
          try {
            if (Array.isArray(node[key])) node[key].length = 0;
            else delete node[key];
          } catch (_) {}
          continue;
        }
        pruneAds(node[key], depth + 1);
      }
    } catch (_) {}
    return node;
  }

  function hookGlobal(name) {
    try {
      let value = page[name];
      if (value) pruneAds(value, 0);

      const descriptor = Object.getOwnPropertyDescriptor(page, name);
      if (descriptor && descriptor.configurable === false) return;

      const getter = exportFunction(function() {
        return value;
      }, page);

      const setter = exportFunction(function(next) {
        try { value = pruneAds(next, 0); }
        catch (_) { value = next; }
      }, page);

      Object.defineProperty(page, name, {
        configurable: true,
        enumerable: true,
        get: getter,
        set: setter
      });
    } catch (_) {}
  }

  if (typeof exportFunction === "function") {
    hookGlobal("ytInitialPlayerResponse");
    hookGlobal("ytInitialData");

    // Last-resort pruning for JSON player/browse responses.
    try {
      const originalParse = page.JSON.parse;
      page.JSON.parse = exportFunction(function(text, reviver) {
        const result = arguments.length > 1
          ? originalParse.call(page.JSON, text, reviver)
          : originalParse.call(page.JSON, text);

        if (typeof text === "string" &&
            (text.indexOf('"adPlacements"') !== -1 ||
             text.indexOf('"playerAds"') !== -1 ||
             text.indexOf('"adSlots"') !== -1 ||
             text.indexOf('"adClientParams"') !== -1)) {
          try { pruneAds(result, 0); } catch (_) {}
        }
        return result;
      }, page.JSON);
    } catch (_) {}

    // Block ad telemetry beacons in the page world too.
    try {
      if (page.navigator && typeof page.navigator.sendBeacon === "function") {
        const originalBeacon = page.navigator.sendBeacon;
        page.navigator.sendBeacon = exportFunction(function(url, data) {
          const s = String(url || "");
          if (/doubleclick\.net|googlesyndication\.com|googleadservices\.com|\/pagead\/|\/api\/stats\/ads|\/ptracking/.test(s)) {
            return true;
          }
          return originalBeacon.call(page.navigator, url, data);
        }, page.navigator);
      }
    } catch (_) {}
  }

  function markPageType() {
    try {
      if (location.pathname.startsWith("/shorts/")) {
        document.body && document.body.setAttribute("data-ytlite-shorts-page", "1");
      } else {
        document.body && document.body.removeAttribute("data-ytlite-shorts-page");
      }
    } catch (_) {}
  }

  function destroyVisibleAds() {
    try {
      const selectors = [
        "ytm-promoted-sparkles-web-renderer",
        "ytm-promoted-video-renderer",
        "ytm-display-ad-renderer",
        "ytm-ad-slot-renderer",
        "ytm-companion-ad-renderer",
        "ytm-promoted-sparkles-text-search-renderer",
        "ytm-search-pyv-renderer",
        ".ytp-ad-module",
        ".video-ads",
        "#player-ads",
        "#masthead-ad"
      ];
      document.querySelectorAll(selectors.join(",")).forEach(el => el.remove());

      document.querySelectorAll(
        ".ytp-ad-skip-button, .ytp-skip-ad-button, " +
        "button[aria-label*='Pular'], button[aria-label*='Skip']"
      ).forEach(el => { try { el.click(); } catch (_) {} });

      // Fallback for an in-stream ad that survived network/data pruning.
      const player = document.querySelector(".html5-video-player, #movie_player");
      if (player && player.classList && player.classList.contains("ad-showing")) {
        const video = document.querySelector("video");
        if (video) {
          try { video.muted = true; } catch (_) {}
          try { video.playbackRate = 16; } catch (_) {}
          try {
            if (Number.isFinite(video.duration) && video.duration > 0) {
              video.currentTime = Math.max(0, video.duration - 0.05);
            }
          } catch (_) {}
        }
      }

      // No "Open app" nag.
      document.querySelectorAll(
        '[href^="intent://"], [aria-label*="Abrir app"], [aria-label*="Open app"], ' +
        "ytm-app-promo, ytm-promo-layer"
      ).forEach(el => {
        const host = el.closest("ytm-app-promo, ytm-promo-layer, button, a") || el;
        host.style.setProperty("display", "none", "important");
      });

      // Hide Create/Post only. Keep the Shorts destination.
      document.querySelectorAll(
        '[aria-label="Criar"], [aria-label="Create"], ' +
        '[aria-label*="Criar um"], [aria-label*="Create a"]'
      ).forEach(el => {
        const host = el.closest("ytm-pivot-bar-item-renderer, button, a") || el;
        host.style.setProperty("display", "none", "important");
      });

      markPageType();
    } catch (_) {}
  }

  // ---------- PT-BR captions ----------
  let lastCaptionVideo = "";

  function getVideoId() {
    try { return new URL(location.href).searchParams.get("v") || ""; }
    catch (_) { return ""; }
  }

  function autoPtBrCaptions() {
    try {
      if (!location.pathname.includes("/watch")) return;
      const player = document.querySelector("#movie_player");
      if (!player || typeof player.setOption !== "function") return;

      try {
        if (typeof player.loadModule === "function") player.loadModule("captions");
      } catch (_) {}

      let tracks = [];
      try { tracks = player.getOption("captions", "tracklist") || []; }
      catch (_) {}
      if (!Array.isArray(tracks) || tracks.length === 0) return;

      const pt = tracks.find(t => /^pt(?:-|$)/i.test(String(t.languageCode || "")));
      if (pt) {
        try { player.setOption("captions", "track", pt); } catch (_) {}
      } else {
        const source =
          tracks.find(t => String(t.kind || "").toLowerCase() === "asr") || tracks[0];
        const desc = {};

        [
          "languageCode", "kind", "name", "vssId", "vss_id",
          "is_servable", "isServable", "is_translatable", "isTranslatable"
        ].forEach(k => {
          if (source && source[k] !== undefined) desc[k] = source[k];
        });

        desc.translationLanguage = "pt";
        try {
          player.setOption("captions", "track", desc);
          try { player.setOption("captions", "reload", true); } catch (_) {}
        } catch (_) {}
      }

      lastCaptionVideo = getVideoId();
    } catch (_) {}
  }

  function maintenance() {
    destroyVisibleAds();

    try {
      if (page.ytInitialPlayerResponse) pruneAds(page.ytInitialPlayerResponse, 0);
      if (page.ytInitialData) pruneAds(page.ytInitialData, 0);
    } catch (_) {}

    const id = getVideoId();
    if (id && id !== lastCaptionVideo) {
      setTimeout(autoPtBrCaptions, 900);
      setTimeout(autoPtBrCaptions, 2400);
    }
  }

  const start = () => {
    try {
      new MutationObserver(destroyVisibleAds)
        .observe(document.documentElement, { childList: true, subtree: true });
    } catch (_) {}
    maintenance();
  };

  if (document.documentElement) start();
  else document.addEventListener("DOMContentLoaded", start, { once: true });

  document.addEventListener("yt-navigate-finish", () => {
    lastCaptionVideo = "";
    setTimeout(maintenance, 100);
    setTimeout(autoPtBrCaptions, 1200);
  });

  // 350 ms is intentionally aggressive only while YouTube is open.
  setInterval(maintenance, 350);
})();
