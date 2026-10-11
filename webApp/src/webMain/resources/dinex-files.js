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
