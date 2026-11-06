# Filiz - Windows Desktop E-Reader

Windows için tamamen offline çalışan, sesli okuma özellikli e-kitap okuyucu.

## Özellikler

### 📚 Dosya Formatları
- PDF
- DOC
- DOCX
- EPUB
- HTML/HTM
- TXT (UTF-8, Windows-1254, ISO-8859-9)

### 🔊 Sesli Okuma (TTS)
- Windows Speech API kullanarak tamamen offline
- Çoklu dil desteği
- Okuma hızı ayarı (0.5x - 2.0x)
- Play/Pause kontrolü
- İleri/Geri atlama

### 📖 Okuma Özellikleri
- Birden fazla font ailesi
- Font boyutu ayarı
- Satır aralığı ayarı
- Otomatik kaydırma (auto-scroll)
- Sayfa sayacı ve yüzde göstergesi
- Son okunma pozisyonu otomatik kayıt

### 🎨 Tema
- Koyu/Açık mod
- Dinamik renk paletleri
- Material Design 3

### 🔍 Arama
- Metin içinde arama
- Sonraki/önceki navigasyon

### 📝 Notlar
- Metin seçimi ile not alma
- Renk kodlu vurgulama
- Not yönetimi

### 💾 Veri
- SQLite veritabanı
- Otomatik kayıt
- İlerleme takibi

## Gereksinimler

- **Java 17 veya üzeri**
- **Windows 10 veya üzeri** (Speech API için)

## Kurulum ve Çalıştırma

### Derleme (Windows'ta)
```bash
cd filiz-desktop
.\gradlew.bat clean build
```

### Çalıştırma
```bash
.\gradlew.bat run
```

### EXE/MSI Paketi Oluşturma
```bash
.\gradlew.bat createDistributable
```
Oluşturulan dosyalar `build/compose/binaries/main/` klasöründe bulunur. EXE adı `Filiz.exe` olur.

## Proje Yapısı

```
filiz-desktop/
├── src/desktopMain/kotlin/com/example/filiz/desktop/
│   ├── Main.kt                      # Uygulama giriş noktası
│   ├── App.kt                       # Ana uygulama composable
│   ├── theme/                       # Renkler ve temalar
│   ├── ui/                          # UI bileşenleri
│   │   └── screens/                 # Ekranlar (Library, Reader)
│   ├── model/                       # Veri modelleri
│   ├── service/                     # Servis katmanı
│   │   ├── TTSService.kt           # Windows Speech API
│   │   ├── DocumentReaderService.kt # Dosya okuma
│   │   ├── DatabaseService.kt      # SQLite işlemleri
│   │   └── PreferenceService.kt     # Ayarlar
│   └── utils/                       # Yardımcı fonksiyonlar
```

## Offline Çalışma

Uygulama tamamen offline çalışacak şekilde tasarlanmıştır:

- **TTS**: Windows Speech API (sistem seviyesinde, internet gerekmez)
- **Dosya okuma**: PDFBox, POI, EPUBLib (gömülü kütüphaneler)
- **Veri depolama**: SQLite (yerel dosya tabanlı)
- **Ayarlar**: JSON dosyası (.filiz klasörü)

## Kullanım

1. Uygulamayı açın
2. "Doküman Ekle" butonuna tıklayın
3. Desteklenen dosya formatlarından birini seçin
4. Doküman otomatik olarak kitaplığa eklenir
5. Kitaplıkta dokümana tıklayarak okumaya başlayın

## Lisans

© 2024 Filiz - Tüm hakları saklıdır.
