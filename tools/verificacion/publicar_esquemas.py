#!/usr/bin/env python3
"""Publica esquemas nuevos o modificados generados por Room para revisión desde CI, sin escribirlos a mano.

La anotación comprimida es útil cuando el entorno de revisión no puede descargar
artefactos de Actions. Contiene únicamente la estructura pública de la base, nunca datos.
"""
import base64
import gzip
import hashlib
import pathlib
import subprocess

raiz = pathlib.Path(__file__).resolve().parents[2]
for archivo in sorted((raiz / "Android/data/schemas/cu.shalev.data.db.ShalevDatabase").glob("*.json")):
    ruta = archivo.relative_to(raiz).as_posix()
    versionado = subprocess.run(["git", "ls-files", "--error-unmatch", ruta], cwd=raiz, capture_output=True).returncode == 0
    cambiado = subprocess.run(["git", "diff", "--quiet", "--", ruta], cwd=raiz).returncode != 0
    if not versionado or cambiado:
        datos = archivo.read_bytes()
        comprimido = base64.b64encode(gzip.compress(datos, mtime=0)).decode("ascii")
        huella = hashlib.sha256(datos).hexdigest()
        # GitHub recorta cada mensaje a 4096 caracteres: trozos pequeños y hash del JSON completo.
        partes = [comprimido[i:i + 2800] for i in range(0, len(comprimido), 2800)]
        for numero, parte in enumerate(partes, 1):
            print(f"::notice title=Esquema Room {archivo.stem} parte {numero}/{len(partes)}::{huella}:{parte}")
