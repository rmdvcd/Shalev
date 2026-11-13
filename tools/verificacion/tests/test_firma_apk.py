"""Contrato del parser de apksigner con salidas simuladas; NO firma ni verifica un APK real."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'verificar_firma_apk.sh'
HUELLA = '139d3da93bd3abb881a2c55503dae0b059ab4c36a68ad16394a8b526ffd41738'
V2 = 'Verified using v2 scheme (APK Signature Scheme v2): true'


class FirmaApkTest(unittest.TestCase):
    def ejecutar(self, salida, codigo=0):
        with tempfile.TemporaryDirectory() as tmp:
            raiz = Path(tmp)
            apk = raiz / 'app de prueba.apk'
            apk.write_bytes(b'fixture del parser, no es un APK')
            sdk = raiz / 'sdk android'
            tools = sdk / 'build-tools' / '35.0.0'
            tools.mkdir(parents=True)
            ejecutable = tools / 'apksigner'
            ejecutable.write_text(f"#!/bin/sh\ncat <<'SALIDA_APKSIGNER'\n{salida}\nSALIDA_APKSIGNER\nexit {codigo}\n", encoding='utf-8')
            ejecutable.chmod(0o755)
            return subprocess.run(['bash', str(SCRIPT), str(apk)], capture_output=True, text=True,
                                  env={**os.environ, 'ANDROID_HOME': str(sdk)})

    def test_formato_real_sha_sin_dos_puntos(self):
        r = self.ejecutar(f'{V2}\nSigner #1 certificate SHA-256 digest: {HUELLA}')
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        self.assertIn('OK: firmado con la clave vigente', r.stdout)

    def test_formato_corto_y_sha_con_dos_puntos(self):
        colon = ':'.join(HUELLA[i:i + 2] for i in range(0, len(HUELLA), 2)).upper()
        r = self.ejecutar(f'Verified using v2 scheme: true\nSigner #1 certificate SHA-256 digest: {colon}')
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)

    def test_no_acepta_otra_clave(self):
        self.assertNotEqual(0, self.ejecutar(f'{V2}\nSigner #1 certificate SHA-256 digest: {"a" * 64}').returncode)

    def test_no_basta_una_huella_sin_verificacion_v2(self):
        for v2 in (V2.replace('true', 'false'), 'Verified using v3 scheme (APK Signature Scheme v3): true'):
            with self.subTest(v2=v2):
                self.assertNotEqual(0, self.ejecutar(f'{v2}\nSigner #1 certificate SHA-256 digest: {HUELLA}').returncode)

    def test_huella_ausente_o_truncada_falla_cerrado(self):
        for linea in ('', 'Signer #1 certificate SHA-256 digest: 139d', 'Signer #1 certificate SHA-256 digest: texto'):
            with self.subTest(linea=linea):
                self.assertNotEqual(0, self.ejecutar(f'{V2}\n{linea}').returncode)

    def test_rechaza_multiples_firmantes(self):
        salida = f'{V2}\nSigner #1 certificate SHA-256 digest: {HUELLA}\nSigner #2 certificate SHA-256 digest: {"a" * 64}'
        self.assertNotEqual(0, self.ejecutar(salida).returncode)

    def test_error_de_apksigner_no_se_oculta(self):
        salida = f'{V2}\nSigner #1 certificate SHA-256 digest: {HUELLA}'
        self.assertNotEqual(0, self.ejecutar(salida, codigo=1).returncode)


if __name__ == '__main__':
    unittest.main()
