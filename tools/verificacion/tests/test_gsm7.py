"""Pruebas de S-5 (tools/verificacion/gsm7.py): mensajes de licencia 100% GSM-7."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import gsm7  # noqa: E402


class ReglasGsm7(unittest.TestCase):
    def test_tildes_fuera_del_alfabeto_se_detectan(self):
        # "í", "ú" y "Á" no estan en GSM-7; "é" si (y "ñ" tambien).
        self.assertEqual(["í"], gsm7.caracteres_fuera_gsm7("Rodríguez"))
        self.assertEqual(["ú"], gsm7.caracteres_fuera_gsm7("Núñez"))
        self.assertEqual(["í", "Á"], gsm7.caracteres_fuera_gsm7("María Ángela"))
        self.assertEqual([], gsm7.caracteres_fuera_gsm7("Carné de identidad, Teléfono, Peña"))

    def test_extension_cuenta_doble(self):
        self.assertEqual(2, gsm7.contar_septetos("€"))

    def test_partes_sms(self):
        self.assertEqual(1, gsm7.partes_sms("A" * 160))
        self.assertEqual(2, gsm7.partes_sms("A" * 161))
        self.assertEqual(2, gsm7.partes_sms("A" * 306))
        self.assertEqual(3, gsm7.partes_sms("A" * 307))

    def test_el_aviso_de_7_dias_esta_en_las_tres_plataformas(self):
        self.assertEqual([], gsm7._avisos_en_fuentes())

    def test_la_parte_legible_no_usa_los_separadores_de_pantalla(self):
        # Money.format / Cup.formatted usan U+202F (fuera de GSM-7); el SMS usa el formato de exportacion.
        kotlin = gsm7.RUTA_KOTLIN.read_text(encoding="utf-8")
        bloque = gsm7._bloque(kotlin, "fun solicitud(", "fun licencia(")
        self.assertNotIn("Money.cup(", bloque)
        self.assertNotIn("Money.format(", bloque)
        swift = gsm7.RUTA_SWIFT.read_text(encoding="utf-8")
        bloque_swift = gsm7._bloque(swift, "private func requestMessage(", "public enum GLLicenseMessage")
        self.assertNotIn(".formatted)", bloque_swift)
        self.assertIn("formattedForExport", bloque_swift)

    def test_textos_de_android_e_ios_son_gsm7(self):
        for texto in gsm7.textos_kotlin() + gsm7.textos_swift():
            self.assertEqual([], texto.fuera, f"{texto.plataforma}/{texto.nombre}")
            if texto.nombre == "solicitud":
                self.assertLessEqual(texto.partes, gsm7.MAX_PARTES_SOLICITUD, texto.plataforma)


if __name__ == "__main__":
    unittest.main()
