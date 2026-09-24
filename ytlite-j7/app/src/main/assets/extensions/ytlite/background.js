(() => {
  "use strict";

  // Independent network shield. uBlock Origin remains the primary filter engine;
  // this layer blocks high-confidence ad endpoints before page scripts see them.
  const AD_URLS = [
    "*://*.doubleclick.net/*",
    "*://*.googlesyndication.com/*",
    "*://*.googleadservices.com/*",
    "*://googleads.g.doubleclick.net/*",
    "*://ad.doubleclick.net/*",
    "*://pubads.g.doubleclick.net/*",
    "*://securepubads.g.doubleclick.net/*",
    "*://*.2mdn.net/*",
    "*://s0.2mdn.net/*",
    "*://www.youtube.com/pagead/*",
    "*://m.youtube.com/pagead/*",
    "*://www.youtube.com/api/stats/ads*",
    "*://m.youtube.com/api/stats/ads*",
    "*://www.youtube.com/get_midroll_info*",
    "*://m.youtube.com/get_midroll_info*",
    "*://www.youtube.com/ptracking*",
    "*://m.youtube.com/ptracking*"
  ];

  browser.webRequest.onBeforeRequest.addListener(
    () => ({ cancel: true }),
    { urls: AD_URLS },
    ["blocking"]
  );
})();
