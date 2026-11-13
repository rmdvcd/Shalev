#!/usr/bin/env python3
"""Video manual vertical de 10 minutos para Reels, sin subtítulos.

  .venv/bin/python tools/video_manual/generar_reel_10min.py --preview
  .venv/bin/python tools/video_manual/generar_reel_10min.py --render

Entrada: capturas del manual y .cache/shalev_video_reel/voz/01.wav ... 10.wav.
Salida: promocion/reel_manual_10min/Shalev_reel_manual_10min_1080x1920.mp4.
"""
from __future__ import annotations

import argparse
from collections import OrderedDict
import json
import math
import os
from pathlib import Path
import subprocess
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageOps
import imageio_ffmpeg

import generar_video as base

ROOT = base.ROOT
WORK = ROOT / ".cache" / "shalev_video_reel"
VOICE = WORK / "voz"
PREVIEW = WORK / "preview"
OUT = ROOT / "promocion" / "reel_manual_10min"
W, H = 1080, 1920
FPS = 30
TOTAL = 600.0
CHAPTER = 60.0
VOICE_DELAY = 0.80
TRANSITION = 0.38
A = "Android/app/capturas/claro"
PROMO = "promocion/capturas"

NAVY, DARK, BLUE, CYAN = base.NAVY, base.DARK, base.BLUE, base.CYAN
ORANGE, ORANGE_DARK, TEAL = base.ORANGE, base.ORANGE_DARK, base.TEAL
INK, MUTED, WHITE = base.INK, base.MUTED, base.WHITE
PALE_BLUE, PALE_CYAN, PALE_ORANGE = base.PALE_BLUE, base.PALE_CYAN, base.PALE_ORANGE
GREEN, RED, AMBER = base.GREEN, base.RED, base.AMBER
font = base.font
card, pill, paragraph, arrow = base.card, base.pill, base.paragraph, base.arrow
hexrgb, ease, lerp = base.hexrgb, base.ease, base.lerp


def V(path, h=1040, y=270, crop=None, label=None, x=None, frame="phone"):
    return {"path": path, "h": h, "y": y, "crop": crop, "label": label, "x": x, "frame": frame}


def P(title, *args, kind="bullets", note="", cards=None, chips=None, formula="", items=None):
    return {"title": title, "items": list(args)+list(items or []), "kind": kind, "note": note,
            "cards": cards or [], "chips": chips or [], "formula": formula}


def Ph(at, heading, screen=None, panel=None, layout="standard", taps=None, highlights=None):
    return {"at": at, "heading": heading, "screen": screen, "panel": panel,
            "layout": layout, "taps": taps or [], "highlights": highlights or []}


def Tap(at, xy, box=None):
    return {"at": at, "xy": xy, "box": box}


def Hi(start, end, box, label=""):
    return {"start": start, "end": end, "box": box, "label": label}


GUIDE = json.loads((ROOT / "tools/video_manual/reel_guion.json").read_text(encoding="utf-8"))

SCENES = [
    {
        "phases": [
            Ph(0, "Manual completo · 10 minutos", V(f"{PROMO}/shot_inicio_claro.png", 800, 720), layout="intro"),
            Ph(8, "Configuración inicial guiada", V(f"{A}/10b_onboarding_bienvenida.png"),
               P("Un rol para cada teléfono", kind="cards", cards=[
                   ("PRINCIPAL", "Una por negocio. Centraliza datos, catálogo, respaldos y licencia."),
                   ("SECUNDARIA", "Para empleados. Se vincula a la Principal mediante QR."),
               ], note="La elección del rol es definitiva: revísala antes de continuar."),
               taps=[Tap(10.0, (0.58, 0.955), (0.48, 0.91, 0.68, 0.99))]),
            Ph(18, "Tu negocio, a tu medida", V(f"{A}/10c_onboarding_datos.png"),
               P("Datos y protección desde el inicio",
                 "Activa huella, rostro o PIN si tu teléfono tiene bloqueo.",
                 "Selecciona los módulos Ventas, Inventario y/o Servicios.",
                 "Completa el titular: se usa en transferencias y licencia.",
                 note="Puedes retomar las opciones desde Ajustes → Completar configuración.")),
            Ph(29, "Avisos de inventario configurables", V(f"{A}/10e_onboarding_alertas.png"),
               P("Umbrales según tu ritmo de venta",
                 "Valores iniciales: stock bajo 5 y crítico 1.",
                 "Productos en unidades; insumos en kg, L, g, ml o unidades.",
                 "Los avisos aparecen en Inicio antes de quedarte sin existencias."),
               taps=[Tap(31.5, (0.81, 0.35), (0.72, 0.30, 0.90, 0.40))]),
            Ph(40, "Cinco secciones siempre a mano", V(f"{PROMO}/shot_inicio_claro.png"),
               P("Navegación principal", kind="chips", chips=["Inicio", "Inventario", "Servicios", "Registros", "Ajustes"],
                 items=["Desliza entre secciones o toca su icono.", "Mantén pulsado un botón para ver su nombre."],
                 note="El gesto de deslizar se pausa al escribir, filtrar o abrir ventanas."),
               taps=[Tap(43.0, (0.30, 0.92), (0.22, 0.88, 0.38, 0.97))]),
            Ph(50, "Todo listo para empezar", V(f"{A}/07a2_ajustes_configuracion_completa.png"),
               P("Prueba completa y control local",
                 "Siete días gratuitos con todas las funciones.",
                 "Cámara opcional: fotos de catálogo y escaneo de vinculación.",
                 "Temas claro y oscuro, y textos que respetan el tamaño del sistema.",
                 note="Android 8.0 o superior · Sin anuncios ni rastreo")),
        ]
    },
    {
        "phases": [
            Ph(0, "Inicio: el centro de control", V(f"{A}/01j_inicio_acordeones_cerrados.png"),
               P("Alertas que te llevan al problema",
                 "Sin existencia, stock crítico o bajo e insumos escasos.",
                 "Próximos a caducar en los siguientes siete días.",
                 "Toca una alerta para abrir directamente los artículos afectados."),
               highlights=[Hi(1, 7, (0.03, 0.35, 0.97, 0.54), "Alertas de inventario")]),
            Ph(10, "Analiza el período que necesitas", V(f"{PROMO}/shot_inicio_claro.png"),
               P("Gráficos y rankings plegables", kind="cards", cards=[
                   ("VENTAS Y GANANCIA", "Ingresos, costo y margen del período."),
                   ("INVENTARIO Y PAGOS", "Categorías, efectivo y transferencias."),
                   ("TOP 3", "Productos, rentabilidad y lento movimiento."),
                   ("EQUIPO Y CLIENTES", "Mejores vendedores y clientes por transferencia."),
               ], note="Períodos: turno, hoy, 7 días, mes, año y más.")),
            Ph(20, "1 · Abrir el turno", V(f"{PROMO}/shot_inicio_claro.png"),
               P("Antes de la primera venta", kind="steps", items=[
                   "Activa el interruptor Turno cerrado.",
                   "Escribe el fondo de caja en CUP; puede ser cero.",
                   "Confirma con ✓ para habilitar Nueva venta y Caja.",
               ], note="Regla contable: no se vende sin un turno abierto."),
               taps=[Tap(22.0, (0.875, 0.134), (0.77, 0.105, 0.96, 0.165))]),
            Ph(30, "2 · Registrar movimientos de caja", V(f"{A}/01j_inicio_acordeones_cerrados.png"),
               P("Cada peso debe tener explicación",
                 "Usa el botón de billetes para Entrada o Salida.",
                 "Escribe importe y un motivo claro de 3 a 60 caracteres.",
                 "Para corregir, registra el movimiento contrario: no se borran.",
                 note="El efectivo esperado = fondo + ventas + entradas − salidas"),
               taps=[Tap(32.0, (0.86, 0.936), (0.76, 0.895, 0.96, 0.98))]),
            Ph(40, "3 · Conteo y conciliación al cerrar", V(f"{A}/01f_inicio_dialogo_cerrar_turno.png", 850, 290),
               P("Cierre en tres pasos", kind="formula", formula="Efectivo → Inventario → Revisión",
                 items=["Cuenta denominaciones y monedas, o usa Cuadra si coincide.",
                        "Revisa solo productos e insumos con movimiento en el turno.",
                        "Explica faltantes o sobrantes y confirma el ajuste."],
                 note="Los elaborados se controlan contando sus insumos reales.")),
            Ph(50, "La jornada queda documentada", V(f"{A}/04s_turno_detalle_cerrado.png"),
               P("Informe integral del turno", kind="cards", cards=[
                   ("RESUMEN", "Apertura, cierre, duración y totales."),
                   ("VENTAS", "Operaciones de productos y servicios."),
                   ("INVENTARIO", "Conteo, conciliación y diferencias."),
                   ("CAJA", "Esperado, contado, billetes y movimientos."),
               ], note="Disponible en Registros → Turnos y exportable a PDF o Excel.")),
        ]
    },
    {
        "phases": [
            Ph(0, "Inventario: todo lo físico del negocio", V(f"{PROMO}/shot_inventario_claro.png"),
               P("Información útil en cada fila",
                 "Foto, nombre, descripción, existencia, unidad y categoría.",
                 "Precio de venta destacado y costo en segundo plano.",
                 "Indicador lateral: normal, bajo, crítico o sin existencia."),
               taps=[Tap(2.0, (0.90, 0.846), (0.82, 0.80, 0.97, 0.89))]),
            Ph(10, "Crear un artículo", V(f"{A}/08j_producto_nuevo.png"),
               P("Catálogo claro y visual",
                 "Toma una foto o elige una imagen de la galería.",
                 "Selecciona una categoría o escribe una nueva.",
                 "Usa Descripción para diferenciar formato, tamaño o presentación.",
                 note="Los campos marcados con * son obligatorios."),
               taps=[Tap(12.0, (0.42, 0.28), (0.34, 0.23, 0.50, 0.33))]),
            Ph(20, "Precios, cantidad y caducidad", V(f"{A}/08l_producto_editar.png"),
               P("Validaciones que evitan pérdidas",
                 "Escribe costo, precio de venta y cantidad inicial.",
                 "La venta debe ser mayor que el costo.",
                 "La fecha de caducidad opcional avisa siete días antes.",
                 "Guarda con el botón ✓ de la barra inferior.")),
            Ph(30, "Buscar y combinar filtros", V(f"{A}/02i_inventario_hoja_filtro.png"),
               P("Encuentra lo que necesitas en segundos", kind="chips",
                 chips=["Estado", "Tipo", "Categoría"],
                 items=["Estado: bajo, crítico, caducidad o sin existencia.",
                        "Tipo: artículos, elaborados o insumos.",
                        "Confirma con ✓ o limpia los filtros para volver atrás."]),
               taps=[Tap(33.0, (0.58, 0.66), (0.50, 0.61, 0.68, 0.71))]),
            Ph(40, "Selección múltiple", V(f"{A}/02f_inventario_seleccion.png"),
               P("Acciones solo sobre lo marcado",
                 "Toca las casillas de una o varias filas.",
                 "Usa Seleccionar todo cuando necesites todo el lote.",
                 "Exportar tomará únicamente los elementos seleccionados."),
               taps=[Tap(43.0, (0.13, 0.26), (0.07, 0.22, 0.20, 0.31))]),
            Ph(50, "Reposición antes de la ruptura de stock", V(f"{A}/02f2_inventario_filtro_critico.png"),
               P("Alertas adaptadas a tu operación",
                 "Define umbrales distintos para productos e insumos.",
                 "Los insumos admiten hasta tres decimales de existencia.",
                 "Filtra por crítico o bajo para preparar la próxima compra.",
                 note="Prevenir faltantes evita ventas detenidas y compras apuradas.")),
        ]
    },
    {
        "phases": [
            Ph(0, "Insumos: las materias primas", V(f"{A}/08n_producto_hoja_insumos.png"),
               P("Medidas que se adaptan a cada receta",
                 "Unidades: kg, g, L, ml o unidades enteras.",
                 "Existencias con hasta tres decimales, por ejemplo 2.500 kg.",
                 "Precio de venta opcional si también ofreces el insumo suelto.")),
            Ph(10, "Receta de UNA unidad", V(f"{A}/08m_producto_elaborado_receta.png", 850, 290, (0, 600, 720, 1900), "Vista ampliada"),
               P("De ingredientes a un elaborado", kind="recipe",
                 note="El costo sale de la receta; tú defines el precio de venta."),
               taps=[Tap(12.0, (0.50, 0.615), (0.39, 0.56, 0.61, 0.67))]),
            Ph(20, "Costo y disponibilidad automáticos", V(f"{A}/02k2_inventario_ficha_elaborado.png", 850, 290),
               P("El ingrediente más escaso marca el límite", kind="formula", formula="Venta → descuento exacto de cada insumo",
                 items=["«Alcanza para N» se recalcula con las existencias.",
                        "Si un ingrediente no alcanza, la venta se bloquea.",
                        "El inventario nunca queda negativo por un elaborado."],
                 note="Menos cálculos manuales y mejor control del margen.")),
            Ph(30, "Servicios: cobra también tu trabajo", V(f"{PROMO}/shot_servicios_claro.png"),
               P("Un catálogo para cada especialidad", kind="chips",
                 chips=["Barbería", "Belleza", "Lavandería", "Reparaciones", "Domicilio"],
                 items=["Cortes, manicure, lavado, instalaciones, fotocopias y más.",
                        "Busca, filtra y crea servicios con el botón +."]),
               taps=[Tap(32.0, (0.90, 0.846), (0.82, 0.80, 0.97, 0.89))]),
            Ph(40, "Crear un servicio", V(f"{A}/09e_servicio_form.png"),
               P("Lo necesario para cobrar y medir",
                 "Foto opcional, nombre, tipo, importe y descripción.",
                 "Agrega insumos que se consumen cada vez que lo prestas.",
                 "Al cobrar, se descuentan materiales y se calcula la ganancia real.")),
            Ph(50, "Productos y servicios bajo el mismo control", V(f"{A}/09d_servicios_ficha.png"),
               P("Una herramienta, muchos modelos de negocio", kind="cards", cards=[
                   ("CAFETERÍA", "Recetas, insumos y disponibilidad."),
                   ("BARBERÍA", "Servicios y consumo de productos."),
                   ("TALLER", "Piezas, materiales y reparaciones."),
                   ("DOMICILIO", "Entregas y catálogo de servicios."),
               ], note="Sin libretas ni hojas de cálculo paralelas.")),
        ]
    },
    {
        "phases": [
            Ph(0, "Nueva venta con el turno abierto", V(f"{A}/01j_inicio_acordeones_cerrados.png"),
               P("Empieza desde Inicio",
                 "Toca el carrito flotante de la esquina inferior izquierda.",
                 "Si no aparece, revisa que el turno esté abierto.",
                 "El total y las existencias se actualizan con cada operación."),
               taps=[Tap(2.0, (0.14, 0.936), (0.03, 0.895, 0.23, 0.98))]),
            Ph(8, "Productos y servicios por separado", V(f"{A}/01e_inicio_dialogo_nueva_venta.png"),
               P("Dos flujos para estadísticas ordenadas", kind="cards", cards=[
                   ("PRODUCTOS", "Artículos físicos y elaborados con control de stock."),
                   ("SERVICIOS", "Trabajos cobrados con insumos opcionales."),
               ], note="Selecciona el tipo de venta para abrir su catálogo.")),
            Ph(16, "Arma el carrito", V(f"{A}/05a_venta_carrito.png"),
               P("Cantidades y total al instante",
                 "Añade artículos y ajusta unidades con − y +.",
                 "Puedes escribir la cantidad o quitar una línea.",
                 "Elige Efectivo o Transferencia y avanza con →."),
               taps=[Tap(18.0, (0.63, 0.335), (0.55, 0.29, 0.72, 0.38))]),
            Ph(24, "Cobro en efectivo", V(f"{A}/05e_venta_comprobante.png"),
               P("Comprobante antes de confirmar",
                 "Revisa artículos, ajustes de precio y total.",
                 "Al confirmar, el importe se suma al efectivo esperado.",
                 "Las existencias se descuentan en ese mismo instante.")),
            Ph(32, "Configurar Pago electrónico", V(f"{A}/06c_pago_lista.png"),
               P("Un teléfono y una cuenta «En uso»",
                 "El teléfono recibe el SMS; la tarjeta recibe el dinero.",
                 "Solo puede haber uno activo de cada tipo.",
                 "Las tarjetas se muestran protegidas con sus últimos dígitos."),
               taps=[Tap(34.0, (0.08, 0.285), (0.03, 0.25, 0.15, 0.33))]),
            Ph(40, "Transfermóvil: QR e importe", V(f"{A}/05f_venta_qr.png"),
               P("El cliente escanea y escribe el total", kind="formula", formula="QR: tarjeta + móvil · Importe: visible debajo",
                 items=["El estándar oficial del QR no transporta el importe.",
                        "El cliente evita escribir los dígitos de la tarjeta.",
                        "Verifica el importe antes de avanzar."],
                 note="Después completa los datos de la transferencia."),
               highlights=[Hi(41, 46, (0.20, 0.43, 0.84, 0.52), "Importe a transferir")]),
            Ph(50, "Validación del SMS y clientes fijos", V(f"{A}/05h_venta_datos_cliente.png"),
               P("Tres vías para registrar la transacción", kind="chips",
                 chips=["Pegar SMS", "Compartir", "Notificaciones opcionales"],
                 items=["Cliente fijo autocompleta datos en próximas compras.",
                        "Shalev extrae el número y compara el importe.",
                        "Si hay diferencia, muestra un aviso antes de confirmar."],
                 note="La captura automática es opcional y no lee tu buzón antiguo."),
               taps=[Tap(52.0, (0.50, 0.65), (0.41, 0.60, 0.59, 0.70))]),
        ]
    },
    {
        "phases": [
            Ph(0, "Reglas automáticas de precios", V(f"{A}/06l_precios_hoja_formulario.png"),
               P("Descuentos y recargos sin editar el catálogo",
                 "Elige Sube o Baja y escribe el porcentaje.",
                 "Aplica a cualquier pago, solo efectivo o transferencia.",
                 "Importe mínimo opcional para ventas al por mayor.",
                 "Selecciona productos y revisa la vista previa.")),
            Ph(10, "Activa o pausa cada estrategia", V(f"{A}/06i_precios_lista.png"),
               P("Control total sobre las reglas",
                 "Enciende o apaga cada ajuste con su interruptor.",
                 "El precio guardado del producto no cambia.",
                 "Ninguna rebaja puede quedar por debajo del costo."),
               taps=[Tap(12.0, (0.875, 0.325), (0.79, 0.29, 0.96, 0.36))]),
            Ph(20, "Registros: seis vistas especializadas", V(f"{A}/04a_registros_ventas.png"),
               P("Un historial ordenado por fecha", kind="chips",
                 chips=["Ventas", "Servicios", "Transferencias", "Movimientos", "Clientes", "Turnos"],
                 items=["Filtra por período, importe o vendedor.",
                        "En Movimientos, filtra por período y existencias."],
                 note="Toca una fila para abrir su ficha completa."),
               taps=[Tap(22.0, (0.78, 0.04), (0.71, 0.015, 0.85, 0.075))]),
            Ph(28, "Transferencias y clientes", V(f"{A}/04b_registros_transferencias.png"),
               P("Evidencia bancaria asociada a la venta",
                 "Fecha, número de transacción, cliente, vendedor e importe.",
                 "La ficha de un cliente fijo muestra todo su historial.",
                 "Puedes editar sus datos o quitarlo del directorio.")),
            Ph(36, "Modificar o anular con trazabilidad", V(f"{A}/04o_registros_ficha_venta.png"),
               P("Correcciones controladas", kind="cards", cards=[
                   ("MODIFICAR", "Cantidades, artículos, pago o transacción con motivo."),
                   ("ANULAR", "Deja de sumar y devuelve existencias automáticamente."),
               ], items=["Solo con turno abierto y desde la app Principal."],
                 note="Si hubo transferencia, la devolución bancaria se hace en Transfermóvil."),
               taps=[Tap(38.0, (0.50, 0.30), (0.07, 0.26, 0.94, 0.34))]),
            Ph(44, "Editar un ajuste de existencias", V(f"{A}/04q_registros_ficha_movimiento.png"),
               P("Movimientos coherentes durante el turno",
                 "Abre un movimiento de tipo Ajuste y toca Editar ajuste.",
                 "Escribe la nueva cantidad y el motivo.",
                 "Se recalcula la existencia posterior de ese turno.")),
            Ph(52, "Los turnos cerrados quedan sellados", V(f"{A}/04s_turno_detalle_cerrado.png"),
               P("Historia contable protegida",
                 "Consulta resumen, ventas, inventario y caja cuando lo necesites.",
                 "Los turnos cerrados no admiten modificaciones.",
                 "Así los arqueos históricos conservan su cuadre.",
                 note="Toda corrección abierta conserva motivo y enlace con la original.")),
        ]
    },
    {
        "phases": [
            Ph(0, "Exportar una selección o lo visible", V(f"{A}/02f_inventario_seleccion.png"),
               P("Tú decides el alcance del documento",
                 "Con filas marcadas: exporta solo la selección.",
                 "Sin selección: exporta lo visible según búsqueda o filtro.",
                 "Después elige Guardar en el teléfono o Enviar."),
               taps=[Tap(2.0, (0.13, 0.26), (0.07, 0.22, 0.20, 0.31))]),
            Ph(10, "Documentos para administración", V(f"{A}/02j_inventario_hoja_exportar.png"),
               P("PDF y Excel para uso interno", kind="formats",
                 items=["Inventario: existencias, costos, precios y caducidad.",
                        "Registros: tablas con importes CUP listos para sumar.",
                        "Páginas A4 automáticas, horizontales si hay más de 5 columnas."],
                 note="Revisa el destinatario antes de compartir costos o ganancias."),
               taps=[Tap(13.0, (0.75, 0.45), (0.67, 0.40, 0.82, 0.50))]),
            Ph(22, "Material promocional para clientes", V(".cache/shalev_video_reel/tarjetas_ejemplo.png", 1030, 285, frame="card", label="Ejemplo visual de tarjetas promocionales"),
               P("Imágenes sin costos ni existencias", kind="cards", cards=[
                   ("LISTA DE PRECIOS", "Hasta 25 filas por imagen, ordenada por categoría."),
                   ("TARJETAS", "Hasta 6 productos con foto, nombre y precio."),
                   ("ENCABEZADO", "Promoción opcional de hasta 160 caracteres."),
                   ("COMPARTIR", "Lista para WhatsApp, Telegram y redes sociales."),
               ])),
            Ph(32, "Tarjeta individual de un producto", V(f"{A}/02l_inventario_hoja_compartir_ficha.png"),
               P("Del catálogo a una publicación",
                 "Genera una imagen o tarjeta desde la ficha del producto.",
                 "Incluye foto, nombre, categoría y precio de venta.",
                 "Nunca expone el costo ni la cantidad disponible.")),
            Ph(42, "Informe integral de un turno", V(f"{A}/04s_turno_detalle_cerrado.png"),
               P("Un solo documento para revisar la jornada", kind="chips",
                 chips=["Resumen", "Arqueo", "Inventario", "Ventas", "Movimientos"],
                 items=["Disponible en PDF y Excel desde el detalle del turno.",
                        "Conserva el desglose de billetes, entradas y salidas."])),
            Ph(50, "Privacidad al compartir", V(f"{A}/04m_registros_hoja_exportar.png"),
               P("Documentos comerciales ≠ documentos contables",
                 "Para clientes: imágenes y tarjetas sin costos.",
                 "Uso interno: PDF y Excel con información administrativa.",
                 "Transferencias incluye nombre, carné completo y teléfono.",
                 note="Comprueba siempre quién recibirá el archivo antes de enviarlo.")),
        ]
    },
    {
        "phases": [
            Ph(0, "Principal y secundarias en red local", V(f"{A}/07p_vinculacion_principal_turnos.png", 850, 290),
               P("Vinculación por QR", kind="network",
                 note="Misma Wi-Fi del local o Zona Wi-Fi de la Principal · Sin datos móviles"),
               taps=[Tap(2.0, (0.875, 0.94), (0.79, 0.89, 0.96, 0.985))]),
            Ph(10, "Permisos y cobro por empleado", V(f"{A}/07q_vinculacion_ficha_cobro_cierre.png"),
               P("El dueño mantiene el control",
                 "Permisos iniciales: vender productos, servicios y exportar.",
                 "Opcionales: editar inventario/servicios y cambiar precios.",
                 "Asigna la tarjeta que recibirá las transferencias del empleado.",
                 "El SMS llega al móvil del empleado para verificar al instante.")),
            Ph(20, "La app del empleado", V(f"{A}/07s_vinculacion_secundaria.png"),
               P("Catálogo y permisos siempre actualizados",
                 "Al vincularse descarga productos, servicios, precios y fotos.",
                 "La Principal muestra «Conectada ahora» en la misma red.",
                 "Cada licencia cubre de cero a diez apps secundarias.")),
            Ph(30, "Sincronización manual y automática", V(f"{A}/07s_vinculacion_secundaria.png", 850, 290),
               P("Ventas y catálogo viajan entre teléfonos", kind="network",
                 items=["Sincronizar ahora envía operaciones y recibe novedades."],
                 note="También sincroniza automáticamente al abrir y solicitar el cierre."),
               taps=[Tap(32.0, (0.50, 0.315), (0.40, 0.27, 0.60, 0.36))]),
            Ph(40, "Si se interrumpe la Wi-Fi", V(f"{A}/07r_vinculacion_pedir_cierre.png"),
               P("La venta no se detiene",
                 "El empleado puede seguir vendiendo durante su turno abierto.",
                 "Las operaciones quedan pendientes de enviar.",
                 "Para abrir otro turno o cerrar, vuelve a sincronizar.",
                 note="La conexión local evita gastar saldo durante la jornada.")),
            Ph(50, "Cierre revisado por el encargado", V(f"{A}/07q_vinculacion_ficha_cobro_cierre.png"),
               P("Aprobar o pedir un nuevo conteo", kind="steps", items=[
                   "El empleado declara efectivo e inventario.",
                   "El encargado compara esperado, contado y diferencias.",
                   "Aprueba para aplicar ajustes o rechaza para repetir el conteo.",
               ], note="La Principal centraliza el inventario después de aprobar.")),
        ]
    },
    {
        "phases": [
            Ph(0, "Privacidad y acceso con clave", V(f"{A}/07a2_ajustes_configuracion_completa.png"),
               P("Protección dentro y fuera de la app",
                 "Huella, rostro, PIN o patrón al abrir desde cero.",
                 "Tras diez minutos fuera, vuelve a pedir acceso.",
                 "El carrito permanece intacto al desbloquear.",
                 "Pantallas sensibles bloquean capturas y vista en Recientes.")),
            Ph(10, "Exportar un respaldo protegido", V(f"{A}/09a_respaldo_paso1.png", 1040, 270, (0, 0, 720, 1750), "Vista ampliada"),
               P("Archivo .shalev con contraseña",
                 "Usa al menos ocho caracteres y repite la contraseña.",
                 "Anótala en un lugar seguro: sin ella no se puede recuperar.",
                 "El archivo se llama Shalev_respaldo_AAAA-MM-DD.shalev."),
               taps=[Tap(13.0, (0.85, 0.337), (0.76, 0.29, 0.94, 0.38))]),
            Ph(20, "Guardar o enviar la copia", V(f"{A}/09c_respaldo_paso2.png", 850, 290),
               P("Qué incluye el respaldo", kind="backup",
                 note="No incluye fotos, licencia activa ni códigos QR activos de vinculación.")),
            Ph(30, "Importar al cambiar de teléfono", V(f"{A}/09g_respaldo_dialogo_importar.png"),
               P("Mudanza en orden", kind="steps", items=[
                   "Exporta una copia reciente en el teléfono anterior.",
                   "Instala como Principal en el nuevo y elige el archivo.",
                   "Escribe la contraseña y restaura tus datos.",
               ], note="Importar reemplaza TODOS los datos actuales del teléfono."),
               taps=[Tap(33.0, (0.50, 0.55), (0.38, 0.49, 0.62, 0.61))]),
            Ph(40, "Comprobación antes de reemplazar datos", V(f"{A}/09k_respaldo_incompleto.png"),
               P("La integridad se valida primero",
                 "Detecta archivos incompletos, dañados o de versión incompatible.",
                 "Si la copia no es válida, tus datos actuales quedan intactos.",
                 "Después de restaurar, recupera la licencia con el ID anterior.",
                 note="Genera nuevos QR para las secundarias y repón las fotos si las necesitas.")),
            Ph(50, "Un hábito que protege tu negocio", V(f"{A}/09j_respaldo_importado.png"),
               P("Respaldo y privacidad en la rutina",
                 "Shalev recuerda exportar una copia después de 30 días.",
                 "Las tarjetas muestran solo los últimos cuatro dígitos.",
                 "Soporte jamás debe pedir tu contraseña, PIN o códigos privados.",
                 note="Guarda copias periódicas también fuera del teléfono.")),
        ]
    },
    {
        "phases": [
            Ph(0, "Prueba y licencia", V(f"{A}/07n_licencia_activa.png"),
               P("Siete días para evaluarlo todo",
                 "La prueba incluye hasta cinco apps secundarias.",
                 "La licencia se gestiona en la Principal y cubre al equipo.",
                 "Elige de cero a diez secundarias según tus necesidades.")),
            Ph(8, "Planes disponibles", V(f"{A}/07k_licencia_paso2_solicitar.png", 1040, 270, (0, 0, 720, 1580), "Tarifas de la versión 1.0"),
               P("Elige el período de licencia", kind="cards", cards=[
                   ("MENSUAL", "6,000 CUP · Principal"),
                   ("SEMESTRAL", "30,000 CUP · Principal"),
                   ("ANUAL", "50,000 CUP · Principal"),
                   ("PERPETUA", "90,000 CUP · Principal"),
               ], note="Cada app secundaria adicional suma su importe correspondiente.")),
            Ph(16, "Solicitud por WhatsApp o SMS", V(f"{A}/07k_licencia_paso2_solicitar.png", 1040, 270, (0, 1450, 720, 3150), "Solicitud · vista ampliada"),
               P("Tres pasos guiados",
                 "Revisa tus datos y elige el tipo de licencia.",
                 "Ajusta las secundarias y envía la solicitud.",
                 "Pega la respuesta de activación y confirma con ✓.",
                 note="Renovar antes de vencer suma los días nuevos a los que te quedan."),
               taps=[Tap(19.0, (0.50, 0.68), (0.41, 0.63, 0.59, 0.73))]),
            Ph(24, "Activación y renovación", V(f"{A}/07l_licencia_paso3_activar.png"),
               P("Activar sin perder días",
                 "Copia completo el mensaje de activación recibido.",
                 "Pégalo o compártelo directamente hacia Shalev.",
                 "Confirma y conserva el mismo vencimiento al recuperar otro teléfono.")),
            Ph(32, "Actualizaciones seguras", V(f"{A}/07a2_ajustes_configuracion_completa.png"),
               P("Mejoras sin descuidar tus datos",
                 "Búsqueda manual en Ajustes → Actualizar o chequeo semanal.",
                 "Antes de instalar se crea un respaldo automático.",
                 "La Principal distribuye la versión por Wi-Fi a las secundarias.",
                 note="Buscar actualizaciones sí requiere conexión; vender no la requiere."),
               taps=[Tap(34.0, (0.50, 0.80), (0.10, 0.75, 0.90, 0.85))]),
            Ph(40, "Ayuda y soporte dentro de la app", V(f"{A}/07g_ayuda.png"),
               P("Respuestas incluso sin conexión",
                 "Ajustes → Ayuda: manual resumido por temas y esquemas.",
                 "Ajustes → Soporte: contacto directo con el desarrollador.",
                 "Recupera configuración, licencia y respaldo desde Ajustes.")),
            Ph(48, "Beneficios que se notan cada día", V(f"{A}/07h_soporte.png", 850, 290),
               P("Menos trabajo repetido, más control", kind="benefits",
                 note="Cafeterías · Mercados · Barberías · Talleres · Lavanderías · Domicilio")),
            Ph(55, "Empieza hoy", layout="outro"),
        ]
    },
]
for i, (scene, guide) in enumerate(zip(SCENES, GUIDE), 1):
    scene["n"] = i
    scene["title"] = guide["titulo"]
    scene["narration"] = guide["narracion"]
    scene["audio"] = f"{i:02d}.wav"
    scene["start"] = (i-1)*CHAPTER
    scene["duration"] = CHAPTER


def background(kind="light"):
    if kind == "dark":
        c1, c2 = np.array(hexrgb(DARK)), np.array(hexrgb("#17355A"))
    else:
        c1, c2 = np.array(hexrgb("#F8FAFD")), np.array(hexrgb("#E7EEF8"))
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    mix = np.clip(0.35*xx/W + 0.65*yy/H, 0, 1)[..., None]
    arr = (c1*(1-mix)+c2*mix).astype(np.uint8)
    im = Image.fromarray(arr, "RGB")
    d = ImageDraw.Draw(im, "RGBA")
    if kind == "dark":
        d.ellipse((-350, 1150, 650, 2150), fill=(*hexrgb(TEAL), 35))
        d.ellipse((480, -400, 1450, 570), fill=(*hexrgb(BLUE), 65))
        for y in range(30, H, 90):
            d.line((0, y, W, y), fill=(255, 255, 255, 5), width=1)
    else:
        d.ellipse((650, -300, 1370, 420), fill=(*hexrgb(CYAN), 24))
        d.ellipse((-400, 1400, 300, 2100), fill=(*hexrgb(ORANGE), 13))
        d.rounded_rectangle((24, 40, 34, 185), radius=5, fill=BLUE)
    return im


BG_LIGHT, BG_DARK = background("light"), background("dark")


def create_promo_asset():
    """Ejemplo ilustrativo con las fotos y los datos de demostración del manual.

    No se presenta como una captura de exportación: es un diagrama del material
    que el usuario puede crear, con fotos, nombres y precios, sin costos ni stock.
    """
    path = ROOT / ".cache/shalev_video_reel/tarjetas_ejemplo.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im = Image.new("RGB", (1080, 1200), "#F8F9FA")
    d = ImageDraw.Draw(im)
    d.text((540, 112), "Cafetería María · ¡Ofertas del fin de semana!", font=font(25, True), fill=MUTED, anchor="mm")
    products = [
        ("tools/seed_demo/seed_fotos/prod40.jpg", "Sándwich de jamón", "Elaborado", "220.00 CUP"),
        ("tools/seed_demo/seed_fotos/prod39.jpg", "Refresco natural de mango", "Elaborado", "120.00 CUP"),
        ("tools/seed_demo/seed_fotos/prod38.jpg", "Capuchino", "Elaborado", "180.00 CUP"),
    ]
    for i, (photo_path, name, category, price) in enumerate(products):
        x = 35+i*350
        card(d, (x, 175, x+310, 1050), fill=WHITE, outline="#D8E0EC", radius=24)
        photo = Image.open(ROOT/photo_path).convert("RGB")
        photo = ImageOps.fit(photo, (278, 390), Image.Resampling.LANCZOS)
        mask = Image.new("L", photo.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, photo.width-1, photo.height-1), radius=18, fill=255)
        im.paste(photo, (x+16, 195), mask)
        y = paragraph(d, (x+20, 625), name, size=27, fill=INK, bold=True, max_width=270, line_gap=8)
        d.text((x+20, y+20), category, font=font(22), fill=MUTED)
        d.text((x+20, y+90), price, font=font(29, True), fill=BLUE)
        pill(d, (x+20, 945, x+290, 1000), "PARA CLIENTES", fill=PALE_CYAN, color=TEAL, size=18)
    d.text((540, 1125), "Fotos · Nombres · Categorías · Precios | Sin costos ni existencias", font=font(22, True), fill=TEAL, anchor="mm")
    im.save(path, quality=95)


def resolve(spec):
    spec = dict(spec)
    crop = tuple(spec["crop"]) if spec.get("crop") else None
    spec["crop"] = crop
    asset, _ = base.phone_asset(spec["path"], crop, spec["h"])
    if spec.get("x") is None:
        spec["x"] = (W-asset.width)//2
    return spec


def geom(spec):
    return base.phone_geom(spec)


def paste_visual(frame, raw_spec):
    spec = resolve(raw_spec)
    if spec.get("frame") == "phone":
        base.paste_phone(frame, spec)
        return spec
    # Las exportaciones se presentan como documentos, no como pantallas de teléfono.
    x, y, w, h = geom(spec)
    asset, mask = base.phone_asset(spec["path"], spec.get("crop"), spec["h"])
    d = ImageDraw.Draw(frame, "RGBA")
    d.rounded_rectangle((x+12, y+16, x+w+12, y+h+16), radius=40, fill=(16, 37, 75, 40))
    d.rounded_rectangle((x-8, y-8, x+w+8, y+h+8), radius=48, fill=WHITE)
    frame.paste(asset, (x, y), mask)
    if spec.get("label"):
        pill(d, (x+30, y+20, x+w-30, y+72), spec["label"], fill=(16, 37, 75, 230), color=WHITE, size=20)
    return spec


def draw_header(frame, scene, ph):
    d = ImageDraw.Draw(frame)
    d.text((60, 42), f"CAPÍTULO {scene['n']:02d} · SHALEV", font=font(20, True), fill=BLUE)
    d.text((60, 83), scene["title"], font=font(42, True), fill=INK)
    d.rounded_rectangle((60, 151, 230, 158), radius=3, fill=ORANGE)
    pill(d, (900, 42, 1020, 88), f"{scene['n']:02d} / 10", fill=PALE_BLUE, color=BLUE, size=18)
    d.text((60, 197), ph["heading"], font=font(31, True), fill=BLUE)


def note_pill(d, note, y, fill=PALE_BLUE, color=BLUE, size=20):
    if not note:
        return
    # Hasta dos líneas, sin ocultar el resto del panel.
    lines = base.wrap_lines(d, note, font(size, True), 870)
    h = 42 + max(0, len(lines)-1)*27
    d.rounded_rectangle((80, y-h, 1000, y), radius=20, fill=fill)
    for i, line in enumerate(lines):
        d.text((540, y-h+20+i*27), line, font=font(size, True), fill=color, anchor="mm")


def draw_bullets(d, items, x, y, max_width=850, size=26, gap=13, bottom=None):
    for item in items:
        d.ellipse((x, y+2, x+30, y+32), fill=PALE_CYAN)
        d.text((x+15, y+16), "✓", font=font(19, True), fill=TEAL, anchor="mm")
        y = paragraph(d, (x+46, y), item, size=size, fill=INK, max_width=max_width-46, line_gap=7) + gap
    return y


def draw_panel(frame, ph, scene):
    panel = ph.get("panel")
    if not panel:
        return
    spec = resolve(ph["screen"]) if ph.get("screen") else None
    default_y = (spec["y"]+spec["h"]+40) if spec else 300
    y0 = default_y
    y1 = 1860
    d = ImageDraw.Draw(frame)
    card(d, (50, y0, 1030, y1), fill=WHITE, outline="#D8E0EC", radius=28)
    kind = panel["kind"]
    d.text((88, y0+28), panel["title"], font=font(29, True), fill=BLUE)
    content_y = y0+88
    note = panel.get("note", "")
    note_reserved = 90 if note else 20
    if kind == "bullets":
        size = 25 if len(panel["items"]) >= 4 else 27
        draw_bullets(d, panel["items"], 92, content_y, size=size, bottom=y1-note_reserved)
        note_pill(d, note, y1-22)
    elif kind == "cards":
        cards = panel["cards"]
        for i, (title, detail) in enumerate(cards):
            x = 82+(i%2)*458
            y = content_y+(i//2)*150
            card(d, (x, y, x+434, y+132), fill=PALE_BLUE if i%2==0 else PALE_CYAN,
                 outline=PALE_BLUE if i%2==0 else PALE_CYAN, radius=20, shadow=False)
            d.text((x+20, y+17), title, font=font(21, True), fill=BLUE if i%2==0 else TEAL)
            paragraph(d, (x+20, y+55), detail, size=21, fill=INK, max_width=394, line_gap=5)
        if panel["items"]:
            draw_bullets(d, panel["items"], 92, content_y+((len(cards)+1)//2)*150+5, size=23)
        note_pill(d, note, y1-22)
    elif kind == "chips":
        x, y = 88, content_y
        for i, label in enumerate(panel["chips"]):
            w = 54+len(label)*13
            if x+w > 990:
                x, y = 88, y+60
            pill(d, (x, y, x+w, y+46), label, fill=PALE_BLUE if i%2==0 else PALE_CYAN,
                 color=BLUE if i%2==0 else TEAL, size=19)
            x += w+12
        draw_bullets(d, panel["items"], 92, y+75, size=24, gap=10)
        note_pill(d, note, y1-22)
    elif kind == "steps":
        for i, item in enumerate(panel["items"], 1):
            y = content_y+(i-1)*103
            d.ellipse((90, y, 144, y+54), fill=BLUE)
            d.text((117, y+26), str(i), font=font(23, True), fill=WHITE, anchor="mm")
            paragraph(d, (166, y+3), item, size=25, fill=INK, max_width=790, line_gap=6)
        note_pill(d, note, y1-22, fill=PALE_ORANGE, color=ORANGE_DARK)
    elif kind == "formula":
        d.rounded_rectangle((82, content_y, 998, content_y+92), radius=22, fill=NAVY)
        lines = base.wrap_lines(d, panel["formula"], font(25, True), 850)
        for i, line in enumerate(lines):
            d.text((540, content_y+29+i*32), line, font=font(25, True), fill=CYAN if i==0 else WHITE, anchor="mm")
        draw_bullets(d, panel["items"], 92, content_y+122, size=24, gap=9)
        note_pill(d, note, y1-22)
    elif kind == "formats":
        boxes = [(82, "USO INTERNO", "PDF · EXCEL", NAVY, CYAN), (550, "PARA CLIENTES", "PNG · TARJETAS", TEAL, PALE_CYAN)]
        for x, title, formats, color, accent in boxes:
            d.rounded_rectangle((x, content_y, x+448, content_y+118), radius=22, fill=color)
            d.text((x+22, content_y+20), title, font=font(20, True), fill=accent)
            d.text((x+22, content_y+61), formats, font=font(28, True), fill=WHITE)
        draw_bullets(d, panel["items"], 92, content_y+148, size=23, gap=8)
        note_pill(d, note, y1-22, fill=PALE_ORANGE, color=ORANGE_DARK)
    elif kind == "recipe":
        ingredients = [("Harina", "0.25 kg"), ("Puré", "0.10 kg"), ("Queso", "0.08 kg")]
        for i, (name, qty) in enumerate(ingredients):
            y = content_y+i*112
            card(d, (82, y, 440, y+88), fill=PALE_CYAN, outline=PALE_CYAN, radius=18, shadow=False)
            d.text((104, y+13), name, font=font(21, True), fill=TEAL)
            d.text((418, y+48), qty, font=font(24, True), fill=INK, anchor="rm")
            arrow(d, (450, y+44), (570, content_y+160), color=TEAL, width=4)
        card(d, (590, content_y+45, 970, content_y+300), fill=PALE_ORANGE, outline=ORANGE, radius=26, shadow=False)
        d.text((780, content_y+100), "1 PIZZA", font=font(34, True), fill=ORANGE_DARK, anchor="mm")
        d.text((780, content_y+158), "Costo: 275 CUP", font=font(25, True), fill=INK, anchor="mm")
        d.text((780, content_y+208), "Precio: 400 CUP", font=font(24), fill=INK, anchor="mm")
        pill(d, (650, content_y+244, 910, content_y+288), "ALCANZA PARA N", fill=WHITE, color=ORANGE_DARK, size=18)
        note_pill(d, note, y1-22)
    elif kind == "network":
        # El diagrama se dibuja dentro de la tarjeta y sus paquetes se animan después.
        d.rounded_rectangle((90, content_y+95, 390, content_y+250), radius=24, fill=PALE_BLUE, outline=BLUE, width=3)
        d.text((240, content_y+142), "PRINCIPAL", font=font(23, True), fill=BLUE, anchor="mm")
        d.text((240, content_y+195), "Datos · Licencia", font=font(20), fill=INK, anchor="mm")
        for i, label in enumerate(["EMPLEADO 1", "EMPLEADO 2", "+ HASTA 10"]):
            y = content_y+i*125
            d.rounded_rectangle((650, y, 960, y+96), radius=22, fill=PALE_CYAN, outline=TEAL, width=3)
            d.text((805, y+46), label, font=font(21, True), fill=TEAL, anchor="mm")
            arrow(d, (400, content_y+170), (635, y+48), color=BLUE, width=4)
        if panel["items"]:
            draw_bullets(d, panel["items"], 92, content_y+405, size=23)
        note_pill(d, note, y1-22)
    elif kind == "backup":
        stages = [("1", "EXPORTAR", "Datos del negocio"), ("2", "GUARDAR", "Descargas o enviar"), ("3", "RESTAURAR", "Nuevo teléfono")]
        for i, (num, title, detail) in enumerate(stages):
            x = 82+i*302
            card(d, (x, content_y+30, x+274, content_y+260), fill=PALE_BLUE if i!=1 else PALE_CYAN,
                 outline=PALE_BLUE if i!=1 else PALE_CYAN, radius=20, shadow=False)
            d.ellipse((x+20, content_y+50, x+68, content_y+98), fill=BLUE)
            d.text((x+44, content_y+73), num, font=font(20, True), fill=WHITE, anchor="mm")
            d.text((x+20, content_y+123), title, font=font(21, True), fill=BLUE)
            paragraph(d, (x+20, content_y+165), detail, size=21, fill=INK, max_width=230, line_gap=5)
            if i<2:
                arrow(d, (x+277, content_y+145), (x+298, content_y+145), color=ORANGE_DARK, width=4, head=10)
        paragraph(d, (92, content_y+305), "Incluye productos, recetas, servicios, clientes, turnos, ventas, movimientos y configuración.", size=24, fill=INK, max_width=860)
        note_pill(d, note, y1-22, fill=PALE_ORANGE, color=ORANGE_DARK)
    elif kind == "benefits":
        benefits = [("Menos errores", "Totales y vuelto"), ("Inventario al día", "Recetas y alertas"),
                    ("Caja cuadrada", "Turnos y arqueos"), ("Cobros verificables", "QR y transacciones"),
                    ("Mejores decisiones", "Ganancias y rankings")]
        for i, (title, detail) in enumerate(benefits):
            x = 82+(i%2)*458
            y = content_y+(i//2)*128
            d.ellipse((x, y+8, x+46, y+54), fill=BLUE if i%2==0 else TEAL)
            d.text((x+23, y+30), "✓", font=font(22, True), fill=WHITE, anchor="mm")
            d.text((x+62, y+2), title, font=font(23, True), fill=INK)
            d.text((x+62, y+42), detail, font=font(20), fill=MUTED)
        note_pill(d, note, y1-22)
    else:
        raise ValueError(kind)


def draw_intro(frame, scene, ph):
    d = ImageDraw.Draw(frame, "RGBA")
    logo = Image.open(ROOT / "Android/play_store/icono_512.png").convert("RGB").resize((270, 270), Image.Resampling.LANCZOS)
    mask = Image.new("L", (270, 270), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 269, 269), radius=62, fill=255)
    frame.paste(logo, (405, 130), mask)
    d.text((540, 455), "SHALEV", font=font(82, brand=True), fill=WHITE, anchor="mm")
    d.text((540, 545), "MANUAL COMPLETO · 10 MINUTOS", font=font(25, True), fill=CYAN, anchor="mm")
    d.text((540, 615), "Vende. Controla. Crece.", font=font(39, True), fill=WHITE, anchor="mm")
    paste_visual(frame, ph["screen"])
    pill(d, (90, 1570, 520, 1635), "100 % SIN INTERNET", fill=CYAN, color=DARK, size=23)
    pill(d, (560, 1570, 990, 1635), "7 DÍAS DE PRUEBA", fill=ORANGE_DARK, color=WHITE, size=23)
    d.text((540, 1715), "Punto de venta · Inventario · Servicios", font=font(27), fill="#DCE6FA", anchor="mm")
    d.text((540, 1770), "Para pequeños negocios", font=font(25), fill="#AFC3E4", anchor="mm")


def draw_outro(frame, scene, ph):
    d = ImageDraw.Draw(frame, "RGBA")
    logo = Image.open(ROOT / "Android/play_store/icono_512.png").convert("RGB").resize((300, 300), Image.Resampling.LANCZOS)
    mask = Image.new("L", (300, 300), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 299, 299), radius=68, fill=255)
    frame.paste(logo, (390, 260), mask)
    d.text((540, 650), "SHALEV", font=font(90, brand=True), fill=WHITE, anchor="mm")
    d.text((540, 775), "Vende mejor. Controla más.", font=font(45, True), fill=WHITE, anchor="mm")
    d.text((540, 855), "Haz crecer tu negocio.", font=font(34), fill=CYAN, anchor="mm")
    card(d, (100, 990, 980, 1280), fill="#142C4D", outline="#36557D", radius=30, shadow=False)
    for i, txt in enumerate(["Menos errores", "Inventario y caja al día", "Cobros y decisiones con datos"]):
        d.ellipse((170, 1045+i*75, 210, 1085+i*75), fill=TEAL)
        d.text((190, 1064+i*75), "✓", font=font(21, True), fill=WHITE, anchor="mm")
        d.text((240, 1047+i*75), txt, font=font(27, True), fill=WHITE)
    pill(d, (180, 1390, 900, 1470), "7 DÍAS DE PRUEBA GRATUITA", fill=ORANGE_DARK, color=WHITE, size=27)
    d.text((540, 1560), "ANDROID 8.0 O SUPERIOR", font=font(23, True), fill="#C4D4EE", anchor="mm")
    d.text((540, 1660), "TU NEGOCIO, BAJO CONTROL", font=font(22, True), fill=CYAN, anchor="mm")


def build_static(scene_idx, phase_idx):
    scene, ph = SCENES[scene_idx], SCENES[scene_idx]["phases"][phase_idx]
    layout = ph["layout"]
    frame = (BG_DARK if layout in ("intro", "outro") else BG_LIGHT).copy()
    if layout == "intro":
        draw_intro(frame, scene, ph)
    elif layout == "outro":
        draw_outro(frame, scene, ph)
    else:
        draw_header(frame, scene, ph)
        if ph.get("screen"):
            paste_visual(frame, ph["screen"])
        draw_panel(frame, ph, scene)
    return frame


_CACHE = OrderedDict()


def cached(scene_idx, phase_idx):
    key = (scene_idx, phase_idx)
    if key in _CACHE:
        _CACHE.move_to_end(key)
        return _CACHE[key]
    im = build_static(scene_idx, phase_idx)
    _CACHE[key] = im
    if len(_CACHE) > 14:
        _CACHE.popitem(last=False)
    return im


def phase_index(scene, local_t):
    idx = 0
    for i, ph in enumerate(scene["phases"]):
        if local_t+1e-6 >= ph["at"]:
            idx = i
    return idx


def draw_taps(frame, scene, pidx, local_t):
    ph = scene["phases"][pidx]
    if not ph.get("screen"):
        return
    spec = resolve(ph["screen"])
    x, y, w, h = geom(spec)
    d = ImageDraw.Draw(frame, "RGBA")
    for hi in ph["highlights"]:
        if not (hi["start"] <= local_t <= hi["end"]):
            continue
        b = hi["box"]
        box = (x+b[0]*w, y+b[1]*h, x+b[2]*w, y+b[3]*h)
        pulse = 0.5+0.5*math.sin(local_t*math.pi*2.2)
        d.rounded_rectangle(box, radius=18, outline=(*hexrgb(ORANGE), int(180+60*pulse)), width=6)
        if hi.get("label"):
            ly = max(20, box[1]-50)
            pill(d, (max(20, box[0]-10), ly, min(W-20, box[0]+300), ly+40), hi["label"],
                 fill=(180, 84, 26, 240), color=WHITE, size=17)
    for tap in ph["taps"]:
        dt = local_t-tap["at"]
        if not (0 <= dt <= 1.25):
            continue
        cx, cy = x+tap["xy"][0]*w, y+tap["xy"][1]*h
        if tap.get("box"):
            b = tap["box"]
            d.rounded_rectangle((x+b[0]*w, y+b[1]*h, x+b[2]*w, y+b[3]*h), radius=17,
                                outline=(*hexrgb(ORANGE), 235), width=6)
        p = ease(dt/1.05)
        r = 22+55*p
        d.ellipse((cx-r, cy-r, cx+r, cy+r), outline=(*hexrgb(ORANGE), int(245*(1-p))), width=7)
        d.ellipse((cx-18, cy-18, cx+18, cy+18), fill=(255, 255, 255, 230),
                  outline=(*hexrgb(ORANGE_DARK), 255), width=5)
        lx = min(max(cx-55, 15), W-145)
        ly = cy-92 if cy > 145 else cy+48
        pill(d, (lx, ly, lx+110, ly+40), "TOCA", fill=(*hexrgb(ORANGE_DARK), 245), color=WHITE, size=18)


def draw_special(frame, scene_idx, pidx, local_t):
    scene, ph = SCENES[scene_idx], SCENES[scene_idx]["phases"][pidx]
    d = ImageDraw.Draw(frame, "RGBA")
    if ph["layout"] == "intro":
        for i in range(3):
            p = (local_t*0.28+i/3) % 1
            r = 170+120*p
            d.ellipse((540-r, 265-r, 540+r, 265+r), outline=(*hexrgb(CYAN), int(55*(1-p))), width=5)
    elif ph["layout"] == "outro":
        for i in range(3):
            p = (local_t*0.32+i/3) % 1
            r = 190+160*p
            d.ellipse((540-r, 410-r, 540+r, 410+r), outline=(*hexrgb(CYAN), int(55*(1-p))), width=5)
    elif ph.get("panel") and ph["panel"]["kind"] == "recipe":
        spec = resolve(ph["screen"])
        content_y = spec["y"]+spec["h"]+40+88
        for i in range(3):
            y0 = content_y+i*112+44
            p = (local_t*0.48+i*0.24) % 1
            cx, cy = lerp(450, 570, p), lerp(y0, content_y+160, p)
            d.ellipse((cx-8, cy-8, cx+8, cy+8), fill=(*hexrgb(ORANGE), 220))
    elif ph.get("panel") and ph["panel"]["kind"] == "network":
        spec = resolve(ph["screen"])
        content_y = spec["y"]+spec["h"]+40+88
        for i in range(3):
            end_y = content_y+i*125+48
            p = (local_t*0.42+i*0.29) % 1
            cx, cy = lerp(400, 635, p), lerp(content_y+170, end_y, p)
            d.ellipse((cx-9, cy-9, cx+9, cy+9), fill=(*hexrgb(CYAN), 235), outline=(*hexrgb(BLUE), 245), width=3)


def render_scene(scene_idx, local_t):
    scene = SCENES[scene_idx]
    pidx = phase_index(scene, local_t)
    frame = cached(scene_idx, pidx).copy()
    if pidx > 0:
        dt = local_t-scene["phases"][pidx]["at"]
        if 0 <= dt < TRANSITION:
            frame = Image.blend(cached(scene_idx, pidx-1), frame, ease(dt/TRANSITION))
    draw_special(frame, scene_idx, pidx, local_t)
    draw_taps(frame, scene, pidx, local_t)
    return frame


def render_frame(t):
    idx = min(9, int(t//CHAPTER))
    local = min(CHAPTER-0.001, t-idx*CHAPTER)
    frame = render_scene(idx, local)
    if idx > 0 and local < TRANSITION:
        frame = Image.blend(render_scene(idx-1, CHAPTER-0.001), frame, ease(local/TRANSITION))
    if t < 0.8:
        frame = Image.blend(BG_DARK, frame, ease(t/0.8))
    if t > TOTAL-0.8:
        frame = Image.blend(frame, BG_DARK, ease((t-(TOTAL-0.8))/0.8))
    d = ImageDraw.Draw(frame, "RGBA")
    d.rectangle((0, H-6, int(W*t/TOTAL), H), fill=(*hexrgb(ORANGE), 220))
    return frame


def durations():
    total_voice = 0.0
    for scene in SCENES:
        with wave.open(str(VOICE/scene["audio"]), "rb") as wf:
            if (wf.getframerate(), wf.getnchannels(), wf.getsampwidth()) != (44100, 1, 2):
                raise ValueError(f"Formato incorrecto: {scene['audio']}")
            d = wf.getnframes()/wf.getframerate()
        if d+VOICE_DELAY+0.20 > CHAPTER:
            raise RuntimeError(f"La voz del capítulo {scene['n']} no cabe: {d:.2f} s")
        scene["audio_duration"] = d
        total_voice += d
    return total_voice


def generate_audio():
    sr = 44100
    n = int(TOTAL*sr)
    mix = np.zeros((n, 2), dtype=np.float32)
    chords = [(261.63, 329.63, 392.00), (220.00, 261.63, 329.63),
              (174.61, 220.00, 261.63), (196.00, 246.94, 293.66)]
    # Música ambiental original, generada por bloques para limitar memoria.
    for begin in range(0, n, sr):
        end = min(n, begin+sr)
        t = np.arange(begin, end, dtype=np.float32)/sr
        seg = np.zeros((len(t), 2), dtype=np.float32)
        for ci, chord in enumerate(chords):
            mask = ((t//4).astype(np.int32)%4 == ci)
            if not np.any(mask):
                continue
            local = np.mod(t, 4.0)
            env = np.sin(np.pi*local/4.0).astype(np.float32)**0.65
            for j, freq in enumerate(chord):
                seg[:, 0] += mask*env*np.sin(2*np.pi*freq*t+j*0.23).astype(np.float32)*0.0055
                seg[:, 1] += mask*env*np.sin(2*np.pi*freq*1.0007*t+j*0.31).astype(np.float32)*0.0055
            beat = np.mod(t, 2.0)
            pluck = np.exp(-4.8*beat).astype(np.float32)
            f = chord[2]*2
            seg[:, 0] += mask*pluck*np.sin(2*np.pi*f*t).astype(np.float32)*0.0035
            seg[:, 1] += mask*pluck*np.sin(2*np.pi*f*t+0.18).astype(np.float32)*0.0035
        mix[begin:end] += seg
    for scene in SCENES:
        with wave.open(str(VOICE/scene["audio"]), "rb") as wf:
            samples = np.frombuffer(wf.readframes(wf.getnframes()), dtype="<i2").astype(np.float32)/32768.0
        offset = int(round((scene["start"]+VOICE_DELAY)*sr))
        mix[offset:offset+len(samples), 0] += samples*0.92
        mix[offset:offset+len(samples), 1] += samples*0.92
        for ph in scene["phases"]:
            for k, tap in enumerate(ph["taps"]):
                start = scene["start"]+tap["at"]
                off = int(start*sr)
                length = int(0.12*sr)
                tt = np.arange(length, dtype=np.float32)/sr
                env = np.exp(-42*tt)
                click = (np.sin(2*np.pi*(1150-250*tt)*tt)*0.65+np.sin(2*np.pi*1900*tt)*0.20)*env*0.05
                mix[off:off+length, 0] += click*(0.85 if k%2 else 1.0)
                mix[off:off+length, 1] += click*(1.0 if k%2 else 0.85)
    fade = int(0.8*sr)
    mix[:fade] *= np.linspace(0, 1, fade, dtype=np.float32)[:, None]
    mix[-fade:] *= np.linspace(1, 0, fade, dtype=np.float32)[:, None]
    peak = float(np.max(np.abs(mix)))
    if peak > 0.97:
        mix *= 0.97/peak
    path = WORK / "mezcla_reel_10min.wav"
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(2)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes((np.clip(mix, -1, 1)*32767).astype("<i2").tobytes())
    print(f"Audio: {path} | pico={min(peak,0.97):.3f} RMS={float(np.sqrt(np.mean(mix**2))):.3f}")
    return path


def write_guide(total_voice):
    lines = ["# Shalev · Reel manual de 10 minutos", "",
             "**Duración:** 10:00 exactos  ",
             "**Formato:** 1080 × 1920 · 9:16 · 30 fps · H.264 + AAC  ",
             "**Idioma:** español · narración masculina · sin subtítulos  ",
             f"**Narración total:** {int(total_voice//60)}:{total_voice%60:04.1f} min", "",
             "Basado en `MANUAL_USUARIO.md` y sus capturas reales. Los rótulos, tarjetas y pulsos naranja **TOCA** son elementos gráficos del tutorial, no subtítulos de la narración.", "",
             "## Capítulos", ""]
    for scene in SCENES:
        lines += [f"### {scene['n']:02d}. {scene['title']} · {scene['start']/60:02.0f}:00–{(scene['start']+60)/60:02.0f}:00", "",
                  f"**Narración ({scene['audio_duration']:.2f} s):** {scene['narration']}", "",
                  "**Estados visuales:** "+"; ".join(ph["heading"] for ph in scene["phases"])+".", ""]
    lines += ["## Entrega", "", "- `Shalev_reel_manual_10min_1080x1920.mp4`: video vertical final, sin pistas ni subtítulos incrustados.",
              "- `Shalev_reel_10min_guion.md`: guion, tiempos y estados visuales.", "",
              "La música ambiental y los efectos de toque son sintetizados para este video. La captura con diferencia de importe en el SMS se usa para explicar la validación, no como un cobro confirmado."]
    (OUT / "Shalev_reel_10min_guion.md").write_text("\n".join(lines), encoding="utf-8")


def make_preview():
    PREVIEW.mkdir(parents=True, exist_ok=True)
    thumbs = []
    for i, scene in enumerate(SCENES):
        for j, ph in enumerate(scene["phases"]):
            next_at = scene["phases"][j+1]["at"] if j+1 < len(scene["phases"]) else CHAPTER
            local = ph["at"]+min(1.1, max(0.25, (next_at-ph["at"])*0.20))
            im = render_frame(scene["start"]+local)
            path = PREVIEW / f"capitulo_{i+1:02d}_estado_{j+1:02d}.png"
            im.save(path, optimize=True)
            thumb = im.resize((216, 384), Image.Resampling.LANCZOS)
            td = ImageDraw.Draw(thumb, "RGBA")
            td.rounded_rectangle((6, 6, 210, 36), radius=10, fill=(8, 21, 38, 225))
            td.text((13, 12), f"{i+1:02d}.{j+1} · {ph['heading'][:24]}", font=font(12, True), fill=WHITE)
            thumbs.append((i, thumb))
    for group in range(2):
        selected = [im for i, im in thumbs if i//5 == group]
        # Selecciona hasta 30 miniaturas por hoja, cinco columnas.
        selected = selected[:30]
        rows = math.ceil(len(selected)/5)
        sheet = Image.new("RGB", (1080, rows*384), NAVY)
        for k, im in enumerate(selected):
            sheet.paste(im, ((k%5)*216, (k//5)*384))
        path = PREVIEW / f"storyboard_reel_{group+1}.png"
        sheet.save(path, optimize=True)
        print("Storyboard:", path.relative_to(ROOT))


def render_video(audio):
    output = OUT / "Shalev_reel_manual_10min_1080x1920.mp4"
    frames = int(TOTAL*FPS)
    cmd = [imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "warning",
           "-f", "rawvideo", "-vcodec", "rawvideo", "-pix_fmt", "rgb24",
           "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-", "-i", str(audio),
           "-c:v", "libx264", "-preset", "veryfast", "-crf", "18",
           "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.1",
           "-threads", str(min(8, os.cpu_count() or 4)),
           "-c:a", "aac", "-b:a", "192k", "-ar", "44100",
           "-movflags", "+faststart", "-t", f"{TOTAL:.3f}", str(output)]
    print(f"Render: {output.relative_to(ROOT)} | {frames} frames | {TOTAL:.2f} s")
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    try:
        for i in range(frames):
            frame = render_frame(i/FPS)
            proc.stdin.write(frame.tobytes())
            if i % (FPS*30) == 0:
                print(f"  {i}/{frames} · {i/FPS:.0f}/600 s", flush=True)
        proc.stdin.close()
        code = proc.wait()
        if code:
            raise RuntimeError(f"ffmpeg terminó con código {code}")
    except Exception:
        if proc.stdin and not proc.stdin.closed:
            proc.stdin.close()
        proc.kill()
        raise
    print("Video listo:", output)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--render", action="store_true")
    args = parser.parse_args()
    if not args.preview and not args.render:
        args.preview = True
    OUT.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    create_promo_asset()
    total_voice = durations()
    print(f"Voz total: {total_voice:.3f} s · Video: {TOTAL:.3f} s · Sin subtítulos")
    write_guide(total_voice)
    if args.preview:
        make_preview()
    if args.render:
        audio = generate_audio()
        render_video(audio)


if __name__ == "__main__":
    main()
