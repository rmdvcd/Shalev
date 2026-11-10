"""Los iconos compartidos mantienen el trazado de la referencia Android."""
import re
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "designsystem/src/main/kotlin/cu/shalev/designsystem/icon/ShalevIcons.kt"
ASSETS = ROOT / "IShalev/Resources/Assets.xcassets"


def trazados_android(nombre):
    fuente = SOURCE.read_text(encoding="utf-8")
    patron = rf'val {nombre}: ImageVector by lazy \{{ icono\("{nombre}", (.*?)\) \}}'
    coincidencia = re.search(patron, fuente)
    assert coincidencia, nombre
    return re.findall(r'"([^"]+)"', coincidencia.group(1))


def test_iconos_ios_usan_trazados_android():
    for nombre in ("Home", "Inventory2", "ManoRecibe", "History", "Build", "Settings"):
        svg = ElementTree.parse(ASSETS / f"Shalev{nombre}.imageset/icon.svg")
        assert [path.attrib["d"] for path in svg.getroot()] == trazados_android(nombre)


def test_iconos_escritorio_usan_trazados_android():
    plantilla = (ROOT / "DShalev/dshalev/templates/base.html").read_text(encoding="utf-8")
    for nombre in ("Home", "Inventory2", "ShoppingCart", "Storefront", "History", "ListAlt", "Person", "Wifi", "Backup", "ManoRecibe", "Build", "Key"):
        for trazado in trazados_android(nombre):
            assert f'<path d="{trazado}"/>' in plantilla
