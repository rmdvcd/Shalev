"""Recorridos guiados de DShalev.

Android tiene **51 recorridos** con 127 objetivos (`Android/app/src/main/kotlin/cu/shalev/app/recorrido/Recorridos.kt`)
que resaltan un elemento de la pantalla en cada paso y se guardan con la clave `<clave>_tour_shown` para no repetirse
(`OnboardingPreferences.kt:65`). El escritorio no tenía ninguno.

Aquí se recogen los **once recorridos que existen en el escritorio** —las seis pantallas principales y las cinco
pantallas de trabajo que Android también guía (turnos, clientes, módulos, respaldo y licencia)—, con los textos de
Android y adaptados a lo que hay en cada página. Se mantiene la decisión de no copiar los 51 de Android: solo se
guían las pantallas que el escritorio tiene de verdad.

Los textos hablan como se usa un navegador: se **pulsa** con el ratón o se escribe, no se «toca» (eso es del teléfono).
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
    #: Frase de una línea para el índice de Ayuda («lista, búsqueda y alta»). No es un paso del recorrido.
    resumen: str = ""

    @property
    def clave_estado(self) -> str:
        return self.clave + SUFIJO_VISTO


#: Recorridos del escritorio. Textos tomados de Android (`Recorrido`) donde la pantalla coincide.
RECORRIDOS: tuple[Recorrido, ...] = (
    Recorrido("inicio", "Inicio", (
        Paso("inicio-resumen", "Resumen",
             "Aquí ves las ventas y la ganancia de los últimos siete días."),
        Paso("inicio-ganancia", "Ganancia bruta",
             "Barra azul: venta del día. Barra clara: su costo. La diferencia es la ganancia."),
        Paso("inicio-inventario", "Tu inventario",
             "Reparto de las existencias por categoría, valorado al costo actual."),
        Paso("inicio-ventas", "Ventas por día",
             "Las barras muestran cuánto se vendió cada día del período elegido."),
        Paso("inicio-accesos", "Accesos",
             "Desde aquí abres el inventario, las ventas o los reportes en un toque."),
    ), resumen="resumen, ventas por día y accesos"),
    Recorrido("inventario", "Inventario", (
        Paso("inventario-lista", "Tus productos",
             "Pulsa un artículo para ver su ficha y editarlo."),
        Paso("inventario-buscar", "Buscar",
             "Escribe parte del nombre para encontrar un artículo."),
        Paso("inventario-exportar", "Exportar",
             "Pulsa para enviar la lista en Excel, CSV o como material para clientes."),
        Paso("inventario-agregar", "Agregar",
             "Pulsa Agregar para crear un producto o un insumo."),
    ), resumen="lista, búsqueda, exportar y agregar"),
    Recorrido("servicios", "Servicios", (
        Paso("servicios-lista", "Tus servicios",
             "Pulsa un servicio para ver su ficha y editarlo."),
        Paso("servicios-buscar", "Buscar",
             "Escribe parte del nombre para encontrar un servicio."),
        Paso("servicios-agregar", "Agregar",
             "Pulsa Agregar para crear un servicio con su precio."),
    ), resumen="lista, búsqueda y alta de servicios"),
    Recorrido("ventas", "Punto de venta", (
        Paso("ventas-agregar", "Agregar",
             "Busca un artículo y tócalo para sumarlo a la venta."),
        Paso("ventas-total", "Total",
             "Revisa aquí el importe que vas a cobrar."),
        Paso("ventas-metodo", "Forma de pago",
             "Pulsa Efectivo o Transferencia antes de cobrar."),
        Paso("ventas-cobrar", "Cobrar",
             "Pulsa Cobrar: en efectivo ves el comprobante; con transferencia, el QR."),
    ), resumen="agregar, total, forma de pago y cobrar"),
    Recorrido("reportes", "Reportes", (
        Paso("reportes-periodo", "Período",
             "Elige las fechas para acotar lo que quieres ver y exportar."),
        Paso("reportes-exportar", "Exportar",
             "Pulsa para enviar o guardar la tabla en Excel o CSV."),
    ), resumen="período y exportación"),
    Recorrido("herramientas", "Herramientas", (
        Paso("herramientas-modulos", "Módulos",
             "Elige si el negocio vende productos, controla inventario o vende servicios."),
        Paso("herramientas-precios", "Preajustes",
             "Ajusta precios por método de pago, volumen o tipo de cliente."),
        Paso("herramientas-fichas", "Fichas de costo",
             "Calcula el costo de un producto a partir de sus partidas y el margen."),
    ), resumen="módulos, preajustes y fichas de costo"),
    Recorrido("turnos", "Turnos", (
        Paso("turnos-lista", "Tus turnos",
             "Cada fila es un turno con su fondo, sus ventas y su diferencia de cierre."),
    ), resumen="arqueos, efectivo esperado y diferencias"),
    Recorrido("clientes", "Clientes", (
        Paso("clientes-alta", "Nuevo cliente",
             "Escribe el nombre y, si quieres, teléfono, correo y notas para tenerlo fijo."),
        Paso("clientes-lista", "Tus clientes",
             "Aquí aparecen los clientes fijos; toca uno para editar sus datos."),
    ), resumen="alta rápida y lista de clientes fijos"),
    Recorrido("modulos", "Módulos", (
        Paso("modulos-eleccion", "Qué usas",
             "Marca ventas, inventario o servicios. Lo que no marques se oculta, no se borra."),
        Paso("modulos-guardar", "Guardar",
             "Pulsa Guardar: el menú cambia en el acto y tus datos siguen intactos."),
    ), resumen="qué módulos usas y cómo guardarlos"),
    Recorrido("respaldo", "Respaldo", (
        Paso("respaldo-crear", "Crear respaldo",
             "Elige una contraseña de 12 caracteres o más; sin ella no se puede restaurar."),
        Paso("respaldo-restaurar", "Restaurar",
             "Restaurar reemplaza todos los datos: crea antes un respaldo de este equipo."),
    ), resumen="crear un respaldo cifrado y restaurarlo"),
    Recorrido("licencia", "Licencia", (
        Paso("licencia-estado", "Tu licencia",
             "Consulta el estado del período de prueba o de la licencia activa."),
        Paso("licencia-identidad", "Este equipo",
             "Copia este ID cuando pidas una licencia o una recuperación."),
        Paso("licencia-solicitar", "Solicitar licencia",
             "Rellena la solicitud con tus datos; aquí mismo ves las instrucciones de pago."),
    ), resumen="estado, identidad del equipo y solicitud"),
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
