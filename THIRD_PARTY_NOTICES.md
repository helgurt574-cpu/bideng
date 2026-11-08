# Üçüncü taraf bileşenler / Third-party components

Bideng aşağıdaki açık kaynak bileşenleri ve verileri kullanır. Her bileşen kendi lisansına tabidir.
Bideng uses the following open-source components and data, each under its own license.

## Ses motoru / Speech engine

| Bileşen | Lisans | Kaynak |
|---|---|---|
| sherpa-onnx | Apache-2.0 | https://github.com/k2-fsa/sherpa-onnx |
| ONNX Runtime | MIT | https://github.com/microsoft/onnxruntime |
| eSpeak NG (fonem verisi ve kitaplığı, sherpa-onnx içinde) | GPL-3.0-or-later | https://github.com/espeak-ng/espeak-ng |
| piper-phonemize | MIT | https://github.com/rhasspy/piper-phonemize |

## Sesler / Voices (Piper)

Ses modelleri Piper projesinden gelir; her sesin kullanım koşulları kendi model kartında ve eğitim verisinin
kaynağında belirtilir. Voice models come from the Piper project; see each model card for its terms.

| Ses | Dil | Model kartı |
|---|---|---|
| Fahrettin (medium) | Türkçe | https://huggingface.co/rhasspy/piper-voices/tree/main/tr/tr_TR/fahrettin/medium |
| DFKI (medium) | Türkçe | https://huggingface.co/rhasspy/piper-voices/tree/main/tr/tr_TR/dfki/medium |
| Kareem (low / medium) | Arapça | https://huggingface.co/rhasspy/piper-voices/tree/main/ar/ar_JO/kareem |
| Amir (medium) | Farsça | https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR/amir/medium |
| Gyro (medium) | Farsça | https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR/gyro/medium |

Sherpa-onnx biçimine dönüştürülmüş kopyalar: https://huggingface.co/csukuangfj

## Belge okuma ve karakter tanıma / Document parsing and OCR

| Bileşen | Lisans | Kaynak |
|---|---|---|
| Apache PDFBox (masaüstü) | Apache-2.0 | https://pdfbox.apache.org |
| PdfBox-Android | Apache-2.0 | https://github.com/TomRoush/PdfBox-Android |
| Apache POI, XMLBeans, Commons Collections | Apache-2.0 | https://poi.apache.org |
| jsoup | MIT | https://jsoup.org |
| tess-two (Tesseract OCR, Leptonica) | Apache-2.0 / BSD-2-Clause | https://github.com/rmtheis/tess-two |
| Tesseract eğitim verisi (tur, eng) | Apache-2.0 | https://github.com/tesseract-ocr/tessdata |

## Uygulama çatısı / App frameworks

| Bileşen | Lisans | Kaynak |
|---|---|---|
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | https://kotlinlang.org |
| Compose Multiplatform, Skiko | Apache-2.0 | https://github.com/JetBrains/compose-multiplatform |
| AndroidX Room | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| SQLite JDBC | Apache-2.0 | https://github.com/xerial/sqlite-jdbc |
| SLF4J | MIT | https://www.slf4j.org |
| Eclipse Temurin (masaüstü paketlerindeki Java çalışma ortamı) | GPL-2.0 with Classpath Exception | https://adoptium.net |
