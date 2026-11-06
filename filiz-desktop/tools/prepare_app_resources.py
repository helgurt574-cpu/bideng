#!/usr/bin/env python3
"""Masaüstü kurulum paketine gömülecek dosyaları hazırlar (tamamen çevrimdışı çalışma için).

build/app-resources/common/tts/      -> ortak espeak-ng verisi + 5 ses (model.onnx + tokens.txt)
build/app-resources/<os>/sherpa/     -> o işletim sistemine ait sherpa-onnx-offline-tts ve kütüphaneleri

Kullanım: python3 tools/prepare_app_resources.py <windows|macos|linux> [--smoke-test | --smoke-only]
Yalnızca derleme sırasında internet gerekir; kurulan uygulama hiçbir şey indirmez.
"""
import hashlib
import os
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

DESKTOP = Path(__file__).resolve().parent.parent
REPO = DESKTOP.parent
ANDROID_TTS = REPO / "app" / "src" / "main" / "assets" / "tts"
OUT = DESKTOP / "build" / "app-resources"
CACHE = DESKTOP / "build" / "download-cache"

SHERPA_VERSION = "2.0.1"
SHERPA_ARCHIVES = {
    "windows": f"sherpa-onnx-v{SHERPA_VERSION}-win-x64-shared-MD-Release.tar.bz2",
    "macos": f"sherpa-onnx-v{SHERPA_VERSION}-osx-arm64-shared.tar.bz2",
    "linux": f"sherpa-onnx-v{SHERPA_VERSION}-linux-x64-shared.tar.bz2",
}

HF = "https://huggingface.co/csukuangfj"
# APK ile aynı sesler; modeller Android'in tokens.txt dosyalarıyla eşleşir.
DOWNLOADED_VOICES = [
    ("tr_TR_dfki_medium", f"{HF}/vits-piper-tr_TR-dfki-medium/resolve/main/tr_TR-dfki-medium.onnx",
     "35c924643ba76043184b4b685b468ef8e06492d1c6eb029fbe12cac63b56ff82"),
    ("vits-piper-ar_JO-kareem-low", f"{HF}/vits-piper-ar_JO-kareem-medium/resolve/main/ar_JO-kareem-medium.onnx",
     "f942091e1fdb7221b289256cc2238295b5472d326029532f79bb84836380fe53"),
    ("vits-piper-fa_IR-amir-medium", f"{HF}/vits-piper-fa_IR-amir-medium/resolve/main/fa_IR-amir-medium.onnx",
     "5a832803df099857d92cf7ec1e340c7ef6910433f089d9aca9ff1ebf9b7fa6c5"),
    ("vits-piper-fa_IR-gyro-medium", f"{HF}/vits-piper-fa_IR-gyro-medium/resolve/main/fa_IR-gyro-medium.onnx",
     "79646f0a1942d8be05562f4103da6470bcf69337e65bd8a1ec95afe1c6a89e6b"),
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def download(url: str, target: Path, expected_sha256: str | None = None) -> Path:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and (expected_sha256 is None or sha256(target) == expected_sha256):
        return target
    print(f"indiriliyor: {url}", flush=True)
    partial = target.with_suffix(target.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "Bideng-build"})
    with urllib.request.urlopen(request, timeout=120) as response, open(partial, "wb") as output:
        shutil.copyfileobj(response, output, 1 << 20)
    if expected_sha256 is not None and sha256(partial) != expected_sha256:
        partial.unlink()
        raise SystemExit(f"SHA-256 uyuşmuyor: {url}")
    partial.replace(target)
    return target


def prepare_voices() -> None:
    tts = OUT / "common" / "tts"
    if tts.exists():
        shutil.rmtree(tts)
    tts.mkdir(parents=True)
    shutil.copytree(ANDROID_TTS / "espeak-ng-data", tts / "espeak-ng-data")
    # Fahrettin APK ile birebir aynı (depodaki model)
    fahrettin = tts / "tr_TR_fahrettin_medium"
    fahrettin.mkdir()
    shutil.copy2(ANDROID_TTS / "tr_TR_fahrettin_medium" / "model.onnx", fahrettin / "model.onnx")
    shutil.copy2(ANDROID_TTS / "tr_TR_fahrettin_medium" / "tokens.txt", fahrettin / "tokens.txt")
    for directory, url, expected in DOWNLOADED_VOICES:
        model = download(url, CACHE / f"{directory}.onnx", expected)
        voice_dir = tts / directory
        voice_dir.mkdir()
        shutil.copy2(model, voice_dir / "model.onnx")
        shutil.copy2(ANDROID_TTS / directory / "tokens.txt", voice_dir / "tokens.txt")


def prepare_sherpa(target_os: str) -> Path:
    archive_name = SHERPA_ARCHIVES[target_os]
    url = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v{SHERPA_VERSION}/{archive_name}"
    archive = download(url, CACHE / archive_name)
    destination = OUT / target_os / "sherpa"
    if destination.exists():
        shutil.rmtree(destination)
    (destination / "bin").mkdir(parents=True)
    (destination / "lib").mkdir(parents=True)
    with tempfile.TemporaryDirectory() as temp:
        with tarfile.open(archive, "r:bz2") as tar:
            tar.extractall(temp, filter="data")
        root = next(Path(temp).iterdir())
        exe_name = "sherpa-onnx-offline-tts.exe" if target_os == "windows" else "sherpa-onnx-offline-tts"
        shutil.copy2(root / "bin" / exe_name, destination / "bin" / exe_name)
        for folder in ("lib", "bin"):
            source = root / folder
            if not source.is_dir():
                continue
            for item in source.iterdir():
                if item.suffix.lower() == ".dll":
                    # Windows'ta DLL'ler exe'nin yanında olmalı
                    shutil.copy2(item, destination / "bin" / item.name)
                elif folder == "lib" and (".so" in item.name or item.suffix == ".dylib"):
                    shutil.copy2(item, destination / "lib" / item.name, follow_symlinks=True)
        exe = destination / "bin" / exe_name
        exe.chmod(exe.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    return destination / "bin" / exe_name


def smoke_test(exe: Path, target_os: str) -> None:
    tts = OUT / "common" / "tts"
    env = dict(os.environ)
    lib = exe.parent.parent / "lib"
    if target_os == "linux":
        env["LD_LIBRARY_PATH"] = str(lib)
    elif target_os == "macos":
        env["DYLD_LIBRARY_PATH"] = str(lib)
    samples = {
        "tr_TR_fahrettin_medium": "Merhaba, bu bir deneme cümlesidir.",
        "vits-piper-ar_JO-kareem-low": "مرحبا، هذه جملة تجريبية.",
        "vits-piper-fa_IR-amir-medium": "سلام، این یک جمله آزمایشی است.",
    }
    for voice, text in samples.items():
        wav = OUT / f"smoke-{voice}.wav"
        result = subprocess.run([
            str(exe),
            f"--vits-model={tts / voice / 'model.onnx'}",
            f"--vits-tokens={tts / voice / 'tokens.txt'}",
            f"--vits-data-dir={tts / 'espeak-ng-data'}",
            f"--output-filename={wav}",
            text,
        ], cwd=exe.parent, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if result.returncode != 0 or not wav.exists() or wav.stat().st_size < 10_000:
            print(result.stdout[-2000:], result.stderr[-2000:])
            raise SystemExit(f"Ses denemesi başarısız: {voice}")
        print(f"ses denemesi tamam: {voice} ({wav.stat().st_size} bayt)")
        wav.unlink()


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in SHERPA_ARCHIVES:
        raise SystemExit(__doc__)
    target_os = sys.argv[1]
    if "--smoke-only" in sys.argv:
        # Hazırlanmış (ve macOS'ta imzalanmış) paket üzerinde yalnızca ses denemesi
        exe_name = "sherpa-onnx-offline-tts.exe" if target_os == "windows" else "sherpa-onnx-offline-tts"
        smoke_test(OUT / target_os / "sherpa" / "bin" / exe_name, target_os)
        return
    for other in SHERPA_ARCHIVES:
        if other != target_os and (OUT / other).exists():
            shutil.rmtree(OUT / other)
    prepare_voices()
    exe = prepare_sherpa(target_os)
    if "--smoke-test" in sys.argv:
        smoke_test(exe, target_os)
    total = sum(f.stat().st_size for f in OUT.rglob("*") if f.is_file())
    print(f"hazır: {OUT} ({total / 1_048_576:.0f} MB)")


if __name__ == "__main__":
    main()
