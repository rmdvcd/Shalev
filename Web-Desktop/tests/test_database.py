from __future__ import annotations

from pathlib import Path
import sqlite3

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


def test_esquema_9_migra_a_10_con_registro_append_only_de_retiros(tmp_path: Path):
    path = tmp_path / "v9" / "business.sqlite3"
    db = Database(path)
    db.initialize()
    with db.transaction() as conn:
        conn.execute("DROP TRIGGER item_retirements_no_update")
        conn.execute("DROP TRIGGER item_retirements_no_delete")
        conn.execute("DROP TABLE item_retirements")
        conn.execute("UPDATE app_meta SET value='9' WHERE key='schema_version'")
    db.close()

    upgraded = Database(path)
    upgraded.initialize()
    with upgraded.connect() as conn:
        assert conn.execute("SELECT value FROM app_meta WHERE key='schema_version'").fetchone()[0] == "10"
        assert conn.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='item_retirements'").fetchone()
        triggers = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type='trigger'")}
        assert {"item_retirements_no_update", "item_retirements_no_delete"}.issubset(triggers)
    upgraded.close()


def test_database_does_not_silently_create_new_key_if_key_is_missing(tmp_path: Path):
    path = tmp_path / "DShalevData" / "business.sqlite3"
    db = Database(path)
    db.initialize()
    path.with_name("database.key").unlink()
    with pytest.raises(RuntimeError, match="Falta la clave de cifrado"):
        Database(path)


def test_legacy_fractional_stock_migrates_to_integer_milli_and_records_opening_balance(tmp_path: Path):
    path = tmp_path / "legacy" / "business.sqlite3"
    db = Database(path)
    with db.connect() as conn:
        conn.executescript("""
            CREATE TABLE items (
                id INTEGER PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, unit TEXT NOT NULL,
                stock INTEGER NOT NULL DEFAULT 0, sale_price_cents INTEGER NOT NULL DEFAULT 0,
                cost_cents INTEGER NOT NULL DEFAULT 0, low_stock INTEGER NOT NULL DEFAULT 5,
                critical_stock INTEGER NOT NULL DEFAULT 1, active INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
            );
            INSERT INTO items(name,category,unit,stock,low_stock,critical_stock)
            VALUES('Harina','Insumo','kg',0.3-0.1,0.5,0.1);
        """)
    db.initialize()
    with db.connect() as conn:
        row = conn.execute("SELECT stock,stock_milli,low_stock_milli,critical_stock_milli,typeof(stock_milli) FROM items WHERE id=1").fetchone()
        movement = conn.execute("SELECT * FROM inventory_movements WHERE item_id=1").fetchone()
        assert conn.execute("SELECT value FROM app_meta WHERE key='schema_version'").fetchone()[0] == "10"
        assert conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='item_retirements'").fetchone()[0] == "item_retirements"
        with pytest.raises(sqlite3.IntegrityError):
            conn.execute("UPDATE items SET stock_milli=9000000000000000001 WHERE id=1")
        with pytest.raises(sqlite3.IntegrityError):
            conn.execute("UPDATE items SET low_stock_milli=-1 WHERE id=1")
    assert row["stock_milli"] == 200 and row["typeof(stock_milli)"] == "integer"
    assert row["low_stock_milli"] == 500 and row["critical_stock_milli"] == 100
    assert movement["movement_type"] == "MIGRACION" and movement["delta_milli"] == 200
    assert "0.19999999999999998" in movement["reason"] and "0.2" in movement["reason"]

    db.initialize()
    with db.connect() as conn:
        assert conn.execute("SELECT COUNT(*) FROM inventory_movements").fetchone()[0] == 1
        with pytest.raises(sqlite3.IntegrityError):
            conn.execute("UPDATE inventory_movements SET reason='alterado' WHERE id=1")
