// Feedback de envío para los formularios clásicos (POST con recarga de página): al enviar se deshabilita
// el botón, se muestra progreso y el formulario queda aria-busy. Evita el doble envío por doble clic.
// Los formularios con gestión propia (POS, respaldo y distribución, que usan fetch o descargan archivos)
// llevan data-manual y quedan fuera. La restauración con "pageshow" cubre la vuelta atrás desde la caché.
(() => {
  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (form.dataset.manual !== undefined || (form.method || '').toLowerCase() !== 'post') return;
    if (!form.checkValidity()) return; // la validación nativa ya muestra su aviso junto al campo
    const boton = event.submitter && event.submitter.form === form ? event.submitter : form.querySelector('[type=submit]');
    form.setAttribute('aria-busy', 'true');
    if (!boton || boton.disabled) return;
    boton.dataset.etiqueta = boton.textContent;
    // Después de que el navegador recoja los datos del envío: así el nombre/valor del botón no se pierde.
    setTimeout(() => {
      boton.disabled = true;
      boton.textContent = 'Un momento…';
    }, 0);
  }, true);

  window.addEventListener('pageshow', () => {
    for (const form of document.querySelectorAll('form[aria-busy]')) {
      form.removeAttribute('aria-busy');
      const boton = form.querySelector('[type=submit]');
      if (boton && boton.dataset.etiqueta) {
        boton.disabled = false;
        boton.textContent = boton.dataset.etiqueta;
      }
    }
  });
})();
