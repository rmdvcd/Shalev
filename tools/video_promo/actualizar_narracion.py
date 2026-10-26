#!/usr/bin/env python3
"""Sustituye la narración sin recodificar ni cambiar los fotogramas del video.

Entradas: MP4 y SRT existentes, y diez voces nuevas en
.cache/shalev_promo90/voz/01.mp3 … 10.mp3.
La voz se ajusta a cada escena con atempo, que conserva el tono original.
Se reconstruyen la música atenuada y los efectos del generador original.

Uso: .venv/bin/python tools/video_promo/actualizar_narracion.py
Dependencias: numpy, Pillow, imageio-ffmpeg (las mismas del generador).
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import subprocess
import wave

import imageio_ffmpeg

import generar_promo_90 as promo


def segundos(marca: str) -> float:
    h, m, s = marca.strip().replace(",", ".").split(":")
    return int(h) * 3600 + int(m) * 60 + float(s)


def restaurar_tiempos(srt: Path) -> float:
    """Recupera los cortes del montaje a partir de sus subtítulos originales."""
    bloques = srt.read_text(encoding="utf-8").strip().split("\n\n")
    inicios = [segundos(b.splitlines()[1].split(" --> ")[0]) for b in bloques]
    esperados = sum(len(s["captions"]) for s in promo.SCENES)
    if len(inicios) != esperados:
        raise ValueError(f"Se esperaban {esperados} subtítulos; hay {len(inicios)}")
    indice = 0
    for escena in promo.SCENES:
        escena["start"] = round(inicios[indice] - promo.LEAD, 3)
        escena["narration_start"] = inicios[indice]
        indice += len(escena["captions"])
    total = promo.SCENES[-1]["start"] + promo.LEAD + 4.4 + promo.TAIL
    for i, escena in enumerate(promo.SCENES):
        escena["end"] = promo.SCENES[i + 1]["start"] if i + 1 < len(promo.SCENES) else total
        escena["duration"] = escena["end"] - escena["start"]
        escena["narr_dur"] = escena["duration"] - promo.LEAD - promo.TAIL
    return total


def ajustar_voces() -> None:
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    for escena in promo.SCENES:
        if not escena["audio"]:
            continue
        mp3 = promo.VOZ / escena["audio"]
        if not mp3.is_file():
            raise FileNotFoundError(f"Falta la narración: {mp3}")
        crudo = mp3.with_name(mp3.stem + "_original.wav")
        wav = mp3.with_suffix(".wav")
        subprocess.run([ff, "-y", "-v", "error", "-i", str(mp3),
                        "-ac", "1", "-ar", str(promo.SR), str(crudo)], check=True)
        duracion = promo.wav_duration(crudo)
        destino = escena["narr_dur"]
        ritmo = duracion / destino
        if not 0.75 <= ritmo <= 1.25:
            raise ValueError(f"La escena {escena['n']} requiere un ajuste excesivo ({ritmo:.3f})")
        subprocess.run([ff, "-y", "-v", "error", "-i", str(crudo),
                        "-af", f"atempo={ritmo:.9f},apad,atrim=duration={destino:.6f}",
                        "-ac", "1", "-ar", str(promo.SR), str(wav)], check=True)
        with wave.open(str(wav), "rb") as w:
            assert w.getnchannels() == 1 and w.getframerate() == promo.SR
            assert abs(w.getnframes() / promo.SR - destino) <= 1 / promo.SR
        print(f"Escena {escena['n']:02d}: {duracion:.3f} → {destino:.3f} s; "
              f"ritmo {ritmo:.3f}, tono conservado", flush=True)


def huella_video(path: Path) -> str:
    """Compara el flujo H.264, no el contenedor ni los paquetes de audio."""
    result = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-i", str(path),
                             "-map", "0:v:0", "-c:v", "copy", "-f", "hash",
                             "-hash", "sha256", "-"], check=True, capture_output=True, text=True)
    return result.stdout.strip()


def main() -> None:
    video = promo.OUT / "Shalev_promo_90s_1080p60.mp4"
    srt = video.with_name("Shalev_promo_90s.srt")
    temporal = promo.WORK / "Shalev_promo_narracion_nueva.mp4"
    promo.WORK.mkdir(parents=True, exist_ok=True)
    total = restaurar_tiempos(srt)
    hash_antes = huella_video(video)
    hash_srt = hashlib.sha256(srt.read_bytes()).hexdigest()
    ajustar_voces()
    audio = promo.build_audio(total)
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-v", "error",
                    "-i", str(video), "-i", str(audio),
                    "-map", "0:v:0", "-map", "1:a:0", "-map_metadata", "0",
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
                    "-movflags", "+faststart", str(temporal)], check=True)
    if huella_video(temporal) != hash_antes:
        raise RuntimeError("El flujo de video cambió; se conserva el archivo anterior")
    if hashlib.sha256(srt.read_bytes()).hexdigest() != hash_srt:
        raise RuntimeError("Los subtítulos originales cambiaron")
    temporal.replace(video)
    print(f"Video actualizado: {video}\nFotogramas originales verificados: {hash_antes}", flush=True)


if __name__ == "__main__":
    main()
