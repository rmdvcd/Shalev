(() => {
  const input = document.querySelector('#perfil-logo-file');
  const preview = document.querySelector('#perfil-logo-preview');
  const empty = document.querySelector('#perfil-logo-empty');
  const remove = document.querySelector('#perfil-logo-remove');
  const status = document.querySelector('#perfil-logo-status');
  const csrf = document.querySelector('#perfil-logo-csrf')?.value || '';
  if (!input || !preview || !status) return;

  const mostrar = (image) => {
    preview.src = image;
    preview.hidden = false;
    empty.hidden = true;
    remove.hidden = false;
  };

  input.addEventListener('change', async () => {
    const file = input.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/') || file.size > 12 * 1024 * 1024) {
      status.textContent = 'Elige una imagen válida de hasta 12 MiB.';
      input.value = '';
      return;
    }
    status.textContent = 'Preparando imagen…';
    const objectUrl = URL.createObjectURL(file);
    try {
      const image = new Image();
      image.src = objectUrl;
      await image.decode();
      const scale = Math.min(1, 512 / Math.max(image.naturalWidth, image.naturalHeight));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
      canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
      const ctx = canvas.getContext('2d', {alpha: false});
      if (!ctx) throw new Error('No se pudo procesar la imagen.');
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      const dataUrl = canvas.toDataURL('image/png');
      if (dataUrl.length > 2.8 * 1024 * 1024) throw new Error('La imagen optimizada supera 2 MiB.');
      const response = await fetch('/perfil/logo', {
        method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({csrf, image: dataUrl}),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.detail || 'No se pudo guardar la imagen.');
      mostrar(result.image);
      status.textContent = 'Imagen guardada en la base cifrada.';
    } catch (error) {
      status.textContent = error instanceof Error ? error.message : 'No se pudo procesar la imagen.';
    } finally {
      URL.revokeObjectURL(objectUrl);
      input.value = '';
    }
  });

  remove?.addEventListener('click', async () => {
    status.textContent = 'Quitando imagen…';
    try {
      const response = await fetch('/perfil/logo/eliminar', {
        method: 'POST', credentials: 'same-origin', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({csrf}),
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.detail || 'No se pudo quitar la imagen.');
      preview.removeAttribute('src');
      preview.hidden = true;
      empty.hidden = false;
      remove.hidden = true;
      status.textContent = 'Imagen eliminada.';
    } catch (error) {
      status.textContent = error instanceof Error ? error.message : 'No se pudo quitar la imagen.';
    }
  });
})();
