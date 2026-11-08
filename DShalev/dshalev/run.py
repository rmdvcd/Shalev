from __future__ import annotations

import argparse
import logging
import sys
import threading
import time
import webbrowser
from pathlib import Path

import uvicorn

from .app import create_app
from .config import DEFAULT_PORT, load_config, save_config


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="DShalev · Punto de venta web de escritorio")
    parser.add_argument("--lan", action="store_true", help="Habilita acceso en la red local (requiere autenticación)")
    parser.add_argument("--remote", action="store_true", help="Habilita HTTPS remoto usando un certificado configurado")
    parser.add_argument("--tls-cert", default="", help="Ruta al certificado TLS")
    parser.add_argument("--tls-key", default="", help="Ruta a la clave privada TLS")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--no-browser", action="store_true", help="No abrir el navegador al iniciar")
    return parser.parse_args()


def _show_error(message: str) -> None:
    if sys.platform == "win32" and getattr(sys, "frozen", False):
        try:
            import ctypes
            ctypes.windll.user32.MessageBoxW(None, message, "DShalev no pudo iniciar", 0x10)
            return
        except Exception:
            pass
    print(message, file=sys.stderr)


def main() -> None:
    args = _arguments()
    config = load_config()
    if not 1 <= args.port <= 65535:
        _show_error("El puerto debe estar entre 1 y 65535.")
        raise SystemExit(2)
    requested_mode = "remote" if args.remote else "lan" if args.lan else None
    if requested_mode:
        config = save_config(config, network_mode=requested_mode,
                             tls_cert=args.tls_cert or config.tls_cert, tls_key=args.tls_key or config.tls_key)
    if config.network_mode == "remote":
        cert, key = Path(config.tls_cert), Path(config.tls_key)
        if not cert.is_file() or not key.is_file():
            _show_error("El modo remoto requiere un certificado TLS y una clave privada existentes. Corrige las rutas en DShalevData/portable-settings.json o selecciona modo LAN.")
            raise SystemExit(2)
    log_dir = config.data_dir / "logs"
    log_dir.mkdir(exist_ok=True)
    logging.basicConfig(filename=log_dir / "DShalev.log", level=logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s %(message)s", encoding="utf-8")
    try:
        app = create_app(config, port=args.port)
        scheme = "https" if config.tls_enabled else "http"
        address = f"{scheme}://127.0.0.1:{args.port}"
        if not args.no_browser and config.network_mode != "remote":
            def open_browser() -> None:
                time.sleep(1.0)
                webbrowser.open(address)
            threading.Thread(target=open_browser, daemon=True).start()
        uvicorn.run(app, host=config.host, port=args.port, ssl_certfile=config.tls_cert or None,
                    ssl_keyfile=config.tls_key or None, log_config=None, access_log=False)
    except Exception as exc:
        logging.exception("DShalev no pudo iniciar")
        _show_error(f"No se pudo iniciar DShalev en el puerto {args.port}. Revisa DShalevData/logs/DShalev.log.\n\n{exc}")
        raise SystemExit(1) from exc


if __name__ == "__main__":
    main()
