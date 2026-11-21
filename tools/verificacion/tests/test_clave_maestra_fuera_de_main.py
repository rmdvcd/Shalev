"""Verificación de seguridad S5 (IMPLEMENTACION_SHALEV.md).

Comprueba que el literal de clave maestra 'GLMK-PERM-MAXS-9999' no esté presente en
ningún archivo de código de producción (especialmente cualquier directorio src/main).
"""

from pathlib import Path
import unittest

ROOT_DIR = Path(__file__).resolve().parents[2]
LITERAL_MAESTRA = "GLMK-PERM-MAXS-9999"


class ClaveMaestraFueraDeMainTest(unittest.TestCase):
    def test_literal_maestra_no_esta_en_src_main(self):
        """Escanea todos los archivos bajo src/main asegurando que el literal no existe."""
        hallazgos = []
        for path in ROOT_DIR.glob("**/src/main/**/*"):
            if not path.is_file():
                continue
            # Ignorar binarios, imágenes, etc.
            if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp", ".wav", ".mp4", ".jar", ".keystore"}:
                continue
            try:
                contenido = path.read_text(encoding="utf-8", errors="ignore")
                if LITERAL_MAESTRA in contenido:
                    hallazgos.append(str(path.relative_to(ROOT_DIR)))
            except Exception:
                pass

        self.assertEqual(
            [],
            hallazgos,
            f"El literal {LITERAL_MAESTRA} fue encontrado en archivos de src/main: {hallazgos}",
        )

    def test_literal_maestra_no_esta_en_dshalev_produccion(self):
        """Escanea Web-Desktop/dshalev asegurando que el literal no existe en producción desktop."""
        hallazgos = []
        dshalev_dir = ROOT_DIR / "Web-Desktop" / "dshalev"
        if dshalev_dir.is_dir():
            for path in dshalev_dir.glob("**/*"):
                if not path.is_file() or path.suffix.lower() != ".py":
                    continue
                contenido = path.read_text(encoding="utf-8", errors="ignore")
                if LITERAL_MAESTRA in contenido:
                    hallazgos.append(str(path.relative_to(ROOT_DIR)))

        self.assertEqual(
            [],
            hallazgos,
            f"El literal {LITERAL_MAESTRA} fue encontrado en código de producción de DShalev: {hallazgos}",
        )

    def test_literal_maestra_no_esta_en_ios_produccion(self):
        """Escanea iOS/Sources asegurando que el literal no existe en producción iOS."""
        hallazgos = []
        ios_dir = ROOT_DIR / "iOS" / "Sources"
        if ios_dir.is_dir():
            for path in ios_dir.glob("**/*"):
                if not path.is_file() or path.suffix.lower() != ".swift":
                    continue
                contenido = path.read_text(encoding="utf-8", errors="ignore")
                if LITERAL_MAESTRA in contenido:
                    hallazgos.append(str(path.relative_to(ROOT_DIR)))

        self.assertEqual(
            [],
            hallazgos,
            f"El literal {LITERAL_MAESTRA} fue encontrado en código de producción de iOS: {hallazgos}",
        )


if __name__ == "__main__":
    unittest.main()
