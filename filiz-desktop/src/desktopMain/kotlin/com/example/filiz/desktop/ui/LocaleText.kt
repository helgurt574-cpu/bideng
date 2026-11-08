package com.example.filiz.desktop.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiLanguage(val id: String, val label: String) {
    Turkish("tr", "Türkçe"),
    Kurdish("ku", "Kurdî (Kurmancî)"),
    Arabic("ar", "العربية");

    val isRtl: Boolean get() = this == Arabic

    companion object {
        fun fromId(id: String): UiLanguage = entries.firstOrNull { it.id == id } ?: Turkish
    }
}

val LocalStrings = staticCompositionLocalOf { Strings(UiLanguage.Turkish) }

class Strings(val language: UiLanguage) {
    fun t(turkish: String, kurdish: String, arabic: String): String = when (language) {
        UiLanguage.Turkish -> turkish
        UiLanguage.Kurdish -> kurdish
        UiLanguage.Arabic -> arabic
    }

    // Genel
    val cancel get() = t("İptal", "Betal", "إلغاء")
    val close get() = t("Kapat", "Bigire", "إغلاق")
    val save get() = t("Kaydet", "Tomar bike", "حفظ")
    val delete get() = t("Sil", "Jê bibe", "حذف")
    val ok get() = t("Tamam", "Temam", "تم")
    val back get() = t("Geri", "Paş", "رجوع")
    val search get() = t("Ara", "Bigere", "بحث")
    val theme get() = t("Tema", "Tema", "السمة")
    val notes get() = t("Notlar", "Not", "الملاحظات")
    val languageLabel get() = t("Dil", "Ziman", "اللغة")
    val more get() = t("Seçenekler", "Vebijark", "خيارات")
    fun themeName(id: String) = when (id) {
        "light" -> t("Açık", "Ronî", "فاتح")
        "sepia" -> t("Sepya", "Sepya", "سيبيا")
        else -> t("Koyu", "Tarî", "داكن")
    }

    // Kütüphane
    val library get() = t("Kütüphane", "Pirtûkxane", "المكتبة")
    fun libraryCount(n: Int) = if (n == 0) t("Henüz belge eklenmedi", "Hêj belge nehatiye zêdekirin", "لم تتم إضافة أي مستند بعد")
        else t("$n belge · okumaların burada saklanır", "$n belge · xwendinên te li vir tên parastin", "$n مستند · قراءاتك محفوظة هنا")
    val addDocument get() = t("Belge ekle", "Belge zêde bike", "إضافة مستند")
    val continueReading get() = t("Kaldığın yerden devam et", "Ji cihê ku lê mayî berdewam bike", "تابع من حيث توقفت")
    val pausedReading get() = t("Sesli okuma duraklatıldı", "Xwendina bi deng hate sekinandin", "القراءة الصوتية متوقفة مؤقتًا")
    fun documentsLabel(n: Int) = t("Belgeler · $n", "Belge · $n", "المستندات · $n")
    fun resultsLabel(n: Int) = t("Sonuçlar · $n", "Encam · $n", "النتائج · $n")
    val noResults get() = t("Bu aramaya uyan belge yok", "Ji bo vê lêgerînê belge tune", "لا توجد مستندات مطابقة")
    val searchLibraryHint get() = t("Belge adı ara", "Navê belgeyê bigere", "ابحث باسم المستند")
    val emptyTitle get() = t("Kütüphanen boş", "Pirtûkxaneya te vala ye", "مكتبتك فارغة")
    val emptyBody get() = t(
        "PDF, EPUB, Word veya metin dosyası ekle ya da dosyaları bu pencereye sürükle; Bideng senin için sesli okusun.",
        "PDF, EPUB, Word an pelê nivîsê zêde bike an jî pelan bikişîne vê paceyê.",
        "أضف ملف PDF أو EPUB أو Word أو نص، أو اسحب الملفات إلى هذه النافذة."
    )
    fun readPercent(p: Int) = t("%$p okundu", "%$p hate xwendin", "%$p مقروء")
    val notOpened get() = t("henüz açılmadı", "hêj nehatiye vekirin", "لم يُفتح بعد")
    val rename get() = t("Yeniden adlandır", "Nav biguherîne", "إعادة تسمية")
    val renameTitle get() = t("Belgeyi yeniden adlandır", "Navê belgeyê biguherîne", "إعادة تسمية المستند")
    val documentName get() = t("Belge adı", "Navê belgeyê", "اسم المستند")
    val deleteTitle get() = t("Belge silinsin mi?", "Bila belge were jêbirin?", "هل تريد حذف المستند؟")
    fun deleteMessage(name: String) = t(
        "\"$name\" kütüphaneden ve notlarıyla birlikte silinecek.",
        "\"$name\" ji pirtûkxaneyê û bi notên xwe re dê were jêbirin.",
        "سيتم حذف \"$name\" من المكتبة مع ملاحظاته."
    )
    fun importing(name: String) = t("İçe aktarılıyor: $name", "Tê barkirin: $name", "جارٍ الاستيراد: $name")
    fun opening(name: String) = t("Açılıyor: $name", "Tê vekirin: $name", "جارٍ الفتح: $name")
    fun imported(name: String) = t("Eklendi: $name", "Hat zêdekirin: $name", "تمت الإضافة: $name")
    val unsupported get() = t("Desteklenen belge bulunamadı", "Belgeyeke piştgirîkirî nehat dîtin", "لم يتم العثور على مستند مدعوم")
    val fileMissing get() = t("Belge dosyası bulunamadı", "Pela belgeyê nehat dîtin", "لم يتم العثور على ملف المستند")
    val chooseFiles get() = t("Belge seç", "Belgeyê hilbijêre", "اختر مستندًا")

    // Okuyucu
    val readyHint get() = t("Okumak için ▶'ye ya da bir kelimeye tıkla", "Ji bo xwendinê li ▶ an peyvekê bixe", "اضغط ▶ أو انقر على كلمة لبدء القراءة")
    val preparing get() = t("Ses hazırlanıyor…", "Deng tê amadekirin…", "جارٍ تجهيز الصوت…")
    val reading get() = t("Okunuyor", "Tê xwendin", "تجري القراءة")
    val paused get() = t("Duraklatıldı · devam etmek için ▶", "Sekinî · ji bo berdewamiyê ▶", "متوقف مؤقتًا · اضغط ▶ للمتابعة")
    val stopped get() = t("Sesli okuma durduruldu", "Xwendina bi deng rawestiya", "تم إيقاف القراءة الصوتية")
    val finished get() = t("Okuma tamamlandı", "Xwendin temam bû", "اكتملت القراءة")
    val readFromHere get() = t("Buradan okumak için ▶", "Ji bo ji vir bixwînî ▶", "اضغط ▶ للقراءة من هنا")
    val play get() = t("Oynat", "Lê bide", "تشغيل")
    val pause get() = t("Duraklat", "Bisekinîne", "إيقاف مؤقت")
    val resume get() = t("Devam et", "Berdewam bike", "متابعة")
    val stop get() = t("Durdur", "Rawestîne", "إيقاف")
    val previousSentence get() = t("Önceki cümle", "Hevoka berê", "الجملة السابقة")
    val nextSentence get() = t("Sonraki cümle", "Hevoka din", "الجملة التالية")
    val autoScroll get() = t("Otomatik kaydır", "Kişandina xweber", "التمرير التلقائي")
    val autoScrollOn get() = t("Otomatik kaydırma açık · durdurmak için tekrar bas", "Kişandina xweber vekirî ye", "التمرير التلقائي مفعّل")
    val readingSettings get() = t("Okuma ayarları", "Mîhengên xwendinê", "إعدادات القراءة")
    val searchInText get() = t("Metinde ara", "Di nivîsê de bigere", "ابحث في النص")
    val previousResult get() = t("Önceki sonuç", "Encama berê", "النتيجة السابقة")
    val nextResult get() = t("Sonraki sonuç", "Encama din", "النتيجة التالية")
    fun searchFound(i: Int, n: Int) = t("$i / $n sonuç", "$i / $n encam", "$i / $n نتيجة")
    val searchNone get() = t("Sonuç yok", "Encam tune", "لا نتائج")
    fun pageLabel(unit: String, current: Int, total: Int) = when (unit) {
        "chapter" -> t("Bölüm $current / $total", "Beş $current / $total", "الفصل $current / $total")
        "location" -> t("Konum $current / $total", "Cih $current / $total", "الموضع $current / $total")
        else -> t("Sayfa $current / $total", "Rûpel $current / $total", "الصفحة $current / $total")
    }
    val voice get() = t("Ses", "Deng", "الصوت")
    val speechSpeed get() = t("Konuşma hızı", "Lezîna axaftinê", "سرعة النطق")
    val appearance get() = t("Görünüm", "Xuyang", "المظهر")
    val sleep get() = t("Uyku", "Xew", "النوم")
    val focusMode get() = t("Odak", "Baldarî", "تركيز")
    val sleepTitle get() = t("Uyku zamanlayıcısı", "Demjimêra xewê", "مؤقت النوم")
    val sleepOff get() = t("Kapalı", "Girtî", "إيقاف")
    fun minutes(m: Int) = t("$m dk", "$m deq", "$m د")
    val voiceTitle get() = t("Ses seçimi", "Hilbijartina dengê", "اختيار الصوت")
    val builtIn get() = t("uygulamayla gelir", "bi sepanê re tê", "مضمّن في التطبيق")
    fun voiceLanguage(code: String) = when (code) {
        "ar" -> t("Arapça", "Erebî", "العربية")
        "fa" -> t("Farsça", "Farisî", "الفارسية")
        else -> t("Türkçe", "Tirkî", "التركية")
    }
    val languageTitle get() = t("Arayüz dili", "Zimanê navrûyê", "لغة الواجهة")
    val fontSize get() = t("Yazı boyutu", "Mezinahiya nivîsê", "حجم الخط")
    val lineSpacing get() = t("Satır aralığı", "Navbera rêzan", "تباعد الأسطر")
    val fontFamily get() = t("Yazı tipi", "Curenivîs", "نوع الخط")
    val selectionHint get() = t("Seçili metni not olarak ekle", "Nivîsa hilbijartî wek not zêde bike", "أضف النص المحدد كملاحظة")
    val addNote get() = t("Not ekle", "Not zêde bike", "أضف ملاحظة")
    val noteAdded get() = t("Not eklendi", "Not hat zêdekirin", "تمت إضافة الملاحظة")

    // Notlar
    val notesEmpty get() = t(
        "Henüz not yok. Okurken bir bölümü fareyle seçip \"Not ekle\"ye basarak not alabilirsin.",
        "Hêj not tune ye. Di dema xwendinê de beşekê hilbijêre û \"Not zêde bike\" bitikîne.",
        "لا توجد ملاحظات بعد. حدّد نصًا أثناء القراءة ثم اضغط \"أضف ملاحظة\"."
    )
    val exportNotes get() = t("Dışa aktar", "Derxe derve", "تصدير")
    val deleteAll get() = t("Tümünü sil", "Hemûyan jê bibe", "حذف الكل")
    val deleteNoteTitle get() = t("Not silinsin mi?", "Bila not were jêbirin?", "هل تريد حذف الملاحظة؟")
    val clearNotesTitle get() = t("Tüm notlar silinsin mi?", "Bila hemû not werin jêbirin?", "هل تريد حذف كل الملاحظات؟")
    fun clearNotesMessage(n: Int) = t("$n not silinecek.", "$n not dê werin jêbirin.", "سيتم حذف $n ملاحظة.")
    fun exported(path: String) = t("Notlar kaydedildi: $path", "Not hatin tomarkirin: $path", "تم حفظ الملاحظات: $path")
    val untitled get() = t("Adsız belge", "Belgeya bênav", "مستند بلا اسم")
}
