# Filiz Desktop (Bideng) — İnceleme Raporu

**Hedef:** Kullanım kolaylığı + modern masaüstü görünümü
**Kapsam:** `filiz-desktop/` modülü (Compose Multiplatform Desktop, Kotlin, Material 3)
**Not:** Bu rapor sadece tespit içerir; kodda hiçbir değişiklik yapılmadı.

---

## 1. Genel İzlenim

Mimari iskelet sağlam: Material 3, koyu/açık tema, üç dil (TR/KU/AR), sidebar + kütüphane paneli + sekmeler + okuyucu deseni, sürükle-bırak, klavye kısayolları, SQLite kalıcılık, sherpa-onnx tabanlı offline TTS — bunların hepsi profesyonel bir masaüstü uygulamasının temel taşları. Yani çatı doğru kurulmuş.

Eksik olan, çatının üzerine giren **incelik katmanı**: tutarlı marka, modern Windows 11 görsel diline uyum, gerçek e-kitap deneyimi (kapaklar, gerçek sayfalama, EPUB), keşfedilebilirlik (menüler, tooltip, kısayol ipuçları) ve sıkı bir cilalama (tek kaynak metin sistemi, RTL, animasyon).

Aşağıda bulgular **önceliklendirilmiş** olarak sunulmuştur.

---

## 2. Kritik Sorunlar (Kullanıcı Deneyimini Doğrudan Etkileyen)

### 2.1 Marka tutarsızlığı: "Filiz" mi, "Bideng" mi?
- Klasör adı: `filiz-desktop`, paket: `com.example.filiz.desktop`, README başlığı: **"Filiz - Windows Desktop E-Reader"**.
- Pencere başlığı (`Main.kt:32`): **"Bideng"**, sidebar başlığı (`App.kt:601`): **"Bideng"**, ayar dizini: `~/.bideng`, Gradle paket adı: **"Bideng"**.
- Aynı uygulama iki farklı isim kullanıyor. Kullanıcı için ciddi karışıklık, profesyonel algıyı düşürür. Tek isimde karar kılınmalı (kanıtlar Bideng'i işaret ediyor; o zaman README, paket adı ve klasör adı da hizalanmalı).

### 2.2 Eski ve yeni iki farklı UI mimarisi yan yana yaşıyor
- `ui/screens/LibraryScreen.kt` (303 satır) → `Scaffold`, `TopAppBar`, `NavigationBar` ile **mobil paterni**. Renkli/arası bol, "İçe Aktar" Card'ı, alt navigation bar.
- `ui/App.kt` → `AppSidebar` + `LibraryPane` + `DocumentTabs` ile **modern masaüstü paterni**.
- Çağrı izlerine bakıldığında `LibraryScreen` aktif akışta kullanılmıyor (App.kt doğrudan kendi `LibraryPane`'ini render ediyor). Yani **ölü kod** olarak kalmış. Tutarsızlık ve bakım yükü yaratıyor; ya tamamen silinmeli ya da modern paterne göre yeniden yazılmalı.

### 2.3 Belge formatı vaadi vs. gerçeklik uyuşmazlığı
- README diyor ki: PDF, **DOCX, DOC, EPUB, ODT, RTF**, HTML, TXT.
- `DocumentReaderService.kt` gerçekte sadece: **PDF, TXT, DOCX, HTML/HTM** destekliyor. EPUB, DOC, ODT, RTF için `UnsupportedOperationException` fırlatıyor.
- Bir e-kitap okuyucusu için **EPUB desteğinin olmaması** çok büyük bir eksiklik. Kullanıcı `.epub` dosyası eklemeye çalışırsa exception fırlar — try/catch da yok, hata UI'a iyi sızdırılmıyor.

### 2.4 TTS vaadi vs. gerçeklik uyuşmazlığı
- README: "Windows Speech API kullanarak tamamen offline, internet gerekmez."
- Kod (`TTSService.kt:48-77`): Sherpa-ONNX tabanlı, **harici `sherpa-onnx-offline-tts.exe` ve VITS modelleri** gerektiriyor (`appHome/sherpa/bin/`). Kullanıcı bu paketi indirmediyse TTS sessizce başarısız olur (status mesajı dışında geri dönüş yok).
- Hem teknik gerçeklik hem de kullanıcı beklentisi açısından yanıltıcı. Gerçek mimari belgelenmeli; modeller kurulu değilse uygulama içinde **indir/kur akışı** sunulmalı.

### 2.5 "Sayfa" kavramı sahte
- `PAGE_SIZE = 3000` karakter ile sabit bölme yapılıyor. Kullanıcıya gösterilen "Sayfa 12 / 384" gerçek bir sayfa değil — font büyüklüğü değişince bile değişmiyor.
- PDF'in gerçek sayfa numaraları, EPUB'ın bölümleri tamamen yok sayılmış. "İçindekiler" paneli (`ReaderScreen.kt:858`) "Sayfa 1, Sayfa 2 ... Sayfa 40" listeliyor — bu bir TOC değil.
- Modern bir okuyucudan beklenen: gerçek sayfalama, bölüm başlıkları, gezinilebilir içindekiler.

### 2.6 Çoklu dil sistemi yarım uygulanmış
`Strings` sınıfı (LocaleText.kt) güzel kurulmuş ama **kodun büyük bölümü hâlâ Türkçe sabit string**. Örnekler:
- `App.kt:340-341` "Kitap silinsin mi?", "kitaplıktan silinsin mi?"
- `App.kt:435-442` "Aç", "Dosya konumunu aç", "Kitaplıktan sil"
- `App.kt:520-547` "Kaldığın yerden devam et", "Son kitabı aç", "Özet", "${X} belge kitaplıkta"
- `App.kt:601` "Bideng", `App.kt:611` "Ana"
- `App.kt:617` "Koyu tema" / "Açık tema"
- `ReaderScreen.kt`: "Bilgi", "İlerleme", "Kelime", "Tahmini süre", "Yer İmleri", "Notlar", "İçindekiler", "Önceki", "Sonraki", "TXT Dışa Aktar", "Tümünü Sil", "Dark mode"/"Light mode" (bu kez İngilizce!), "Gorunum"

Sonuç: Kürtçe veya Arapça seçildiğinde arayüz yarı yarıya Türkçe kalıyor — bu profesyonel olmayan bir izlenim verir.

### 2.7 Arapça için RTL desteği yok
`UiLanguage.Arabic` seçildiğinde bile uygulama soldan-sağa düzeninde kalıyor. Sidebar solda, ileri/geri yön okları ters dönmüyor. `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` Arapça modda otomatik aktif edilmeli.

### 2.8 Yazım/Aksent hataları (Türkçe metinde)
- `ReaderScreen.kt:1000` "**Gorunum**" → "Görünüm"
- `ReaderScreen.kt:1012-1013` "Dark mode"/"Light mode" Türkçe arayüzde İngilizce kalmış; "**Tema secimi**" → "Tema seçimi"
- `ReaderScreen.kt:514` "Once metinden kelime ya da cumle sec" → "Önce metinden kelime ya da cümle seç"
- `LocaleText.kt:33` "Okuma hazır. Metne dokunarak başla." — masaüstünde "dokunarak" değil "tıklayarak" olmalı

### 2.9 Pencere durumu hatırlanmıyor
`Main.kt:28` her seferinde sabit 1280×820 ile açıyor. Kullanıcı pencereyi büyüttüğünde, taşıdığında bir sonraki açılışta sıfırlanıyor. Modern uygulamaların en temel beklentilerinden biri: son pencere boyutu/konumu/ekran kaydı.

---

## 3. Modern Masaüstü Görünümü Açısından Eksikler

### 3.1 Pencere chrome'u standart
- `undecorated = false`, yani Windows'un standart başlık çubuğu kullanılıyor.
- VS Code, Spotify, Discord, Notion, Obsidian, Edge gibi modern uygulamalar kendi başlık çubuğunu çizip menü, sekmeler, arama, kullanıcı menüsünü buraya entegre ediyor (Windows 11 üzerinde caption-button uyumlu).
- Compose Desktop ile `undecorated = true` + `WindowDraggableArea` ile bu pekala yapılabilir.

### 3.2 Windows 11 Mica/Acrylic efektleri yok
- Modern bir Windows 11 uygulaması arka planda **Mica** (statik) veya **Acrylic** (canlı bulanıklık) efekti kullanır.
- JNA / JNI ile `DwmSetWindowAttribute` çağrılarak (DWMWA_SYSTEMBACKDROP_TYPE) etkinleştirilebilir. Şu anda düz renk arka plan var.

### 3.3 Renk paleti güvenli ama karaktersiz
- `Color.kt`: Primary `#2F6FED` (genel mavi), Secondary `#16756F` (teal). Material default'una çok yakın, "marka kimliği" hissi vermiyor.
- Uygulama "Bideng" ise (kürtçe "ses, çağrı"), o ruha uygun bir aksan rengi (örn. sıcak amber, derin yeşim, mürekkep moru) marka karakterini güçlendirir.
- `AccentPurple = #2563EB` aslında mavi — değişken adı yanlış. `AccentYellow`, `AccentOrange`, `AccentGreen` tanımlanmış ama kodda hiç kullanılmamış.

### 3.4 Tipografi sistem fontuna terk edilmiş
- `Theme.kt:71+` tüm typography `FontFamily.Default`. Windows'ta Segoe UI, modern havayı vermez.
- En azından Windows 11 için **Segoe UI Variable** veya gömülü bir Inter/Geist kullanmak görsel kaliteyi belirgin şekilde artırır.
- Ayrıca tipografi ölçek tek katman: harf aralığı (`letterSpacing = 0.sp`) hep sıfır, küçük metinlerde okunabilirliği düşürüyor.

### 3.5 Animasyon ve geçiş yok
- Bölüm değiştirme (Home ↔ Library ↔ Reader), kütüphane panelinin açılıp kapanması, sekme eklenip silinmesi — hepsi anlık.
- Modern hissi vermek için `AnimatedVisibility`, `animateContentSize`, `Crossfade` ile minik geçişler bile büyük fark yaratır.
- Sidebar kütüphane paneli aç/kapa kavşağında özellikle göze batıyor (panel anında "pop" oluyor).

### 3.6 Kitaplıkta kapak/küçük resim yok
- `LibraryRow` her kitabı `Icons.Default.Menu` (hamburger ikonu — kavramsal olarak da yanlış) ile gösteriyor.
- Modern e-kitap uygulamalarının (Apple Books, Calibre, Kindle, ReadEra) ortak özelliği: **kapak görseli**. Yoksa bile prosedürel kapak (gradient + başlık baş harfi) üretilebilir.
- Liste görünümü dışında **grid (kapak duvarı)** seçeneği de modern bir e-reader'dan beklenir.

### 3.7 Boş durum ekranları sade
- `EmptyReaderPane`, `LibraryPane` boş durumu, Notes boş durumu sadece tek satır metin gösteriyor.
- İllüstrasyon, kısa rehber adımlar veya "ilk içe aktarma" çağrısı (Ctrl+O ipucuyla) ile boş durumlar deneyimin bir parçası haline getirilebilir.

### 3.8 Tooltip ve kısayol ipuçları yok
- Sağ üstteki ikon butonlar (Ara, Notlar, Ayarlar, Yer imi, Bilgi, Otomatik kaydır...) sadece `contentDescription` taşıyor — masaüstü kullanıcısı **hover** ettiğinde açıklayıcı bir tooltip görmüyor.
- Compose Desktop'ta `TooltipArea` ile bu kolayca yapılabilir.
- Kısayol ipuçları (örn. "Aç (Ctrl+O)") hiçbir yerde görünmüyor — keşfedilebilirlik düşük.

### 3.9 Menü çubuğu yok
- Hiçbir File / Edit / View / Tools / Help menüsü yok. `compose.ui.window.MenuBar` ile bunlar pekala yapılabilir.
- Bu hem keşfedilebilirlik hem de "modern masaüstü uygulaması" hissi için önemli. Şu anki Ctrl+O / F11 / Ctrl+, kısayolları sadece kodda — kullanıcı asla göremez.

### 3.10 Özel scrollbar yok
- Compose Desktop'taki `VerticalScrollbar` kullanılmıyor. Liste ve okuma alanı varsayılan davranışta.
- Modern desktop uygulamalarda ince, temaya uyumlu, hover'da büyüyen scrollbar standardı.

### 3.11 Status bar yok
- Reader'da status mesajları dock içinde 160-280dp genişlikte sıkışmış (`ReaderScreen.kt:925`).
- Home ve Library bölümlerinde herhangi bir status feedback'i yok (içe aktarma sırasında ne oluyor?).
- Pencerenin altında bir minimal **status bar** (ilerleme, mesaj, durum çubuğu) modern bir uygulama hissi verir.

### 3.12 İkonografi tutarsız
- `Icons.Default.Menu` (hamburger) hem Library sidebar item'ı hem her kitap satırı hem boş kütüphane illüstrasyonu olarak kullanılıyor — anlamsal aşırı yüklenme.
  - Kitap için: `Icons.AutoMirrored.Filled.MenuBook` veya `Icons.Outlined.Book`.
  - Library için: `Icons.AutoMirrored.Filled.LibraryBooks`.
- `Icons.Default.PlayArrow` "Reader" sidebar item'ında — okuma değil, sesli oynatma çağrışımı yapıyor. `Icons.Outlined.AutoStories` daha uygun.
- `Icons.Default.LightMode` "Tema değiştir" ve aynı zamanda LibraryScreen'de "Okuma" navigation item'ı için kullanılmış (`LibraryScreen.kt:100`). Çok kafa karıştırıcı.
- `DocumentTabs`'ta sekme kapatma `Icons.Default.Delete` (çöp kutusu) ile yapılıyor — `Icons.Default.Close` olmalı. Şu anki haliyle "sekmeyi sil" gibi ürkütücü duruyor.

---

## 4. Kullanım Kolaylığı (UX) Sorunları

### 4.1 Sidebar'daki Settings butonu sezgisel değil
- `App.kt:254-258`: Settings tıklandığında `preferencesRequest++` ve `selectedSection = Reader` — yani Home/Library'deyken ayarlara basınca uygulama Reader'a atlıyor ve bir kitap açıksa drawer açılıyor; yoksa sadece reader empty state'e geçiyor.
- Beklenti: Settings, **bağımsız bir tam ekran** veya **modal pencere** olmalı, hangi bölümde olunduğundan bağımsız çalışmalı.

### 4.2 Silme aksiyonu çok kolay tetikleniyor
- `LibraryRow` her satırda görünür çöp kutusu var. Yanlışlıkla tıklama riski yüksek.
- En azından hover-only gösterim, ya da satır seçildikten sonra bir aksiyon barı, ya da sağ-tık menüsüne taşıma.
- Şu anda sağ-tık menüsünde de "Kitaplıktan sil" var, yani silme iki yerden ulaşılabilir; ekrandaki çöp kutusu kaldırılabilir.

### 4.3 Geri alma (undo) yok
- Kitap silindiğinde, not silindiğinde, yer imi silindiğinde geri alma yok. AlertDialog onayı tek savunma. **Snackbar + Undo** çok daha modern ve affedici bir UX.

### 4.4 Sürükle-bırak sadece tek dosya kabul ediyor
- `App.kt:222-224`: `files.firstOrNull()?.let { importFile(File(it)) }` — birden fazla dosya bırakıldığında ilki dışındakiler düşürülüyor. Sessiz veri kaybı.
- Çoklu içe aktarma desteklenmeli; ayrıca klasör bırakıldığında klasör içindeki desteklenen dosyaları taraması beklenir.

### 4.5 Reader dock kalabalık ve responsive değil
- `ReaderScreen.kt:927-963`: Tek satırda 6 ikon + "Sesli Oku" butonu (156dp sabit) + 2 nav buton + auto-scroll = pencere küçüldüğünde taşma garantili.
- Modern yaklaşım: aksiyonları gruplara ayır, ya da overflow menüsü (`MoreVert`).
- Status metninin sabit `widthIn(min = 160.dp, max = 280.dp)` olarak dock içinde olması, dock'un esnekliğini bozuyor — bunun yerini ayrı bir status bar daha doğru.

### 4.6 Not rengi seçilemiyor
- `NoteColor` enum'ında 4 renk var (Yellow, Pink, Green, Blue) ama `addNote` (`ReaderScreen.kt:288`) **her zaman Yellow** atıyor.
- README "Renk kodlu vurgulama" diyor, gerçekte tek renk var. UI hiçbir yerde renk seçtirmiyor.

### 4.7 Notes dashboard ve okuyucu birbirinden kopuk
- Sidebar'dan "Notlar"a tıklayınca açılan `NotesDashboard` (`App.kt:557-576`) sadece liste gösteriyor. Notu tıklayınca hangi kitapta olduğu açılmıyor, gezinme yok, ekleme/silme/dışa aktarma yok.
- "Sayfa ${(note.startIndex / 3000) + 1}" yazıyor — hangi kitap olduğu bile yazılmıyor.

### 4.8 Arama AlertDialog içinde
- Modern uygulamalar (browser, IDE, Notion) arama kutusunu okuma içeriğinin **üstüne kayan inline bar** olarak gösterir. Dialog ile sayfa görünümü kapanmış oluyor — yazıp gezinmek için ileri-geri eşlemesi kötüleşiyor.
- Eşleşme sayacı dialog'un kendi içinde küçük bir metin — modern karşılığı: kayar bar üzerinde "12/89" + ileri/geri/x butonları.

### 4.9 Yükleme göstergesi yok
- `openItem` (`App.kt:126`) dosyayı senkron okuyor (PDF okuma uzun sürer). UI o sırada donar. Hiçbir progress göstergesi yok.
- Reader `pages = remember(document.text) { document.text.chunked(PAGE_SIZE) }` — büyük dosyalar için `chunked()` da pahalı bir işlem; chunk'lama ve dosya okuma I/O dispatcher'a alınmalı.

### 4.10 Veritabanı yazımı UI thread'inde
- `ReaderScreen.kt:165-171`: `LaunchedEffect(listState.firstVisibleItemIndex) { ... DatabaseService.updateLibraryItem(...) }` — `LaunchedEffect` Main dispatcher'da çalışıyor, her sayfa kaydırmada SQLite write tetikleniyor. Büyük dosyalarda jank riski.
- Üstelik bu sayede `Bookmark`/`Note` yazımları da `Main`'de. Bu, varsayılan olarak küçük SQLite'larda fark edilmese de doğru desen değil.

### 4.11 Hata mesajları kullanıcıya gizli
- `DocumentReaderService` "PDF okuma hatası: …" döndürüyor — bu metin doğrudan **dokümanın içeriği gibi okuyucuda gösteriliyor**. Snackbar/dialog daha doğru.

### 4.12 Kütüphane sıralama/filtreleme yok
- Sadece `last_read_at DESC` ile sıralı. Ad/eklenme tarihi/boyut sıralaması, kategori/etiket sistemi yok. Kütüphane büyüdükçe yönetilemez.

### 4.13 Reader'da klavye kısayolları sınırlı
- Ctrl+F (arama), Ctrl+G (sonraki eşleşme), Ctrl+B (yer imi), Ctrl+N (not), +/− (yazı boyutu), J/K (sayfa) gibi modern okuyucularda standart kısayollar yok.
- `App.kt`'de Ctrl+W "açık dokümanı kapat" var ama Ctrl+T (yeni sekme), Ctrl+Tab (sekme değiştir) yok.

### 4.14 Ayarlar paneli dar
- `ModalDrawerSheet width = 360.dp`, voices listesi `height = 140.dp` sabit. Onlarca ses olunca kullanılamaz.
- Yazı tipi seçimi `AssistChip`'lerin dikey LazyColumn'unda — `FlowRow` daha doğal. Ayarlar genel olarak kategorili tabbed bir tam-ekran sayfa olabilir.

### 4.15 Erişilebilirlik
- İkon butonların çoğunda `contentDescription = null` (örn. dock içindeki bazı ikonlar). Ekran okuyucu desteği zayıf.
- Yüksek kontrast modu, font ölçeklendirme bağımsız ayarı, focus indicator vurgusu yok.

---

## 5. Mimari ve Teknik Borç (Yan Etkili)

### 5.1 `ui/screens/LibraryScreen.kt` ölü kod
Bölüm 2.2'de değinildi. Kütüphanedeki `UIManager.setLookAndFeel` çağrısı bile içinde kalmış — kaldırılmalı.

### 5.2 `viewModel`, `repository`, `utils` boş klasörler
Klasörler oluşturulmuş ama dosya yok. ViewModel/Repository deseni başlanmış ama uygulanmamış. Şu an tüm state ekran içinde `remember { mutableStateOf(...) }` — bu, daha büyük özellikler eklendiğinde sürdürülemez.

### 5.3 `theme/` ve `ui/theme/` iki yerde
`com/example/filiz/desktop/theme/` boş, `com/example/filiz/desktop/ui/theme/` dolu. Tek konuma toplanmalı.

### 5.4 Magic number'lar
`PAGE_SIZE = 3000`, `READER_TARGET_OFFSET_PX = 180`, `ESTIMATED_READER_PAGE_HEIGHT_PX = 2600`, voices list `height(140.dp)`, library width `coerceIn(260.dp, 520.dp)`. Açıklayıcı sabitlere ya da hesaplanan değerlere taşınmalı.

### 5.5 Settings entegrasyonu kısmen tek yönlü
`PreferenceService.setX` UI'dan çağrılıyor ama `Strings`/`isDarkMode` gibi state'ler ekran düzeyinde tutuluyor — birden fazla ekran açıkken senkron değil. Tek bir `AppState`/Flow tabanlı yaklaşım daha sağlam olur.

### 5.6 Kapatılmamış JDBC kaynakları
`DatabaseService` içinde `stmt?.close()` var ama `ResultSet` kapatma `use {}` ile yapılıyor — `getAllLibraryItems` gibi fonksiyonlarda `Statement` kapatılmıyor (ResultSet kapanırken statement kapanmıyor). Sızıntı riski.

### 5.7 Çoklu pencere yok
Aynı anda iki kitabı yan yana okumak için ikinci pencere açma yok (`Window` tek tane). Modern okuyucularda yaygın özellik.

### 5.8 Belge önbelleği yok
Aynı PDF her açıldığında baştan parse ediliyor. Ekstrakte edilen metin bir kez SQLite'a/diske yazılıp tekrar açılışlarda hızlı yüklenebilir.

---

## 6. Eksik veya Tartışmalı Özellikler

| Özellik | Durum | Etki |
|---|---|---|
| EPUB okuma | Yok (README vaat ediyor) | Kritik — e-reader temel beklentisi |
| Gerçek PDF render (görsel/layout korunarak) | Yok (sadece text strip) | Yüksek — modernlik beklentisi |
| Kitap kapakları | Yok | Yüksek — görsel kalite |
| Gerçek sayfalama / TOC | Yok | Yüksek — okuma deneyimi |
| Renk kodlu vurgulama (4 renk) | Yarım (sadece sarı kullanılıyor) | Orta |
| RTL (Arapça) | Yok | Yüksek (Arapça kullanıcı için kritik) |
| Pencere durumu hatırlama | Yok | Orta |
| Çoklu dosya import | Yarım (sadece ilk dosya) | Orta |
| Çoklu pencere | Yok | Düşük |
| Tooltip & kısayol ipuçları | Yok | Yüksek (keşfedilebilirlik) |
| Menü çubuğu | Yok | Yüksek (Windows alışkanlığı) |
| Mica/Acrylic | Yok | Orta (Windows 11 modernliği) |
| Animasyon/transition | Yok | Orta |
| Erişilebilirlik | Zayıf | Orta |
| İçerik önbelleği | Yok | Orta (performans) |
| Notları PDF/MD'ye aktarma | Yarım (sadece TXT) | Düşük |

---

## 7. Önceliklendirilmiş Geliştirme Önerileri

### Yüksek öncelik (1–2 haftalık iş, deneyimi en çok değiştirir)
1. **Marka birliği**: Filiz mi Bideng mi karar; README, paket adı, dosya yolları, UI başlıkları hizala.
2. **Çeviri tam hattı**: Tüm hard-code Türkçe stringleri `Strings`'e taşı. Arapça için `LayoutDirection.Rtl` ekle.
3. **EPUB desteği**: `epublib` veya `nl.siegmann.epublib` ile EPUB okuma + bölüm/TOC çıkarma.
4. **Gerçek sayfalama ve TOC**: PDF'te `PDDocument.numberOfPages`, EPUB'ta TOC item'ları kullanılarak gerçek navigasyon.
5. **Ölü kod temizliği**: `LibraryScreen.kt`, boş `theme/`, boş `viewModel/repository/utils` ya kullan ya kaldır.
6. **Pencere durumunu kalıcılaştır**: PreferenceService'e `windowWidth/Height/X/Y/maximized` ekle.
7. **TooltipArea + Menü çubuğu**: Windows alışkanlığına uygun, kısayolları görünür kıl.

### Orta öncelik (görsel ve UX cilası)
8. **Custom title bar + Mica**: Windows 11'de modern hissi katı şekilde yükseltir.
9. **Custom font (Inter / Segoe UI Variable)** + tipografi düzeltmesi.
10. **Kitap kapakları**: PDF ilk sayfa thumbnail'ı veya prosedürel kapak.
11. **Library grid/list toggle, sıralama, arama** (arama zaten var, sıralama yok).
12. **Inline find bar** (Ctrl+F kayar bar).
13. **AnimatedVisibility / Crossfade** geçişleri.
14. **Hover-only delete + Snackbar undo**.
15. **Sürükle-bırak çoklu dosya** + klasör desteği.
16. **Reader dock'unu yeniden organize**, status bar'ı pencere altına ayır.

### Düşük öncelik (uzun vadede)
17. Renk kodlu not seçimi UI'sı, not dashboard'unu gerçek anlamda kullanışlı hale getir.
18. Çoklu pencere, çoklu sekme klavye kısayolları.
19. PDF görsel render (PDFBox + image, ya da JPedal/IcePDF).
20. ViewModel/Repository katmanı.
21. İçerik önbelleği.

---

## 8. Tek Cümlelik Özet

**İskelet sağlam ama yarı tamamlanmış**: temel okuyucu+TTS+kütüphane çatısı çalışıyor, ama marka tutarsızlığı, yarım çeviri, gerçek e-reader özelliklerinin (EPUB, kapaklar, gerçek sayfalama) eksikliği ve modern Windows 11 görsel diline (Mica, custom title bar, tooltip, menü çubuğu, özel font) hiç dokunulmamış olması, uygulamayı "fonksiyonel ama 2018 hissi veren" bir noktada tutuyor — yukarıdaki yüksek öncelik 7 kalemi tamamlamak çok büyük bir sıçrama yaratır.
