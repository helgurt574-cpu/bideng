| Sistem | Dosya |
|---|---|
| Windows | `Bideng-1.2.0-windows-x64-setup.exe` |
| macOS (M1–M4) | `Bideng-1.2.0-macos-apple-silicon.dmg` |
| macOS (Intel, macOS 13 Ventura ve sonrası) | `Bideng-1.2.0-macos-intel.dmg` |
| Ubuntu / Debian / Mint | `Bideng-1.2.0-linux-amd64.deb` |
| Diğer Linux | `Bideng-1.2.0-linux-x86_64.AppImage` |
| Android | `Bideng-1.1-offline.apk` |

Yeni: Masaüstü sürümü artık **taranmış (yalnızca resimden oluşan) PDF'leri** de okuyor. Yazı tanıma (OCR) cihazda, internetsiz yapılır; Türkçe, İngilizce, Arapça ve Farsça tanınır. İlk açılışta sayfa başına birkaç saniye sürer, sonuç saklanır ve belge sonraki açılışlarda hemen açılır.

**macOS'ta ilk açılış:** Paket imzasız olduğu için macOS ilk açılışta uygulamayı engelleyebilir. `.dmg` dosyasını açın, Bideng'i Uygulamalar klasörüne sürükleyin, sonra Uygulamalar'da Bideng'e sağ tıklayıp **Aç**'ı seçin ve çıkan pencerede yine **Aç**'a basın. Olmazsa Terminal'de şunu çalıştırın:

```
xattr -dr com.apple.quarantine /Applications/Bideng.app
```

Kurulum: `Bideng-Kullanim-Kilavuzu.pdf`
