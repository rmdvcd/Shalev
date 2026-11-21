"""Pruebas de verificación de raíces de confianza para release (D3)."""

import unittest
from tools.verificacion.verificar_raices_release import (
    extraer_raices_android,
    extraer_raices_dshalev,
    extraer_raices_ios,
    verificar_raices,
)


class RaicesReleaseTest(unittest.TestCase):
    def test_extraccion_raices_en_tres_plataformas(self):
        a_a, a_b = extraer_raices_android()
        ios_a, ios_b = extraer_raices_ios()
        d_a, d_b = extraer_raices_dshalev()

        # En desarrollo están presentes (como strings, aunque sean vacías/marcadores)
        self.assertIsInstance(a_a, str)
        self.assertIsInstance(a_b, str)
        self.assertIsInstance(ios_a, str)
        self.assertIsInstance(ios_b, str)
        self.assertIsInstance(d_a, str)
        self.assertIsInstance(d_b, str)

    def test_modo_desarrollo_pasa_sin_errores(self):
        errores = verificar_raices(es_release=False)
        self.assertEqual([], errores)

    def test_modo_release_falla_si_raices_vacias_o_de_prueba(self):
        errores = verificar_raices(es_release=True)
        # Debe reportar fallos para Android, iOS y DShalev
        self.assertTrue(len(errores) >= 3)
        self.assertTrue(any("Android" in e for e in errores))
        self.assertTrue(any("iOS" in e for e in errores))
        self.assertTrue(any("DShalev" in e for e in errores))
