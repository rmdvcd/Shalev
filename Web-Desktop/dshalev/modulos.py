"""Módulos del negocio en DShalev (para qué usa Shalev).

Misma regla que `Modulo` de Android (`Android/domain/src/main/kotlin/cu/shalev/domain/model/Modulo.kt`) y que las
variables `shalev.module.*` de iOS:

* **VENTAS** — vender productos del inventario. Necesita el inventario, así que lo activa también.
* **INVENTARIO** — controlar existencias (sin vender, o vendiendo solo servicios con insumos).
* **SERVICIOS** — vender servicios.

Reglas no negociables (las tres plataformas hacen lo mismo):

1. Al menos uno activo. Vacío = todos, nunca se deja la aplicación sin secciones.
2. Marcar VENTAS marca INVENTARIO.
3. Desmarcar INVENTARIO desmarca VENTAS.

Lo no elegido **no se borra**: solo deja de ofrecerse en la navegación; volver a marcarlo devuelve sus datos.
"""

from __future__ import annotations

from dataclasses import dataclass

#: Clave de `app_meta` donde se guarda la selección (lista separada por comas).
CLAVE = "modulos"


@dataclass(frozen=True)
class Modulo:
    clave: str
    etiqueta: str
    detalle: str

    @property
    def marcado_por(self) -> tuple[str, ...]:
        """Módulos que este arrastra al marcarse (Android: `alternar` con `m == VENTAS`)."""
        return ("INVENTARIO",) if self.clave == "VENTAS" else ()


#: Los tres módulos, en el orden en que se muestran (el mismo que Android).
MODULOS: tuple[Modulo, ...] = (
    Modulo("VENTAS", "Ventas", "Vender productos del inventario"),
    Modulo("INVENTARIO", "Inventario", "Controlar existencias de productos e insumos"),
    Modulo("SERVICIOS", "Servicios", "Vender servicios"),
)

CLAVES = frozenset(m.clave for m in MODULOS)
TODOS = CLAVES


def normalizar(seleccion: set[str] | None) -> set[str]:
    """Aplica las tres reglas a una selección cualquiera (también a la que llega de un respaldo antiguo)."""
    if not seleccion:
        return set(TODOS)  # vacío = todos: nunca se deja la app sin secciones
    validos = {s for s in seleccion if s in CLAVES}
    if not validos:
        return set(TODOS)
    if "VENTAS" in validos:
        validos.add("INVENTARIO")
    return validos


def alternar(actual: set[str], clave: str) -> set[str]:
    """Marca o desmarca un módulo. Puede devolver vacío: quien lo use decide si lo permite (el perfil no deja
    desmarcar el último)."""
    if clave not in CLAVES:
        raise ValueError("Módulo desconocido.")
    if clave in actual:
        resultado = set(actual) - {clave}
        if clave == "INVENTARIO":
            resultado -= {"VENTAS"}
        return resultado
    if clave == "VENTAS":
        return set(actual) | {"VENTAS", "INVENTARIO"}
    return set(actual) | {clave}


def de_texto(crudo: str | None) -> set[str]:
    """Lee lo guardado («VENTAS,SERVICIOS») o lo que llega de un formulario.

    Sin valor guardado (instalación nueva o respaldo antiguo) se devuelven **todos** los módulos: es la misma regla
    que `normalizar(set())` y evita que una instalación recién creada arranque sin secciones.
    """
    if crudo is None or not crudo.strip():
        return set(TODOS)
    return normalizar({parte.strip().upper() for parte in crudo.replace("|", ",").split(",") if parte.strip()})


def a_texto(seleccion: set[str]) -> str:
    """Guarda en el orden de `MODULOS` para que el valor sea estable entre escrituras."""
    return ",".join(m.clave for m in MODULOS if m.clave in seleccion)


def permite(seleccion: set[str], clave: str) -> bool:
    """¿La navegación puede ofrecer esta sección con los módulos elegidos?"""
    return clave in normalizar(seleccion)


def secciones_visibles(seleccion: set[str]) -> dict[str, bool]:
    """Qué bloques de la navegación se muestran. `inventario` lo ven Ventas e Inventario (Android: la pestaña
    Inventario aparece si cualquiera de los dos está activo); `servicios`, solo si SERVICIOS está activo."""
    activos = normalizar(seleccion)
    return {
        "inventario": bool(activos & {"VENTAS", "INVENTARIO"}),
        "servicios": "SERVICIOS" in activos,
        # Las ventas se registran desde el punto de venta, que solo tiene sentido con VENTAS activo.
        "punto_de_venta": "VENTAS" in activos,
    }
