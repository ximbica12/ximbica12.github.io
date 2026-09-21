(() => {
  "use strict";

  const translated = new WeakSet();
  let scheduled = false;
  const translationPattern = /^(translate post|translate tweet|traduzir post|traduzir tweet|traduzir)$/i;

  function hidePromoted(root = document) {
    root.querySelectorAll('[data-testid="placementTracking"]').forEach((marker) => {
      const article = marker.closest("article");
      const cell = marker.closest('[data-testid="cellInnerDiv"]');
      (article || cell || marker).setAttribute("data-xlite-ad", "1");
    });
  }

  function optimizeVideos(root = document) {
    root.querySelectorAll("video").forEach((video) => {
      if (video.hasAttribute("data-xlite-video")) return;
      video.setAttribute("data-xlite-video", "1");
      video.setAttribute("playsinline", "");
      if (!video.autoplay) video.preload = "metadata";
    });
  }

  function autoTranslate(root = document) {
    root.querySelectorAll('button, a, [role="button"]').forEach((control) => {
      if (translated.has(control)) return;

      const text = (control.textContent || "").trim();
      if (!translationPattern.test(text)) return;

      const rect = control.getBoundingClientRect();
      const visible =
        rect.bottom >= 0 &&
        rect.top <= window.innerHeight &&
        rect.width > 0 &&
        rect.height > 0;

      if (!visible) return;

      translated.add(control);
      setTimeout(() => {
        try {
          control.click();
        } catch (_) {
        }
      }, 120);
    });
  }

  function run() {
    scheduled = false;
    hidePromoted();
    optimizeVideos();
    autoTranslate();
  }

  function schedule() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(run);
  }

  new MutationObserver(schedule).observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  document.addEventListener("scroll", schedule, { passive: true });
  document.addEventListener("visibilitychange", schedule, { passive: true });

  run();
})();
