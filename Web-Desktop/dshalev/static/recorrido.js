// Recorrido guiado de las pantallas principales de DShalev. Equivale al coach-mark de Android
// (CoachMarkOverlay): resalta el elemento de cada paso, explica qué hace y deja pasar al usuario.
//
// Reglas:
//  - Los pasos cuyo objetivo no está en la página se saltan (igual que en Android).
//  - Al terminar o cerrar, se marca <clave>_tour_shown en el servidor para no repetirlo solo.
//  - Un paso que ya no se ve al llegar (por ejemplo, porque el usuario navegó) se salta al avanzar.
//  - Escape o «Saltar» cierran. Sin telemetría: el estado vive en este equipo.
(() => {
  const raiz = document.querySelector('[data-recorrido]');
  if (!raiz) return;
  let datos;
  try {
    datos = JSON.parse(raiz.dataset.recorrido);
  } catch (error) {
    return; // un JSON roto no debe romper el resto de la página
  }
  if (!datos || !Array.isArray(datos.pasos) || datos.pasos.length === 0) return;

  const focoPrevio = document.activeElement;
  let indice = 0;
  let terminado = false;

  const capa = document.createElement('div');
  capa.className = 'recorrido-capa';
  capa.setAttribute('role', 'dialog');
  capa.setAttribute('aria-modal', 'false');
  capa.setAttribute('aria-label', `Recorrido: ${datos.titulo}`);

  const tarjeta = document.createElement('div');
  tarjeta.className = 'recorrido-tarjeta';
  capa.appendChild(tarjeta);

  const previo = document.createElement('p');
  previo.className = 'recorrido-paso';
  const titulo = document.createElement('h2');
  titulo.className = 'recorrido-titulo';
  const frase = document.createElement('p');
  frase.className = 'recorrido-frase';
  const acciones = document.createElement('div');
  acciones.className = 'recorrido-acciones';
  const saltar = document.createElement('button');
  saltar.type = 'button';
  saltar.className = 'quiet-button';
  saltar.textContent = 'Saltar';
  const siguiente = document.createElement('button');
  siguiente.type = 'button';
  siguiente.className = 'primary-button';
  acciones.append(saltar, siguiente);
  tarjeta.append(previo, titulo, frase, acciones);

  function objetivoActual() {
    const paso = datos.pasos[indice];
    if (!paso) return null;
    const elemento = document.getElementById(paso.objetivo);
    if (!elemento) return null;
    const caja = elemento.getBoundingClientRect();
    if (caja.width === 0 && caja.height === 0) return null; // oculto por la maquetación
    return { paso, elemento, caja };
  }

  function limpiarResalte() {
    for (const el of document.querySelectorAll('.recorrido-resaltado')) {
      el.classList.remove('recorrido-resaltado');
      el.removeAttribute('data-recorrido-pulso');
    }
  }

  function colocar() {
    const actual = objetivoActual();
    limpiarResalte();
    if (!actual) return false;
    actual.elemento.classList.add('recorrido-resaltado');
    const margen = 12;
    const alto = window.innerHeight;
    const ancho = window.innerWidth;
    // Debajo del elemento si cabe; si no, encima; siempre dentro de la ventana.
    const arriba = actual.caja.bottom + margen + 190 < alto ? actual.caja.bottom + margen : Math.max(margen, actual.caja.top - 200);
    const izquierda = Math.min(Math.max(margen, actual.caja.left), Math.max(margen, ancho - 340));
    // `position: fixed`: las coordenadas del rectángulo ya son de la ventana, sin sumar el desplazamiento.
    tarjeta.style.position = 'fixed';
    tarjeta.style.top = `${Math.min(arriba, alto - 200)}px`;
    tarjeta.style.left = `${izquierda}px`;
    actual.elemento.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    return true;
  }

  function pintar() {
    let intentos = 0;
    while (!colocar() && intentos < datos.pasos.length) {
      indice += 1;
      intentos += 1;
    }
    if (indice >= datos.pasos.length) {
      cerrar(true);
      return;
    }
    const paso = datos.pasos[indice];
    previo.textContent = `Paso ${indice + 1} de ${datos.pasos.length}`;
    titulo.textContent = paso.titulo;
    frase.textContent = paso.frase;
    siguiente.textContent = indice === datos.pasos.length - 1 ? 'Listo' : 'Siguiente';
    siguiente.focus({ preventScroll: true });
  }

  function marcarVisto() {
    if (!datos.claveEstado) return;
    // Se envía el estado al servidor; si falla, el recorrido no se rompe (solo volverá a ofrecerse).
    const cuerpo = new URLSearchParams();
    cuerpo.set('csrf', raiz.dataset.csrf || '');
    cuerpo.set('clave', datos.claveEstado);
    fetch('/ayuda/recorrido-visto', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: cuerpo.toString(),
      credentials: 'same-origin',
    }).catch(() => {});
  }

  function cerrar(visto) {
    if (terminado) return;
    terminado = true;
    limpiarResalte();
    capa.remove();
    document.removeEventListener('keydown', alTeclear);
    window.removeEventListener('resize', colocar);
    window.removeEventListener('scroll', colocar, true);
    if (focoPrevio && typeof focoPrevio.focus === 'function') focoPrevio.focus({ preventScroll: true });
    if (visto) marcarVisto();
  }

  function alTeclear(evento) {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      cerrar(false);
    } else if (evento.key === 'Tab') {
      // El foco no debe irse de la tarjeta: es un diálogo.
      const enfocables = [saltar, siguiente];
      const posicion = enfocables.indexOf(document.activeElement);
      evento.preventDefault();
      enfocables[(posicion + (evento.shiftKey ? enfocables.length - 1 : 1)) % enfocables.length].focus();
    }
  }

  saltar.addEventListener('click', () => cerrar(false));
  siguiente.addEventListener('click', () => {
    indice += 1;
    if (indice >= datos.pasos.length) {
      cerrar(true);
      return;
    }
    pintar();
  });
  document.addEventListener('keydown', alTeclear);
  window.addEventListener('resize', colocar);
  window.addEventListener('scroll', colocar, true);

  document.body.appendChild(capa);
  pintar();
})();
