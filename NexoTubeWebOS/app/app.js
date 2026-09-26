(function () {
  'use strict';

  var API_INSTANCES = [
    'https://pipedapi.kavin.rocks',
    'https://pipedapi.leptons.xyz',
    'https://piped-api.garudalinux.org'
  ];

  var REGIONS = [
    { code: 'BR', name: 'Brasil' },
    { code: 'PT', name: 'Portugal' },
    { code: 'US', name: 'Estados Unidos' },
    { code: 'GB', name: 'Reino Unido' },
    { code: 'JP', name: 'Japão' },
    { code: 'DE', name: 'Alemanha' }
  ];

  var state = {
    view: 'home',
    instanceIndex: 0,
    currentVideo: null,
    currentVideoInfo: null,
    playerFallbackTried: false,
    homeToken: 0
  };

  var screen = document.getElementById('screen');
  var title = document.getElementById('view-title');
  var subtitle = document.getElementById('view-subtitle');
  var status = document.getElementById('status');
  var playerOverlay = document.getElementById('player-overlay');
  var video = document.getElementById('video-player');
  var playerTitle = document.getElementById('player-title');
  var playerMeta = document.getElementById('player-meta');
  var playerMessage = document.getElementById('player-message');
  var btnSubscribe = document.getElementById('btn-subscribe');
  var btnWatchLater = document.getElementById('btn-watchlater');
  var btnClosePlayer = document.getElementById('btn-close-player');
  var dialog = document.getElementById('dialog');
  var dialogTitle = document.getElementById('dialog-title');
  var dialogBody = document.getElementById('dialog-body');

  function safeParse(text, fallback) {
    try { return JSON.parse(text); } catch (e) { return fallback; }
  }

  function loadProfiles() {
    var profiles = safeParse(localStorage.getItem('nexotube.profiles'), null);
    if (!profiles || !profiles.length) {
      profiles = [{ id: 'default', name: 'Principal' }];
      localStorage.setItem('nexotube.profiles', JSON.stringify(profiles));
    }
    return profiles;
  }

  function activeProfileId() {
    var profiles = loadProfiles();
    var id = localStorage.getItem('nexotube.activeProfile') || profiles[0].id;
    var exists = false;
    var i;
    for (i = 0; i < profiles.length; i++) {
      if (profiles[i].id === id) exists = true;
    }
    if (!exists) id = profiles[0].id;
    localStorage.setItem('nexotube.activeProfile', id);
    return id;
  }

  function profileKey() {
    return 'nexotube.profile.' + activeProfileId();
  }

  function profileData() {
    var data = safeParse(localStorage.getItem(profileKey()), null);
    if (!data) {
      data = {
        subscriptions: [],
        history: [],
        watchLater: [],
        region: 'BR'
      };
      saveProfileData(data);
    }
    if (!data.subscriptions) data.subscriptions = [];
    if (!data.history) data.history = [];
    if (!data.watchLater) data.watchLater = [];
    if (!data.region) data.region = 'BR';
    return data;
  }

  function saveProfileData(data) {
    localStorage.setItem(profileKey(), JSON.stringify(data));
  }

  function activeProfileName() {
    var profiles = loadProfiles();
    var id = activeProfileId();
    var i;
    for (i = 0; i < profiles.length; i++) {
      if (profiles[i].id === id) return profiles[i].name;
    }
    return 'Principal';
  }

  function updateProfileBadge() {
    document.getElementById('profile-badge').textContent = 'Perfil: ' + activeProfileName();
  }

  function currentApiBase() {
    var saved = localStorage.getItem('nexotube.api');
    return saved || API_INSTANCES[state.instanceIndex] || API_INSTANCES[0];
  }

  function setApiBase(base) {
    localStorage.setItem('nexotube.api', base);
    status.textContent = 'API: ' + base.replace('https://', '');
  }

  function xhrJson(base, path, cb) {
    var xhr = new XMLHttpRequest();
    var done = false;
    var timer = setTimeout(function () {
      if (done) return;
      done = true;
      try { xhr.abort(); } catch (e) {}
      cb(new Error('Tempo limite'), null);
    }, 12000);

    xhr.onreadystatechange = function () {
      if (xhr.readyState !== 4 || done) return;
      done = true;
      clearTimeout(timer);
      if (xhr.status >= 200 && xhr.status < 300) {
        cb(null, safeParse(xhr.responseText, null));
      } else {
        cb(new Error('HTTP ' + xhr.status), null);
      }
    };

    try {
      xhr.open('GET', base + path, true);
      xhr.setRequestHeader('Accept', 'application/json');
      xhr.send();
    } catch (e) {
      if (!done) {
        done = true;
        clearTimeout(timer);
        cb(e, null);
      }
    }
  }

  function apiGet(path, cb) {
    var preferred = currentApiBase();
    var candidates = [preferred];
    var i;
    for (i = 0; i < API_INSTANCES.length; i++) {
      if (API_INSTANCES[i] !== preferred) candidates.push(API_INSTANCES[i]);
    }

    function attempt(index) {
      if (index >= candidates.length) {
        cb(new Error('Nenhuma instância respondeu'), null);
        return;
      }
      xhrJson(candidates[index], path, function (err, data) {
        if (!err && data) {
          if (candidates[index] !== preferred) setApiBase(candidates[index]);
          cb(null, data);
        } else {
          attempt(index + 1);
        }
      });
    }

    attempt(0);
  }

  function esc(text) {
    if (text === null || text === undefined) return '';
    return String(text)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function videoIdFromUrl(url) {
    var match;
    if (!url) return '';
    match = String(url).match(/[?&]v=([A-Za-z0-9_-]{11})/);
    if (match) return match[1];
    match = String(url).match(/\/shorts\/([A-Za-z0-9_-]{11})/);
    if (match) return match[1];
    if (/^[A-Za-z0-9_-]{11}$/.test(url)) return url;
    return '';
  }

  function channelIdFromUrl(url) {
    var match = String(url || '').match(/\/channel\/([A-Za-z0-9_-]{20,30})/);
    return match ? match[1] : '';
  }

  function durationText(seconds) {
    seconds = Number(seconds || 0);
    if (!seconds) return '';
    var h = Math.floor(seconds / 3600);
    var m = Math.floor((seconds % 3600) / 60);
    var s = seconds % 60;
    if (h) return h + ':' + (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
    return m + ':' + (s < 10 ? '0' : '') + s;
  }

  function normalizeItem(item) {
    if (!item) return null;
    return {
      title: item.title || item.name || 'Sem título',
      thumbnail: item.thumbnail || item.thumbnailUrl || '',
      url: item.url || '',
      duration: item.duration || 0,
      uploaderName: item.uploaderName || item.uploader || '',
      uploaderUrl: item.uploaderUrl || '',
      uploadedDate: item.uploadedDate || '',
      views: item.views || 0,
      isShort: !!item.isShort
    };
  }

  function uniqVideos(items) {
    var out = [];
    var seen = {};
    var i, it, id;
    items = items || [];
    for (i = 0; i < items.length; i++) {
      it = normalizeItem(items[i]);
      if (!it) continue;
      id = videoIdFromUrl(it.url);
      if (!id || seen[id]) continue;
      seen[id] = true;
      out.push(it);
    }
    return out;
  }

  function cardHtml(item, wide) {
    var v = normalizeItem(item);
    var id = videoIdFromUrl(v.url);
    if (!id) return '';
    return '' +
      '<div class="card focusable' + (wide ? ' wide' : '') + '" tabindex="0" data-video="' + esc(id) + '">' +
        '<img src="' + esc(v.thumbnail) + '" alt="">' +
        (v.duration ? '<span class="card-duration">' + esc(durationText(v.duration)) + '</span>' : '') +
        '<div class="card-body">' +
          '<div class="card-title">' + esc(v.title) + '</div>' +
          '<div class="card-meta">' + esc(v.uploaderName || v.uploadedDate || '') + '</div>' +
        '</div>' +
      '</div>';
  }

  function sectionHtml(name, items, wide) {
    var list = uniqVideos(items);
    var html = '<section class="section"><h2 class="section-title">' + esc(name) + '</h2><div class="row">';
    var i;
    for (i = 0; i < list.length; i++) html += cardHtml(list[i], wide);
    html += '</div></section>';
    return list.length ? html : '';
  }

  function bindVideoCards() {
    var cards = screen.querySelectorAll('[data-video]');
    var i;
    for (i = 0; i < cards.length; i++) {
      cards[i].onclick = function () {
        openVideo(this.getAttribute('data-video'));
      };
    }
  }

  function showLoading(message) {
    screen.innerHTML = '<div class="loading">' + esc(message || 'Carregando…') + '</div>';
  }

  function setViewMeta(main, sub) {
    title.textContent = main;
    subtitle.textContent = sub || '';
  }

  function setActiveNav(view) {
    var navs = document.querySelectorAll('.nav-item');
    var i;
    for (i = 0; i < navs.length; i++) {
      if (navs[i].getAttribute('data-view') === view) navs[i].classList.add('active');
      else navs[i].classList.remove('active');
    }
  }

  function navigate(view) {
    state.view = view;
    setActiveNav(view);
    if (view === 'home') renderHome();
    else if (view === 'search') renderSearch();
    else if (view === 'subscriptions') renderSubscriptions();
    else if (view === 'library') renderLibrary();
    else if (view === 'profiles') renderProfiles();
    else if (view === 'settings') renderSettings();
  }

  function renderHome() {
    var token = ++state.homeToken;
    var data = profileData();
    var region = data.region || 'BR';
    setViewMeta('Início', 'Descoberta sem depender da sessão do YouTube oficial');
    showLoading('Montando recomendações…');

    var trending = [];
    var feed = [];
    var related = [];
    var pending = 1;

    function finishPart() {
      pending--;
      if (pending > 0 || token !== state.homeToken) return;

      var historyIds = {};
      var i;
      for (i = 0; i < data.history.length; i++) historyIds[videoIdFromUrl(data.history[i].url)] = true;

      var personalized = uniqVideos(related.concat(trending));
      personalized = personalized.filter(function (it) {
        return !historyIds[videoIdFromUrl(it.url)];
      });

      var html = '';
      html += sectionHtml('Para você', personalized.slice(0, 14), true);
      if (feed.length) html += sectionHtml('Das suas inscrições', feed.slice(0, 12), false);
      html += sectionHtml('Em alta no ' + region, trending.slice(0, 14), false);
      if (!html) html = '<div class="empty">Ainda não consegui carregar conteúdo. Tente novamente em alguns segundos.</div>';
      screen.innerHTML = html;
      bindVideoCards();
      focusFirstContent();
    }

    apiGet('/trending?region=' + encodeURIComponent(region), function (err, items) {
      trending = err ? [] : (items || []);
      pending = pending + Math.min(2, data.history.length);
      if (data.subscriptions.length) pending++;

      if (data.subscriptions.length) {
        apiGet('/feed/unauthenticated?channels=' + encodeURIComponent(data.subscriptions.join(',')), function (feedErr, items2) {
          feed = feedErr ? [] : (items2 || []);
          finishPart();
        });
      }

      var seeds = data.history.slice(0, 2);
      var j;
      for (j = 0; j < seeds.length; j++) {
        (function (seed) {
          var id = videoIdFromUrl(seed.url);
          if (!id) { finishPart(); return; }
          apiGet('/streams/' + encodeURIComponent(id), function (relErr, info) {
            if (!relErr && info && info.relatedStreams) related = related.concat(info.relatedStreams);
            finishPart();
          });
        })(seeds[j]);
      }

      finishPart();
    });
  }

  function renderSearch() {
    setViewMeta('Buscar', 'Pesquisa pelo catálogo independente');
    screen.innerHTML =
      '<div class="toolbar">' +
        '<input id="search-box" class="search-input focusable" tabindex="0" placeholder="Buscar vídeos, canais ou assuntos">' +
        '<button id="search-go" class="action-btn primary focusable" tabindex="0">Buscar</button>' +
      '</div>' +
      '<div id="search-results"><div class="empty">Digite algo para pesquisar.</div></div>';

    var input = document.getElementById('search-box');
    var button = document.getElementById('search-go');

    function doSearch() {
      var q = input.value.replace(/^\s+|\s+$/g, '');
      if (!q) return;
      document.getElementById('search-results').innerHTML = '<div class="loading">Buscando “' + esc(q) + '”…</div>';
      apiGet('/search?q=' + encodeURIComponent(q) + '&filter=videos', function (err, result) {
        var container = document.getElementById('search-results');
        if (err || !result) {
          container.innerHTML = '<div class="empty">A busca falhou. Tente novamente.</div>';
          return;
        }
        var items = result.items || [];
        container.innerHTML = sectionHtml('Resultados', items.slice(0, 28), true) ||
          '<div class="empty">Nenhum vídeo encontrado.</div>';
        bindVideoCards();
        focusFirstContent();
      });
    }

    button.onclick = doSearch;
    input.onkeydown = function (e) {
      if (e.keyCode === 13) {
        doSearch();
        e.preventDefault();
      }
    };
    setTimeout(function () { try { input.focus(); } catch (e) {} }, 80);
  }

  function renderSubscriptions() {
    var data = profileData();
    setViewMeta('Inscrições', 'Canais deste perfil do NexoTube');
    if (!data.subscriptions.length) {
      screen.innerHTML = '<div class="empty">Você ainda não adicionou canais neste perfil.<br>Abra um vídeo e use “Inscrever-se”.</div>';
      return;
    }
    showLoading('Atualizando inscrições…');
    apiGet('/feed/unauthenticated?channels=' + encodeURIComponent(data.subscriptions.join(',')), function (err, items) {
      if (err) {
        screen.innerHTML = '<div class="empty">Não consegui atualizar as inscrições agora.</div>';
        return;
      }
      screen.innerHTML = sectionHtml('Novos vídeos', (items || []).slice(0, 32), true) ||
        '<div class="empty">Nenhum vídeo recente.</div>';
      bindVideoCards();
      focusFirstContent();
    });
  }

  function renderLibrary() {
    var data = profileData();
    setViewMeta('Biblioteca', 'Dados locais — não compartilhados com o YouTube oficial');
    var html = '';
    html += sectionHtml('Assistir mais tarde', data.watchLater.slice(0, 20), false);
    html += sectionHtml('Histórico', data.history.slice(0, 24), false);
    if (!html) html = '<div class="empty">Sua biblioteca local ainda está vazia.</div>';
    screen.innerHTML = html;
    bindVideoCards();
    focusFirstContent();
  }

  function renderProfiles() {
    var profiles = loadProfiles();
    var active = activeProfileId();
    setViewMeta('Perfis', 'Cada perfil mantém inscrições e histórico separados');
    var html = '<button id="new-profile" class="action-btn primary focusable" tabindex="0">+ Novo perfil</button>';
    var i;
    for (i = 0; i < profiles.length; i++) {
      html += '<div class="profile-card focusable" tabindex="0" data-profile="' + esc(profiles[i].id) + '">' +
        '<h3>' + (profiles[i].id === active ? '✓ ' : '') + esc(profiles[i].name) + '</h3>' +
        '<p>Dados locais independentes</p>' +
      '</div>';
    }
    screen.innerHTML = html;

    document.getElementById('new-profile').onclick = function () {
      openTextDialog('Novo perfil', 'Nome do perfil', function (name) {
        if (!name) return;
        var list = loadProfiles();
        var id = 'p' + new Date().getTime();
        list.push({ id: id, name: name });
        localStorage.setItem('nexotube.profiles', JSON.stringify(list));
        localStorage.setItem('nexotube.activeProfile', id);
        updateProfileBadge();
        renderProfiles();
      });
    };

    var cards = screen.querySelectorAll('[data-profile]');
    for (i = 0; i < cards.length; i++) {
      cards[i].onclick = function () {
        localStorage.setItem('nexotube.activeProfile', this.getAttribute('data-profile'));
        updateProfileBadge();
        navigate('home');
      };
    }
    focusFirstContent();
  }

  function regionName(code) {
    var i;
    for (i = 0; i < REGIONS.length; i++) if (REGIONS[i].code === code) return REGIONS[i].name;
    return code;
  }

  function renderSettings() {
    var data = profileData();
    var base = currentApiBase();
    setViewMeta('Configurações', 'NexoTube TV 0.2 — frontend independente');

    screen.innerHTML =
      '<div id="setting-region" class="settings-line focusable" tabindex="0"><span>Região de descoberta</span><span>' + esc(regionName(data.region)) + '</span></div>' +
      '<div id="setting-api" class="settings-line focusable" tabindex="0"><span>Instância de dados</span><span>' + esc(base.replace('https://', '')) + '</span></div>' +
      '<div class="settings-line"><span>Sessão do YouTube oficial</span><span>Não utilizada</span></div>' +
      '<div class="settings-line"><span>Armazenamento de perfis</span><span>Somente local</span></div>' +
      '<div class="settings-line"><span>Versão</span><span>0.2.0-alpha</span></div>';

    document.getElementById('setting-region').onclick = function () {
      var current = 0;
      var i;
      for (i = 0; i < REGIONS.length; i++) if (REGIONS[i].code === data.region) current = i;
      current = (current + 1) % REGIONS.length;
      data.region = REGIONS[current].code;
      saveProfileData(data);
      renderSettings();
    };

    document.getElementById('setting-api').onclick = function () {
      var idx = 0;
      var i;
      for (i = 0; i < API_INSTANCES.length; i++) if (API_INSTANCES[i] === currentApiBase()) idx = i;
      idx = (idx + 1) % API_INSTANCES.length;
      setApiBase(API_INSTANCES[idx]);
      renderSettings();
    };
    focusFirstContent();
  }

  function rememberHistory(item) {
    var data = profileData();
    var id = videoIdFromUrl(item.url);
    var out = [normalizeItem(item)];
    var i;
    for (i = 0; i < data.history.length; i++) {
      if (videoIdFromUrl(data.history[i].url) !== id) out.push(data.history[i]);
      if (out.length >= 50) break;
    }
    data.history = out;
    saveProfileData(data);
  }

  function isSubscribed(channelId) {
    var data = profileData();
    return data.subscriptions.indexOf(channelId) >= 0;
  }

  function updatePlayerButtons() {
    if (!state.currentVideoInfo) return;
    var channelId = channelIdFromUrl(state.currentVideoInfo.uploaderUrl);
    btnSubscribe.textContent = channelId && isSubscribed(channelId) ? 'Inscrito ✓' : 'Inscrever-se';

    var data = profileData();
    var id = state.currentVideo;
    var exists = false;
    var i;
    for (i = 0; i < data.watchLater.length; i++) {
      if (videoIdFromUrl(data.watchLater[i].url) === id) exists = true;
    }
    btnWatchLater.textContent = exists ? 'Remover do Assistir mais tarde' : 'Assistir mais tarde';
  }

  function toggleSubscription() {
    if (!state.currentVideoInfo) return;
    var channelId = channelIdFromUrl(state.currentVideoInfo.uploaderUrl);
    if (!channelId) {
      showPlayerMessage('Canal indisponível');
      return;
    }
    var data = profileData();
    var pos = data.subscriptions.indexOf(channelId);
    if (pos >= 0) data.subscriptions.splice(pos, 1);
    else data.subscriptions.push(channelId);
    saveProfileData(data);
    updatePlayerButtons();
  }

  function toggleWatchLater() {
    if (!state.currentVideoInfo) return;
    var data = profileData();
    var id = state.currentVideo;
    var out = [];
    var found = false;
    var i;
    for (i = 0; i < data.watchLater.length; i++) {
      if (videoIdFromUrl(data.watchLater[i].url) === id) found = true;
      else out.push(data.watchLater[i]);
    }
    if (!found) {
      out.unshift(normalizeItem({
        title: state.currentVideoInfo.title,
        thumbnail: state.currentVideoInfo.thumbnailUrl,
        url: '/watch?v=' + id,
        duration: state.currentVideoInfo.duration,
        uploaderName: state.currentVideoInfo.uploader,
        uploaderUrl: state.currentVideoInfo.uploaderUrl
      }));
    }
    data.watchLater = out.slice(0, 100);
    saveProfileData(data);
    updatePlayerButtons();
  }

  function chooseProgressive(info) {
    var streams = info.videoStreams || [];
    var best = null;
    var i, s, height;
    for (i = 0; i < streams.length; i++) {
      s = streams[i];
      if (!s || s.videoOnly || !s.url) continue;
      if (String(s.mimeType || '').indexOf('video/mp4') < 0 && String(s.format || '').indexOf('MP4') < 0) continue;
      height = Number(s.height || parseInt(String(s.quality || '').replace('p', ''), 10) || 0);
      if (height > 1080) continue;
      if (!best || height > best.height) best = { url: s.url, height: height };
    }
    return best ? best.url : null;
  }

  function setPlayerSource(info) {
    state.playerFallbackTried = false;
    video.pause();
    video.removeAttribute('src');
    video.load();

    var progressive = chooseProgressive(info);
    if (info.hls) {
      video.src = info.hls;
      video.setAttribute('data-fallback', progressive || '');
    } else if (progressive) {
      video.src = progressive;
      video.setAttribute('data-fallback', '');
    } else if (info.dash) {
      video.src = info.dash;
      video.setAttribute('data-fallback', '');
    } else {
      showPlayerMessage('Nenhum stream compatível foi encontrado.');
      return;
    }

    try {
      var playResult = video.play();
      if (playResult && playResult.catch) playResult.catch(function () {});
    } catch (e) {}
  }

  function openVideo(videoId) {
    if (!videoId) return;
    state.currentVideo = videoId;
    state.currentVideoInfo = null;
    playerOverlay.classList.remove('hidden');
    playerTitle.textContent = 'Carregando…';
    playerMeta.textContent = '';
    playerMessage.textContent = 'Preparando vídeo';
    video.poster = '';
    video.pause();

    apiGet('/streams/' + encodeURIComponent(videoId), function (err, info) {
      if (err || !info || state.currentVideo !== videoId) {
        showPlayerMessage('Não consegui carregar este vídeo.');
        return;
      }
      state.currentVideoInfo = info;
      playerTitle.textContent = info.title || 'Vídeo';
      playerMeta.textContent = (info.uploader || '') + (info.views ? ' • ' + info.views + ' visualizações' : '');
      video.poster = info.thumbnailUrl || '';
      rememberHistory({
        title: info.title,
        thumbnail: info.thumbnailUrl,
        url: '/watch?v=' + videoId,
        duration: info.duration,
        uploaderName: info.uploader,
        uploaderUrl: info.uploaderUrl
      });
      updatePlayerButtons();
      setPlayerSource(info);
      setTimeout(function () { try { video.focus(); } catch (e) {} }, 80);
    });
  }

  function closePlayer() {
    state.currentVideo = null;
    state.currentVideoInfo = null;
    try {
      video.pause();
      video.removeAttribute('src');
      video.load();
    } catch (e) {}
    playerOverlay.classList.add('hidden');
    playerMessage.textContent = '';
    focusFirstContent();
  }

  function showPlayerMessage(text) {
    playerMessage.textContent = text;
    setTimeout(function () {
      if (playerMessage.textContent === text) playerMessage.textContent = '';
    }, 3500);
  }

  video.addEventListener('playing', function () {
    playerMessage.textContent = '';
  });

  video.addEventListener('waiting', function () {
    playerMessage.textContent = 'Carregando…';
  });

  video.addEventListener('error', function () {
    if (!state.currentVideoInfo) return;
    var fallback = video.getAttribute('data-fallback');
    if (!state.playerFallbackTried && fallback) {
      state.playerFallbackTried = true;
      playerMessage.textContent = 'Tentando stream alternativo…';
      video.src = fallback;
      video.setAttribute('data-fallback', '');
      try { video.play(); } catch (e) {}
    } else {
      showPlayerMessage('Falha de reprodução nesta fonte.');
    }
  });

  btnSubscribe.onclick = toggleSubscription;
  btnWatchLater.onclick = toggleWatchLater;
  btnClosePlayer.onclick = closePlayer;

  function openTextDialog(heading, placeholder, done) {
    dialogTitle.textContent = heading;
    dialogBody.innerHTML =
      '<input id="dialog-input" class="search-input focusable" tabindex="0" placeholder="' + esc(placeholder) + '">' +
      '<div style="margin-top:18px">' +
        '<button id="dialog-ok" class="action-btn primary focusable" tabindex="0">Salvar</button>' +
        '<button id="dialog-cancel" class="action-btn focusable" tabindex="0">Cancelar</button>' +
      '</div>';
    dialog.classList.remove('hidden');
    var input = document.getElementById('dialog-input');
    document.getElementById('dialog-ok').onclick = function () {
      var value = input.value.replace(/^\s+|\s+$/g, '');
      dialog.classList.add('hidden');
      done(value);
    };
    document.getElementById('dialog-cancel').onclick = function () {
      dialog.classList.add('hidden');
    };
    setTimeout(function () { try { input.focus(); } catch (e) {} }, 60);
  }

  function closeDialog() {
    dialog.classList.add('hidden');
  }

  function focusFirstContent() {
    setTimeout(function () {
      var first = screen.querySelector('.focusable');
      if (first) try { first.focus(); } catch (e) {}
    }, 50);
  }

  function visibleFocusables() {
    var all = document.querySelectorAll('.focusable');
    var out = [];
    var i, rect;
    for (i = 0; i < all.length; i++) {
      if (all[i].offsetParent === null) continue;
      rect = all[i].getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) out.push(all[i]);
    }
    return out;
  }

  function moveFocus(direction) {
    var current = document.activeElement;
    var list = visibleFocusables();
    if (!current || list.indexOf(current) < 0) {
      if (list.length) list[0].focus();
      return;
    }

    var r = current.getBoundingClientRect();
    var cx = r.left + r.width / 2;
    var cy = r.top + r.height / 2;
    var best = null;
    var bestScore = 99999999;
    var i, t, tx, ty, dx, dy, primary, cross, score;

    for (i = 0; i < list.length; i++) {
      if (list[i] === current) continue;
      t = list[i].getBoundingClientRect();
      tx = t.left + t.width / 2;
      ty = t.top + t.height / 2;
      dx = tx - cx;
      dy = ty - cy;

      if (direction === 'left' && dx >= -5) continue;
      if (direction === 'right' && dx <= 5) continue;
      if (direction === 'up' && dy >= -5) continue;
      if (direction === 'down' && dy <= 5) continue;

      primary = (direction === 'left' || direction === 'right') ? Math.abs(dx) : Math.abs(dy);
      cross = (direction === 'left' || direction === 'right') ? Math.abs(dy) : Math.abs(dx);
      score = primary + cross * 1.8;
      if (score < bestScore) {
        bestScore = score;
        best = list[i];
      }
    }

    if (best) {
      best.focus();
      if (best.scrollIntoView) best.scrollIntoView(false);
    }
  }

  function handlePlayerKey(code) {
    if (code === 461 || code === 27) {
      closePlayer();
      return true;
    }
    if (code === 415) {
      try { video.play(); } catch (e) {}
      return true;
    }
    if (code === 19) {
      video.pause();
      return true;
    }
    if (code === 37) {
      try { video.currentTime = Math.max(0, video.currentTime - 10); } catch (e) {}
      return true;
    }
    if (code === 39) {
      try { video.currentTime = Math.min(video.duration || 999999, video.currentTime + 10); } catch (e) {}
      return true;
    }
    if (code === 13 && document.activeElement === video) {
      if (video.paused) {
        try { video.play(); } catch (e) {}
      } else video.pause();
      return true;
    }
    return false;
  }

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode;

    if (!playerOverlay.classList.contains('hidden')) {
      if (handlePlayerKey(code)) {
        e.preventDefault();
        return;
      }
    }

    if (!dialog.classList.contains('hidden') && (code === 461 || code === 27)) {
      closeDialog();
      e.preventDefault();
      return;
    }

    if (code === 461 || code === 27) {
      if (state.view !== 'home') navigate('home');
      e.preventDefault();
      return;
    }

    if (document.activeElement && document.activeElement.tagName === 'INPUT') return;

    if (code === 37) { moveFocus('left'); e.preventDefault(); }
    else if (code === 38) { moveFocus('up'); e.preventDefault(); }
    else if (code === 39) { moveFocus('right'); e.preventDefault(); }
    else if (code === 40) { moveFocus('down'); e.preventDefault(); }
    else if (code === 13) {
      if (document.activeElement && document.activeElement.click) {
        document.activeElement.click();
        e.preventDefault();
      }
    }
  });

  function bindNavigation() {
    var navs = document.querySelectorAll('.nav-item');
    var i;
    for (i = 0; i < navs.length; i++) {
      navs[i].onclick = function () {
        navigate(this.getAttribute('data-view'));
      };
    }
  }

  function init() {
    bindNavigation();
    updateProfileBadge();
    setApiBase(currentApiBase());
    navigate('home');
    setTimeout(function () {
      var active = document.querySelector('.nav-item.active');
      if (active) active.focus();
    }, 120);
  }

  init();
})();