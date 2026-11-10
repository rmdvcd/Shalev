from __future__ import annotations

import threading
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from dshalev.licencia import proteccion
from dshalev.licencia.almacen import (
    AlmacenError,
    AlmacenLicencia,
    AnclaInstalacion,
    LicenciaLocal,
    ruta_ancla_por_defecto,
    ruta_licencia,
)
from dshalev.licencia.config_licencia import MAXIMO_DOCUMENTO_LICENCIA

HUELLA = "A" * 43
HUELLA_AJENA = "B" * 43
APP = "dshalev"
DOC_A = "GL-REDEEM-LICENSE-v1|documento-de-prueba-A"
DOC_B = "GL-REDEEM-LICENSE-v1|documento-de-prueba-B"


@pytest.fixture(autouse=True)
def entorno_de_prueba(monkeypatch):
    if not proteccion.es_windows():
        monkeypatch.setenv(proteccion.VARIABLE_PRUEBA, "1")
    yield


class Reloj:
    def __init__(self, momento: datetime) -> None:
        self.momento = momento

    def __call__(self) -> datetime:
        return self.momento


def crear_almacen(
    tmp_path: Path,
    *,
    huella: str = HUELLA,
    ancla: str = "perfil/ancla.bin",
    reloj: Reloj | None = None,
):
    reloj = reloj or Reloj(datetime(2026, 11, 10, 12, 0, tzinfo=timezone.utc))
    return (
        AlmacenLicencia(
            tmp_path / "DShalevData" / "licencia" / "licencia.bin",
            huella=huella,
            app_id=APP,
            ancla=AnclaInstalacion(tmp_path / ancla, huella=huella, ahora=reloj),
            ahora=reloj,
        ),
        reloj,
    )


def test_guarda_y_recupera_una_licencia_sellada(tmp_path: Path):
    almacen, _ = crear_almacen(tmp_path)
    vacio = almacen.observar()
    assert vacio.licencia is None
    assert vacio.advertencias == () and vacio.reloj_retrocedido is False

    licencia = almacen.guardar_licencia(DOC_A)
    assert licencia.documento == DOC_A

    crudo = almacen.ruta.read_bytes()
    assert crudo.startswith((proteccion.CABECERA_DPAPI, proteccion.CABECERA_PRUEBA))
    assert DOC_A.encode() not in crudo

    leido = almacen.observar()
    assert leido.licencia is not None and leido.licencia.documento == DOC_A
    assert leido.reloj_retrocedido is False and leido.restauracion_antigua is False
    assert leido.licencia_ajena_ignorada is False


def test_una_restauracion_antigua_conserva_la_licencia_mas_reciente(tmp_path: Path):
    almacen, reloj = crear_almacen(tmp_path)
    almacen.guardar_licencia(DOC_A, ahora=reloj.momento)
    almacen.observar(ahora=reloj.momento)
    copia_vieja = almacen.ruta.read_bytes()

    reloj.momento += timedelta(days=3)
    almacen.guardar_licencia(DOC_B, ahora=reloj.momento)

    # Se restaura la carpeta de datos anterior (el ancla vive fuera y no se toca).
    almacen.ruta.write_bytes(copia_vieja)

    resultado = almacen.observar(ahora=reloj.momento + timedelta(hours=1))
    assert resultado.licencia is not None and resultado.licencia.documento == DOC_B
    assert resultado.restauracion_antigua is True
    assert any("restauró" in aviso.lower() for aviso in resultado.advertencias)

    # La licencia vigente vuelve a quedar escrita en la carpeta de datos.
    de_nuevo = almacen.observar(ahora=reloj.momento + timedelta(hours=2))
    assert de_nuevo.licencia is not None and de_nuevo.licencia.documento == DOC_B


def test_una_licencia_de_otro_equipo_no_se_activa_y_no_destruye_la_propia(tmp_path: Path):
    almacen, reloj = crear_almacen(tmp_path)
    almacen.guardar_licencia(DOC_A, ahora=reloj.momento)
    ruta = almacen.ruta

    # Otra instalación escribe su licencia sobre el mismo archivo.
    ancla_ajena = AnclaInstalacion(tmp_path / "perfil" / "ancla-ajena.bin", huella=HUELLA_AJENA, ahora=reloj)
    otro = AlmacenLicencia(ruta, huella=HUELLA_AJENA, app_id=APP, ancla=ancla_ajena, ahora=reloj)
    otro.guardar_licencia("GL-REDEEM-LICENSE-v1|de-otro-equipo", ahora=reloj.momento)

    resultado = almacen.observar(ahora=reloj.momento)
    assert resultado.licencia_ajena_ignorada is True
    assert resultado.licencia is not None and resultado.licencia.documento == DOC_A
    assert any("otro equipo" in aviso for aviso in resultado.advertencias)

    # Una instalación sin licencia propia no la activa ni la borra.
    vacia, _ = crear_almacen(tmp_path, huella="C" * 43, ancla="perfil/ancla-vacia.bin", reloj=reloj)
    antes = ruta.read_bytes()
    sin_licencia = vacia.observar(ahora=reloj.momento)
    assert sin_licencia.licencia is None
    assert sin_licencia.licencia_ajena_ignorada is True
    assert ruta.read_bytes() == antes


def test_archivo_danado_se_recupera_desde_el_resguardo(tmp_path: Path):
    almacen, reloj = crear_almacen(tmp_path)
    almacen.guardar_licencia(DOC_A, ahora=reloj.momento)
    almacen.observar(ahora=reloj.momento)
    almacen.ruta.write_bytes(b"archivo danado por una copia interrumpida")

    resultado = almacen.observar(ahora=reloj.momento)
    assert resultado.recuperada_del_ancla is True
    assert resultado.licencia is not None and resultado.licencia.documento == DOC_A
    assert almacen.observar(ahora=reloj.momento).licencia.documento == DOC_A


def test_archivo_danado_sin_resguardo_falla_sin_borrar(tmp_path: Path):
    almacen, reloj = crear_almacen(tmp_path)
    almacen.ruta.parent.mkdir(parents=True, exist_ok=True)
    almacen.ruta.write_bytes(b"basura")

    resultado = almacen.observar(ahora=reloj.momento)
    assert resultado.licencia is None
    assert any("resguardo" in aviso.lower() for aviso in resultado.advertencias)
    assert almacen.ruta.read_bytes() == b"basura"


def test_el_reloj_retrocedido_falla_cerrado_y_no_baja_la_marca(tmp_path: Path):
    almacen, reloj = crear_almacen(tmp_path)
    almacen.guardar_licencia(DOC_A, ahora=reloj.momento)
    assert almacen.observar(ahora=reloj.momento).reloj_retrocedido is False

    resultado = almacen.observar(ahora=reloj.momento - timedelta(hours=3))
    assert resultado.reloj_retrocedido is True
    assert resultado.licencia is not None  # se puede mostrar, pero no habilitar
    assert almacen.ancla.ultimo_visto() == reloj.momento

    dentro_de_tolerancia = reloj.momento - timedelta(hours=1)
    assert almacen.observar(ahora=dentro_de_tolerancia).reloj_retrocedido is False
    assert almacen.ancla.ultimo_visto() == reloj.momento


def test_un_resguardo_de_otra_instalacion_falla_sin_borrarse(tmp_path: Path):
    ruta = tmp_path / "perfil" / "ancla.bin"
    ajena = AnclaInstalacion(ruta, huella=HUELLA_AJENA, ahora=Reloj(datetime(2026, 11, 10, 12, tzinfo=timezone.utc)))
    ajena.registrar_licencia(LicenciaLocal(DOC_B, datetime(2026, 11, 10, 12, tzinfo=timezone.utc)))

    propia = AnclaInstalacion(ruta, huella=HUELLA)
    with pytest.raises(AlmacenError, match="otra instalación"):
        propia.licencia()
    assert ruta.exists()


def test_un_fallo_al_escribir_el_espejo_no_pierde_la_licencia(tmp_path: Path, monkeypatch):
    almacen, reloj = crear_almacen(tmp_path)
    almacen.guardar_licencia(DOC_A, ahora=reloj.momento)
    anterior = almacen.ruta.read_bytes()

    def romper(self, licencia):
        raise AlmacenError("disco lleno simulado")

    with monkeypatch.context() as parche:
        parche.setattr(AlmacenLicencia, "_escribir_licencia", romper)
        with pytest.raises(AlmacenError, match="resguardo"):
            almacen.guardar_licencia(DOC_B, ahora=reloj.momento + timedelta(minutes=5))
    assert almacen.ruta.read_bytes() == anterior

    recuperado = almacen.observar(ahora=reloj.momento + timedelta(minutes=6))
    assert recuperado.licencia is not None and recuperado.licencia.documento == DOC_B


def test_guardados_concurrentes_dejan_un_archivo_valido(tmp_path: Path):
    almacen, reloj = crear_almacen(tmp_path)
    errores: list[BaseException] = []
    barrera = threading.Barrier(6)

    def trabajo(indice: int) -> None:
        try:
            barrera.wait(timeout=5)
            almacen.guardar_licencia(f"GL-REDEEM-LICENSE-v1|{indice}", ahora=reloj.momento + timedelta(seconds=indice))
        except BaseException as exc:  # noqa: BLE001 - se reenvía al hilo principal
            errores.append(exc)

    hilos = [threading.Thread(target=trabajo, args=(indice,)) for indice in range(6)]
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join(timeout=20)

    assert not errores, errores
    resultado = almacen.observar(ahora=reloj.momento + timedelta(minutes=1))
    assert resultado.licencia is not None
    assert resultado.licencia.documento.startswith("GL-REDEEM-LICENSE-v1|")


@pytest.mark.parametrize("documento", [
    pytest.param("", id="vacio"),
    pytest.param("   ", id="solo_espacios"),
    pytest.param("x" * (MAXIMO_DOCUMENTO_LICENCIA + 1), id="demasiado_largo"),
    pytest.param("a\x00b", id="con_control"),
])
def test_documentos_invalidos_no_se_guardan(tmp_path: Path, documento: str):
    almacen, _ = crear_almacen(tmp_path)
    with pytest.raises(AlmacenError):
        almacen.guardar_licencia(documento)
    assert not almacen.ruta.exists()


@pytest.mark.parametrize("huella,app", [("corta", APP), (HUELLA, "Mayúsculas")])
def test_configuracion_invalida_del_almacen(tmp_path: Path, huella: str, app: str):
    with pytest.raises(AlmacenError):
        AlmacenLicencia(tmp_path / "l.bin", huella=huella, app_id=app)
    assert not (tmp_path / "l.bin").exists()


@pytest.mark.skipif(proteccion.es_windows(), reason="en Windows el ancla usa LOCALAPPDATA")
def test_el_resguardo_vive_fuera_de_la_carpeta_de_datos(tmp_path: Path, monkeypatch):
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path / "perfil"))
    ruta = ruta_ancla_por_defecto()
    assert ruta == tmp_path / "perfil" / "DShalev" / "ancla-licencia.bin"
    assert "DShalevData" not in ruta.parts


def test_la_licencia_guardada_no_expone_el_documento_en_claro(tmp_path: Path):
    almacen, _ = crear_almacen(tmp_path)
    almacen.guardar_licencia(DOC_A)
    assert DOC_A.encode() not in almacen.ruta.read_bytes()
    assert DOC_A.encode() not in (tmp_path / "perfil" / "ancla.bin").read_bytes()


def test_las_rutas_son_por_instalacion_y_no_por_local(tmp_path: Path):
    almacen, _ = crear_almacen(tmp_path)
    assert ruta_licencia(tmp_path) == tmp_path / "DShalevData" / "licencia" / "licencia.bin"
    assert ruta_licencia(tmp_path) == almacen.ruta
    assert "locales" not in ruta_licencia(tmp_path).parts


@pytest.mark.skipif(proteccion.es_windows(), reason="DPAPI no usa permisos POSIX")
def test_los_archivos_locales_son_solo_del_usuario(tmp_path: Path):
    almacen, _ = crear_almacen(tmp_path)
    almacen.guardar_licencia(DOC_A)
    assert almacen.ruta.stat().st_mode & 0o077 == 0
    assert (tmp_path / "perfil" / "ancla.bin").stat().st_mode & 0o077 == 0


def test_un_local_no_tiene_identidad_ni_licencia_propias(tmp_path: Path):
    from dshalev.config import load_config
    from dshalev.licencia.identidad import ruta_identidad

    centro = load_config(tmp_path, local="centro")
    assert centro.data_dir == tmp_path / "DShalevData" / "locales" / "centro"
    # La identidad y la licencia viven en la raíz de la instalación, compartidas por los locales.
    assert ruta_identidad(tmp_path) == tmp_path / "DShalevData" / "licencia" / "identidad.bin"
    assert ruta_licencia(tmp_path) == tmp_path / "DShalevData" / "licencia" / "licencia.bin"
    for ruta in (ruta_identidad(tmp_path), ruta_licencia(tmp_path)):
        assert centro.data_dir not in ruta.parents
        assert "locales" not in ruta.parts
