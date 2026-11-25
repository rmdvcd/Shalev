"""Lista de precios y tarjetas promocionales de DShalev (salidas para clientes).

Android tiene cuatro salidas del botón Exportar de Inventario (`FormatoSalida` en
`Android/app/src/main/kotlin/cu/shalev/app/inventario/InventarioLogic.kt:85`): PDF, Excel, **Imagen** (lista de precios)
y **Tarjetas promocionales** (foto, nombre y precio para redes y mensajería). El escritorio solo tenía XLSX y CSV.

Aquí se cubren las dos salidas «para clientes»:

* **Lista de precios**, igual que `TablasExport.listaPrecios`
  (`Android/domain/src/main/kotlin/cu/shalev/domain/service/TablasExport.kt:299`): columnas Categoría / Producto /
  Precio, ordenada por categoría y nombre sin distinguir mayúsculas. **Nunca** costo ni existencias: se comparte fuera
  del negocio.
* **Tarjetas promocionales**, con la distribución de `DisenoTarjetas`
  (`Android/app/src/main/kotlin/cu/shalev/app/inventario/Tarjetas.kt:30`): una tarjeta grande si hay un solo
  artículo, rejilla de dos columnas y hasta 6 por página.

Lo que en Android es un PNG rasterizado aquí es **HTML listo para imprimir o guardar como PDF** desde el navegador:
el escritorio ya sirve HTML y no añade dependencias de imagen. El contenido es el mismo y el pie lo dice.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable, Sequence

#: Máximo de tarjetas por página (Android: `DisenoTarjetas.POR_IMAGEN = 6`).
TARJETAS_POR_PAGINA = 6
#: Largo máximo del comentario opcional que encabeza las tarjetas (Android: `take(160)`).
COMENTARIO_MAX = 160

#: Categorías cuyo artículo es un servicio: no tienen existencias que mostrar.
CATEGORIA_SERVICIO = "Servicio"


@dataclass(frozen=True)
class FilaPrecio:
    categoria: str
    nombre: str
    precio_cents: int


@dataclass(frozen=True)
class TarjetaArticulo:
    nombre: str
    categoria: str
    precio_cents: int
    unidad: str


def clave_orden(texto: str) -> str:
    """Clave de orden sin distinguir mayúsculas, como `String.CASE_INSENSITIVE_ORDER` de Android.

    Se usa `casefold()` (no `lower()`) para que «Ávila» y «ávila» queden juntos también en español.
    """
    return (texto or "").casefold()


def lista_precios(items: Iterable[dict[str, Any]]) -> list[FilaPrecio]:
    """Filas de la lista de precios para clientes.

    - Descarta los artículos que no están activos o están marcados como servicio sin precio útil.
    - Ordena por categoría y después por nombre, sin distinguir mayúsculas (misma regla que Android).
    - **No** incluye costo ni existencias, aunque el registro los tenga.
    """
    filas: list[FilaPrecio] = []
    for item in items:
        nombre = str(item.get("name") or "").strip()
        if not nombre:
            continue
        filas.append(FilaPrecio(
            categoria=str(item.get("category") or "").strip(),
            nombre=nombre,
            precio_cents=max(0, int(item.get("sale_price_cents") or 0)),
        ))
    filas.sort(key=lambda f: (clave_orden(f.categoria), clave_orden(f.nombre)))
    return filas


def tarjetas(items: Iterable[dict[str, Any]], comentario: str = "") -> list[list[TarjetaArticulo]]:
    """Tarjetas agrupadas de 6 en 6, en el orden recibido (el de la pantalla de Inventario).

    Android no reordena para las tarjetas: respeta el orden del catálogo que se está viendo. El comentario se recorta
    a 160 caracteres porque encabeza todas las páginas.
    """
    tarjetas_: list[TarjetaArticulo] = []
    for item in items:
        nombre = str(item.get("name") or "").strip()
        if not nombre:
            continue
        tarjetas_.append(TarjetaArticulo(
            nombre=nombre,
            categoria=str(item.get("category") or "").strip(),
            precio_cents=max(0, int(item.get("sale_price_cents") or 0)),
            unidad=str(item.get("unit") or "").strip(),
        ))
    paginas = [tarjetas_[i:i + TARJETAS_POR_PAGINA] for i in range(0, len(tarjetas_), TARJETAS_POR_PAGINA)]
    return paginas


def comentario_limpio(texto: str | None) -> str:
    """Una sola línea, sin controles y de hasta 160 caracteres."""
    limpio = " ".join((texto or "").split())
    return limpio[:COMENTARIO_MAX]


def texto_seleccion(crudo: str | None, disponibles: Sequence[str]) -> list[str]:
    """Nombres marcados en el formulario, conservando el orden del catálogo y sin duplicados.

    Solo se aceptan valores que existan en el catálogo: un identificador inventado no debe colarse en la salida.
    """
    pedidos = {parte.strip() for parte in (crudo or "").split("|") if parte.strip()}
    vistos: list[str] = []
    for nombre in disponibles:
        if nombre in pedidos and nombre not in vistos:
            vistos.append(nombre)
    return vistos
