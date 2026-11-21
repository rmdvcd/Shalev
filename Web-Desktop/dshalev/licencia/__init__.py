"""Licencia de canje GL: identidad, configuración fijada, contrato y almacén.

El paquete implementa el contrato del servicio GL de canje
(``services/redeem``) tal como está publicado: transcripts canónicos con
longitud ``uint32`` big-endian, normalización del código, verificación de la
licencia y del estado firmados por el servicio, y el cliente de
``POST /v1/redeem`` y ``POST /v1/status``.

Sigue sin haber configuración de producción: sin endpoint HTTPS y claves
aprobadas, ``configuracion().comercial_habilitada`` es ``False`` y la
activación comercial está deshabilitada. El enforcement en rutas y la UI de
activación/transferencia aún no están conectados.
"""

from .almacen import AlmacenError, AlmacenLicencia, AnclaInstalacion, LicenciaLocal, ResultadoAlmacen
from .cliente_gl import ClienteGL, ErrorGL, ResultadoCanje, ResultadoEstado, TransporteUrllib
from .config_licencia import ConfiguracionLicencia, ConfiguracionLicenciaError, configuracion, motivo_activacion_deshabilitada
from .contrato import ContratoError, normalizar_codigo
from .corta import (
    CamposLicenciaCorta,
    LicenciaCortaError,
    ResultadoActivacion,
    activar_spvi2,
    descifrar_y_verificar,
    extraer_spvi2,
)
from .estado import obtener_info_licencia
from .identidad import Identidad, IdentidadError, cargar_identidad, cargar_o_crear_identidad, ruta_identidad
from .proteccion import ProteccionError
from .solicitud import (
    AVISO_FRESCURA,
    SolicitudError,
    calcular_precio,
    calcular_precio_ampliacion,
    derivar_device_id,
    derivar_device_pub,
    generar_solicitud,
    normalizar_gsm7,
)
from .verificador import (
    EstadoVerificado,
    LicenciaInvalida,
    LicenciaVerificada,
    verificar_estado,
    verificar_licencia,
)

__all__ = [
    "AlmacenError",
    "AlmacenLicencia",
    "AnclaInstalacion",
    "ClienteGL",
    "ConfiguracionLicencia",
    "ConfiguracionLicenciaError",
    "ContratoError",
    "ErrorGL",
    "EstadoVerificado",
    "Identidad",
    "IdentidadError",
    "LicenciaInvalida",
    "LicenciaLocal",
    "LicenciaVerificada",
    "ProteccionError",
    "ResultadoAlmacen",
    "ResultadoCanje",
    "ResultadoEstado",
    "TransporteUrllib",
    "cargar_identidad",
    "cargar_o_crear_identidad",
    "ruta_identidad",
    "CamposLicenciaCorta",
    "LicenciaCortaError",
    "ResultadoActivacion",
    "SolicitudError",
    "activar_spvi2",
    "descifrar_y_verificar",
    "extraer_spvi2",
    "generar_solicitud",
    "obtener_info_licencia",
    "derivar_device_id",
    "derivar_device_pub",
    "calcular_precio",
    "calcular_precio_ampliacion",
    "normalizar_gsm7",
    "AVISO_FRESCURA",
    "configuracion",
    "motivo_activacion_deshabilitada",
    "normalizar_codigo",
    "verificar_estado",
    "verificar_licencia",
]
