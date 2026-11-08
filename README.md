# Bideng

Çevrimdışı sesli belge okuyucu. PDF, EPUB, Word ve metin belgelerini cihazda, internetsiz seslendirir (Türkçe, Arapça, Farsça).
Android, Windows, Linux ve macOS.

*Offline document reader with text-to-speech for Android, Windows, Linux and macOS (Turkish, Arabic, Persian).*

## İndir

Kurulum dosyaları: [Releases](../../releases) · Kılavuz: [`docs/kilavuz/Bideng-Kullanim-Kilavuzu.pdf`](docs/kilavuz/Bideng-Kullanim-Kilavuzu.pdf)

## Derleme

JDK 17 gerekir.

```bash
./gradlew assembleOfflineRelease                                  # Android
python3 filiz-desktop/tools/prepare_app_resources.py <windows|macos|linux>
cd filiz-desktop && ./gradlew packageDistributionForCurrentOS      # Windows, Linux, macOS
```

## Lisans

[GPL-3.0-or-later](LICENSE) · Üçüncü taraf bileşenler: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
