from __future__ import annotations

import json
import os
import secrets
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

APP_NAME = "DShalev"
DEFAULT_PORT = 47812


def portable_root() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class AppConfig:
    data_dir: Path
    settings_path: Path
    database_path: Path
    secret_key: str
    network_mode: str = "local"
    tls_cert: str = ""
    tls_key: str = ""
    public_host: str = ""

    @property
    def host(self) -> str:
        return "127.0.0.1" if self.network_mode == "local" else "0.0.0.0"

    @property
    def tls_enabled(self) -> bool:
        return self.network_mode == "remote"


def load_config(root: Path | None = None) -> AppConfig:
    root = root or portable_root()
    data_dir = root / "DShalevData"
    data_dir.mkdir(parents=True, exist_ok=True)
    path = data_dir / "portable-settings.json"
    try:
        raw: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        raw = {}
    secret_key = str(raw.get("secret_key") or secrets.token_urlsafe(48))
    network_mode = str(raw.get("network_mode", "local"))
    if network_mode not in {"local", "lan", "remote"}:
        network_mode = "local"
    config = AppConfig(
        data_dir=data_dir,
        settings_path=path,
        database_path=data_dir / "dshalev.sqlite3",
        secret_key=secret_key,
        network_mode=network_mode,
        tls_cert=str(raw.get("tls_cert", "")),
        tls_key=str(raw.get("tls_key", "")),
        public_host=str(raw.get("public_host", "")),
    )
    save_config(config, root=root)
    return config


def save_config(config: AppConfig, *, root: Path | None = None, **changes: str) -> AppConfig:
    values = asdict(config)
    for key, value in changes.items():
        if key not in {"network_mode", "tls_cert", "tls_key", "public_host"}:
            raise ValueError(f"Configuración no editable: {key}")
        values[key] = value
    updated = AppConfig(**values)
    updated.settings_path.parent.mkdir(parents=True, exist_ok=True)
    temp = updated.settings_path.with_suffix(".tmp")
    temp.write_text(json.dumps({
        "secret_key": updated.secret_key,
        "network_mode": updated.network_mode,
        "tls_cert": updated.tls_cert,
        "tls_key": updated.tls_key,
        "public_host": updated.public_host,
    }, indent=2), encoding="utf-8")
    try:
        os.chmod(temp, 0o600)
    except OSError:
        pass
    temp.replace(updated.settings_path)
    return updated
