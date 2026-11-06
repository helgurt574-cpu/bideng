# Filiz Windows Desktop - Hızlı Başlangıç

## ⚡ Hızlı Başlangıç

### Windows'ta Çalıştırma

1. **Önce Java 17+ kurulu olmalı**
   - `java -version` komutuyla kontrol edin
   - Yüklü değilse: https://www.java.com/tr/download/

2. **Uygulamayı başlatın**
   - Çift tıklayarak `run.bat` dosyasını çalıştırın
   - veya komut satırında: `gradlew.bat run`

3. **EXE/MSI paketi oluşturmak için**
   - Komut satırında: `gradlew.bat createDistributable`
   - Oluşan dosya: `build/compose/binaries/main/exe/Filiz.exe`

## 📁 Dosya Konumu

Uygulama verileri şu klasörde saklanır:
```
C:\Users\KullaniciAdiniz\.filiz\
├── library.db        # Kitaplık ve notlar
└── settings.json     # Ayarlar
```

## 🎯 Kullanım

1. **Doküman Ekle**
   - Kitaplık ekranında "+ Doküman Ekle" kartına tıklayın
   - Dosya seçici açılacak
   - Desteklenen formatlar: PDF, DOCX, EPUB, TXT, ODT, RTF, HTML

2. **Okuma**
   - Kitaplıkta dokümana tıklayın
   - Metin otomatik yüklenir
   - Kaydırarak okuyun veya sesli okumayı başlatın

3. **Sesli Okuma (TTS)**
   - Okuma ekranında play butonuna basın
   - Mevcut sayfa okunur
   - Hız ayarını settings ekranından değiştirebilirsiniz

4. **Ayarlar**
   - ⚙️ ikonuna tıklayın
   - Font, boyut, satır aralığı, tema vb.

5. **Notlar**
   - 📝 ikonuna tıklayın
   - Metni seçin veya not panelinden ekle
   - Renk kodlu vurgulama

## 🔧 Sorun Giderme

### "Java bulunamadı" hatası
- Java 17+ kurun ve JAVA_HOME ortam değişkenini ayarlayın

### TTS ses çalmıyor
- Windows'ta Speech API kontrol edin
- Ayarlar > Zaman ve Dil > Konuşma > Sesler

### Türkçe karakterler görüntülenmiyor
- Dosyanın kodlamasını kontrol edin (UTF-8, Windows-1254, ISO-8859-9)

## 💡 İpuçları

- **Klavye kısayolları** gelecekte eklenecek
- **Otomatik kaydetme** her işlemde gerçekleşir
- **İlerleme** otomatik kaydedilir, kaldığınız yerden devam edebilirsiniz
- **Auto-scroll** ayarı: ⏬ butonuna tıklayarak açın/kapatın

## 📝 Desteklenen Dosya Formatları

| Format | Açıklama |
|--------|----------|
| PDF | PDF dosyaları (text extraction) |
| DOCX | Modern Word dosyaları |
| DOC | Eski Word dosyaları |
| EPUB | E-kitap formatı |
| ODT | OpenDocument Text |
| RTF | Rich Text Format |
| HTML/HTM | Web sayfaları (tag stripping) |
| TXT | Düz metin (UTF-8, Windows-1254, ISO-8859-9) |

## 🔒 Offline Güvencesi

✅ **Tamamen offline** - İnternet bağlantısı gerektirmez
✅ **Windows Speech API** - Sistem seslerini kullanır
✅ **Yerel veritabanı** - SQLite ile yerel saklama
✅ **Gömülü kütüphaneler** - Her şey uygulamanın içinde

---
Filiz - Offline E-Reader with TTS
© 2024
