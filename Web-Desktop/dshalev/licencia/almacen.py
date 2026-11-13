"""Almacén local de la licencia, marca de reloj y defensas ante restauraciones.

Tres piezas relacionadas:

``licencia.bin`` (dentro de ``DShalevData/licencia/``)
    Espejo local de la licencia firmada por GL, ligado a la huella de la
    instalación y al ``appId``. Se escribe de forma atómica y protegido en
    reposo (DPAPI en Windows).

``ancla`` (fuera de ``DShalevData``)
    Resguardo de la licencia más reciente y marca de agua del reloj, en el
    perfil del usuario de Windows. Sirve para dos cosas que un archivo dentro
    de la carpeta de datos no puede hacer: detectar que se restauró un respaldo
    anterior y conservar la licencia vigente cuando la carpeta se reemplaza.

``Reloj``
    La marca de agua se guarda en el ancla y nunca retrocede. Un retroceso de
    más de dos horas deja la licencia sin habilitar hasta que la hora vuelva a
    la marca. Esto detecta un ajuste burdo del reloj; **no** es un reloj
    confiable antimanipulación ni sustituye a la hora del servidor.

``documento`` sigue siendo el sobre JSON firmado exacto que entregó el
servicio, guardado como texto opaco: el almacén no lo interpreta, pero
conserva junto a él el último estado verificado y la marca de revocación, de
modo que una revocación confirmada no se pierde al quedarse sin red ni al
recibir un documento de estado antiguo. La verificación de firma, campos y
derechos vive en ``verificador.py``.
"""

from __future__ import annotations

import json
import os
import re
import threading
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable, Mapping

from . import proteccion
from .config_licencia import MAXIMO_DOCUMENTO_LICENCIA, TOLERANCIA_RELOJ
from .contrato import ESTADOS_LICENCIA

DESCRIPCION_LICENCIA = "DShalev license state"
DESCRIPCION_ANCLA = "DShalev license anchor"
VERSION_ALMACEN = 1
NOMBRE_LICENCIA = "licencia.bin"

_HUELLA_RE = re.compile(r"^[A-Za-z0-9_-]{43}$")
_ORDEN_MIN = datetime.min.replace(tzinfo=timezone.utc)
_APP_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$")


class AlmacenError(RuntimeError):
    """El estado local de la licencia no se puede usar con seguridad."""


class _ArchivoDanado(Exception):
    """El archivo local no se pudo abrir, pero el ancla puede recuperarlo."""


def _ahora_utc() -> datetime:
    return datetime.now(timezone.utc)


def _normalizar(momento: datetime) -> datetime:
    if not isinstance(momento, datetime) or momento.tzinfo is None:
        raise AlmacenError("Se esperaba una fecha con zona horaria.")
    return momento.astimezone(timezone.utc).replace(microsecond=0)


def _a_iso(momento: datetime) -> str:
    return _normalizar(momento).isoformat().replace("+00:00", "Z")


def _de_iso(texto: Any) -> datetime:
    if not isinstance(texto, str) or not texto:
        raise ValueError("fecha vacía")
    momento = datetime.fromisoformat(texto.replace("Z", "+00:00"))
    if momento.tzinfo is None:
        raise ValueError("fecha sin zona horaria")
    return momento.astimezone(timezone.utc)


def _reciente(licencia: "LicenciaLocal") -> tuple[datetime, datetime, bool]:
    """Ordena licencias por guardado, último estado verificado y revocación."""
    return (
        licencia.guardada_en,
        licencia.estado_comprobado_en or _ORDEN_MIN,
        licencia.revocada,
    )


def _validar_documento(documento: str) -> str:
    if not isinstance(documento, str):
        raise AlmacenError("La licencia recibida no es texto.")
    if not documento or documento != documento.strip():
        raise AlmacenError("La licencia recibida está vacía o tiene espacios sobrantes.")
    if len(documento) > MAXIMO_DOCUMENTO_LICENCIA:
        raise AlmacenError("La licencia recibida supera el tamaño máximo admitido.")
    if any(ord(caracter) < 0x20 and caracter not in "\r\n\t" for caracter in documento):
        raise AlmacenError("La licencia recibida contiene caracteres de control.")
    return documento


@dataclass(frozen=True)
class LicenciaLocal:
    """Licencia guardada, su último estado verificado y la revocación local.

    ``revocada`` es pegajosa: una vez confirmada por un documento de estado
    firmado, ningún otro documento la borra. Solo una licencia nueva (otro
    canje) empieza de cero.
    """

    documento: str
    guardada_en: datetime
    revocada: bool = False
    ultimo_estado: str | None = None
    estado_comprobado_en: datetime | None = None

    def __post_init__(self) -> None:
        _validar_documento(self.documento)
        object.__setattr__(self, "guardada_en", _normalizar(self.guardada_en))
        if not isinstance(self.revocada, bool):
            raise AlmacenError("El estado de revocación no es booleano.")
        if self.ultimo_estado is not None and self.ultimo_estado not in ESTADOS_LICENCIA:
            raise AlmacenError("El último estado guardado no es conocido.")
        if self.estado_comprobado_en is not None:
            object.__setattr__(self, "estado_comprobado_en", _normalizar(self.estado_comprobado_en))
        if self.revocada and self.ultimo_estado not in (None, "REVOCADA"):
            raise AlmacenError("Una licencia revocada no puede tener otro estado.")

    def a_json(self) -> dict[str, Any]:
        return {
            "documento": self.documento,
            "guardada_en": _a_iso(self.guardada_en),
            "revocada": self.revocada,
            "ultimo_estado": self.ultimo_estado,
            "estado_comprobado_en": (
                None if self.estado_comprobado_en is None else _a_iso(self.estado_comprobado_en)
            ),
        }

    @classmethod
    def desde_json(cls, datos: Any) -> "LicenciaLocal":
        if not isinstance(datos, dict):
            raise ValueError("licencia no es un objeto")
        comprobado = datos.get("estado_comprobado_en")
        return cls(
            documento=datos.get("documento"),
            guardada_en=_de_iso(datos.get("guardada_en")),
            revocada=datos.get("revocada", False),
            ultimo_estado=datos.get("ultimo_estado"),
            estado_comprobado_en=None if comprobado in (None, "") else _de_iso(comprobado),
        )


@dataclass(frozen=True)
class ResultadoAlmacen:
    """Lo que el almacén pudo leer, sin decidir por sí solo si la licencia vale.

    ``reloj_retrocedido`` obliga a fallar cerrado. ``licencia_ajena_ignorada`` y
    ``restauracion_antigua`` son avisos para el operador; nunca borran archivos.
    """

    licencia: LicenciaLocal | None
    reloj_retrocedido: bool = False
    restauracion_antigua: bool = False
    licencia_ajena_ignorada: bool = False
    recuperada_del_ancla: bool = False
    advertencias: tuple[str, ...] = field(default=())


def ruta_licencia(raiz_instalacion: Path) -> Path:
    """Ruta del espejo local de la licencia (por instalación, no por local)."""
    return Path(raiz_instalacion) / "DShalevData" / "licencia" / NOMBRE_LICENCIA


def ruta_ancla_por_defecto() -> Path:
    """Resguardo fuera de ``DShalevData``: perfil del usuario, no la carpeta portable."""
    if proteccion.es_windows():
        base = os.environ.get("LOCALAPPDATA")
        if not base:
            base = str(Path.home() / "AppData" / "Local")
        return Path(base) / "DShalev" / "ancla-licencia.bin"
    base = os.environ.get("XDG_DATA_HOME") or str(Path.home() / ".local" / "share")
    return Path(base) / "DShalev" / "ancla-licencia.bin"


class AnclaInstalacion:
    """Resguardo de la licencia y marca de agua del reloj, fuera de la carpeta de datos."""

    def __init__(
        self,
        ruta: Path,
        *,
        huella: str,
        tolerancia: timedelta = TOLERANCIA_RELOJ,
        ahora: Callable[[], datetime] = _ahora_utc,
    ) -> None:
        self.ruta = Path(ruta)
        if not _HUELLA_RE.fullmatch(str(huella)):
            raise AlmacenError("La huella de la instalación no tiene el formato esperado.")
        if tolerancia < timedelta(0):
            raise AlmacenError("La tolerancia de reloj no puede ser negativa.")
        self.huella = huella
        self.tolerancia = tolerancia
        self._ahora = ahora
        self._cerrojo = threading.RLock()

    def _leer(self) -> dict[str, Any] | None:
        if not self.ruta.exists():
            return None
        try:
            claro = proteccion.leer_protegido(self.ruta, descripcion=DESCRIPCION_ANCLA)
        except FileNotFoundError:
            return None
        except proteccion.ProteccionError as exc:
            raise AlmacenError(
                f"El resguardo de licencia de este equipo no se puede abrir y no se borró. {exc}"
            ) from None
        try:
            datos = json.loads(claro)
        except (ValueError, UnicodeDecodeError):
            raise AlmacenError("El resguardo de licencia está dañado y no se borró.") from None
        if not isinstance(datos, dict) or datos.get("version") != VERSION_ALMACEN:
            raise AlmacenError("El resguardo de licencia tiene una versión no reconocida; no se borró.")
        if datos.get("huella") != self.huella:
            raise AlmacenError(
                "El resguardo de licencia pertenece a otra instalación (otro usuario de Windows o copia de otro equipo). "
                "No se usa ni se borra: restaura el perfil original o usa la transferencia manual."
            )
        return datos

    def _guardar(self, datos: Mapping[str, Any]) -> None:
        cuerpo = dict(datos)
        cuerpo["version"] = VERSION_ALMACEN
        cuerpo["huella"] = self.huella
        cuerpo["actualizada_en"] = _a_iso(self._ahora())
        try:
            proteccion.escribir_protegido(
                self.ruta,
                json.dumps(cuerpo, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
                descripcion=DESCRIPCION_ANCLA,
            )
        except proteccion.ProteccionError as exc:
            raise AlmacenError(f"No se pudo guardar el resguardo de licencia. {exc}") from None

    def ultimo_visto(self) -> datetime | None:
        datos = self._leer()
        if not datos:
            return None
        try:
            return _de_iso(datos.get("ultimo_visto")) if datos.get("ultimo_visto") else None
        except ValueError:
            raise AlmacenError("La marca de reloj del resguardo está dañada; no se borró.") from None

    def avanzar_reloj(self, ahora: datetime | None = None) -> bool:
        """Actualiza la marca de agua y dice si el reloj retrocedió de más."""
        momento = _normalizar(ahora or self._ahora())
        with self._cerrojo:
            datos = self._leer() or {}
            try:
                visto = _de_iso(datos["ultimo_visto"]) if datos.get("ultimo_visto") else None
            except (ValueError, KeyError):
                raise AlmacenError("La marca de reloj del resguardo está dañada; no se borró.") from None
            retrocedido = bool(visto and momento < visto - self.tolerancia)
            if not retrocedido and (visto is None or momento > visto):
                datos["ultimo_visto"] = _a_iso(momento)
                self._guardar(datos)
            return retrocedido

    def licencia(self) -> LicenciaLocal | None:
        with self._cerrojo:
            datos = self._leer()
            if not datos or not datos.get("licencia"):
                return None
            try:
                return LicenciaLocal.desde_json(datos["licencia"])
            except (ValueError, AlmacenError):
                raise AlmacenError("El resguardo de la licencia está dañado; no se borró.") from None

    def registrar_licencia(self, licencia: LicenciaLocal) -> None:
        with self._cerrojo:
            datos = self._leer() or {}
            datos["licencia"] = licencia.a_json()
            self._guardar(datos)


class AlmacenLicencia:
    """Licencia local + ancla. Una instancia por proceso; no hay bloqueo entre procesos."""

    def __init__(
        self,
        ruta: Path,
        *,
        huella: str,
        app_id: str,
        ancla: AnclaInstalacion | None = None,
        ahora: Callable[[], datetime] = _ahora_utc,
    ) -> None:
        self.ruta = Path(ruta)
        if not _HUELLA_RE.fullmatch(str(huella)):
            raise AlmacenError("La huella de la instalación no tiene el formato esperado.")
        if not _APP_ID_RE.fullmatch(str(app_id)):
            raise AlmacenError("La configuración de la aplicación no tiene un appId válido.")
        self.huella = huella
        self.app_id = app_id
        self._ahora = ahora
        self.ancla = ancla or AnclaInstalacion(ruta_ancla_por_defecto(), huella=huella, ahora=ahora)
        self._cerrojo = threading.RLock()

    # ── archivo local ────────────────────────────────────────────────────────
    def _escribir_licencia(self, licencia: LicenciaLocal) -> None:
        cuerpo = {
            "version": VERSION_ALMACEN,
            "huella": self.huella,
            "app_id": self.app_id,
            "licencia": licencia.a_json(),
        }
        try:
            proteccion.escribir_protegido(
                self.ruta,
                json.dumps(cuerpo, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
                descripcion=DESCRIPCION_LICENCIA,
            )
        except proteccion.ProteccionError as exc:
            raise AlmacenError(f"No se pudo guardar la licencia en la carpeta de datos. {exc}") from None

    def _leer_archivo(self) -> tuple[LicenciaLocal | None, bool, bool]:
        """Devuelve (licencia, es_de_otro_equipo, es_de_otra_app)."""
        if not self.ruta.exists():
            return None, False, False
        try:
            claro = proteccion.leer_protegido(self.ruta, descripcion=DESCRIPCION_LICENCIA)
        except FileNotFoundError:
            return None, False, False
        except proteccion.ProteccionError as exc:
            raise _ArchivoDanado(
                f"No se pudo abrir la licencia guardada; se intentará el resguardo de la instalación. {exc}"
            ) from None
        try:
            datos = json.loads(claro)
        except (ValueError, UnicodeDecodeError):
            raise _ArchivoDanado("La licencia guardada está dañada; se intentará el resguardo de la instalación.") from None
        if not isinstance(datos, dict) or datos.get("version") != VERSION_ALMACEN:
            raise _ArchivoDanado("La licencia guardada tiene una versión no reconocida; no se borró.")
        ajeno = datos.get("huella") != self.huella
        otra_app = datos.get("app_id") != self.app_id
        if ajeno or otra_app or not datos.get("licencia"):
            return None, ajeno, otra_app
        try:
            return LicenciaLocal.desde_json(datos["licencia"]), False, False
        except (ValueError, AlmacenError):
            raise _ArchivoDanado("La licencia guardada está incompleta; se intentará el resguardo.") from None

    # ── API ──────────────────────────────────────────────────────────────────
    def observar(self, ahora: datetime | None = None) -> ResultadoAlmacen:
        """Lee la licencia y actualiza la marca de reloj. Nunca borra archivos."""
        momento = _normalizar(ahora or self._ahora())
        advertencias: list[str] = []
        with self._cerrojo:
            local: LicenciaLocal | None = None
            ajena = otra_app = danado = False
            try:
                local, ajena, otra_app = self._leer_archivo()
            except _ArchivoDanado as exc:
                danado = True
                advertencias.append(str(exc))

            guarda = self.ancla.licencia()
            elegida = local
            restauracion = recuperada = False

            if guarda is not None and (local is None or _reciente(guarda) > _reciente(local)):
                elegida = guarda
                if local is None:
                    recuperada = True
                elif guarda.guardada_en > local.guardada_en:
                    restauracion = True
                    advertencias.append(
                        "Se restauró una copia anterior de los datos: se conservó la licencia más reciente."
                    )
                elif guarda.revocada and not local.revocada:
                    advertencias.append(
                        "Se recuperó del resguardo una revocación ya confirmada; la licencia sigue revocada."
                    )
                self._escribir_licencia(guarda)
            elif local is not None and guarda is None:
                # Primer arranque con licencia ya guardada (o ancla perdido): se resguarda.
                self.ancla.registrar_licencia(local)

            if ajena:
                advertencias.append(
                    "La licencia guardada pertenece a otro equipo y no se activó; no se borró."
                )
            if otra_app:
                advertencias.append(
                    "La licencia guardada pertenece a otra aplicación y no se activó; no se borró."
                )
            if danado and elegida is None:
                advertencias.append("No hay una copia utilizable en el resguardo de la instalación.")

            retrocedido = self.ancla.avanzar_reloj(momento)
            if retrocedido:
                advertencias.append(
                    "El reloj del equipo retrocedió más de lo permitido. La licencia no se habilitará hasta que la hora vuelva a la marca guardada."
                )

            return ResultadoAlmacen(
                licencia=elegida,
                reloj_retrocedido=retrocedido,
                restauracion_antigua=restauracion,
                licencia_ajena_ignorada=ajena,
                recuperada_del_ancla=recuperada,
                advertencias=tuple(advertencias),
            )

    def guardar_licencia(
        self,
        documento: str,
        *,
        ahora: datetime | None = None,
        estado: str | None = None,
    ) -> LicenciaLocal:
        """Guarda la licencia ya validada. El resguardo se escribe antes que el espejo.

        Sustituir el documento solo es legítimo tras verificar la firma del
        servicio; por eso el estado de comprobación remota empieza vacío y solo
        lo llena ``registrar_estado`` con el ``checkedAt`` firmado. Así un
        estado del servidor no se descarta por compararlo con la hora local.
        """
        if estado is not None and estado not in ESTADOS_LICENCIA:
            raise AlmacenError("Estado de licencia desconocido.")
        momento = _normalizar(ahora or self._ahora())
        licencia = LicenciaLocal(
            documento=_validar_documento(documento),
            guardada_en=momento,
            revocada=estado == "REVOCADA",
            ultimo_estado=estado,
            estado_comprobado_en=None,
        )
        with self._cerrojo:
            self.ancla.registrar_licencia(licencia)
            try:
                self._escribir_licencia(licencia)
            except AlmacenError as exc:
                raise AlmacenError(
                    "La licencia quedó en el resguardo de la instalación, pero no se pudo escribir en la carpeta de datos; "
                    f"se recuperará al reiniciar. {exc}"
                ) from None
        return licencia

    # ── estado remoto ────────────────────────────────────────────────────────
    def registrar_estado(
        self,
        estado: str,
        *,
        comprobado_en: datetime,
        ahora: datetime | None = None,
    ) -> bool:
        """Aplica un estado ya verificado; devuelve ``True`` si avanzó.

        Reglas deliberadamente conservadoras:

        * un documento con ``checkedAt`` anterior o igual al último aceptado se
          ignora (respuesta antigua o repetida);
        * ``REVOCADA`` es pegajoso: un documento posterior que diga ``ACTIVA``
          no reactiva la licencia;
        * la falta de red nunca llega aquí, así que no puede borrar nada.
        """
        if estado not in ESTADOS_LICENCIA:
            raise AlmacenError("Estado de licencia desconocido.")
        momento = _normalizar(comprobado_en)
        with self._cerrojo:
            resultado = self.observar(ahora)
            actual = resultado.licencia
            if actual is None:
                raise AlmacenError("No hay licencia guardada a la que aplicar el estado.")
            if actual.estado_comprobado_en is not None and momento <= actual.estado_comprobado_en:
                return False
            nuevo_estado = "REVOCADA" if actual.revocada else estado
            licencia = LicenciaLocal(
                documento=actual.documento,
                guardada_en=actual.guardada_en,
                revocada=actual.revocada or estado == "REVOCADA",
                ultimo_estado=nuevo_estado,
                estado_comprobado_en=momento,
            )
            self.ancla.registrar_licencia(licencia)
            self._escribir_licencia(licencia)
            return True

    def marcar_revocada(self, *, comprobado_en: datetime, ahora: datetime | None = None) -> bool:
        """Atajo para persistir una revocación confirmada por el servicio."""
        return self.registrar_estado("REVOCADA", comprobado_en=comprobado_en, ahora=ahora)
