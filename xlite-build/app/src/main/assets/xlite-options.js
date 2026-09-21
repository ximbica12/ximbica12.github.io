(() => {
  const bridge = window.AndroidConfig;
  let effective = {};
  try { effective = JSON.parse(bridge.getEffectiveConfig() || '{}'); } catch (_) {}

  function valueFor(el) {
    if (el.type === 'checkbox') return !!el.checked;
    return el.value;
  }

  function apply() {
    document.querySelectorAll('[name]').forEach(el => {
      if (!(el.name in effective)) return;
      const v = effective[el.name];
      if (el.type === 'checkbox') el.checked = !!v;
      else el.value = v == null ? '' : String(v);
    });
    const unsafe = document.querySelector('[name="bypassAgeVerification"]');
    if (unsafe) {
      unsafe.checked = false;
      const row = unsafe.closest('section.checkbox') || unsafe.closest('label');
      if (row) row.style.display = 'none';
    }
    document.querySelectorAll('.desktop').forEach(x => x.style.display = 'none');
    const exp = document.getElementById('export-config');
    if (exp) exp.closest('section').style.display = 'none';
    const version = document.getElementById('version');
    if (version) version.textContent = 'Control Panel 4.24.1 • XLite v0.4';
  }

  let timer;
  function flashSaved() {
    let n = document.querySelector('.xlite-saved');
    if (!n) {
      n = document.createElement('div');
      n.className = 'xlite-saved';
      n.textContent = 'Salvo';
      document.body.appendChild(n);
    }
    n.classList.add('show');
    clearTimeout(timer);
    timer=setTimeout(()=>n.classList.remove('show'),700);
  }

  function save() {
    const out = {};
    document.querySelectorAll('[name]').forEach(el => {
      if (!el.name || el.type === 'button') return;
      out[el.name] = valueFor(el);
    });
    out.bypassAgeVerification = false;
    try {
      bridge.saveConfig(JSON.stringify(out));
      effective = Object.assign({}, effective, out);
      flashSaved();
    } catch (_) {}
  }

  document.addEventListener('change', save);
  document.addEventListener('input', e => {
    if (e.target && e.target.tagName === 'TEXTAREA') save();
  });

  apply();
})();