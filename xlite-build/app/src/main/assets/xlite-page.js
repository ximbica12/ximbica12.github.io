(() => {
  const XL = window.__XLITE || {};
  const autoTranslate = XL.autoTranslate !== false;
  const lite = XL.lite !== false;

  function installLite() {
    if (!lite || document.getElementById('xlite-v4-css')) return;
    const s = document.createElement('style');
    s.id = 'xlite-v4-css';
    s.textContent = `
      *,*::before,*::after{
        animation-duration:.001s!important;
        animation-iteration-count:1!important;
        transition-duration:.001s!important;
        scroll-behavior:auto!important
      }
      [style*="backdrop-filter"]{
        backdrop-filter:none!important;
        -webkit-backdrop-filter:none!important
      }
      video{background:#000!important}
    `;
    (document.head || document.documentElement).appendChild(s);
  }

  const translateWords = [
    'translate post','translate tweet','translate bio',
    'traduzir post','traduzir tweet','traduzir biografia',
    'ver tradução','ver traducao','mostrar tradução','mostrar traducao'
  ];

  function maybeTranslate(root) {
    if (!autoTranslate) return;
    const scope = root && root.querySelectorAll ? root : document;
    const nodes = scope.querySelectorAll('button,a,[role="button"],[role="link"]');
    for (const el of nodes) {
      if (el.dataset && el.dataset.xliteTranslated === '1') continue;
      const t = ((el.innerText || el.textContent || '') + '').trim().toLowerCase();
      if (!t || t.length > 80) continue;
      if (translateWords.some(x => t === x || t.startsWith(x + ' '))) {
        if (el.dataset) el.dataset.xliteTranslated = '1';
        try { el.click(); } catch (_) {}
      }
    }
  }

  function tuneVideos(root) {
    const scope = root && root.querySelectorAll ? root : document;
    for (const v of scope.querySelectorAll('video')) {
      try {
        v.preload = 'metadata';
        v.setAttribute('playsinline', '');
        v.disablePictureInPicture = false;
      } catch (_) {}
    }
  }

  function run(root) {
    installLite();
    maybeTranslate(root);
    tuneVideos(root);
  }

  function start() {
    run(document);
    const target = document.body || document.documentElement;
    if (!target) return;
    new MutationObserver(ms => {
      for (const m of ms) {
        for (const n of m.addedNodes || []) {
          if (n.nodeType === 1) run(n);
        }
      }
    }).observe(target, {childList:true, subtree:true});
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, {once:true});
  } else {
    start();
  }
})();