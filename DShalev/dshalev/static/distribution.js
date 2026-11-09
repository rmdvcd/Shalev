(() => {
  async function sendAsset(event, { endpoint, statusId, contentType, headersFor }) {
    event.preventDefault();
    const form = event.currentTarget;
    const status = document.getElementById(statusId);
    const file = form.elements.file.files[0];
    if (!file) return;
    status.textContent = 'Subiendo archivo de forma segura…';
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': contentType, ...headersFor(form) },
        body: file
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.detail || 'No se pudo publicar el archivo.');
      status.textContent = result.message;
      window.setTimeout(() => window.location.reload(), 900);
    } catch (error) {
      status.textContent = error.message || 'No se pudo leer el archivo.';
    }
  }
  const photo = document.getElementById('photo-form');
  photo?.addEventListener('submit', (event) => sendAsset(event, {
    endpoint: '/distribucion/foto', statusId: 'photo-status', contentType: 'image/jpeg',
    headersFor: (form) => ({
      'X-CSRF-Token': form.elements.csrf.value,
      'X-Entity': form.elements.entity.value,
      'X-Entity-Id': form.elements.entity_id.value
    })
  }));
  const apk = document.getElementById('apk-form');
  apk?.addEventListener('submit', (event) => sendAsset(event, {
    endpoint: '/distribucion/apk', statusId: 'apk-status', contentType: 'application/vnd.android.package-archive',
    headersFor: (form) => ({
      'X-CSRF-Token': form.elements.csrf.value,
      'X-Version-Code': form.elements.version_code.value
    })
  }));
})();
