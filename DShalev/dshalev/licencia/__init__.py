"""Licencia GL de DShalev: identidad del equipo, configuración fijada y almacén local.

El protocolo de canje (transcripción canónica, licencia firmada y estado) todavía
no está implementado: depende del contrato del servicio GL, que no forma parte
de este repositorio. Este paquete contiene solo las piezas que no dependen del
formato de cable.
"""

from .almacen import AlmacenError, AlmacenLicencia, AnclaInstalacion, LicenciaLocal, ResultadoAlmacen
from .config_licencia import ConfiguracionLicencia, ConfiguracionLicenciaError, configuracion, motivo_activacion_deshabilitada
from .identidad import Identidad, IdentidadError, cargar_identidad, cargar_o_crear_identidad
from .proteccion import ProteccionError

__all__ = [
    "AlmacenError",
    "AlmacenLicencia",
    "AnclaInstalacion",
    "ConfiguracionLicencia",
    "ConfiguracionLicenciaError",
    "Identidad",
    "IdentidadError",
    "LicenciaLocal",
    "ProteccionError",
    "ResultadoAlmacen",
    "cargar_identidad",
    "cargar_o_crear_identidad",
    "configuracion",
    "motivo_activacion_deshabilitada",
]
