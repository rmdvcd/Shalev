from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).parents[1]
CSS = (ROOT / "dshalev" / "static" / "app.css").read_text(encoding="utf-8")
DASHBOARD = (ROOT / "dshalev" / "templates" / "dashboard.html").read_text(encoding="utf-8")


def bloque(selector: str) -> str:
    match = re.search(re.escape(selector) + r"\s*\{([^{}]*)\}", CSS, re.DOTALL)
    assert match, f"No existe el bloque CSS {selector!r}"
    return match.group(1)


def variables(bloque_css: str) -> dict[str, str]:
    return dict(re.findall(r"--([\w-]+)\s*:\s*([^;{}]+)\s*(?:;|$)", bloque_css))


def resolver(valor: str, tokens: dict[str, str]) -> str:
    for _ in range(12):
        match = re.search(r"var\(--([\w-]+)\)", valor)
        if not match:
            return valor.strip()
        nombre = match.group(1)
        assert nombre in tokens, f"Token CSS sin definir: --{nombre}"
        valor = valor[: match.start()] + tokens[nombre] + valor[match.end() :]
    raise AssertionError(f"Referencia circular de variables CSS: {valor}")


def luminancia(hex_color: str) -> float:
    value = hex_color.strip().removeprefix("#")
    if re.fullmatch(r"[0-9a-fA-F]{3}", value):
        value = "".join(char * 2 for char in value)
    assert re.fullmatch(r"[0-9a-fA-F]{6}", value), f"Se esperaba un color opaco: {hex_color}"
    channels = [int(value[i : i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contraste(foreground: str, background: str) -> float:
    high, low = sorted((luminancia(foreground), luminancia(background)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def test_la_escala_css_y_la_identidad_existente_estan_tokenizadas():
    root = variables(bloque(":root"))
    assert [root[f"space-{i}"] for i in ("1", "2", "3", "4", "6", "8", "12", "20")] == [
        "4px", "8px", "12px", "16px", "24px", "32px", "48px", "80px"
    ]
    assert root["accent"] == "#2b4fa3"
    assert root["surface"] == "#fff"
    assert root["touch-target"] == "44px"
    assert root["motion-press"] == "0ms"
    radios = re.findall(r"border-radius\s*:\s*([^;}]+)", CSS)
    assert all(not re.search(r"\d+px", radio) for radio in radios)
    sin_tokens = re.sub(r":root\s*\{[^{}]*\}", "", CSS)
    sin_tokens = re.sub(r'html\[data-theme="oscuro"\]\s*\{[^{}]*\}', "", sin_tokens)
    assert not re.search(r"#[0-9a-fA-F]{3,8}\b|rgba?\(", sin_tokens)


def test_objetivos_interactivos_tienen_al_menos_44px():
    selectores = (
        ".primary-button,.secondary-button,.quiet-button",
        ".brand",
        ".form-stack input,.form-stack select,.form-row input,.form-row select,.search-form input,.compact-label input",
        ".permission-editor summary",
        ".permission-editor .secondary-button",
        ".retire-action summary",
        ".check-row",
        ".cart-quantity",
        ".pos-remove",
        ".theme-control select",
        ".text-link",
    )
    for selector in selectores:
        assert "var(--touch-target)" in bloque(selector), selector
    assert re.search(r"summary\s*\{\s*min-height:var\(--touch-target\)", CSS)
    remove = bloque(".pos-remove")
    assert "width:var(--touch-target)" in remove and "height:var(--touch-target)" in remove


def test_el_punto_de_estado_es_decorativo_y_el_estado_aparece_en_texto():
    assert 'class="status-dot {% if shift %}on{% endif %}" aria-hidden="true"' in DASHBOARD
    assert "Abierto" in DASHBOARD and "Cerrado" in DASHBOARD


def test_contraste_de_avisos_aa_en_tema_claro_y_oscuro():
    root = variables(bloque(":root"))
    dark = variables(bloque('html[data-theme="oscuro"]'))
    for theme, tokens in (("claro", root), ("oscuro", root | dark)):
        for role in ("success", "info", "warning", "danger"):
            foreground = resolver(tokens[f"status-{role}-text"], tokens)
            background = resolver(tokens[f"status-{role}-bg"], tokens)
            ratio = contraste(foreground, background)
            assert ratio >= 4.5, f"{theme} {role}: contraste {ratio:.2f}:1"


def test_contraste_de_roles_de_texto_y_controles_en_ambos_temas():
    root = variables(bloque(":root"))
    dark = variables(bloque('html[data-theme="oscuro"]'))
    for theme, tokens in (("claro", root), ("oscuro", root | dark)):
        background = resolver(tokens["bg"], tokens)
        surface = resolver(tokens["surface"], tokens)
        text_roles = (
            "text", "text-muted", "text-business", "text-navigation", "text-rail-footer",
            "text-metric-foot", "text-metric-neutral", "text-table-header", "text-table",
            "text-cell-sub", "text-empty-strong", "text-label", "text-help", "text-tiny",
            "text-employee-state",
        )
        for role in text_roles:
            foreground = resolver(tokens[role], tokens)
            assert contraste(foreground, surface) >= 4.5, f"{theme} {role} no alcanza AA sobre surface"
        assert contraste(resolver(tokens["text-navigation-icon"], tokens), background) >= 3.0
        assert contraste(resolver(tokens["status-idle"], tokens), background) >= 3.0
        assert contraste(resolver(tokens["text-empty-icon"], tokens), surface) >= 3.0
        assert contraste(resolver(tokens["text-pill"], tokens), resolver(tokens["surface-pill"], tokens)) >= 4.5
        assert contraste(resolver(tokens["accent-text"], tokens), resolver(tokens["accent"], tokens)) >= 4.5
        assert contraste(resolver(tokens["primary-action-text"], tokens), resolver(tokens["primary-action-bg"], tokens)) >= 4.5
        stops = re.findall(r"#[0-9a-fA-F]{6}", tokens["metric-accent-gradient"])
        for stop in stops:
            assert contraste(resolver(tokens["metric-accent-text"], tokens), stop) >= 4.5


def test_pulsacion_foco_movimiento_reducido_y_elevacion_oscura():
    root = variables(bloque(":root"))
    dark = variables(bloque('html[data-theme="oscuro"]'))
    assert "transform:scale(.97)" in CSS
    assert "transition-duration:var(--motion-press)" in CSS
    assert "outline:3px solid var(--accent)" in CSS
    assert "outline-offset:2px" in CSS
    assert "@media (prefers-reduced-motion: reduce)" in CSS
    tokens_dark = root | dark
    assert resolver(tokens_dark["shadow"], tokens_dark) == "none"
    assert resolver(tokens_dark["shadow-raised"], tokens_dark) == "none"
    assert resolver(tokens_dark["shadow-floating"], tokens_dark) == "none"
