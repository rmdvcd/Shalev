import pytest
from dshalev.config import load_config
from dshalev.database import Database


def test_locales_aislan_datos_usuarios_claves_y_respaldo(tmp_path):
    original = load_config(tmp_path)
    a = load_config(tmp_path, local='centro')
    b = load_config(tmp_path, local='norte-2')
    assert original.data_dir == tmp_path / 'DShalevData'
    assert a.data_dir == tmp_path / 'DShalevData' / 'locales' / 'centro'
    assert len({original.secret_key, a.secret_key, b.secret_key}) == 3
    db_a, db_b = Database(a.database_path), Database(b.database_path)
    db_a.initialize(); db_b.initialize()
    with db_a.transaction() as conn:
        conn.execute("INSERT INTO items(name,category,unit,stock) VALUES('Solo centro','Producto','unidad',9)")
    with db_b.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM items').fetchone()[0] == 0
    assert db_a.key_path != db_b.key_path
    backup = db_a.export_backup('contraseña segura 123')
    with db_b.connect() as conn:
        assert conn.execute('SELECT COUNT(*) FROM items').fetchone()[0] == 0
    db_a.restore_backup(backup, 'contraseña segura 123')
    with db_a.connect() as conn:
        assert conn.execute('SELECT name FROM items').fetchone()[0] == 'Solo centro'
    assert load_config(tmp_path, local='centro').secret_key == a.secret_key
    db_a.close(); db_b.close()


@pytest.mark.parametrize('name', ['', '../fuera', '/tmp/x', 'Centro', '-centro', 'centro-', 'a'*41, 'a/b', 'a\\b', '.'])
def test_rechaza_identificadores_de_local_inseguros(tmp_path, name):
    with pytest.raises(ValueError):
        load_config(tmp_path, local=name)
    assert not (tmp_path / 'fuera').exists()


def test_dos_locales_de_la_misma_instalacion_comparten_una_sola_identidad(tmp_path, monkeypatch):
    """La identidad y la licencia son de la instalación, no del local: dos locales no crean dos identidades.

    El escritorio guarda la identidad fuera de la carpeta del local. La prueba fija dónde vive para comprobar que
    los dos locales comparten un solo archivo y que el segundo no lo reemplaza.
    """
    monkeypatch.setenv("DSHALEV_ALLOW_DEV_IDENTITY", "1")
    monkeypatch.setenv("XDG_DATA_HOME", str(tmp_path / "xdg"))
    monkeypatch.setenv("LOCALAPPDATA", str(tmp_path / "localappdata"))

    from dshalev.licencia.identidad import cargar_o_crear_identidad, ruta_identidad

    a, b = load_config(tmp_path, local="centro"), load_config(tmp_path, local="norte-2")
    ruta = ruta_identidad(tmp_path)
    primera = cargar_o_crear_identidad(ruta)
    segunda = cargar_o_crear_identidad(ruta)
    assert primera.huella == segunda.huella
    # La ruta está en la raíz de la instalación, no dentro de la carpeta de ningún local.
    assert ruta == tmp_path / "DShalevData" / "licencia" / ruta.name
    assert "locales" not in ruta.parts
    assert a.data_dir != b.data_dir
