"""Small dependency-free XLSX writer for safe tabular exports."""
from __future__ import annotations

import math
import re
import zipfile
from io import BytesIO
from typing import Any, Iterable, Sequence
from xml.etree import ElementTree as ET

_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
_CONTENT = "http://schemas.openxmlformats.org/package/2006/content-types"
ET.register_namespace("", _MAIN)
ET.register_namespace("r", _REL)


def _tag(namespace: str, name: str) -> str:
    return f"{{{namespace}}}{name}"


def _column_name(index: int) -> str:
    if not 1 <= index <= 16_384:
        raise ValueError("La exportación supera el límite de columnas de Excel.")
    result = ""
    while index:
        index, remainder = divmod(index - 1, 26)
        result = chr(65 + remainder) + result
    return result


def _xml(element: ET.Element) -> bytes:
    return ET.tostring(element, encoding="utf-8", xml_declaration=True)


def _safe_sheet_name(name: str, used: set[str]) -> str:
    cleaned = re.sub(r"[\\/*?:\[\]]", " ", str(name)).strip().strip("'")[:31] or "Hoja"
    candidate, suffix = cleaned, 2
    while candidate.casefold() in used:
        tail = f" ({suffix})"
        candidate = cleaned[:31 - len(tail)] + tail
        suffix += 1
    used.add(candidate.casefold())
    return candidate


def build_xlsx(sheets: Iterable[tuple[str, Iterable[Sequence[Any]]]]) -> bytes:
    """Return an OpenXML workbook. Text is always stored as inline text, never as formulas."""
    materialized: list[tuple[str, list[Sequence[Any]]]] = []
    used: set[str] = set()
    for name, rows in sheets:
        if len(materialized) >= 255:
            raise ValueError("El libro supera el máximo de hojas permitido.")
        table = list(rows)
        if len(table) > 1_048_576:
            raise ValueError("La hoja supera el máximo de filas de Excel.")
        if any(len(row) > 16_384 for row in table):
            raise ValueError("La hoja supera el máximo de columnas de Excel.")
        materialized.append((_safe_sheet_name(name, used), table))
    if not materialized:
        raise ValueError("El libro debe contener al menos una hoja.")

    content_types = ET.Element(_tag(_CONTENT, "Types"))
    ET.SubElement(content_types, _tag(_CONTENT, "Default"), {"Extension": "rels", "ContentType": "application/vnd.openxmlformats-package.relationships+xml"})
    ET.SubElement(content_types, _tag(_CONTENT, "Default"), {"Extension": "xml", "ContentType": "application/xml"})
    ET.SubElement(content_types, _tag(_CONTENT, "Override"), {"PartName": "/xl/workbook.xml", "ContentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"})
    for index in range(1, len(materialized) + 1):
        ET.SubElement(content_types, _tag(_CONTENT, "Override"), {"PartName": f"/xl/worksheets/sheet{index}.xml", "ContentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"})

    workbook = ET.Element(_tag(_MAIN, "workbook"))
    sheets_node = ET.SubElement(workbook, _tag(_MAIN, "sheets"))
    rels = ET.Element(_tag(_PKG_REL, "Relationships"))
    for index, (name, rows) in enumerate(materialized, 1):
        ET.SubElement(sheets_node, _tag(_MAIN, "sheet"), {"name": name, "sheetId": str(index), _tag(_REL, "id"): f"rId{index}"})
        ET.SubElement(rels, _tag(_PKG_REL, "Relationship"), {"Id": f"rId{index}", "Type": f"{_REL}/worksheet", "Target": f"worksheets/sheet{index}.xml"})

    package_rels = ET.Element(_tag(_PKG_REL, "Relationships"))
    ET.SubElement(package_rels, _tag(_PKG_REL, "Relationship"), {"Id": "rId1", "Type": f"{_REL}/officeDocument", "Target": "xl/workbook.xml"})

    output = BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", _xml(content_types))
        archive.writestr("_rels/.rels", _xml(package_rels))
        archive.writestr("xl/workbook.xml", _xml(workbook))
        archive.writestr("xl/_rels/workbook.xml.rels", _xml(rels))
        for sheet_index, (_name, rows) in enumerate(materialized, 1):
            sheet = ET.Element(_tag(_MAIN, "worksheet"))
            data = ET.SubElement(sheet, _tag(_MAIN, "sheetData"))
            for row_index, values in enumerate(rows, 1):
                row = ET.SubElement(data, _tag(_MAIN, "row"), {"r": str(row_index)})
                for column_index, value in enumerate(values, 1):
                    if value is None:
                        continue
                    address = f"{_column_name(column_index)}{row_index}"
                    cell = ET.SubElement(row, _tag(_MAIN, "c"), {"r": address})
                    if isinstance(value, bool):
                        cell.set("t", "b")
                        ET.SubElement(cell, _tag(_MAIN, "v")).text = "1" if value else "0"
                    elif isinstance(value, (int, float)):
                        if isinstance(value, float) and not math.isfinite(value):
                            raise ValueError("No se pueden exportar números no finitos.")
                        ET.SubElement(cell, _tag(_MAIN, "v")).text = repr(value)
                    else:
                        cell.set("t", "inlineStr")
                        inline = ET.SubElement(cell, _tag(_MAIN, "is"))
                        text = ET.SubElement(inline, _tag(_MAIN, "t"))
                        text.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
                        text.text = str(value)
            archive.writestr(f"xl/worksheets/sheet{sheet_index}.xml", _xml(sheet))
    return output.getvalue()
