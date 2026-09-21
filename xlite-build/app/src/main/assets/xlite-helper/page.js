(() => {
  if (window.__XLITE_PAGE_V05__) return;
  window.__XLITE_PAGE_V05__ = true;

  let maxHeight = 720;

  window.addEventListener('message', event => {
    if (event.source !== window || !event.data || event.data.type !== 'XLITE_CONFIG') return;
    const cfg = event.data.config || {};
    if (cfg.videoMax === 720 || cfg.videoMax === 1080) maxHeight = cfg.videoMax;
  });

  function capM3u8(text) {
    if (typeof text !== 'string' || !text.includes('#EXT-X-STREAM-INF')) return text;

    const lines = text.replace(/\r\n/g, '\n').split('\n');
    const out = [];
    let variants = 0;
    let kept = 0;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (!line.startsWith('#EXT-X-STREAM-INF')) {
        out.push(line);
        continue;
      }

      variants++;
      const m = /RESOLUTION\s*=\s*(\d+)\s*x\s*(\d+)/i.exec(line);
      let allow = true;
      if (m) {
        const w = Number(m[1]);
        const h = Number(m[2]);
        allow = Math.min(w, h) <= maxHeight;
      }

      const next = i + 1 < lines.length ? lines[i + 1] : '';
      if (allow) {
        kept++;
        out.push(line);
        if (i + 1 < lines.length) {
          out.push(next);
          i++;
        }
      } else if (i + 1 < lines.length && !next.startsWith('#')) {
        i++;
      }
    }

    return variants > 0 && kept === 0 ? text : out.join('\n');
  }

  const originalFetch = window.fetch;
  if (typeof originalFetch === 'function') {
    window.fetch = async function(input, init) {
      const response = await originalFetch.apply(this, arguments);
      try {
        const url = typeof input === 'string'
          ? input
          : (input && input.url) || response.url || '';

        if (!/\.m3u8(?:\?|$)/i.test(url)) return response;

        const originalText = await response.clone().text();
        const capped = capM3u8(originalText);
        if (capped === originalText) return response;

        const headers = new Headers(response.headers);
        headers.delete('content-length');
        headers.delete('content-encoding');

        return new Response(capped, {
          status: response.status,
          statusText: response.statusText,
          headers
        });
      } catch (_) {
        return response;
      }
    };
  }

  // Keep only nearby videos active. X already does this in most cases, this is a
  // lightweight safeguard for old devices when several videos remain mounted.
  const seenVideos = new WeakSet();
  const videoObserver = new IntersectionObserver(entries => {
    for (const entry of entries) {
      const video = entry.target;
      if (!entry.isIntersecting && !video.paused) {
        try { video.pause(); } catch (_) {}
      }
    }
  }, {rootMargin: '320px 0px 320px 0px'});

  function registerVideos(root) {
    if (!root || root.nodeType !== 1) return;
    const list = [];
    if (root.matches && root.matches('video')) list.push(root);
    if (root.querySelectorAll) list.push(...root.querySelectorAll('video'));

    for (const video of list) {
      if (seenVideos.has(video)) continue;
      seenVideos.add(video);
      try {
        video.preload = 'metadata';
        video.setAttribute('playsinline', '');
      } catch (_) {}
      videoObserver.observe(video);
    }
  }

  const start = () => {
    registerVideos(document.documentElement);
    const target = document.body || document.documentElement;
    if (!target) return;

    new MutationObserver(records => {
      for (const record of records) {
        for (const node of record.addedNodes) registerVideos(node);
      }
    }).observe(target, {childList: true, subtree: true});
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, {once: true});
  } else {
    start();
  }
})();