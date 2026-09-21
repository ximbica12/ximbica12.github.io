(() => {
  let config = {videoMax: 720, autoTranslate: true};

  const injectPage = () => {
    const s = document.createElement('script');
    s.src = browser.runtime.getURL('page.js');
    s.onload = () => s.remove();
    (document.documentElement || document).appendChild(s);
  };
  injectPage();

  const postConfig = () => {
    try {
      window.postMessage({type: 'XLITE_CONFIG', config}, '*');
    } catch (_) {}
  };

  browser.runtime.sendNativeMessage('xlite', {type: 'getConfig'})
    .then(reply => {
      if (reply && typeof reply === 'object') {
        if (reply.videoMax === 1080 || reply.videoMax === 720) config.videoMax = reply.videoMax;
        if (typeof reply.autoTranslate === 'boolean') config.autoTranslate = reply.autoTranslate;
      }
      postConfig();
      if (config.autoTranslate) startTranslation();
    })
    .catch(() => {
      postConfig();
      if (config.autoTranslate) startTranslation();
    });

  const translateLabels = [
    'translate post', 'translate tweet', 'translate',
    'traduzir post', 'traduzir tweet', 'traduzir',
    'ver tradução', 'ver traducao', 'mostrar tradução', 'mostrar traducao'
  ];

  const seen = new WeakSet();
  const queue = new Set();
  let scheduled = false;

  function isTranslateControl(el) {
    const text = ((el.innerText || el.textContent || '') + '').trim().toLowerCase();
    if (!text || text.length > 70) return false;
    return translateLabels.some(x => text === x || text.startsWith(x + ' '));
  }

  function processArticle(article) {
    if (!article || seen.has(article)) return;
    seen.add(article);

    const controls = article.querySelectorAll('button,a,[role="button"],[role="link"]');
    for (const el of controls) {
      if (isTranslateControl(el)) {
        try { el.click(); } catch (_) {}
      }
    }

    // One delayed second pass catches controls rendered after the text.
    setTimeout(() => {
      try {
        const controls2 = article.querySelectorAll('button,a,[role="button"],[role="link"]');
        for (const el of controls2) {
          if (isTranslateControl(el)) {
            try { el.click(); } catch (_) {}
          }
        }
      } catch (_) {}
    }, 650);
  }

  function flush() {
    scheduled = false;
    const items = Array.from(queue);
    queue.clear();
    for (const node of items) {
      if (node.matches && node.matches('article')) processArticle(node);
      if (node.querySelectorAll) {
        for (const article of node.querySelectorAll('article')) processArticle(article);
      }
    }
  }

  function enqueue(node) {
    if (!node || node.nodeType !== 1) return;
    queue.add(node);
    if (!scheduled) {
      scheduled = true;
      setTimeout(flush, 90);
    }
  }

  function startTranslation() {
    const start = () => {
      for (const a of document.querySelectorAll('article')) processArticle(a);
      const target = document.body || document.documentElement;
      if (!target) return;
      new MutationObserver(records => {
        for (const r of records) {
          for (const n of r.addedNodes) enqueue(n);
        }
      }).observe(target, {childList: true, subtree: true});
    };

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', start, {once: true});
    } else {
      start();
    }
  }
})();