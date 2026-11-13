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
