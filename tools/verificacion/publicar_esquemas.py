#!/usr/bin/env python3
"""Publica solo esquemas NUEVOS generados por Room para revisión desde CI, sin escribirlos a mano.

La anotación comprimida es útil cuando el entorno de revisión no puede descargar
artefactos de Actions. Contiene únicamente la estructura pública de la base, nunca datos.
"""
import base64
import gzip
import pathlib
import subprocess

raiz = pathlib.Path(__file__).resolve().parents[2]
for archivo in sorted((raiz / "data/schemas/cu.shalev.data.db.ShalevDatabase").glob("*.json")):
    ruta = archivo.relative_to(raiz).as_posix()
    versionado = subprocess.run(["git", "ls-files", "--error-unmatch", ruta], cwd=raiz, capture_output=True).returncode == 0
    if not versionado:
        comprimido = base64.b64encode(gzip.compress(archivo.read_bytes(), mtime=0)).decode("ascii")
        print(f"::notice title=Esquema Room {archivo.stem} (gzip+base64)::{comprimido}")
