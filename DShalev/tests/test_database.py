from __future__ import annotations

from pathlib import Path

import pytest

from dshalev.database import DB_MAGIC, Database


def test_sqlite_snapshot_is_authenticated_encrypted_and_reopenable(tmp_path: Path):
    path = tmp_path / "DShalevData" / "business.sqlite3"
    db = Database(path)
    db.initialize()
    with db.transaction() as conn:
        conn.execute("UPDATE app_meta SET value='Cafetería privada' WHERE key='business_name'")
    encrypted = path.read_bytes()
    assert encrypted.startswith(DB_MAGIC)
    assert b"Cafeter" not in encrypted

    reopened = Database(path)
    reopened.initialize()
    with reopened.connect() as conn:
        row = conn.execute("SELECT value FROM app_meta WHERE key='business_name'").fetchone()
    assert row["value"] == "Cafetería privada"


def test_passphrase_backup_restores_database_and_rejects_wrong_password(tmp_path: Path):
    path = tmp_path / "DShalevData" / "business.sqlite3"
    db = Database(path)
    db.initialize()
    with db.transaction() as conn:
        conn.execute("UPDATE app_meta SET value='Respaldo original' WHERE key='business_name'")
    backup = db.export_backup("Frase de respaldo fuerte 2026!")
    assert backup.startswith(b"DSHALEV-BACKUP-1") and b"Respaldo original" not in backup
    with pytest.raises(ValueError, match="Contraseña incorrecta"):
        db.restore_backup(backup, "Contraseña equivocada 2026!")
    with db.transaction() as conn:
        conn.execute("UPDATE app_meta SET value='Datos nuevos' WHERE key='business_name'")
    db.restore_backup(backup, "Frase de respaldo fuerte 2026!")
    with db.connect() as conn:
        assert conn.execute("SELECT value FROM app_meta WHERE key='business_name'").fetchone()[0] == "Respaldo original"
    reopened = Database(path)
    reopened.initialize()
    with reopened.connect() as conn:
        assert conn.execute("SELECT value FROM app_meta WHERE key='business_name'").fetchone()[0] == "Respaldo original"


def test_database_does_not_silently_create_new_key_if_key_is_missing(tmp_path: Path):
    path = tmp_path / "DShalevData" / "business.sqlite3"
    db = Database(path)
    db.initialize()
    path.with_name("database.key").unlink()
    with pytest.raises(RuntimeError, match="Falta la clave de cifrado"):
        Database(path)
