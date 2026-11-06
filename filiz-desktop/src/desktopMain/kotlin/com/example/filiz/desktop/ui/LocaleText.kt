package com.example.filiz.desktop.ui

enum class UiLanguage(val id: String, val label: String) {
    Turkish("tr", "Turkce"),
    Kurdish("ku", "Kurdi"),
    Arabic("ar", "العربية");

    companion object {
        fun fromId(id: String): UiLanguage {
            return entries.firstOrNull { it.id == id } ?: Turkish
        }
    }
}

fun tr(language: UiLanguage, turkish: String, kurdish: String, arabic: String): String {
    return when (language) {
        UiLanguage.Turkish -> turkish
        UiLanguage.Kurdish -> kurdish
        UiLanguage.Arabic -> arabic
    }
}

class Strings(private val uiLanguage: UiLanguage) {
    fun text(turkish: String, kurdish: String, arabic: String): String = tr(uiLanguage, turkish, kurdish, arabic)

    val libraryTitle get() = text("Bideng - Belge Kütüphanesi", "Bideng - Pirtûkxaneya Belgeyan", "Bideng - مكتبة المستندات")
    val libraryTab get() = text("Kitaplık", "Pirtûkxane", "المكتبة")
    val readerTab get() = text("Okuma", "Xwendin", "القراءة")
    val home get() = text("Ana", "Mal", "الرئيسية")
    val focus get() = text("Odak", "Baldarî", "تركيز")
    val importDocument get() = text("İçe Aktar", "Veguhêze", "استيراد")
    val emptyLibrary get() = text("Belge Kütüphanesi Boş", "Pirtûkxane vala ye", "المكتبة فارغة")
    val characters get() = text("karakter", "tîp", "حرف")

    val readyToRead get() = text("Okuma hazır. Metne tıklayarak başla.", "Xwendin amade ye. Bi nivîsê dest pê bike.", "القراءة جاهزة. ابدأ بالنقر على النص.")
    val readingStarts get() = text("Okuma başlıyor...", "Xwendin dest pê dike...", "تبدأ القراءة...")
    val readingStopped get() = text("■ Okuma durdu", "■ Xwendin rawestiya", "■ توقفت القراءة")
    val settingsOpen get() = text("Ayarlar açık", "Mîheng vekirî ne", "الإعدادات مفتوحة")
    val autoScrollOn get() = text("Otomatik kaydırma: 1.0x", "Kişandina xweber: 1.0x", "التمرير التلقائي: 1.0x")
    val autoScrollOff get() = text("Otomatik kaydırma kapalı", "Kişandina xweber girtî ye", "التمرير التلقائي متوقف")
    val noAutoScrollWhileReading get() = text("Sesli okuma açıkken otomatik kaydırma başlamaz", "Dema xwendina bi deng vekirî be kişandina xweber dest pê nake", "لا يبدأ التمرير التلقائي أثناء القراءة الصوتية")
    val noteReady get() = text("Not seçimi hazır: Notlar > Not Ekle", "Hilbijartina notê amade ye: Not > Not zêde bike", "تحديد الملاحظة جاهز: الملاحظات > أضف ملاحظة")
    val noteAdded get() = text("Not eklendi", "Not hat zêdekirin", "تمت إضافة الملاحظة")
    val selectedNoteAdded get() = text("Seçili metin notlara eklendi", "Nivîsa hilbijartî li notan hat zêdekirin", "تمت إضافة النص المحدد إلى الملاحظات")

    val stop get() = text("Durdur", "Rawestîne", "إيقاف")
    val close get() = text("Kapat", "Bigire", "إغلاق")
    val delete get() = text("Sil", "Jê bibe", "حذف")
    val summary get() = text("Özet", "Kurte", "ملخص")
    val lastRead get() = text("Son okunan", "Ya dawî hatî xwendin", "آخر قراءة")
    val ready get() = text("Hazır", "Amade", "جاهز")
    val sleep get() = text("Uyku", "Xew", "النوم")
    val minutes15 get() = text("15 dk", "15 xulek", "15 د")
    val minutes30 get() = text("30 dk", "30 xulek", "30 د")
    val minutes60 get() = text("1 saat", "1 saet", "ساعة")
    val sleepTimer get() = text("Uyku zamanlayıcısı", "Demjimêra xewê", "مؤقت النوم")
    val off get() = text("Kapalı", "Girtî", "متوقف")
    val appTheme get() = text("Uygulama teması", "Dirba sepanê", "سمة التطبيق")
    val darkTheme get() = text("Koyu tema", "Dirba tarî", "الوضع الداكن")
    val lightTheme get() = text("Açık tema", "Dirba ronî", "الوضع الفاتح")
    val appliesEverywhere get() = text("Uygulama genelinde geçerli.", "Li hemû sepanê derbasdar e.", "ينطبق على التطبيق كله.")
    val deleteBookQuestion get() = text("Kitap silinsin mi?", "Pirtûk were jêbirin?", "هل تريد حذف الكتاب؟")
    val searchLibrary get() = text("Kitap ara", "Li pirtûkan bigere", "ابحث في الكتب")
    val continueReading get() = text("Kaldığın yerden devam et", "Ji cihê ku mayî bidomîne", "تابع من حيث توقفت")
    val openLastBook get() = text("Son kitabı aç", "Pirtûka dawî veke", "افتح آخر كتاب")
    val exportAllNotes get() = text("Tüm notları dışa aktar", "Hemû notan derxe", "تصدير كل الملاحظات")
    val deleteAll get() = text("Tümünü sil", "Hemûyan jê bibe", "حذف الكل")
    val openBook get() = text("Kitabı aç", "Pirtûkê veke", "افتح الكتاب")
    val info get() = text("Bilgi", "Agahî", "معلومات")
    val closePanel get() = text("Paneli kapat", "Panelê bigire", "إغلاق اللوحة")
    val bookmark get() = text("Yer imi", "Nîşana cih", "إشارة مرجعية")
    val autoScroll get() = text("Otomatik kaydır", "Kişandina xweber", "تمرير تلقائي")
    val previous get() = text("Önceki", "Berê", "السابق")
    val next get() = text("Sonraki", "Piştre", "التالي")
    val exportNotesTxt get() = text("Notları TXT olarak dışa aktar", "Notan wek TXT derxe", "تصدير الملاحظات كـ TXT")
    val clearNotes get() = text("Tüm notları sil", "Hemû notan jê bibe", "حذف كل الملاحظات")
    val deleteNoteQuestion get() = text("Not silinsin mi?", "Not were jêbirin?", "هل تريد حذف الملاحظة؟")
    val deleteAllNotesQuestion get() = text("Tüm notlar silinsin mi?", "Hemû not werin jêbirin?", "هل تريد حذف كل الملاحظات؟")
    val emptyReaderHelp get() = text("Kitaplığından bir belge aç veya Ctrl+O ile yeni belge içe aktar.", "Ji pirtûkxaneyê belgeyekê veke an bi Ctrl+O belgeyekê veguhêze.", "افتح مستندًا من المكتبة أو استورد مستندًا جديدًا باستخدام Ctrl+O.")
    val readAloud get() = text("Sesli Oku", "Bi deng bixwîne", "اقرأ بصوت")
    val settings get() = text("Ayarlar", "Mîheng", "الإعدادات")
    val voice get() = text("Ses", "Deng", "الصوت")
    val embeddedVoicesMissing get() = text("Gömülü APK sesleri yüklenemedi.", "Dengên hundirîn nehatin barkirin.", "تعذر تحميل الأصوات المضمنة.")
    val readingView get() = text("Aa Okuma görünümü", "Aa Dîtina xwendinê", "Aa مظهر القراءة")
    val textSize get() = text("Metin boyutu", "Mezinahiya nivîsê", "حجم النص")
    val lineSpacing get() = text("Satır aralığı", "Navbera rêzan", "تباعد الأسطر")
    val languageTitle get() = text("Dil", "Ziman", "اللغة")
    val speechSpeed get() = text("Konuşma hızı", "Lezîna axaftinê", "سرعة النطق")

    val findWord get() = text("Kelime Bul", "Peyv bibîne", "البحث عن كلمة")
    val searchHint get() = text("Aranacak kelimeyi yazın...", "Peyva lêgerînê binivîse...", "اكتب كلمة البحث...")
    val find get() = text("Bul", "Bibîne", "بحث")
    val cancel get() = text("İptal", "Betal", "إلغاء")
    val found get() = text("bulundu", "hat dîtin", "تم العثور")
    val notFound get() = text("bulunamadı", "nehat dîtin", "لم يتم العثور")

    val notes get() = text("Notlar", "Not", "الملاحظات")
    val selectedTextPrefix get() = text("Seçili metin", "Nivîsa hilbijartî", "النص المحدد")
    val addNote get() = text("Not Ekle", "Not zêde bike", "أضف ملاحظة")
    val noNotes get() = text("Henüz not yok. Okuma ekranında Not ile mevcut sayfadan not ekle.", "Hêj not tune ye. Di ekrana xwendinê de bi Notê not zêde bike.", "لا توجد ملاحظات بعد.")
    val ok get() = text("Tamam", "Temam", "تم")
    val selectedTextHint get() = text("Boş bırakılırsa seçili metin not alınır", "Ger vala bimîne nivîsa hilbijartî wek not tê tomarkirin", "إذا تُرك فارغًا سيتم حفظ النص المحدد كملاحظة")
    val noteHint get() = text("Not yaz veya boş bırakıp mevcut sayfadan not al", "Not binivîse an vala bihêle û ji rûpela heyî not bigire", "اكتب ملاحظة أو اتركه فارغًا لحفظ الصفحة الحالية")

    fun pageLabel(current: Int, total: Int, percent: Int): String {
        return text("Konum $current / $total • %$percent", "Cih $current / $total • %$percent", "الموضع $current / $total • %$percent")
    }
}
