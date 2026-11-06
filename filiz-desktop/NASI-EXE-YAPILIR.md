# NASIL EXE YAPILIR?

## Windows'a Taşımanız Gereken Minimum Dosyalar

**Sadece bu klasörü kopyalayın:**
```
filiz-desktop/
├── src/                    # Kaynak kodlar (gerekli)
├── gradle/                 # Gradle (gerekli)
├── gradlew.bat            # Gradle Windows scripti (gerekli)
├── build.gradle.kts       # Build konfigürasyonu (gerekli)
├── settings.gradle.kts    # Settings (gerekli)
├── gradle.properties      # Gradle özellikleri (gerekli)
└── bir-defada-exe.bat     # Tek seferde EXE scripti
```

## Windows'ta Yapılacaklar

### 1. Java Kur (Henüz yüklü değilse)
- https://www.java.com/tr/download/
- Java 17 veya üzeri

### 2. Şu komutları çalıştırın
```cmd
cd filiz-desktop
.\bir-defada-exe.bat
```

### 3. Sonuç
`build\compose\binaries\main\exe\Filiz.exe` dosyası oluşacak.

Artık sadece `Filiz.exe`'yi kopyalayip kullanabilirsiniz!

---

## NEDEN TÜM DOSYALAR GEREKLI?

- `src/` - Kodlar olmadan derleyemez
- `gradle/` - Gradle olmadan build alamaz
- `*.gradle.kts` - Build konfigürasyonları
- `gradlew.bat` - Gradle çalıştırma scripti

EXE oluşturduktan sonra, sadece EXE'yi taşıman yeterli!
