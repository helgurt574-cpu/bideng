## Bideng — çevrimdışı sesli belge okuyucu

Tüm sürümler **tamamen çevrimdışı** çalışır: sesler (Fahrettin, DFKI — Türkçe; Kareem — Arapça; Amir, Gyro — Farsça) ve ses motoru kurulum dosyasının içindedir. Kurulumdan sonra internet gerekmez.

### Hangi dosyayı indirmeliyim?

| Cihaz | Dosya |
|---|---|
| Android (internetsiz kurulum) | `Bideng-1.0-offline.apk` — tüm sesler içinde |
| Android (küçük boyut) | `Bideng-1.0-online.apk` — ek sesler uygulamadan indirilir |
| Windows 10/11 | `…-windows-x64-setup.exe` (önerilen) veya `…-windows-x64.msi` |
| Mac (M1/M2/M3/M4) | `…-macos-apple-silicon.dmg` |
| Linux (Ubuntu/Debian/Mint) | `…-linux-amd64.deb` |
| Linux (tüm dağıtımlar, kurulumsuz) | `…-linux-x86_64.AppImage` |

### Kurulum

**Windows:** `setup.exe` dosyasını çalıştırın. "Windows bilgisayarınızı korudu" uyarısı çıkarsa **Ek bilgi → Yine de çalıştır** deyin (uygulama henüz ticari bir sertifikayla imzalı değil).

**Mac:** `.dmg` dosyasını açıp Bideng'i **Uygulamalar** klasörüne sürükleyin. İlk açılışta "geliştirici doğrulanamadı" uyarısı çıkarsa Bideng'e **sağ tıklayıp Aç** deyin. Yine açılmazsa Terminal'de bir kez şunu çalıştırın:

```
xattr -cr /Applications/Bideng.app
```

**Linux:** Terminalde: `sudo apt install ./Bideng-*-linux-amd64.deb` — ardından menüden Bideng'i açın.

**Linux AppImage:** `chmod +x Bideng-*.AppImage` ile çalıştırılabilir yapıp açın. Uygulama menüsüne eklemek için (Gear Lever ya da elle) kılavuzun 2.5 bölümüne bakın.

**Android:** APK dosyasını telefonda açıp kurun (gerekirse "bilinmeyen kaynaklara izin ver"). Daha önce farklı imzalı eski bir Bideng kuruluysa önce onu kaldırın.

### Kullanım kılavuzu

Ayrıntılı kurulum ve kullanım için `Bideng-Kullanim-Kilavuzu.pdf` dosyasını indirin.
