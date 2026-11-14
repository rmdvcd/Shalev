"""Pruebas de la regla de botones (tools/verificacion/botones.py) y comprobación sobre el código real."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import botones  # noqa: E402


def revisar(codigo: str, extra: str = ""):
    textos = {os.path.join(botones.RAIZ, "A.kt"): codigo}
    if extra:
        textos[os.path.join(botones.RAIZ, "Textos.kt")] = extra
    return botones.revisar(textos)


class ReglaDeBotones(unittest.TestCase):
    def test_una_palabra_pasa(self):
        errores, _ = revisar('ShalevPrimaryButton("Guardar", onClick = {}, icon = ShalevIcons.Guardar)')
        self.assertEqual([], errores)

    def test_varias_palabras_falla(self):
        errores, _ = revisar('ShalevSecondaryButton("Quitar filtros", onClick = {}, icon = ShalevIcons.QuitarFiltros)')
        self.assertEqual(["Quitar filtros"], [e.texto for e in errores])

    def test_la_etiqueta_manda_sobre_la_descripcion(self):
        errores, _ = revisar('ShalevIconAction(ShalevIcons.Cancelar, "Quitar selección", etiqueta = "Quitar", onClick = {})')
        self.assertEqual([], errores)

    def test_constantes_resueltas(self):
        extra = 'object TextosX {\n    const val LARGO = "Ver la venta"\n    const val CORTO = "Venta"\n}\n'
        errores, _ = revisar('ShalevSecondaryButton(TextosX.LARGO, onClick = {}, icon = ShalevIcons.Comprobante)', extra)
        self.assertEqual(["Ver la venta"], [e.texto for e in errores])
        errores, _ = revisar('ShalevSecondaryButton(TextosX.CORTO, onClick = {}, icon = ShalevIcons.Comprobante)', extra)
        self.assertEqual([], errores)

    def test_las_dos_ramas_de_un_if(self):
        errores, _ = revisar('ShalevTextButton(text = if (a) "Saltar todo" else "Cerrar", icon = ShalevIcons.Cancelar, onClick = {})')
        self.assertEqual(["Saltar todo"], [e.texto for e in errores])

    def test_plantilla_cuenta_como_varias_palabras(self):
        errores, _ = revisar('ShalevIconAction(ShalevIcons.Quitar, "Quitar ${l.nombre}", onClick = {})')
        self.assertEqual(1, len(errores))

    def test_dialogo_confirmar(self):
        errores, _ = revisar('ShalevDialog(title = "T", onDismiss = {}, onConfirm = {}, confirmDescription = "Abrir turno")')
        self.assertEqual(["Abrir turno"], [e.texto for e in errores])
        errores, _ = revisar('ShalevDialog(title = "T", onDismiss = {}, onConfirm = {}, confirmDescription = "Abrir turno", confirmEtiqueta = "Abrir")')
        self.assertEqual([], errores)
        errores, _ = revisar('ShalevDialog(title = "T", onDismiss = {}, onConfirm = null, confirmDescription = "Abrir turno")')
        self.assertEqual([], errores)

    def test_solo_icono_solo_en_las_excepciones(self):
        ok, _ = revisar('ShalevIconAction(ShalevIcons.Filtrar, "Filtrar productos", mostrarTexto = false, onClick = {})')
        self.assertEqual([], ok)
        mal, _ = revisar('ShalevIconAction(ShalevIcons.Editar, "Editar", mostrarTexto = false, onClick = {})')
        self.assertEqual(["solo icono fuera de las excepciones"], [e.motivo for e in mal])

    def test_comentarios_y_definiciones_no_cuentan(self):
        codigo = '/**\n * ShalevPrimaryButton("Dos palabras")\n */\nfun ShalevPrimaryButton(text: String) {}\n// ShalevTextButton("Otra cosa")\n'
        errores, _ = revisar(codigo)
        self.assertEqual([], errores)

    def test_el_codigo_de_la_app_cumple_la_regla(self):
        errores, _ = botones.revisar(botones.leer(botones.FUENTES))
        self.assertEqual([], [str(e) for e in errores])


if __name__ == "__main__":
    unittest.main()
