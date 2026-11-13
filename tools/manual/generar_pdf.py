#!/usr/bin/env python3
"""Genera MANUAL_USUARIO.pdf en la raíz del repositorio a partir de MANUAL_USUARIO.md.

Arquitectura editorial en dos pasadas:
  1. ReportLab (Platypus) maqueta texto, listas anidadas, tablas de datos, cuadros
     de aviso (callouts) y galerías de capturas enmarcadas con bordes redondeados y
     sombra suave, dejando marcos exactos para la portada, el índice interactivo y
     los 7 diagramas vectoriales SVG.
  2. PyMuPDF (MuPDF) estampa la portada vectorial + capturas, incrusta los 7 SVG
     como gráficos vectoriales puros (`show_pdf_page`, zoom infinito sin pixelar),
     escribe el Índice de contenidos con números de página exactos e hipervínculos
     internos (`LINK_GOTO`), añade marcadores PDF (`set_toc`) y numera cabeceras y
     pies (`Página X de N`).
"""

from __future__ import annotations

import html
import io
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional, Tuple

import pymupdf
from PIL import Image, ImageChops, ImageDraw, ImageFilter
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    CondPageBreak,
    Flowable,
    Frame,
    Image as RLImage,
    KeepTogether,
    NextPageTemplate,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path(__file__).resolve().parents[2]
MD_PATH = ROOT / "MANUAL_USUARIO.md"
OUT_PDF = ROOT / "MANUAL_USUARIO.pdf"

PAGE_W, PAGE_H = A4  # 595.27 x 841.89 pt
MARGIN_L = 42.0
MARGIN_R = 42.0
MARGIN_T = 54.0
MARGIN_B = 50.0
CONTENT_W = PAGE_W - MARGIN_L - MARGIN_R  # 511.27 pt

# Paleta oficial Shalev (:designsystem)
HEX_NAVY_DARK = "#193169"
HEX_PRI = "#2B4FA3"
HEX_PRI_BG = "#DCE3F5"
HEX_PRI_SOFT = "#F2F5FC"
HEX_SEC = "#B4541A"
HEX_SEC_BG = "#FADEC9"
HEX_SEC_SOFT = "#FDF5EF"
HEX_TER = "#2F6F80"
HEX_TER_BG = "#D3EBF0"
HEX_TER_SOFT = "#EFF7F9"
HEX_GRN = "#0B7A5E"
HEX_TEXT = "#191C20"
HEX_SUB = "#44474E"
HEX_MUTED = "#6E737D"
HEX_BORDER = "#C8CFD9"
HEX_ROW_ALT = "#F7F9FC"

C_PRI = colors.HexColor(HEX_PRI)
C_PRI_BG = colors.HexColor(HEX_PRI_BG)
C_PRI_SOFT = colors.HexColor(HEX_PRI_SOFT)
C_SEC = colors.HexColor(HEX_SEC)
C_SEC_BG = colors.HexColor(HEX_SEC_BG)
C_SEC_SOFT = colors.HexColor(HEX_SEC_SOFT)
C_TER = colors.HexColor(HEX_TER)
C_TER_BG = colors.HexColor(HEX_TER_BG)
C_TER_SOFT = colors.HexColor(HEX_TER_SOFT)
C_TEXT = colors.HexColor(HEX_TEXT)
C_SUB = colors.HexColor(HEX_SUB)
C_MUTED = colors.HexColor(HEX_MUTED)
C_BORDER = colors.HexColor(HEX_BORDER)
C_ROW_ALT = colors.HexColor(HEX_ROW_ALT)
C_WHITE = colors.white


def register_fonts() -> None:
    dejavu_dir = Path("/usr/share/fonts/truetype/dejavu")
    pdfmetrics.registerFont(TTFont("Sans", str(dejavu_dir / "DejaVuSans.ttf")))
    pdfmetrics.registerFont(TTFont("Sans-Bold", str(dejavu_dir / "DejaVuSans-Bold.ttf")))
    # Oblique fallback via DejaVuSans if Oblique isn't installed; check first
    oblique = dejavu_dir / "DejaVuSans-Oblique.ttf"
    bold_oblique = dejavu_dir / "DejaVuSans-BoldOblique.ttf"
    pdfmetrics.registerFont(TTFont("Sans-Italic", str(oblique if oblique.exists() else dejavu_dir / "DejaVuSans.ttf")))
    pdfmetrics.registerFont(
        TTFont("Sans-BoldItalic", str(bold_oblique if bold_oblique.exists() else dejavu_dir / "DejaVuSans-Bold.ttf"))
    )
    pdfmetrics.registerFontFamily(
        "Sans",
        normal="Sans",
        bold="Sans-Bold",
        italic="Sans-Italic",
        boldItalic="Sans-BoldItalic",
    )
    pdfmetrics.registerFont(TTFont("Mono", str(dejavu_dir / "DejaVuSansMono.ttf")))
    orbitron = ROOT / "Android/designsystem/src/main/res/font/orbitron.ttf"
    if orbitron.exists():
        pdfmetrics.registerFont(TTFont("Orbitron", str(orbitron)))


# Registro global de posiciones para la pasada vectorial con PyMuPDF
@dataclass
class SvgPlacement:
    page_idx: int  # 0-based
    x_rl: float
    y_rl: float
    w: float
    h: float
    svg_path: Path


@dataclass
class HeadingRecord:
    level: int  # 1 = H2 capítulo, 2 = H3 subsección
    num_label: str
    title: str
    page_num: int  # 1-based
    y_top_mupdf: float


SVG_PLACEMENTS: List[SvgPlacement] = []
HEADINGS: List[HeadingRecord] = []
FRAMED_CACHE: dict[Tuple[str, int], bytes] = {}


def compact_tall_capture(im: Image.Image) -> Image.Image:
    """Compacta tramos verticales vacíos de capturas largas de desplazamiento (720x3200)."""
    if im.height <= int(im.width * 2.35):
        return im
    rgb = im.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    sample_xs = list(range(8, w - 8, 12))
    uniform = []
    for y in range(h):
        r0, g0, b0 = px[8, y]
        ok = True
        for x in sample_xs:
            r, g, b = px[x, y]
            if abs(r - r0) > 3 or abs(g - g0) > 3 or abs(b - b0) > 3:
                ok = False
                break
        uniform.append(ok)
    keep_rows = []
    y = 0
    while y < h:
        if not uniform[y]:
            keep_rows.append(y)
            y += 1
        else:
            y2 = y
            while y2 < h and uniform[y2]:
                y2 += 1
            keep_n = min(y2 - y, 32)
            keep_rows.extend(range(y, y + keep_n))
            y = y2
    target_min = int(w * (1600 / 720))
    out_h = max(len(keep_rows), min(h, target_min))
    src_rgba = im.convert("RGBA")
    out = Image.new("RGBA", (w, out_h), src_rgba.getpixel((8, h - 8)))
    for dst_y, src_y in enumerate(keep_rows):
        out.paste(src_rgba.crop((0, src_y, w, src_y + 1)), (0, dst_y))
    return out


def frame_screenshot(img_path: Path, target_px_w: int = 560) -> Tuple[bytes, int, int]:
    """Enmarca una captura de teléfono con esquinas redondeadas, borde fino y sombra suave."""
    key = (str(img_path), target_px_w)
    if key in FRAMED_CACHE:
        data = FRAMED_CACHE[key]
        im_tmp = Image.open(io.BytesIO(data))
        return data, im_tmp.width, im_tmp.height
    im = compact_tall_capture(Image.open(img_path).convert("RGBA"))
    scale = target_px_w / im.width
    nw = target_px_w
    nh = max(1, int(round(im.height * scale)))
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)

    radius = max(18, int(nw * 0.048))
    pad = 18
    cw, ch = nw + pad * 2, nh + pad * 2

    # Máscara redondeada para la captura
    mask = Image.new("L", (nw, nh), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle((0, 0, nw - 1, nh - 1), radius=radius, fill=255)

    # Sombra suave sobre fondo blanco puro
    canvas = Image.new("RGBA", (cw, ch), (255, 255, 255, 255))
    shadow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle(
        (pad + 2, pad + 5, pad + nw + 2, pad + nh + 5),
        radius=radius,
        fill=(25, 49, 105, 46),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(7))
    canvas = Image.alpha_composite(canvas, shadow)

    # Pegar captura redondeada
    clipped = Image.new("RGBA", (nw, nh), (255, 255, 255, 255))
    clipped.paste(im, (0, 0), mask)
    canvas.paste(clipped, (pad, pad), mask)

    # Borde nítido alrededor del teléfono
    bdraw = ImageDraw.Draw(canvas)
    bdraw.rounded_rectangle(
        (pad, pad, pad + nw - 1, pad + nh - 1),
        radius=radius,
        outline=(192, 201, 216, 255),
        width=2,
    )

    out = canvas.convert("RGB")
    buf = io.BytesIO()
    out.save(buf, format="PNG", optimize=True)
    data = buf.getvalue()
    FRAMED_CACHE[key] = data
    return data, cw, ch


class VectorSvgPlaceholder(Flowable):
    """Reserva el espacio exacto de un SVG y registra sus coordenadas para PyMuPDF."""

    def __init__(self, svg_path: Path, width: float):
        super().__init__()
        self.svg_path = svg_path
        doc = pymupdf.open(str(svg_path))
        rect = doc[0].rect
        doc.close()
        aspect = rect.height / rect.width
        self.width = width
        self.height = width * aspect

    def wrap(self, availWidth: float, availHeight: float) -> Tuple[float, float]:
        w = min(self.width, availWidth)
        doc = pymupdf.open(str(self.svg_path))
        rect = doc[0].rect
        doc.close()
        h = w * (rect.height / rect.width)
        self.width = w
        self.height = h
        return w, h

    def draw(self) -> None:
        # Obtener coordenadas absolutas en la página actual de ReportLab
        x_abs, y_abs = self.canv.absolutePosition(0, 0)
        page_idx = self.canv.getPageNumber() - 1
        SVG_PLACEMENTS.append(
            SvgPlacement(
                page_idx=page_idx,
                x_rl=x_abs,
                y_rl=y_abs,
                w=self.width,
                h=self.height,
                svg_path=self.svg_path,
            )
        )


class ChapterBanner(Flowable):
    """Encabezado visual de capítulo (H2) con insignia numérica, título y línea de acento."""

    def __init__(self, num_str: str, title_text: str, style: ParagraphStyle, width: float = CONTENT_W):
        super().__init__()
        self.num_str = num_str
        self.title_text = title_text
        self.p = Paragraph(inline_md_to_rl(title_text), style)
        self.width = width
        self.badge_w = 34.0
        self.gap = 10.0
        self.height = 0.0
        self.keepWithNext = True

    def wrap(self, availWidth: float, availHeight: float) -> Tuple[float, float]:
        self.width = availWidth
        pw, ph = self.p.wrap(availWidth - self.badge_w - self.gap - 16, availHeight)
        self.p_h = ph
        self.height = max(36.0, ph + 16.0) + 6.0
        return self.width, self.height

    def draw(self) -> None:
        c = self.canv
        c.saveState()
        box_h = self.height - 6.0
        # Fondo suave redondeado
        c.setFillColor(C_PRI_SOFT)
        c.setStrokeColor(C_PRI_BG)
        c.setLineWidth(1.0)
        c.roundRect(0, 6, self.width, box_h, 8, stroke=1, fill=1)

        # Barra lateral izquierda primaria
        c.setFillColor(C_PRI)
        c.roundRect(0, 6, 5.5, box_h, 3, stroke=0, fill=1)

        # Insignia circular/pastilla con número de capítulo
        bx = 13.0
        bh = 24.0
        by = 6 + (box_h - bh) / 2.0
        c.setFillColor(C_PRI)
        c.roundRect(bx, by, self.badge_w, bh, 6, stroke=0, fill=1)
        c.setFillColor(C_WHITE)
        c.setFont("Sans-Bold", 11)
        c.drawCentredString(bx + self.badge_w / 2.0, by + 7.5, self.num_str.zfill(2))

        # Texto del capítulo
        tx = bx + self.badge_w + self.gap
        ty = 6 + (box_h - self.p_h) / 2.0 + 1.0
        self.p.drawOn(c, tx, ty)
        c.restoreState()

        # Registrar para el Índice y marcadores PDF
        _, y_top_rl = c.absolutePosition(0, self.height)
        HEADINGS.append(
            HeadingRecord(
                level=1,
                num_label=self.num_str,
                title=strip_md(self.title_text),
                page_num=c.getPageNumber(),
                y_top_mupdf=max(36.0, PAGE_H - y_top_rl - 8.0),
            )
        )


class SubHeadingFlowable(Flowable):
    """Subtítulo (H3) con acento lateral y registro en marcadores PDF."""

    def __init__(self, full_text: str, style: ParagraphStyle):
        super().__init__()
        self.full_text = full_text
        self.p = Paragraph(inline_md_to_rl(full_text), style)
        self.keepWithNext = True
        self.width = CONTENT_W
        self.height = 0.0

    def wrap(self, availWidth: float, availHeight: float) -> Tuple[float, float]:
        self.width = availWidth
        _, ph = self.p.wrap(availWidth - 11.0, availHeight)
        self.p_h = ph
        self.height = ph + 4.0
        return self.width, self.height

    def draw(self) -> None:
        c = self.canv
        c.saveState()
        c.setFillColor(C_TER)
        c.roundRect(0, 2, 3.5, self.p_h, 1.7, stroke=0, fill=1)
        self.p.drawOn(c, 10.0, 2.0)
        c.restoreState()

        _, y_top_rl = c.absolutePosition(0, self.height)
        HEADINGS.append(
            HeadingRecord(
                level=2,
                num_label="",
                title=strip_md(self.full_text),
                page_num=c.getPageNumber(),
                y_top_mupdf=max(36.0, PAGE_H - y_top_rl - 6.0),
            )
        )


class CalloutBox(Flowable):
    """Cuadro de nota, consejo o advertencia con franja lateral de color."""

    def __init__(self, md_text: str, style: ParagraphStyle):
        super().__init__()
        head_low = strip_md(md_text)[:45].lower()
        if any(k in head_low for k in ("atención", "cuidado", "privacidad", "recuerda")):
            self.accent = C_SEC
            self.bg = C_SEC_SOFT
            self.border = C_SEC_BG
        elif "consejo" in head_low:
            self.accent = C_TER
            self.bg = C_TER_SOFT
            self.border = C_TER_BG
        else:
            self.accent = C_PRI
            self.bg = C_PRI_SOFT
            self.border = C_PRI_BG
        self.p = Paragraph(inline_md_to_rl(md_text), style)
        self.width = CONTENT_W
        self.height = 0.0

    def wrap(self, availWidth: float, availHeight: float) -> Tuple[float, float]:
        self.width = availWidth
        _, ph = self.p.wrap(availWidth - 28.0, availHeight)
        self.p_h = ph
        self.height = ph + 18.0
        return self.width, self.height

    def draw(self) -> None:
        c = self.canv
        c.saveState()
        c.setFillColor(self.bg)
        c.setStrokeColor(self.border)
        c.setLineWidth(0.9)
        c.roundRect(0, 0, self.width, self.height, 7, stroke=1, fill=1)
        c.setFillColor(self.accent)
        c.roundRect(0, 0, 4.8, self.height, 2.4, stroke=0, fill=1)
        self.p.drawOn(c, 15.0, 9.0)
        c.restoreState()


def strip_md(s: str) -> str:
    s = s.replace(r"\*", "\x00AST\x00")
    s = re.sub(r"`([^`]+)`", r"\1", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"\1", s)
    s = re.sub(r"\*([^*]+)\*", r"\1", s)
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)
    return s.replace("\x00AST\x00", "*").strip()


def inline_md_to_rl(text: str, is_header: bool = False) -> str:
    """Convierte formato en línea de Markdown (**negrita**, *cursiva*, `código`, enlaces) a XML de ReportLab."""
    code_tokens: List[str] = []
    code_color = "#FFFFFF" if is_header else "#2B4FA3"

    def save_code(m: re.Match) -> str:
        idx = len(code_tokens)
        escaped = html.escape(m.group(1))
        code_tokens.append(f'<font name="Mono" size="8.6" color="{code_color}">{escaped}</font>')
        return f"\x00CODE{idx}\x00"

    s = re.sub(r"`([^`]+)`", save_code, text)
    # Proteger asteriscos literales escapados (\*)
    s = s.replace(r"\*", "\x00ESC_AST\x00")
    # Quitar enlaces de ancla manteniendo el texto
    s = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s)
    # Escapar entidades XML
    s = html.escape(s)
    # Negrita + cursiva, negrita, cursiva
    s = re.sub(r"\*\*\*([^*]+)\*\*\*", r"<b><i>\1</i></b>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", s)
    s = re.sub(r"\*([^*]+)\*", r"<i>\1</i>", s)

    s = s.replace("\x00ESC_AST\x00", "*")
    for idx, tok in enumerate(code_tokens):
        s = s.replace(f"\x00CODE{idx}\x00", tok)
    return s


def build_styles() -> dict[str, ParagraphStyle]:
    return {
        "h2": ParagraphStyle(
            "H2",
            fontName="Sans-Bold",
            fontSize=13.5,
            leading=17.5,
            textColor=C_TEXT,
            alignment=TA_LEFT,
        ),
        "h3": ParagraphStyle(
            "H3",
            fontName="Sans-Bold",
            fontSize=11.0,
            leading=14.5,
            textColor=C_PRI,
            alignment=TA_LEFT,
        ),
        "body": ParagraphStyle(
            "Body",
            fontName="Sans",
            fontSize=9.5,
            leading=14.2,
            textColor=C_TEXT,
            alignment=TA_LEFT,
            spaceAfter=5.5,
        ),
        "callout": ParagraphStyle(
            "Callout",
            fontName="Sans",
            fontSize=9.1,
            leading=13.5,
            textColor=C_TEXT,
            alignment=TA_LEFT,
        ),
        "li": ParagraphStyle(
            "ListItem",
            fontName="Sans",
            fontSize=9.4,
            leading=13.8,
            textColor=C_TEXT,
            leftIndent=17.0,
            firstLineIndent=0.0,
            bulletIndent=3.0,
            bulletFontName="Sans-Bold",
            bulletFontSize=9.4,
            bulletColor=C_PRI,
            spaceAfter=3.8,
        ),
        "li_sub": ParagraphStyle(
            "ListItemSub",
            fontName="Sans",
            fontSize=9.0,
            leading=13.2,
            textColor=C_SUB,
            leftIndent=31.0,
            firstLineIndent=0.0,
            bulletIndent=18.0,
            bulletFontName="Sans-Bold",
            bulletFontSize=9.0,
            bulletColor=C_TER,
            spaceAfter=2.8,
        ),
        "th": ParagraphStyle(
            "TableHeader",
            fontName="Sans-Bold",
            fontSize=8.8,
            leading=12.0,
            textColor=C_WHITE,
            alignment=TA_LEFT,
        ),
        "th_r": ParagraphStyle(
            "TableHeaderR",
            fontName="Sans-Bold",
            fontSize=8.8,
            leading=12.0,
            textColor=C_WHITE,
            alignment=TA_RIGHT,
        ),
        "td": ParagraphStyle(
            "TableCell",
            fontName="Sans",
            fontSize=8.6,
            leading=12.2,
            textColor=C_TEXT,
            alignment=TA_LEFT,
        ),
        "td_r": ParagraphStyle(
            "TableCellR",
            fontName="Sans",
            fontSize=8.6,
            leading=12.2,
            textColor=C_TEXT,
            alignment=TA_RIGHT,
        ),
        "caption": ParagraphStyle(
            "Caption",
            fontName="Sans-Italic",
            fontSize=8.3,
            leading=11.4,
            textColor=C_SUB,
            alignment=TA_CENTER,
        ),
    }


def make_gallery_flowable(
    img_paths: List[Path],
    captions: List[str],
    styles: dict[str, ParagraphStyle],
) -> Flowable:
    """Construye una galería horizontal (1, 2 o 3 capturas) con pies de figura alineados."""
    n = len(img_paths)
    if n == 1:
        max_img_h = 248.0
        col_w = 220.0
    elif n == 2:
        max_img_h = 252.0
        col_w = (CONTENT_W - 16.0) / 2.0
    else:
        max_img_h = 228.0
        col_w = (CONTENT_W - 16.0) / n

    img_cells = []
    cap_cells = []
    for idx, p in enumerate(img_paths):
        png_bytes, cw, ch = frame_screenshot(p, target_px_w=540 if n <= 2 else 460)
        aspect = ch / cw
        disp_w = min(col_w - 10.0, max_img_h / aspect)
        disp_h = disp_w * aspect
        rl_img = RLImage(io.BytesIO(png_bytes), width=disp_w, height=disp_h)
        rl_img.hAlign = "CENTER"
        img_cells.append(rl_img)
        cap_txt = captions[idx] if idx < len(captions) else ""
        cap_cells.append(Paragraph(inline_md_to_rl(cap_txt), styles["caption"]))

    col_widths = [col_w] * n
    tbl = Table([img_cells, cap_cells], colWidths=col_widths)
    tbl.hAlign = "CENTER"
    tbl.setStyle(
        TableStyle(
            [
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("VALIGN", (0, 0), (-1, 0), "BOTTOM"),
                ("VALIGN", (0, 1), (-1, 1), "TOP"),
                ("TOPPADDING", (0, 0), (-1, 0), 2),
                ("BOTTOMPADDING", (0, 0), (-1, 0), 4),
                ("TOPPADDING", (0, 1), (-1, 1), 2),
                ("BOTTOMPADDING", (0, 1), (-1, 1), 4),
                ("LEFTPADDING", (0, 0), (-1, -1), 4),
                ("RIGHTPADDING", (0, 0), (-1, -1), 4),
            ]
        )
    )
    return KeepTogether([Spacer(1, 4), tbl, Spacer(1, 6)])


def make_data_table(rows: List[List[str]], align_spec: List[str], styles: dict[str, ParagraphStyle]) -> Flowable:
    """Construye una tabla de datos con cabecera primaria Shalev y anchos proporcionales."""
    ncols = len(rows[0])
    # Calcular anchos proporcionales inteligentes según la longitud media/máxima del texto
    col_scores = []
    for c in range(ncols):
        lengths = [len(strip_md(r[c])) for r in rows if c < len(r)]
        avg_len = sum(lengths) / max(1, len(lengths))
        max_len = max(lengths) if lengths else 10
        score = max(12.0, min(62.0, avg_len * 0.7 + max_len * 0.3))
        col_scores.append(score)
    total_score = sum(col_scores)
    col_widths = [CONTENT_W * (sc / total_score) for sc in col_scores]

    rl_rows = []
    for r_idx, r in enumerate(rows):
        rl_row = []
        for c_idx in range(ncols):
            cell_txt = r[c_idx] if c_idx < len(r) else ""
            is_right = c_idx < len(align_spec) and align_spec[c_idx].endswith(":") and not align_spec[c_idx].startswith(":")
            if r_idx == 0:
                st = styles["th_r"] if is_right else styles["th"]
            else:
                st = styles["td_r"] if is_right else styles["td"]
            rl_row.append(Paragraph(inline_md_to_rl(cell_txt, is_header=(r_idx == 0)), st))
        rl_rows.append(rl_row)

    tbl = Table(rl_rows, colWidths=col_widths, repeatRows=1)
    ts = [
        ("BACKGROUND", (0, 0), (-1, 0), C_PRI),
        ("TEXTCOLOR", (0, 0), (-1, 0), C_WHITE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, 0), 6.5),
        ("BOTTOMPADDING", (0, 0), (-1, 0), 6.5),
        ("TOPPADDING", (0, 1), (-1, -1), 5.5),
        ("BOTTOMPADDING", (0, 1), (-1, -1), 5.5),
        ("LEFTPADDING", (0, 0), (-1, -1), 7.5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7.5),
        ("GRID", (0, 0), (-1, -1), 0.5, C_BORDER),
        ("LINEBELOW", (0, 0), (-1, 0), 1.2, C_PRI),
    ]
    for r_idx in range(1, len(rl_rows)):
        bg = C_ROW_ALT if r_idx % 2 == 0 else C_WHITE
        ts.append(("BACKGROUND", (0, r_idx), (-1, r_idx), bg))
    tbl.setStyle(TableStyle(ts))
    return tbl


def split_md_row(line: str) -> List[str]:
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    return [c.strip() for c in s.split("|")]


def parse_markdown_to_story(md_text: str, styles: dict[str, ParagraphStyle]) -> List[Flowable]:
    """Convierte MANUAL_USUARIO.md completo en una lista de Flowables de ReportLab."""
    story: List[Flowable] = []

    # Página 1: Portada (estampada por PyMuPDF)
    story.append(Spacer(1, 10))
    story.append(PageBreak())

    # Página 2: Índice de contenidos (estampado e hipervinculado por PyMuPDF)
    story.append(Spacer(1, 10))
    story.append(PageBreak())

    lines = md_text.splitlines()
    # Saltar el bloque inicial de cabecera HTML y el Índice en Markdown hasta "## 1."
    i = 0
    while i < len(lines) and not lines[i].startswith("## 1."):
        i += 1

    chapter_count = 0

    while i < len(lines):
        raw = lines[i]
        stripped = raw.strip()

        if not stripped or stripped == "---":
            i += 1
            continue

        # Bloque centrado <div align="center"> ... </div> (captura individual con pie)
        if stripped.startswith('<div align="center">'):
            block_lines = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("</div>"):
                if lines[i].strip():
                    block_lines.append(lines[i].strip())
                i += 1
            i += 1  # saltar </div>
            imgs = []
            caps = []
            for bl in block_lines:
                m_img = re.search(r'src="([^"]+)"', bl)
                if m_img:
                    imgs.append(ROOT / m_img.group(1))
                elif bl.startswith("*") and bl.endswith("*"):
                    caps.append(bl)
            if imgs:
                story.append(make_gallery_flowable(imgs, caps, styles))
            continue

        # Capítulo H2: "## N. Título"
        if stripped.startswith("## "):
            h2_text = stripped[3:].strip()
            m_num = re.match(r"^(\d+)\.\s+(.*)$", h2_text)
            if m_num:
                num_s, rest_title = m_num.group(1), m_num.group(2)
            else:
                num_s, rest_title = str(chapter_count + 1), h2_text
            if chapter_count > 0:
                story.append(Spacer(1, 14))
                story.append(CondPageBreak(220))
            chapter_count += 1
            story.append(ChapterBanner(num_s, rest_title, styles["h2"]))
            story.append(Spacer(1, 8))
            i += 1
            continue

        # Subsección H3: "### Título"
        if stripped.startswith("### "):
            h3_text = stripped[4:].strip()
            story.append(Spacer(1, 7))
            story.append(SubHeadingFlowable(h3_text, styles["h3"]))
            story.append(Spacer(1, 4))
            i += 1
            continue

        # Diagrama SVG o imagen Markdown: "![Alt](path)"
        m_md_img = re.match(r"^!\[([^\]]*)\]\(([^)]+)\)$", stripped)
        if m_md_img:
            alt_txt, rel_path = m_md_img.group(1), m_md_img.group(2)
            full_p = ROOT / rel_path
            if full_p.suffix.lower() == ".svg":
                svg_flow = VectorSvgPlaceholder(full_p, CONTENT_W)
                cap_p = Paragraph(inline_md_to_rl(f"*{alt_txt}*"), styles["caption"])
                story.append(KeepTogether([Spacer(1, 5), svg_flow, Spacer(1, 3), cap_p, Spacer(1, 7)]))
            i += 1
            continue

        # Bloque de cita / Callout: "> ..."
        if stripped.startswith(">"):
            q_lines = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                q_lines.append(lines[i].strip().lstrip(">").strip())
                i += 1
            q_md = " ".join(q_lines)
            story.append(Spacer(1, 3))
            story.append(KeepTogether([CalloutBox(q_md, styles["callout"])]))
            story.append(Spacer(1, 6))
            continue

        # Tablas Markdown (de datos o galerías de capturas `<img src=.../>`)
        if stripped.startswith("|"):
            tbl_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                tbl_lines.append(lines[i].strip())
                i += 1
            if len(tbl_lines) >= 2:
                first_cells = split_md_row(tbl_lines[0])
                # ¿Es una galería de imágenes HTML?
                if any("<img " in c for c in first_cells):
                    imgs = []
                    for c in first_cells:
                        m_src = re.search(r'src="([^"]+)"', c)
                        if m_src:
                            imgs.append(ROOT / m_src.group(1))
                    caps = split_md_row(tbl_lines[2]) if len(tbl_lines) >= 3 else []
                    if imgs:
                        story.append(make_gallery_flowable(imgs, caps, styles))
                else:
                    align_cells = split_md_row(tbl_lines[1])
                    data_rows = [first_cells] + [split_md_row(tl) for tl in tbl_lines[2:]]
                    story.append(Spacer(1, 3))
                    story.append(make_data_table(data_rows, align_cells, styles))
                    story.append(Spacer(1, 7))
            continue

        # Listas numeradas o con viñetas (incluyendo sub-elementos indentados)
        m_ul = re.match(r"^(\s*)([-*])\s+(.*)$", raw)
        m_ol = re.match(r"^(\s*)(\d+)\.\s+(.*)$", raw)
        if m_ul or m_ol:
            indent_spaces = len((m_ul or m_ol).group(1))
            is_sub = indent_spaces >= 2
            if m_ol:
                num_lbl = m_ol.group(2)
                item_body = m_ol.group(3).strip()
            else:
                num_lbl = None
                item_body = m_ul.group(3).strip()

            i += 1
            # Continuación multilínea del mismo ítem (sin empezar otro bullet, encabezado o bloque)
            while i < len(lines):
                nxt = lines[i]
                nxt_s = nxt.strip()
                if (
                    not nxt_s
                    or nxt_s.startswith(("#", ">", "|", "![", "<div", "---"))
                    or re.match(r"^\s*[-*]\s+", nxt)
                    or re.match(r"^\s*\d+\.\s+", nxt)
                ):
                    break
                item_body += " " + nxt_s
                i += 1

            st = styles["li_sub"] if is_sub else styles["li"]
            if num_lbl is not None:
                bullet_txt = f"{num_lbl}."
            else:
                bullet_txt = "•" if not is_sub else "–"
            p_li = Paragraph(inline_md_to_rl(item_body), st, bulletText=bullet_txt)
            if strip_md(item_body).endswith(":"):
                p_li.keepWithNext = True
            story.append(p_li)
            continue

        # Párrafo normal (acumular líneas contiguas simples)
        para_lines = [stripped]
        i += 1
        while i < len(lines):
            nxt = lines[i]
            nxt_s = nxt.strip()
            if (
                not nxt_s
                or nxt_s.startswith(("#", ">", "|", "![", "<div", "---"))
                or re.match(r"^\s*[-*]\s+", nxt)
                or re.match(r"^\s*\d+\.\s+", nxt)
            ):
                break
            para_lines.append(nxt_s)
            i += 1
        joined_para = " ".join(para_lines)
        p_norm = Paragraph(inline_md_to_rl(joined_para), styles["body"])
        if strip_md(joined_para).endswith(":"):
            p_norm.keepWithNext = True
        story.append(p_norm)

    return story


def hex_to_rgb01(h: str) -> Tuple[float, float, float]:
    h = h.lstrip("#")
    return int(h[0:2], 16) / 255.0, int(h[2:4], 16) / 255.0, int(h[4:6], 16) / 255.0


def draw_cover_page(page: pymupdf.Page, orbitron_font: Path, sans_reg: Path, sans_bold: Path) -> None:
    """Dibuja la portada oficial editorial en la Página 1 usando primitivas vectoriales e imágenes."""
    f_orb = pymupdf.Font(fontfile=str(orbitron_font))
    f_reg = pymupdf.Font(fontfile=str(sans_reg))
    f_bld = pymupdf.Font(fontfile=str(sans_bold))
    page.insert_font(fontname="FOrb", fontbuffer=f_orb.buffer)
    page.insert_font(fontname="FReg", fontbuffer=f_reg.buffer)
    page.insert_font(fontname="FBld", fontbuffer=f_bld.buffer)

    # Fondo general claro
    sh = page.new_shape()
    sh.draw_rect(pymupdf.Rect(0, 0, PAGE_W, PAGE_H))
    sh.finish(fill=hex_to_rgb01("#F6F8FC"), color=None)

    # Cabecera Hero Azul Cobalto
    hero_h = 340.0
    sh.draw_rect(pymupdf.Rect(0, 0, PAGE_W, hero_h))
    sh.finish(fill=hex_to_rgb01(HEX_NAVY_DARK), color=None)

    # Franja diagonal/geométrica decorativa sutil en tono primario #2B4FA3 (contenida dentro del hero)
    sh.draw_circle(pymupdf.Point(PAGE_W - 60, 58), 150)
    sh.finish(fill=hex_to_rgb01(HEX_PRI), color=None)
    sh.draw_circle(pymupdf.Point(55, 115), 88)
    sh.finish(fill=hex_to_rgb01("#22418B"), color=None)

    # Línea de acento terracota inferior del hero
    sh.draw_rect(pymupdf.Rect(0, hero_h - 5, PAGE_W, hero_h))
    sh.finish(fill=hex_to_rgb01(HEX_SEC), color=None)

    # Pastilla superior "DOCUMENTACIÓN OFICIAL"
    pill_w, pill_h = 210.0, 22.0
    pill_x = (PAGE_W - pill_w) / 2.0
    pill_y = 36.0
    sh.draw_rect(pymupdf.Rect(pill_x, pill_y, pill_x + pill_w, pill_y + pill_h), radius=0.48)
    sh.finish(fill=hex_to_rgb01("#2E56B2"), color=hex_to_rgb01("#6D8FE0"), width=0.8)
    sh.commit()

    def center_text(y: float, txt: str, font_obj: pymupdf.Font, fname: str, size: float, col: str) -> None:
        tw = font_obj.text_length(txt, fontsize=size)
        page.insert_text(
            pymupdf.Point((PAGE_W - tw) / 2.0, y),
            txt,
            fontname=fname,
            fontsize=size,
            color=hex_to_rgb01(col),
        )

    center_text(pill_y + 15.0, "GUÍA COMPLETA PASO A PASO", f_bld, "FBld", 8.8, "#DCE3F5")

    # Icono oficial de Shalev dentro de tarjeta blanca redondeada
    logo_size = 78.0
    lx = (PAGE_W - logo_size) / 2.0
    ly = 74.0
    sh2 = page.new_shape()
    sh2.draw_rect(pymupdf.Rect(lx - 6, ly - 6, lx + logo_size + 6, ly + logo_size + 6), radius=0.22)
    sh2.finish(fill=(1, 1, 1), color=hex_to_rgb01(HEX_PRI_BG), width=1.2)
    sh2.commit()
    page.insert_image(pymupdf.Rect(lx, ly, lx + logo_size, ly + logo_size), filename=str(ROOT / "Android/play_store/icono_512.png"))

    # Wordmark oficial "SHALEV" en Orbitron
    center_text(196.0, "SHALEV", f_orb, "FOrb", 32.0, "#FFFFFF")
    center_text(226.0, "Manual Oficial de Usuario", f_bld, "FBld", 18.5, "#FFFFFF")
    center_text(
        251.0,
        "Punto de venta, arqueo de caja, inventario con recetas, servicios y trabajo en equipo",
        f_reg,
        "FReg",
        10.2,
        "#DCE3F5",
    )

    # Tres insignias de especificaciones en el hero
    specs = [
        ("Versión 1.0.1", "#FADEC9", HEX_SEC),
        ("Android 8.0 o superior", "#DCE3F5", HEX_PRI),
        ("100 % sin conexión", "#D3EBF0", HEX_TER),
    ]
    sw_each = 142.0
    sgap = 12.0
    sx0 = (PAGE_W - (sw_each * 3 + sgap * 2)) / 2.0
    sy = 278.0
    sh3 = page.new_shape()
    for idx, (lbl, bg_hex, fg_hex) in enumerate(specs):
        rx = sx0 + idx * (sw_each + sgap)
        sh3.draw_rect(pymupdf.Rect(rx, sy, rx + sw_each, sy + 26.0), radius=0.45)
        sh3.finish(fill=hex_to_rgb01(bg_hex), color=None)
    sh3.commit()
    for idx, (lbl, _, fg_hex) in enumerate(specs):
        rx = sx0 + idx * (sw_each + sgap)
        tw = f_bld.text_length(lbl, fontsize=9.2)
        page.insert_text(
            pymupdf.Point(rx + (sw_each - tw) / 2.0, sy + 16.8),
            lbl,
            fontname="FBld",
            fontsize=9.2,
            color=hex_to_rgb01(fg_hex),
        )

    # Galería central de 3 capturas reales sobre la superficie clara
    shots = [
        (ROOT / "promocion/capturas/shot_inicio_claro.png", "Panel de Inicio y estadísticas"),
        (ROOT / "promocion/capturas/shot_inventario_claro.png", "Inventario, fotos y recetas"),
        (ROOT / "promocion/capturas/shot_servicios_claro.png", "Catálogo de Servicios"),
    ]
    card_w = 148.0
    cgap = 18.0
    cx0 = (PAGE_W - (card_w * 3 + cgap * 2)) / 2.0
    cy_base = 368.0
    for idx, (sp, cap) in enumerate(shots):
        png_bytes, cw, ch = frame_screenshot(sp, target_px_w=540)
        disp_w = card_w
        disp_h = disp_w * (ch / cw)
        # Elevar ligeramente la captura central para dinamismo editorial
        cy = cy_base - (8.0 if idx == 1 else 0.0)
        rx = cx0 + idx * (card_w + cgap)
        page.insert_image(pymupdf.Rect(rx, cy, rx + disp_w, cy + disp_h), stream=png_bytes)
        tw = f_reg.text_length(cap, fontsize=8.4)
        page.insert_text(
            pymupdf.Point(rx + (disp_w - tw) / 2.0, cy + disp_h + 12.0),
            cap,
            fontname="FReg",
            fontsize=8.4,
            color=hex_to_rgb01(HEX_SUB),
        )

    # Tarjeta inferior de créditos y resumen rápido
    foot_y = PAGE_H - 96.0
    sh4 = page.new_shape()
    sh4.draw_rect(pymupdf.Rect(MARGIN_L, foot_y, PAGE_W - MARGIN_R, PAGE_H - 32.0), radius=0.14)
    sh4.finish(fill=(1, 1, 1), color=hex_to_rgb01(HEX_BORDER), width=1.0)
    sh4.draw_rect(pymupdf.Rect(MARGIN_L, foot_y, MARGIN_L + 5.5, PAGE_H - 32.0), radius=0.3)
    sh4.finish(fill=hex_to_rgb01(HEX_PRI), color=None)
    sh4.commit()

    page.insert_text(
        pymupdf.Point(MARGIN_L + 18.0, foot_y + 22.0),
        "Diseñado para pequeños negocios, cafeterías, tiendas, talleres y servicios en Cuba",
        fontname="FBld",
        fontsize=9.6,
        color=hex_to_rgb01(HEX_TEXT),
    )
    page.insert_text(
        pymupdf.Point(MARGIN_L + 18.0, foot_y + 39.0),
        "Operación en Pesos Cubanos (CUP) · Cobro con QR de Transfermóvil · Base de datos cifrada en tu dispositivo",
        fontname="FReg",
        fontsize=8.6,
        color=hex_to_rgb01(HEX_SUB),
    )
    page.insert_text(
        pymupdf.Point(MARGIN_L + 18.0, foot_y + 54.0),
        "Desarrollador: Ing. Ronnie Montero Duarte  ·  Soporte por WhatsApp / SMS: +53 51815604",
        fontname="FReg",
        fontsize=8.4,
        color=hex_to_rgb01(HEX_PRI),
    )


def draw_toc_page(
    page: pymupdf.Page,
    chapters: List[HeadingRecord],
    sans_reg: Path,
    sans_bold: Path,
) -> None:
    """Dibuja el Índice de contenidos en la Página 2 con enlaces interactivos LINK_GOTO."""
    f_reg = pymupdf.Font(fontfile=str(sans_reg))
    f_bld = pymupdf.Font(fontfile=str(sans_bold))
    page.insert_font(fontname="FReg", fontbuffer=f_reg.buffer)
    page.insert_font(fontname="FBld", fontbuffer=f_bld.buffer)

    # Encabezado del Índice
    sh = page.new_shape()
    sh.draw_rect(pymupdf.Rect(MARGIN_L, 62.0, PAGE_W - MARGIN_R, 108.0), radius=0.14)
    sh.finish(fill=hex_to_rgb01(HEX_PRI), color=None)
    sh.commit()

    page.insert_text(
        pymupdf.Point(MARGIN_L + 18.0, 84.0),
        "Índice de contenidos",
        fontname="FBld",
        fontsize=15.5,
        color=(1, 1, 1),
    )
    page.insert_text(
        pymupdf.Point(MARGIN_L + 18.0, 100.0),
        "Haz clic o toca cualquier capítulo para ir directamente a su página",
        fontname="FReg",
        fontsize=9.0,
        color=hex_to_rgb01(HEX_PRI_BG),
    )

    row_y = 132.0
    row_h = 37.5
    right_x = PAGE_W - MARGIN_R - 12.0

    for idx, ch in enumerate(chapters):
        sh_row = page.new_shape()
        row_rect = pymupdf.Rect(MARGIN_L, row_y, PAGE_W - MARGIN_R, row_y + row_h - 4.5)
        bg_col = hex_to_rgb01("#F7F9FC") if idx % 2 == 0 else (1, 1, 1)
        sh_row.draw_rect(row_rect, radius=0.16)
        sh_row.finish(fill=bg_col, color=hex_to_rgb01("#E3E8F0"), width=0.7)

        # Pastilla numérica
        badge_rect = pymupdf.Rect(MARGIN_L + 10.0, row_y + 6.5, MARGIN_L + 38.0, row_y + 26.5)
        sh_row.draw_rect(badge_rect, radius=0.25)
        sh_row.finish(fill=hex_to_rgb01(HEX_PRI_BG), color=None)
        sh_row.commit()

        num_txt = ch.num_label.zfill(2)
        nw = f_bld.text_length(num_txt, fontsize=9.5)
        page.insert_text(
            pymupdf.Point(MARGIN_L + 10.0 + (28.0 - nw) / 2.0, row_y + 19.8),
            num_txt,
            fontname="FBld",
            fontsize=9.5,
            color=hex_to_rgb01(HEX_PRI),
        )

        # Título del capítulo
        tx = MARGIN_L + 48.0
        title_txt = ch.title
        page.insert_text(
            pymupdf.Point(tx, row_y + 19.8),
            title_txt,
            fontname="FBld",
            fontsize=9.8,
            color=hex_to_rgb01(HEX_TEXT),
        )

        # Número de página a la derecha
        pg_txt = f"Pág. {ch.page_num}"
        pw = f_bld.text_length(pg_txt, fontsize=9.4)
        page.insert_text(
            pymupdf.Point(right_x - pw, row_y + 19.8),
            pg_txt,
            fontname="FBld",
            fontsize=9.4,
            color=hex_to_rgb01(HEX_PRI),
        )

        # Línea punteada guía entre el título y la página
        tw = f_bld.text_length(title_txt, fontsize=9.8)
        dots_x1 = tx + tw + 8.0
        dots_x2 = right_x - pw - 8.0
        if dots_x2 > dots_x1 + 12.0:
            sh_dots = page.new_shape()
            sh_dots.draw_line(pymupdf.Point(dots_x1, row_y + 17.5), pymupdf.Point(dots_x2, row_y + 17.5))
            sh_dots.finish(color=hex_to_rgb01(HEX_BORDER), width=0.8, dashes="[1.5 3] 0")
            sh_dots.commit()

        # Hipervínculo interactivo interno
        page.insert_link(
            {
                "kind": pymupdf.LINK_GOTO,
                "from": row_rect,
                "page": ch.page_num - 1,
                "to": pymupdf.Point(MARGIN_L, ch.y_top_mupdf),
            }
        )
        row_y += row_h

    # Nota útil al pie del Índice
    note_y = row_y + 12.0
    if note_y + 52.0 < PAGE_H - MARGIN_B:
        sh_n = page.new_shape()
        sh_n.draw_rect(pymupdf.Rect(MARGIN_L, note_y, PAGE_W - MARGIN_R, note_y + 46.0), radius=0.14)
        sh_n.finish(fill=hex_to_rgb01(HEX_TER_SOFT), color=hex_to_rgb01(HEX_TER_BG), width=0.8)
        sh_n.commit()
        page.insert_text(
            pymupdf.Point(MARGIN_L + 14.0, note_y + 18.0),
            "Sugerencia de lectura rápida:",
            fontname="FBld",
            fontsize=9.0,
            color=hex_to_rgb01(HEX_TER),
        )
        page.insert_text(
            pymupdf.Point(MARGIN_L + 14.0, note_y + 34.0),
            "Si hoy es tu primer día con Shalev, revisa primero los capítulos 2, 5, 6 y 8: en 10 minutos estarás vendiendo.",
            fontname="FReg",
            fontsize=8.7,
            color=hex_to_rgb01(HEX_TEXT),
        )


def stamp_headers_footers_and_svgs(pdf_bytes: bytes) -> None:
    """Abre el PDF base de ReportLab y estampa portada, índice, SVGs vectoriales, cabeceras y marcadores."""
    doc = pymupdf.open(stream=pdf_bytes, filetype="pdf")
    total_pages = len(doc)

    orbitron_font = ROOT / "Android/designsystem/src/main/res/font/orbitron.ttf"
    sans_reg = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
    sans_bold = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
    f_reg = pymupdf.Font(fontfile=str(sans_reg))
    f_bld = pymupdf.Font(fontfile=str(sans_bold))
    f_orb = pymupdf.Font(fontfile=str(orbitron_font))

    # 1. Portada en página 1 (índice 0)
    draw_cover_page(doc[0], orbitron_font, sans_reg, sans_bold)

    # 2. Índice de contenidos interactivo en página 2 (índice 1)
    chapters = [h for h in HEADINGS if h.level == 1]
    draw_toc_page(doc[1], chapters, sans_reg, sans_bold)

    # 3. Estampar los 7 diagramas SVG como gráficos vectoriales puros (`show_pdf_page`)
    for sp in SVG_PLACEMENTS:
        page = doc[sp.page_idx]
        # Convertir coordenadas de ReportLab (origen abajo-izquierda) a MuPDF (origen arriba-izquierda)
        top_y = PAGE_H - (sp.y_rl + sp.h)
        bot_y = PAGE_H - sp.y_rl
        target_rect = pymupdf.Rect(sp.x_rl, top_y, sp.x_rl + sp.w, bot_y)

        svg_doc = pymupdf.open(str(sp.svg_path))
        svg_pdf_bytes = svg_doc.convert_to_pdf()
        svg_doc.close()
        svg_pdf = pymupdf.open("pdf", svg_pdf_bytes)
        page.show_pdf_page(target_rect, svg_pdf, 0)
        svg_pdf.close()

    # Mapa de capítulo activo al inicio de cada página para la cabecera superior
    page_chapter: dict[int, str] = {2: "Índice de contenidos"}
    chs_on_page: dict[int, List[HeadingRecord]] = {}
    for ch in chapters:
        chs_on_page.setdefault(ch.page_num, []).append(ch)
    cur_ch = ""
    for pno in range(3, total_pages + 1):
        here = chs_on_page.get(pno, [])
        top_chs = [c for c in here if c.y_top_mupdf < 160.0]
        if top_chs:
            cur_ch = f"Cap. {top_chs[0].num_label} · {top_chs[0].title}"
        page_chapter[pno] = cur_ch
        if here:
            last_c = here[-1]
            cur_ch = f"Cap. {last_c.num_label} · {last_c.title}"

    # 4. Cabeceras y pies de página en páginas 2..N
    for p_idx in range(1, total_pages):
        pno = p_idx + 1
        page = doc[p_idx]
        page.insert_font(fontname="HFReg", fontbuffer=f_reg.buffer)
        page.insert_font(fontname="HFBld", fontbuffer=f_bld.buffer)
        page.insert_font(fontname="HFOrb", fontbuffer=f_orb.buffer)

        sh = page.new_shape()
        # Línea superior e inferior sutil
        sh.draw_line(pymupdf.Point(MARGIN_L, 38.0), pymupdf.Point(PAGE_W - MARGIN_R, 38.0))
        sh.finish(color=hex_to_rgb01(HEX_PRI_BG), width=0.8)
        sh.draw_line(pymupdf.Point(MARGIN_L, PAGE_H - 36.0), pymupdf.Point(PAGE_W - MARGIN_R, PAGE_H - 36.0))
        sh.finish(color=hex_to_rgb01(HEX_PRI_BG), width=0.8)
        sh.commit()

        # Cabecera izquierda: SHALEV · Manual de Usuario
        page.insert_text(
            pymupdf.Point(MARGIN_L, 29.5),
            "SHALEV",
            fontname="HFOrb",
            fontsize=8.2,
            color=hex_to_rgb01(HEX_PRI),
        )
        w_orb = f_orb.text_length("SHALEV", fontsize=8.2)
        page.insert_text(
            pymupdf.Point(MARGIN_L + w_orb + 6.0, 29.5),
            "· Manual Oficial de Usuario",
            fontname="HFReg",
            fontsize=8.0,
            color=hex_to_rgb01(HEX_MUTED),
        )

        # Cabecera derecha: Capítulo actual
        r_label = page_chapter.get(pno, "")
        if len(r_label) > 54:
            r_label = r_label[:52].rstrip() + "…"
        rw = f_reg.text_length(r_label, fontsize=8.0)
        page.insert_text(
            pymupdf.Point(PAGE_W - MARGIN_R - rw, 29.5),
            r_label,
            fontname="HFReg",
            fontsize=8.0,
            color=hex_to_rgb01(HEX_SUB),
        )

        # Pie izquierdo y derecho
        page.insert_text(
            pymupdf.Point(MARGIN_L, PAGE_H - 22.0),
            "Shalev v1.0.1 · Punto de Venta e Inventario sin conexión",
            fontname="HFReg",
            fontsize=8.0,
            color=hex_to_rgb01(HEX_MUTED),
        )
        pg_str = f"Página {pno} de {total_pages}"
        pw = f_bld.text_length(pg_str, fontsize=8.2)
        page.insert_text(
            pymupdf.Point(PAGE_W - MARGIN_R - pw, PAGE_H - 22.0),
            pg_str,
            fontname="HFBld",
            fontsize=8.2,
            color=hex_to_rgb01(HEX_PRI),
        )

    # 5. Marcadores / Esquema PDF (Bookmarks en el visor) y metadatos
    toc_entries = [
        [1, "Portada", 1],
        [1, "Índice de contenidos", 2],
    ]
    for h in HEADINGS:
        if h.level == 1:
            toc_entries.append([1, f"{h.num_label}. {h.title}", h.page_num])
        else:
            toc_entries.append([2, h.title, h.page_num])
    doc.set_toc(toc_entries)
    doc.set_metadata(
        {
            "title": "Manual Oficial de Usuario · Shalev v1.0.1",
            "author": "Ing. Ronnie Montero Duarte",
            "subject": "Manual de Usuario del sistema de punto de venta e inventario Shalev para Android",
            "keywords": "Shalev, Manual de Usuario, POS, Inventario, Cuba, CUP, Transfermóvil, Android",
            "creator": "Shalev Documentation Builder",
        }
    )

    doc.save(str(OUT_PDF), garbage=4, deflate=True)
    doc.close()


def main() -> None:
    register_fonts()
    SVG_PLACEMENTS.clear()
    HEADINGS.clear()

    md_text = MD_PATH.read_text(encoding="utf-8")
    styles = build_styles()
    story = parse_markdown_to_story(md_text, styles)

    buf = io.BytesIO()
    doc = BaseDocTemplate(
        buf,
        pagesize=A4,
        leftMargin=MARGIN_L,
        rightMargin=MARGIN_R,
        topMargin=MARGIN_T,
        bottomMargin=MARGIN_B,
        title="Manual Oficial de Usuario · Shalev v1.0.1",
        author="Ing. Ronnie Montero Duarte",
    )
    frame = Frame(MARGIN_L, MARGIN_B, CONTENT_W, PAGE_H - MARGIN_T - MARGIN_B, id="main", topPadding=0, bottomPadding=0, leftPadding=0, rightPadding=0)
    doc.addPageTemplates([PageTemplate(id="body", frames=[frame])])
    doc.build(story)

    stamp_headers_footers_and_svgs(buf.getvalue())

    final_doc = pymupdf.open(str(OUT_PDF))
    print(
        f"PDF generado con éxito: {OUT_PDF} "
        f"({final_doc.page_count} páginas, {OUT_PDF.stat().st_size / (1024 * 1024):.2f} MB, "
        f"{len(SVG_PLACEMENTS)} diagramas vectoriales, {len([h for h in HEADINGS if h.level == 1])} capítulos)"
    )
    final_doc.close()


if __name__ == "__main__":
    main()
