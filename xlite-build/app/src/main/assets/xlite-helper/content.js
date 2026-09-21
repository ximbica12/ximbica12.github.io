(() => {
  if (window.__XLITE_HELPER_V05__) return;
  window.__XLITE_HELPER_V05__ = true;

  let config = {
    videoMax: 720,
    autoTranslate: true,
    performanceMode: true
  };

  function nativeMessage(type, extra) {
    return browser.runtime.sendNativeMessage(
      'xlite',
      Object.assign({type}, extra || {})
    );
  }

  function injectPage() {
    const s = document.createElement('script');
    s.src = browser.runtime.getURL('page.js');
    s.onload = () => s.remove();
    (document.documentElement || document).appendChild(s);
  }

  function publishConfig() {
    try {
      window.postMessage({type: 'XLITE_CONFIG', config}, '*');
    } catch (_) {}
  }

  function installPerformanceCss() {
    if (!config.performanceMode || document.getElementById('xlite-perf-css')) return;
    const style = document.createElement('style');
    style.id = 'xlite-perf-css';
    style.textContent = `
      html { scroll-behavior: auto !important; }
      video { background: #000 !important; }
      [style*="backdrop-filter"] {
        backdrop-filter: none !important;
        -webkit-backdrop-filter: none !important;
      }
    `;
    (document.head || document.documentElement).appendChild(style);
  }

  const translateLabels = [
    'translate post', 'translate tweet', 'translate',
    'traduzir post', 'traduzir tweet', 'traduzir',
    'ver tradução', 'ver traducao',
    'mostrar tradução', 'mostrar traducao'
  ];

  function isTranslateControl(el) {
    const text = ((el.innerText || el.textContent || '') + '').trim().toLowerCase();
    if (!text || text.length > 80) return false;
    return translateLabels.some(x => text === x || text.startsWith(x + ' '));
  }

  function translateArticle(article) {
    if (!config.autoTranslate || !article || article.dataset.xliteTranslateDone === '1') return;

    let clicked = false;
    for (const el of article.querySelectorAll('button,a,[role="button"],[role="link"]')) {
      if (isTranslateControl(el)) {
        try {
          el.click();
          clicked = true;
        } catch (_) {}
      }
    }

    // Mark only after a translate control was actually found; X often renders it late.
    if (clicked) {
      article.dataset.xliteTranslateDone = '1';
    } else if (!article.dataset.xliteTranslateRetry) {
      article.dataset.xliteTranslateRetry = '1';
      setTimeout(() => {
        delete article.dataset.xliteTranslateRetry;
        translateArticle(article);
      }, 700);
    }
  }

  let articleObserver;

  function observeArticle(article) {
    if (!config.autoTranslate || !article || article.dataset.xliteObserved === '1') return;
    article.dataset.xliteObserved = '1';

    if (!articleObserver) {
      articleObserver = new IntersectionObserver(entries => {
        for (const entry of entries) {
          if (entry.isIntersecting && entry.intersectionRatio > 0.08) {
            translateArticle(entry.target);
          }
        }
      }, {rootMargin: '240px 0px 240px 0px', threshold: [0, 0.08]});
    }

    articleObserver.observe(article);
  }

  function discoverArticles(root) {
    if (!config.autoTranslate || !root || root.nodeType !== 1) return;
    if (root.matches && root.matches('article')) observeArticle(root);
    if (root.querySelectorAll) {
      for (const article of root.querySelectorAll('article')) observeArticle(article);
    }
  }

  function startTranslationObserver() {
    if (!config.autoTranslate) return;
    discoverArticles(document.documentElement);

    const target = document.body || document.documentElement;
    if (!target) return;

    const observer = new MutationObserver(records => {
      for (const record of records) {
        for (const node of record.addedNodes) {
          if (node.nodeType === 1) discoverArticles(node);
        }
      }
    });
    observer.observe(target, {childList: true, subtree: true});
  }

  function bindLogoFallback() {
    const bind = logo => {
      if (!logo || logo.dataset.xliteBound === '1') return;
      logo.dataset.xliteBound = '1';

      let timer = null;
      let sx = 0;
      let sy = 0;

      const cancel = () => {
        if (timer) clearTimeout(timer);
        timer = null;
      };

      logo.addEventListener('pointerdown', e => {
        sx = e.clientX;
        sy = e.clientY;
        cancel();
        timer = setTimeout(() => {
          timer = null;
          nativeMessage('openMenu').catch(() => {});
        }, 650);
      }, {passive: true});

      logo.addEventListener('pointermove', e => {
        if (Math.abs(e.clientX - sx) > 18 || Math.abs(e.clientY - sy) > 18) cancel();
      }, {passive: true});

      logo.addEventListener('pointerup', cancel, {passive: true});
      logo.addEventListener('pointercancel', cancel, {passive: true});
    };

    const scan = () => {
      const candidates = document.querySelectorAll(
        'a[href="/home"] svg, a[href="https://x.com/home"] svg, header svg'
      );
      for (const logo of candidates) bind(logo);
    };

    scan();
    setInterval(scan, 2500);
  }

  function installXMenuEntry() {
    const labels = [
      'settings and privacy',
      'configurações e privacidade',
      'configuracoes e privacidade',
      'settings & privacy'
    ];

    const scan = () => {
      if (document.querySelector('[data-xlite-menu-entry="1"]')) return;

      const controls = document.querySelectorAll(
        'a,[role="link"],button,[role="button"]'
      );

      let anchor = null;
      for (const el of controls) {
        const text = ((el.innerText || el.textContent || '') + '').trim().toLowerCase();
        if (labels.includes(text)) {
          anchor = el;
          break;
        }
      }
      if (!anchor || !anchor.parentNode) return;

      const item = document.createElement('button');
      item.type = 'button';
      item.dataset.xliteMenuEntry = '1';
      item.textContent = '⚙  XLite';
      item.setAttribute('aria-label', 'XLite');
      item.style.cssText = [
        'all:unset',
        'display:flex',
        'align-items:center',
        'box-sizing:border-box',
        'width:100%',
        'min-height:44px',
        'padding:10px 16px',
        'cursor:pointer',
        'font:inherit',
        'font-weight:600',
        'color:inherit'
      ].join(';');

      item.addEventListener('click', e => {
        e.preventDefault();
        e.stopPropagation();
        nativeMessage('openMenu').catch(() => {});
      });

      anchor.parentNode.insertBefore(item, anchor.nextSibling);
    };

    scan();
    setInterval(scan, 1800);
  }

  function start() {
    injectPage();

    nativeMessage('getConfig')
      .then(reply => {
        if (reply && typeof reply === 'object') {
          if (reply.videoMax === 720 || reply.videoMax === 1080) {
            config.videoMax = reply.videoMax;
          }
          if (typeof reply.autoTranslate === 'boolean') {
            config.autoTranslate = reply.autoTranslate;
          }
          if (typeof reply.performanceMode === 'boolean') {
            config.performanceMode = reply.performanceMode;
          }
        }

        publishConfig();
        installPerformanceCss();
        startTranslationObserver();
        bindLogoFallback();
        installXMenuEntry();
      })
      .catch(() => {
        publishConfig();
        installPerformanceCss();
        startTranslationObserver();
        bindLogoFallback();
        installXMenuEntry();
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, {once: true});
  } else {
    start();
  }
})();