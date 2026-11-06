# Bideng

Çevrimdışı sesli belge okuyucu — Android, Windows, macOS (Apple Silicon) ve Linux.

- PDF (taranmış dahil), EPUB, DOCX, DOC, ODT, RTF, HTML, TXT
- Türkçe, Arapça ve Farsça sesler; internet gerekmez
- Arayüz: Türkçe, Kurdî, العربية

**İndirme:** [Releases](../../releases) · **Kılavuz:** [kilavuz/Bideng-Kullanim-Kilavuzu.pdf](kilavuz/Bideng-Kullanim-Kilavuzu.pdf)

## Derleme

```bash
# Android (online + offline APK)
./gradlew assembleOnlineRelease assembleOfflineRelease

# Masaüstü (bulunduğunuz sistem için)
python3 filiz-desktop/tools/prepare_app_resources.py <windows|macos|linux>
cd filiz-desktop && ./gradlew packageDistributionForCurrentOS
```

Sürüm yayımlamak için `vX.Y.Z` etiketi gönderin; GitHub Actions tüm masaüstü paketlerini üretip Release'e ekler.
