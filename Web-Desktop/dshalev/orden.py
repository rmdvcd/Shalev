"""Modos de ordenación de listas, con la misma regla que Android (`ModoOrden`) e iOS (`ModoOrden`).

Cuatro modos ciclables en el mismo orden que el teléfono: recientes → A–Z → mayor importe → menor importe → recientes.
El orden es **estable**: con nombres o importes iguales se conserva el orden de entrada (el que da la consulta), tal
como hace `sortedWith`/`sortedBy` en Android al no haber desempate explícito y como documenta `ModoOrden.aplicar` en
iOS.

En el escritorio el modo vive en la URL (`?orden=alfabetico`), así que la lista se puede marcar, recargar y compartir
sin estado de servidor, y el botón anuncia siempre el modo activo en texto (nunca solo un icono).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Iterable

# El orden de la tupla es el del ciclo: cada pulsación pasa al siguiente.
MODOS: tuple[str, ...] = ("recientes", "alfabetico", "mayor", "menor")

ETIQUETAS: dict[str, str] = {
    "recientes": "Recientes",
    "alfabetico": "A–Z",
    "mayor": "Mayor importe",
    "menor": "Menor importe",
}


@dataclass(frozen=True)
class ModoOrden:
    """Modo activo y su siguiente valor, para pintar el botón que cicla."""

    clave: str

    @staticmethod
    def normalizar(valor: str | None) -> str:
        """Un valor desconocido (o vacío) vuelve al modo por defecto: recientes."""
        return valor if valor in MODOS else MODOS[0]

    @property
    def siguiente(self) -> str:
        return MODOS[(MODOS.index(self.clave) + 1) % len(MODOS)]

    @property
    def etiqueta(self) -> str:
        return ETIQUETAS[self.clave]

    @property
    def etiqueta_siguiente(self) -> str:
        return ETIQUETAS[self.siguiente]

    def aplicar(
        self,
        filas: Iterable[dict[str, Any]],
        *,
        nombre: Callable[[dict[str, Any]], str],
        importe: Callable[[dict[str, Any]], int],
    ) -> list[dict[str, Any]]:
        """Ordena sin mutar la entrada. `importe` va en centavos, como en el resto de la aplicación."""
        lista = list(filas)
        if self.clave == "recientes":
            return lista
        if self.clave == "alfabetico":
            # `casefold` como Android (`CASE_INSENSITIVE_ORDER`) y la lista de precios de este mismo paquete.
            return sorted(lista, key=lambda fila: nombre(fila).casefold())
        if self.clave == "mayor":
            return sorted(lista, key=lambda fila: -int(importe(fila)))
        return sorted(lista, key=lambda fila: int(importe(fila)))


def modo_de(valor: str | None) -> ModoOrden:
    return ModoOrden(ModoOrden.normalizar(valor))


def enlace(base: str, parametros: dict[str, str]) -> str:
    """URL con los parámetros que no están vacíos, para no ensuciar la barra de direcciones."""
    limpios = {clave: valor for clave, valor in parametros.items() if valor}
    if not limpios:
        return base
    from urllib.parse import urlencode

    return f"{base}?{urlencode(limpios)}"
