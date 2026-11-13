"""Regresiones del guardarraíl CI; fixtures XML, no son pruebas Android ejecutadas."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'resumen_instrumentados.py'
OK = '<testsuite><testcase classname="Regresion" name="pasa"/></testsuite>'
OMITIDO = '<testsuite><testcase name="omitido"><skipped/></testcase></testsuite>'


class ResumenInstrumentadosTest(unittest.TestCase):
    def ejecutar(self, archivos):
        with tempfile.TemporaryDirectory() as raiz:
            for modulo, texto in archivos.items():
                ruta = Path(raiz) / modulo / 'build/outputs/androidTest-results/connected/debug/resultados.xml'
                ruta.parent.mkdir(parents=True)
                ruta.write_text(texto, encoding='utf-8')
            return subprocess.run([sys.executable, str(SCRIPT)], cwd=raiz, capture_output=True, text=True)

    def test_sin_resultados_no_equivale_a_ejecutado(self):
        self.assertEqual(1, self.ejecutar({}).returncode)

    def test_falta_un_modulo_o_esta_omitido(self):
        for archivos in ({'app': OK}, {'app': OK, 'data': OMITIDO}, {'app': OMITIDO, 'data': OMITIDO}):
            with self.subTest(archivos=archivos):
                self.assertEqual(1, self.ejecutar(archivos).returncode)

    def test_xml_ilegible_es_fallo(self):
        self.assertEqual(1, self.ejecutar({'app': OK, 'data': '<testsuite>'}).returncode)

    def test_fallo_real_conserva_detalle_y_exit_code(self):
        r = self.ejecutar({'app': OK, 'data': '<testsuite><testcase name="error"><failure>Rollback incorrecto</failure></testcase></testsuite>'})
        self.assertEqual(1, r.returncode)
        self.assertIn('Rollback incorrecto', r.stdout)

    def test_fallos_repetidos_de_ui_no_ocultan_los_de_datos(self):
        app = '<testsuite>' + ''.join(f'<testcase classname="cu.shalev.app.Ui" name="f{i}"><failure>Dependencia incompatible</failure></testcase>' for i in range(30)) + '</testsuite>'
        data = '<testsuite><testcase classname="cu.shalev.data.RoomTest" name="rollback"><failure>Fallo de negocio</failure></testcase></testsuite>'
        r = self.ejecutar({'app': app, 'data': data})
        self.assertEqual(1, r.returncode)
        self.assertLess(r.stdout.index('::error::data:'), r.stdout.index('::error::app:'))
        self.assertEqual(2, r.stdout.count('::error::'))
        self.assertIn('Fallo de negocio', r.stdout)

    def test_mas_de_diez_tipos_conservan_diagnosticos_en_notices(self):
        app = '<testsuite>' + ''.join(f'<testcase classname="Ui" name="f{i}"><failure>Distinto {i}</failure></testcase>' for i in range(15)) + '</testsuite>'
        r = self.ejecutar({'app': app, 'data': OK})
        self.assertEqual(1, r.returncode)
        self.assertEqual(10, r.stdout.count('::error::'))
        for i in range(15):
            self.assertIn(f'Distinto {i}', r.stdout)
        self.assertIn('Casos: Ui.f9', r.stdout)

    def test_dos_modulos_ejecutados_sin_fallos(self):
        self.assertEqual(0, self.ejecutar({'app': OK, 'data': OK}).returncode)


if __name__ == '__main__':
    unittest.main()
