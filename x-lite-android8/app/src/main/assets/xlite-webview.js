(() => {
  "use strict";

  if (window.__XLITE_V3__) return;
  window.__XLITE_V3__ = true;

  const translated = new WeakSet();
  const videos = new WeakSet();
  let timer = 0;
  let running = false;

  const TRANSLATE = /^(translate post|translate tweet|traduzir post|traduzir tweet|traduzir)$/i;

  function visible(el) {
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && r.bottom >= -160 && r.top <= innerHeight + 160;
  }

  function installStyle() {
    if (document.getElementById("xlite-style")) return;
    const style = document.createElement("style");
    style.id = "xlite-style";
    style.textContent = `
      html { text-size-adjust: 100% !important; -webkit-text-size-adjust: 100% !important; }
      body { overflow-x: hidden !important; }
      [data-xlite-ad="1"] { display: none !important; }
      video { max-width: 100% !important; }
      @media (max-width: 700px) {
        main, [data-testid="primaryColumn"] {
          width: 100% !important;
          max-width: 100% !important;
          min-width: 0 !important;
        }
        [data-testid="sidebarColumn"] { display: none !important; }
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  }

  function hidePromoted() {
    document.querySelectorAll('[data-testid="placementTracking"]').forEach(marker => {
      const target =
        marker.closest('[data-testid="cellInnerDiv"]') ||
        marker.closest("article");
      if (target) target.setAttribute("data-xlite-ad", "1");
    });
  }

  function optimizeVideos() {
    document.querySelectorAll("video").forEach(video => {
      if (videos.has(video)) return;
      videos.add(video);
      video.playsInline = true;
      video.setAttribute("playsinline", "");
      if (!video.autoplay) video.preload = "metadata";
    });
  }

  function translateVisible() {
    document.querySelectorAll('button,[role="button"]').forEach(control => {
      if (translated.has(control) || !visible(control)) return;
      const label = (
        control.getAttribute("aria-label") ||
        control.textContent ||
        ""
      ).trim();

      if (!TRANSLATE.test(label)) return;
      translated.add(control);
      try { control.click(); } catch (_) {}
    });
  }

  function run() {
    timer = 0;
    if (running || document.hidden) return;
    running = true;
    try {
      installStyle();
      hidePromoted();
      optimizeVideos();
      translateVisible();
    } finally {
      running = false;
    }
  }

  function schedule(delay = 1100) {
    if (timer) return;
    timer = setTimeout(run, delay);
  }

  installStyle();

  new MutationObserver(() => schedule(1200)).observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  addEventListener("scroll", () => schedule(500), { passive: true });
  document.addEventListener("visibilitychange", () => schedule(250), { passive: true });

  schedule(600);
})();