# Filiz Windows Desktop Uygulaması - Plan

## Teknoloji Stack
- **Framework**: Compose Multiplatform (Desktop)
- **Dil**: Kotlin
- **Build Tool**: Gradle (Kotlin DSL)
- **UI**: Compose Desktop (Material 3)
- **TTS**: Java Speech API (javax.speech) + Windows SAPI
- **PDF**: Apache PDFBox (kotlin-pdfbox-android'ın masaüstü versiyonu)
- **Docx/ODT/RTF/EPUB**: POI, EPUBLib vb.
- **OCR**: Tesseract (isteğe bağlı, .traineddata dosyalarını embed)
- **Veri**: SQLite (kotlinx.coroutines + sqljet)
- **Local Data**: kotlinx.serialization + DataStore/SharedPreferences benzeri

## Proje Yapısı
```
filiz-desktop/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/
│   ├── desktopMain/kotlin/
│   │   └── com/example/filiz/desktop/
│   │       ├── Main.kt (entry point)
│   │       ├── App.kt (main app composable)
│   │       ├── theme/ (renkler, tema)
│   │       ├── ui/ (screen composable'lar)
│   │       ├── model/ (data class'lar)
│   │       ├── viewModel/ (ViewModel'lar)
│   │       ├── repository/ (data layer)
│   │       ├── service/ (TTS, PDF, OCR servisleri)
│   │       └── utils/ (yardımcı fonksiyonlar)
│   └── desktopMain/resources/
│       ├── fonts/ (embed fontlar)
│       ├── icons/ (ikonlar)
│       └── tesseract/ (OCR trained data - isteğe bağlı)
```

## Temel Bileşenler

### 1. Data Layer
- `LibraryItem` - Kitaplık öğesi (ad, yol, ilerleme, son okunma)
- `Note` - Notlar (konum, metin, renk, tarih)
- `AppSettings` - Ayarlar (font, tema, dil, hız vb.)
- `BookDatabase` - SQLite ile veritabanı

### 2. Services
- `DocumentReaderService` - PDF, DOCX, EPUB, TXT okuma
- `TTSService` - Windows SAPI ile sesli okuma
- `OCRService` - Tesseract ile OCR (isteğe bağlı)
- `FileService` - Dosya işlemleri, import/export
- `PreferenceService` - Ayarların saklanması

### 3. UI Screens (Compose)
- `LibraryScreen` - Kitaplık ekranı
- `ReaderScreen` - Okuma ekranı
- `SettingsScreen` - Ayarlar ekranı
- `NotesDialog` - Notlar penceresi
- `SearchDialog` - Arama penceresi

### 4. Features (Android'den kopya)
✅ Dosya formatları: PDF, DOCX, DOC, EPUB, ODT, RTF, HTML, TXT
✅ TTS: Ses seçimi, hız kontrolü (0.5x-2.0x)
✅ Oynatma kontrolleri: Play/Pause, İleri/Geri atlama
✅ Font seçenekleri: Aile, boyut, satır aralığı
✅ Tema: Koyu/Açık mod, renk paletleri
✅ Arama: İçinde arama, sonraki/önceki, vurgulama
✅ Notlar: Metin seçimi, not ekleme, silme, export
✅ İlerleme takibi: Sayfa, yüzde, son pozisyon
✅ Auto-scroll: Otomatik kaydırma, hız ayarı
✅ Çoklu dil: Türkçe, Kürtçe, Arapça UI dil seçimi
✅ Kodlama: UTF-8, Windows-1254, ISO-8859-9

## Offline Olarak Çalışması İçin
- Tüm kütüphaneler compile time'a gömülecek
- OCR trained data dosyaları resources içine gömülecek
- Windows SAPI sistem sesini kullanacak
- İnternet kontrolü YOK

## Uygulama Akışı
1. Başlangıç → Ayarları yükle → Kitaplığı aç
2. Kitaplıkta dosya seç → Dokümanı oku → Reader'a geç
3. Reader'da → Okuma/TTS/Not alma/Arama
4. Otomatik kaydetme her işlemde
5. Çıkışta tüm state kaydedilir

## Bağımlılıklar (build.gradle.kts)
```kotlin
// Compose Multiplatform
implementation(compose.desktop.currentOs)
implementation(compose.material3)
implementation(compose.materialIconsExtended)

// PDF (Android ile aynı kütüphane)
implementation("org.apache.pdfbox:pdfbox:3.0.1")

// TTS - Windows SAPI (system-level, fully offline)
// SAPI direkt kullanılır, ek kütüphane gerekmez

// SQLite
implementation("org.xerial:sqlite-jdbc:3.44.1")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

// DOCX
implementation("org.apache.poi:poi-ooxml:5.2.5")

// EPUB
implementation("nl.siegmann.epublib:epublib-core:3.1")

// ODT
implementation("org.odftoolkit:simple-odf:0.9.0")
```

## TTS Yaklaşımı (Windows)
- **Windows Speech API (SAPI)** - Windows'un yerleşik ses motoru
- **Tamamen offline** - İnternet gerekmez
- **Sistem seslerini kullanır** - Windows'un mevcut TTS sesleri
- **Aynı mantık Android ile** - Sistem TTS motorunu kullanır
- **Platform spesifik** - Windows Native API (System.Speech veya SAPI)

## Öncelik Sırası (Implementation Order)
1. ✅ Proje yapısını oluştur
2. ✅ Temel Compose UI iskeleti (Main, App, LibraryScreen)
3. ✅ Data model'ler ve database
4. ✅ DocumentReaderService (TXT, PDF, DOCX, EPUB, ODT, RTF, HTML)
5. ✅ ReaderScreen ve temel okuma
6. ✅ TTSService ile sesli okuma (Windows SAPI)
7. ✅ Settings ve tema sistemi
8. ⏳ Arama özelliği (UI'de hazır, tam implementasyon gerekli)
9. ⏳ Notlar sistemi (UI'de hazır, tam implementasyon gerekli)
10. ✅ Dosya formatları (DOCX, EPUB, ODT, RTF)
11. ✅ Auto-scroll
12. ⏸️ OCR (isteğe bağlı - daha sonra eklenebilir)
13. ⏳ Polishing ve bug fixes

## Durum: Temel Uygulama Hazır! ✅

Tamamlanan Özellikler:
- ✅ Proje yapısı (Compose Multiplatform)
- ✅ Kitaplık ekranı (LibraryScreen)
- ✅ Okuma ekranı (ReaderScreen)
- ✅ Dosya okuma servisi (PDF, DOCX, EPUB, ODT, RTF, HTML, TXT)
- ✅ SQLite veritabanı (LibraryService)
- ✅ Ayarlar servisi (JSON ile saklama)
- ✅ TTS servisi (Windows SAPI ile offline)
- ✅ Tema sistemi (Koyu/Açık mod)
- ✅ Auto-scroll
- ✅ İlerleme takibi
- ✅ Material Design 3 UI
- ✅ Gradle build yapılandırması
- ✅ Windows batch script'leri (run.bat, create-exe.bat)

Devam Edilecek:
- ⏳ Arama fonksiyonunun tam implementasyonu
- ⏳ Not sisteminin veritabanı entegrasyonu
- ⏳ Settings ekranının tamamlanması
- ⏳ UI'da daha fazla seçenekler
- ⏸️ OCR (isteğe bağlı)

## Windows'ta Çalıştırma

**Derleme ve Çalıştırma:**
```cmd
cd filiz-desktop
.\run.bat
```

**EXE Paketi Oluşturma:**
```cmd
.\create-exe.bat
```

**Komut satırı ile:**
```cmd
.\gradlew.bat run
.\gradlew.bat clean build
```
