"""Pruebas de verificación de raíces de confianza para release (D3)."""

import unittest
from tools.verificacion.verificar_raices_release import (
    extraer_app_id_android,
    extraer_app_id_dshalev,
    extraer_app_id_ios,
    extraer_raices_android,
    extraer_raices_dshalev,
    extraer_raices_ios,
    verificar_raices,
    CANJE_APP_IDS_PERMITIDOS,
)


class RaicesReleaseTest(unittest.TestCase):
    def test_canje_app_id_en_conjunto_permitido(self):
        android_id = extraer_app_id_android()
        ios_id = extraer_app_id_ios()
        dshalev_id = extraer_app_id_dshalev()

        self.assertIn(android_id, CANJE_APP_IDS_PERMITIDOS)
        self.assertIn(ios_id, CANJE_APP_IDS_PERMITIDOS)
        self.assertIn(dshalev_id, CANJE_APP_IDS_PERMITIDOS)
        self.assertEqual("SHALEV-ANDROID", android_id)
        self.assertEqual("SHALEV-IOS", ios_id)
        self.assertEqual("DSHALEV", dshalev_id)
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
