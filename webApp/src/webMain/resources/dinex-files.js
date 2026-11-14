/* Local file operations only. Files never leave the browser. */
globalThis.dinexFiles = {
  pick: function (onPicked, onError) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.xlsx,.csv,.tsv,.txt,.xls';
    input.style.display = 'none';
    document.body.appendChild(input);
    let finished = false;
    function cleanup() { window.removeEventListener('focus', focus); input.remove(); }
    function finish(name, content) { if (finished) return; finished = true; cleanup(); onPicked(name, content); }
    function fail(message) { if (finished) return; finished = true; cleanup(); onError(message); }
    function focus() { setTimeout(() => { if (!finished && (!input.files || !input.files.length)) finish('', ''); }, 1000); }
    input.addEventListener('cancel', () => finish('', ''));
    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      if (!file) return finish('', '');
      if (file.size > 20 * 1024 * 1024) return fail('El archivo supera 20 MB.');
      const reader = new FileReader();
      reader.onerror = () => fail('No pudimos leer el archivo seleccionado.');
      reader.onload = () => finish(file.name, String(reader.result).split(',')[1] || '');
      reader.readAsDataURL(file);
    });
    window.addEventListener('focus', focus);
    input.click();
  },
  save: function (name, mime, base64) {
    const raw = atob(base64);
    const bytes = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
    const url = URL.createObjectURL(new Blob([bytes], { type: mime }));
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }
};

/* Read-only GET for public market prices (CoinGecko). Sends no user data. */
globalThis.dinexHttp = {
  get: function (url, onText, onError) {
    fetch(url, { headers: { Accept: 'application/json' } })
      .then(function (response) {
        if (!response.ok) throw new Error('El mercado respondió ' + response.status);
        return response.text();
      })
      .then(onText, function (error) { onError(String((error && error.message) || error)); });
  },
  // Firebase Authentication, Firestore y Gemini: solo HTTPS, sin cookies ni caché.
  request: function (method, url, body, bearer, onDone, onError) {
    if (String(url).indexOf('https://') !== 0) { onError('Solo se permiten conexiones seguras'); return; }
    var headers = { Accept: 'application/json' };
    // "x-goog-api-key:CLAVE" envía la clave de Gemini en su encabezado; lo demás es un token de Firebase.
    if (bearer && String(bearer).indexOf('x-goog-api-key:') === 0) headers['x-goog-api-key'] = String(bearer).slice(15);
    else if (bearer) headers.Authorization = 'Bearer ' + bearer;
    if (body != null) headers['Content-Type'] = 'application/json; charset=utf-8';
    fetch(url, { method: method, headers: headers, body: body == null ? undefined : body, credentials: 'omit', cache: 'no-store' })
      .then(function (response) {
        return response.text().then(function (text) { onDone(response.status, text); });
      })
      .catch(function (error) { onError(String((error && error.message) || error)); });
  }
};

/* Google OAuth sends only its short-lived access token to Firebase Authentication. */
globalThis.dinexGoogle = {
  request: function (clientId, onToken, onError) {
    if (!globalThis.google || !google.accounts || !google.accounts.oauth2) {
      onError('GOOGLE_NOT_READY');
      return;
    }
    try {
      const client = google.accounts.oauth2.initTokenClient({
        client_id: clientId,
        scope: 'openid email profile',
        callback: function (response) {
          if (response && response.access_token) onToken(response.access_token);
          else onError((response && response.error) || 'GOOGLE_CREDENTIAL_INVALID');
        },
        error_callback: function (error) {
          onError(error && error.type === 'popup_closed' ? 'CANCELLED' : 'GOOGLE_POPUP_ERROR');
        }
      });
      client.requestAccessToken({ prompt: 'select_account' });
    } catch (_) {
      onError('GOOGLE_POPUP_ERROR');
    }
  }
};
