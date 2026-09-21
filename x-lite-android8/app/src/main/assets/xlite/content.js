(() => {
  "use strict";

  const processedVideos = new WeakSet();
  const translatedControls = new WeakSet();

  let timer = 0;
  let running = false;

  const translationPattern =
    /^(translate post|translate tweet|traduzir post|traduzir tweet|traduzir)$/i;

  function visible(el) {
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && r.bottom >= -120 && r.top <= innerHeight + 120;
  }

  function hidePromoted() {
    document.querySelectorAll('[data-testid="placementTracking"]').forEach(marker => {
      const cell = marker.closest('[data-testid="cellInnerDiv"]');
      const article = marker.closest("article");
      const target = cell || article;
      if (target) target.setAttribute("data-xlite-ad", "1");
    });
  }

  function optimizeVisibleVideos() {
    document.querySelectorAll("video").forEach(video => {
      if (processedVideos.has(video)) return;
      processedVideos.add(video);
      video.setAttribute("playsinline", "");
      if (!video.autoplay) video.preload = "metadata";
    });
  }

  function translateVisiblePosts() {
    document.querySelectorAll('button,[role="button"]').forEach(control => {
      if (translatedControls.has(control) || !visible(control)) return;

      const label = (
        control.getAttribute("aria-label") ||
        control.textContent ||
        ""
      ).trim();

      if (!translationPattern.test(label)) return;

      translatedControls.add(control);
      try {
        control.click();
      } catch (_) {}
    });
  }

  function run() {
    timer = 0;
    if (running || document.hidden) return;

    running = true;
    try {
      hidePromoted();
      optimizeVisibleVideos();
      translateVisiblePosts();
    } finally {
      running = false;
    }
  }

  function schedule(delay = 900) {
    if (timer) return;
    timer = setTimeout(run, delay);
  }

  const observer = new MutationObserver(() => schedule(900));
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  document.addEventListener("scroll", () => schedule(450), { passive: true });
  document.addEventListener("visibilitychange", () => schedule(300), { passive: true });

  schedule(1200);
})();
