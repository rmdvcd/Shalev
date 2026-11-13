(() => {
  const form = document.getElementById('restore-form');
  if (!form) return;
  const status = document.getElementById('restore-status');
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const file = form.elements.backup_file.files[0];
    const password = form.elements.passphrase.value;
    if (!file || password.length < 12) {
      status.textContent = 'Selecciona un archivo y escribe la contraseña de al menos 12 caracteres.';
      return;
    }
    if (!window.confirm('Esto reemplazará la base de datos actual. ¿Continuar?')) return;
    status.textContent = 'Verificando y restaurando; espera a que finalice…';
    try {
      const passwordBytes = new TextEncoder().encode(password);
      let passwordBinary = '';
      passwordBytes.forEach((byte) => { passwordBinary += String.fromCharCode(byte); });
      const passwordBase64 = window.btoa(passwordBinary);
      const response = await fetch('/respaldo/restaurar', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/octet-stream',
          'X-CSRF-Token': form.elements.csrf.value,
          'X-Backup-Passphrase-B64': passwordBase64
        },
        body: file
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.detail || 'No se pudo restaurar el respaldo.');
      status.textContent = result.message;
      form.reset();
      window.setTimeout(() => { window.location.assign('/entrar'); }, 1800);
    } catch (error) {
      status.textContent = error.message || 'Error al leer el respaldo.';
    }
  });
})();
