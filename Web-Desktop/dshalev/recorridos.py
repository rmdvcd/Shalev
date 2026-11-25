"""Recorridos guiados de DShalev.

Android tiene **51 recorridos** con 127 objetivos (`Android/app/src/main/kotlin/cu/shalev/app/recorrido/Recorridos.kt`)
que resaltan un elemento de la pantalla en cada paso y se guardan con la clave `<clave>_tour_shown` para no repetirse
(`OnboardingPreferences.kt:65`). El escritorio no tenía ninguno.

Aquí se recogen los **seis recorridos de las pantallas principales** que existen en el escritorio, con los textos de
Android y adaptados a lo que hay en cada página (el escritorio no tiene «fondo de caja» ni «vender» dentro de Inicio).
La mecánica es la misma:

* Cada paso apunta a un elemento de la página por su `id`.
* Los pasos cuyo objetivo no está en la página **se saltan**, igual que en Android («Los pasos cuyo objetivo no esté al
  empezar se saltan»).
* Al terminar o cerrar, la clave `<clave>_tour_shown` queda guardada en `app_meta` y el recorrido no vuelve a salir
  solo; siempre se puede repetir desde «Ayuda».
* El avance es del lado del cliente con `static/recorrido.js`, sin telemetría: el estado solo vive en este equipo.

Los textos respetan el límite de Android: título de hasta 28 caracteres y una frase de hasta 120 que empieza por la
acción.
"""

from __future__ import annotations

from dataclasses import dataclass

#: Prefijo de la clave de estado (Android: `<clave>_tour_shown`).
SUFIJO_VISTO = "_tour_shown"

#: Límites de texto de Android (documentados en `Recorrido`).
TITULO_MAX = 28
FRASE_MAX = 120


@dataclass(frozen=True)
class Paso:
    objetivo: str
    titulo: str
    frase: str

    def __post_init__(self) -> None:
        # Se comprueba al construir para que un texto largo falle en las pruebas y no en la pantalla.
        if len(self.titulo) > TITULO_MAX:
            raise ValueError(f"Título demasiado largo ({len(self.titulo)}): {self.titulo!r}")
        if len(self.frase) > FRASE_MAX:
            raise ValueError(f"Frase demasiado larga ({len(self.frase)}): {self.frase!r}")


@dataclass(frozen=True)
class Recorrido:
    clave: str
    titulo: str
    pasos: tuple[Paso, ...]

    @property
    def clave_estado(self) -> str:
        return self.clave + SUFIJO_VISTO


#: Recorridos del escritorio. Textos tomados de Android (`Recorrido`) donde la pantalla coincide.
RECORRIDOS: tuple[Recorrido, ...] = (
    Recorrido("inicio", "Inicio", (
        Paso("inicio-resumen", "Resumen",
             "Elige el período para ver ventas, ganancia y lo más vendido."),
        Paso("inicio-ventas", "Ventas por día",
             "Las barras muestran cuánto se vendió cada día del período elegido."),
        Paso("inicio-accesos", "Accesos",
             "Desde aquí abres el inventario, las ventas o los reportes en un toque."),
    )),
    Recorrido("inventario", "Inventario", (
        Paso("inventario-lista", "Tus productos",
             "Toca un artículo para ver su ficha y editarlo."),
        Paso("inventario-buscar", "Buscar",
             "Escribe parte del nombre para encontrar un artículo."),
        Paso("inventario-exportar", "Exportar",
             "Toca para enviar la lista en Excel, CSV o como material para clientes."),
        Paso("inventario-agregar", "Agregar",
             "Toca Agregar para crear un producto o un insumo."),
    )),
    Recorrido("servicios", "Servicios", (
        Paso("servicios-lista", "Tus servicios",
             "Toca un servicio para ver su ficha y editarlo."),
        Paso("servicios-buscar", "Buscar",
             "Escribe parte del nombre para encontrar un servicio."),
        Paso("servicios-agregar", "Agregar",
             "Toca Agregar para crear un servicio con su precio."),
    )),
    Recorrido("ventas", "Punto de venta", (
        Paso("ventas-agregar", "Agregar",
             "Busca un artículo y tócalo para sumarlo a la venta."),
        Paso("ventas-total", "Total",
             "Revisa aquí el importe que vas a cobrar."),
        Paso("ventas-metodo", "Forma de pago",
             "Toca Efectivo o Transferencia antes de cobrar."),
        Paso("ventas-cobrar", "Cobrar",
             "Toca Cobrar: en efectivo ves el comprobante; con transferencia, el QR."),
    )),
    Recorrido("reportes", "Reportes", (
        Paso("reportes-periodo", "Período",
             "Elige las fechas para acotar lo que quieres ver y exportar."),
        Paso("reportes-exportar", "Exportar",
             "Toca para enviar o guardar la tabla en Excel o CSV."),
    )),
    Recorrido("herramientas", "Herramientas", (
        Paso("herramientas-modulos", "Módulos",
             "Elige si el negocio vende productos, controla inventario o vende servicios."),
        Paso("herramientas-precios", "Preajustes",
             "Ajusta precios por método de pago, volumen o tipo de cliente."),
        Paso("herramientas-fichas", "Fichas de costo",
             "Calcula el costo de un producto a partir de sus partidas y el margen."),
    )),
)

#: Índice por clave para las rutas.
POR_CLAVE = {recorrido.clave: recorrido for recorrido in RECORRIDOS}


def clave_estado(clave: str) -> str:
    """Nombre del indicador en `app_meta` (Android: `booleanPreferencesKey("${recorrido.clave}_tour_shown")`)."""
    if clave not in POR_CLAVE:
        raise ValueError(f"Recorrido desconocido: {clave!r}")
    return POR_CLAVE[clave].clave_estado


def pasos_disponibles(clave: str, objetivos_presentes: set[str]) -> list[Paso]:
    """Pasos cuyo objetivo existe en la página. Android salta los demás en vez de mostrar un hueco."""
    recorrido = POR_CLAVE.get(clave)
    if recorrido is None:
        return []
    return [paso for paso in recorrido.pasos if paso.objetivo in objetivos_presentes]


def a_diccionario(clave: str) -> dict[str, object]:
    """Estructura que consume `static/recorrido.js`."""
    recorrido = POR_CLAVE[clave]
    return {
        "clave": recorrido.clave,
        "claveEstado": recorrido.clave_estado,
        "titulo": recorrido.titulo,
        "pasos": [{"objetivo": p.objetivo, "titulo": p.titulo, "frase": p.frase} for p in recorrido.pasos],
    }
