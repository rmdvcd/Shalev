"""Módulo de consulta de estado consolidado de licencia para DShalev.

Proporciona información para vistas web y avisos preventivos (C4, C5):
- Detección de licencia instalada (SPVI2 corta o canje GL v1).
- Estado unificado (ACTIVA, VENCIDA, REVOCADA, PERPETUA, SIN_LICENCIA).
- Desglose de tipo, vencimiento y días restantes.
- Cuota de dispositivos secundarios comerciales.
- Identificador de dispositivo (SPVI:hex16) y huella de instalación.
- Verificación de reloj / skew (C4) y recordatorios de renovación a los 7 y 2 días (C5).
"""

from __future__ import annotations

import json
import math
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .almacen import AlmacenLicencia, ruta_ancla_por_defecto, ruta_licencia
from .corta import LIC_PREFIJO, extraer_spvi2, descifrar_y_verificar
from .identidad import cargar_o_crear_identidad, ruta_identidad
from .solicitud import derivar_device_id


def obtener_info_licencia(raiz_app: Path, ahora: datetime | None = None) -> dict[str, Any]:
    """Lee el almacén de licencias y devuelve un diccionario completo de estado."""
    momento = ahora or datetime.now(timezone.utc)
    ruta_id = ruta_identidad(raiz_app)
    id_inst = None
    try:
        id_inst = cargar_o_crear_identidad(ruta_id)
        huella = id_inst.huella
        device_id = derivar_device_id(id_inst)
    except Exception:
        huella = "No disponible"
        device_id = "No disponible"

    resultado: dict[str, Any] = {
        "instalada": False,
        "estado": "SIN_LICENCIA",
        "tipo": "Sin licencia",
        "vencimiento": "No aplica",
        "vence_en": None,
        "dias_restantes": None,
        "secundarias": 0,
        "id": None,
        "device_id": device_id,
        "huella": huella,
        "comprobada_en": "No consultado aún",
        "revocada": False,
        "reloj_retrocedido": False,
        "aviso_reloj": None,
        "banner": None,
    }

    if id_inst is None:
        return resultado

    try:
        ruta_lic = ruta_licencia(raiz_app)
        almacen = AlmacenLicencia(ruta_lic, huella=id_inst.huella, app_id="DSHALEV")
        obs = almacen.observar(momento)
    except Exception:
        return resultado

    if obs.reloj_retrocedido:
        resultado["reloj_retrocedido"] = True
        resultado["aviso_reloj"] = (
            "El reloj del sistema retrocedió respecto al último uso registrado. "
            "Ajusta la hora o la zona horaria en los Ajustes del sistema para evitar bloqueos."
        )

    lic = obs.licencia
    if lic is None or not lic.documento:
        return resultado

    resultado["instalada"] = True
    resultado["revocada"] = lic.revocada
    if lic.estado_comprobado_en:
        resultado["comprobada_en"] = lic.estado_comprobado_en.strftime("%Y-%m-%d %H:%M UTC")

    doc = lic.documento.strip()

    # Caso 1: Licencia corta SPVI2
    if doc.startswith(LIC_PREFIJO):
        binario, _, _ = extraer_spvi2(doc)
        if binario is not None:
            try:
                campos = descifrar_y_verificar(binario, id_inst, device_id=device_id)
                resultado["id"] = campos.id
                resultado["tipo"] = campos.tipo
                resultado["secundarias"] = campos.secundarias
                resultado["vence_en"] = campos.vence_en

                if lic.revocada or campos.estado == "REVOCADA":
                    resultado["estado"] = "REVOCADA"
                elif campos.es_perpetua:
                    resultado["estado"] = "PERPETUA"
                    resultado["vencimiento"] = "Perpetua (sin vencimiento)"
                elif campos.vence_en is not None:
                    if momento >= campos.vence_en:
                        resultado["estado"] = "VENCIDA"
                        resultado["vencimiento"] = f"Venció el {campos.vence_en.strftime('%d/%m/%Y')}"
                    else:
                        resultado["estado"] = "ACTIVA"
                        dias = math.ceil((campos.vence_en - momento).total_seconds() / 86400)
                        resultado["dias_restantes"] = dias
                        resultado["vencimiento"] = f"{campos.vence_en.strftime('%d/%m/%Y')} ({dias} días)"
            except Exception:
                resultado["estado"] = "REVOCADA" if lic.revocada else (lic.ultimo_estado or "ACTIVA")
                resultado["tipo"] = "CORTA_SPVI2"

    # Caso 2: Licencia canje JSON v1
    elif doc.startswith("{"):
        try:
            datos_doc = json.loads(doc)
            lic_obj = datos_doc.get("license", {}) if isinstance(datos_doc, dict) else {}
            lic_datos = lic_obj.get("license", {}) if isinstance(lic_obj, dict) else {}
            tipo = lic_datos.get("type", "DESCONOCIDO")
            resultado["id"] = lic_datos.get("id")
            resultado["tipo"] = tipo
            resultado["secundarias"] = lic_datos.get("secundarias", 0)

            if lic.revocada:
                resultado["estado"] = "REVOCADA"
            elif tipo == "PERPETUA":
                resultado["estado"] = "PERPETUA"
                resultado["vencimiento"] = "Perpetua (sin vencimiento)"
            else:
                exp_str = lic_datos.get("expiresAt")
                if exp_str:
                    vence = datetime.fromisoformat(exp_str.replace("Z", "+00:00"))
                    resultado["vence_en"] = vence
                    if momento >= vence:
                        resultado["estado"] = "VENCIDA"
                        resultado["vencimiento"] = f"Venció el {vence.strftime('%d/%m/%Y')}"
                    else:
                        resultado["estado"] = lic.ultimo_estado or "ACTIVA"
                        dias = math.ceil((vence - momento).total_seconds() / 86400)
                        resultado["dias_restantes"] = dias
                        resultado["vencimiento"] = f"{vence.strftime('%d/%m/%Y')} ({dias} días)"
        except Exception:
            pass

    # Generación de banner de recordatorio / advertencia (C5)
    if resultado["reloj_retrocedido"]:
        resultado["banner"] = {
            "kind": "warning",
            "text": resultado["aviso_reloj"],
        }
    elif resultado["estado"] == "REVOCADA":
        resultado["banner"] = {
            "kind": "error",
            "text": "La licencia comercial fue revocada o transferida a otro equipo.",
        }
    elif resultado["estado"] == "VENCIDA":
        resultado["banner"] = {
            "kind": "error",
            "text": "Tu licencia comercial ha vencido. Renuévala para continuar operando sin interrupción.",
        }
    elif resultado["dias_restantes"] is not None:
        d = resultado["dias_restantes"]
        if d <= 2:
            resultado["banner"] = {
                "kind": "warning",
                "text": f"Atención: tu licencia vence {'hoy' if d == 0 else ('en 1 día' if d == 1 else 'en 2 días')}. Renuévala para evitar bloqueos.",
            }
        elif d <= 7:
            resultado["banner"] = {
                "kind": "info",
                "text": f"Recordatorio: tu licencia vence en {d} días. Puedes renovarla desde el panel de Licencia.",
            }

    return resultado
