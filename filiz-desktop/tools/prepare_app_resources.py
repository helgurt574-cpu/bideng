#!/usr/bin/env python3
"""Masaüstü kurulum paketine gömülecek dosyaları hazırlar (tamamen çevrimdışı çalışma için).

build/app-resources/common/tts/      -> ortak espeak-ng verisi + 5 ses (model.onnx + tokens.txt)
build/app-resources/common/tessdata/ -> taranmış PDF için OCR dil verileri (Türkçe, İngilizce, Arapça, Farsça)
build/app-resources/<os>/sherpa-native/ -> o işletim sistemine ait sherpa-onnx JNI + onnxruntime kütüphaneleri

Kullanım: python3 tools/prepare_app_resources.py <windows|macos|macos-intel|linux> [--smoke-test | --smoke-only]
macos = Apple Silicon (arm64), macos-intel = Intel Mac (x86_64); ikisi de build/app-resources/macos/ altına yazılır.
Yalnızca derleme sırasında internet gerekir; kurulan uygulama hiçbir şey indirmez.
"""
import hashlib
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

DESKTOP = Path(__file__).resolve().parent.parent
REPO = DESKTOP.parent
ANDROID_TTS = REPO / "app" / "src" / "main" / "assets" / "tts"
OUT = DESKTOP / "build" / "app-resources"
CACHE = DESKTOP / "build" / "download-cache"

# 2.x sürümleri espeak-ng desteğini kaldırdı (--vits-data-dir yok sayılıyor); piper sesleri için 1.x gerekli
SHERPA_VERSION = "1.13.8"
# Uygulama sherpa-onnx'i Java API'siyle (JNI) kullanır; her sistem için yerel kütüphane paketi
SHERPA_NATIVE_JARS = {
    "windows": f"sherpa-onnx-native-lib-win-x64-{SHERPA_VERSION}.jar",
    "macos": f"sherpa-onnx-native-lib-osx-aarch64-{SHERPA_VERSION}.jar",
    "macos-intel": f"sherpa-onnx-native-lib-osx-x64-{SHERPA_VERSION}.jar",
    "linux": f"sherpa-onnx-native-lib-linux-x64-{SHERPA_VERSION}.jar",
}
# Compose Desktop yalnızca işletim sistemi adlı klasörü (windows/macos/linux) okur
RESOURCE_DIRS = {"windows": "windows", "macos": "macos", "macos-intel": "macos", "linux": "linux"}
SHERPA_JVM_JAR = DESKTOP / "libs" / f"sherpa-onnx-jvm-{SHERPA_VERSION}.jar"

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

# Tesseract LSTM modelleri (tessdata_best 4.1.0); OCR yerel kütüphaneleri Gradle'dan gelir (org.bytedeco:tesseract)
TESSDATA = "https://github.com/tesseract-ocr/tessdata_best/raw/4.1.0"
OCR_LANGUAGES = [
    ("tur", "e0c3338dc17503dc7d335a507c9ae01b2b46cfd07561171e1e1ac55d85e8e438"),
    ("eng", "8280aed0782fe27257a68ea10fe7ef324ca0f8d85bd2fd145d1c2b560bcb66ba"),
    ("ara", "ab9d157d8e38ca00e7e39c7d5363a5239e053f5b0dbdb3167dde9d8124335896"),
    ("fas", "99e420969b5ddd2cb135b416316a7ed417c59c4faf9e0d28941348f6448114df"),
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


def prepare_tessdata() -> None:
    tessdata = OUT / "common" / "tessdata"
    if tessdata.exists():
        shutil.rmtree(tessdata)
    tessdata.mkdir(parents=True)
    for language, expected in OCR_LANGUAGES:
        name = f"{language}.traineddata"
        shutil.copy2(download(f"{TESSDATA}/{name}", CACHE / "tessdata_best" / name, expected), tessdata / name)


def prepare_native(target_os: str) -> Path:
    jar_name = SHERPA_NATIVE_JARS[target_os]
    url = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/v{SHERPA_VERSION}/{jar_name}"
    jar = download(url, CACHE / jar_name)
    destination = OUT / RESOURCE_DIRS[target_os] / "sherpa-native"
    if destination.parent.exists():
        shutil.rmtree(destination.parent)
    destination.mkdir(parents=True)
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            if entry.startswith("sherpa-onnx/native/") and not entry.endswith("/"):
                (destination / Path(entry).name).write_bytes(archive.read(entry))
    names = sorted(p.name for p in destination.iterdir())
    if not any("sherpa-onnx-jni" in n for n in names) or not any("onnxruntime" in n for n in names):
        raise SystemExit(f"Yerel kütüphaneler eksik: {names}")
    return destination


def smoke_test(native_dir: Path) -> None:
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / "java") if java_home else "java"
    result = subprocess.run([
        java,
        "-cp", str(SHERPA_JVM_JAR),
        f"-Dsherpa_onnx.native.path={native_dir}",
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        str(DESKTOP / "tools" / "TtsSmokeTest.java"),
        str(OUT / "common" / "tts"),
    ], capture_output=True, text=True, encoding="utf-8", errors="replace")
    print(result.stdout)
    if result.returncode != 0:
        print(result.stderr[-3000:])
        raise SystemExit("Ses denemesi başarısız")


def main() -> None:
    # Windows konsolu (cp1252) Türkçe karakterleri yazamıyor
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    if len(sys.argv) < 2 or sys.argv[1] not in SHERPA_NATIVE_JARS:
        raise SystemExit(__doc__)
    target_os = sys.argv[1]
    if "--smoke-only" in sys.argv:
        # Hazırlanmış (ve macOS'ta imzalanmış) paket üzerinde yalnızca ses denemesi
        smoke_test(OUT / RESOURCE_DIRS[target_os] / "sherpa-native")
        return
    for other in set(RESOURCE_DIRS.values()) - {RESOURCE_DIRS[target_os]}:
        if (OUT / other).exists():
            shutil.rmtree(OUT / other)
    prepare_voices()
    prepare_tessdata()
    native_dir = prepare_native(target_os)
    if "--smoke-test" in sys.argv:
        smoke_test(native_dir)
    total = sum(f.stat().st_size for f in OUT.rglob("*") if f.is_file())
    print(f"hazır: {OUT} ({total / 1_048_576:.0f} MB)")


if __name__ == "__main__":
    main()
