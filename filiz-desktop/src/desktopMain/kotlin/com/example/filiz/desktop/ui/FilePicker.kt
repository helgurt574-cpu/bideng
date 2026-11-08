package com.example.filiz.desktop.ui

import com.example.filiz.desktop.service.DocumentReaderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Belge seçici (çoklu seçim).
 *
 * Linux'ta Java'nın GTK dosya penceresi bazı masaüstlerinde (ör. XFCE) içi boş gri açılıyor; bu yüzden
 * önce masaüstünün kendi seçicisi ayrı süreç olarak açılır (zenity: GNOME/XFCE/Cinnamon, kdialog: KDE).
 * İkisi de yoksa Java'nın kendi çizdiği Swing seçicisine düşülür. Mac ve Windows'ta yerel pencere kullanılır.
 * BIDENG_FILE_PICKER=zenity|kdialog|swing|awt ile seçici zorlanabilir (tanılama).
 */
object FilePicker {
    private val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    private val forced = System.getenv("BIDENG_FILE_PICKER")?.lowercase()
    private val extensions get() = DocumentReaderService.supportedExtensions.sorted()

    /** Arayüz iş parçacığından çağrılır; dış seçici açıkken pencere donmaz. */
    suspend fun chooseDocuments(title: String, parent: Frame?): List<File> {
        val order = when {
            forced != null -> listOf(forced)
            isLinux -> listOf("zenity", "kdialog", "swing")
            else -> listOf("awt")
        }
        for (kind in order) {
            val result = when (kind) {
                "zenity" -> withContext(Dispatchers.IO) { zenity(title) }
                "kdialog" -> withContext(Dispatchers.IO) { kdialog(title) }
                "swing" -> swing(title, parent)
                else -> awt(title, parent)
            }
            // null: bu seçici yok ya da çalışmadı → sıradakini dene; boş liste: kullanıcı vazgeçti
            if (result != null) return result
        }
        return emptyList()
    }

    private fun zenity(title: String): List<File>? {
        val pattern = extensions.flatMap { listOf("*.$it", "*.${it.uppercase()}") }.joinToString(" ")
        return runPicker(
            "zenity", "--file-selection", "--multiple", "--separator=\n", "--title=$title",
            "--filename=${System.getProperty("user.home")}/",
            "--file-filter=Belgeler | $pattern", "--file-filter=* | *"
        )
    }

    private fun kdialog(title: String): List<File>? {
        val pattern = extensions.joinToString(" ") { "*.$it" }
        return runPicker(
            "kdialog", "--title", title, "--getopenfilename", "--multiple", "--separate-output",
            System.getProperty("user.home"), "$pattern|Belgeler"
        )
    }

    /** Çıkış 0: seçilen dosyalar; 1: vazgeçildi; diğer / başlatılamadı: null (yedek seçiciye geç). */
    private fun runPicker(vararg command: String): List<File>? {
        val process = runCatching {
            ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrNull() ?: return null
        val output = process.inputStream.bufferedReader().readText()
        return when (process.waitFor()) {
            0 -> parseSelection(output)
            1 -> emptyList()
            else -> null
        }
    }

    internal fun parseSelection(output: String): List<File> =
        output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map(::File).filter { it.exists() }.toList()

    private fun swing(title: String, parent: Frame?): List<File> {
        // GTK görünümü de aynı GTK sorununa takılabilir: Java'nın kendi çizdiği görünüm kullanılır
        val previous = UIManager.getLookAndFeel()
        runCatching { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()) }
        try {
            val chooser = JFileChooser(System.getProperty("user.home")).apply {
                dialogTitle = title
                isMultiSelectionEnabled = true
                fileFilter = FileNameExtensionFilter("Belgeler", *extensions.toTypedArray())
                preferredSize = java.awt.Dimension(900, 580)
            }
            return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFiles.toList().ifEmpty { listOfNotNull(chooser.selectedFile) }
            } else {
                emptyList()
            }
        } finally {
            runCatching { UIManager.setLookAndFeel(previous) }
        }
    }

    private fun awt(title: String, parent: Frame?): List<File> {
        // Ana pencereye bağlı açılsın: sahipsiz pencere uygulamanın arkasında kalabiliyor
        val dialog = FileDialog(parent, title, FileDialog.LOAD).apply {
            isMultipleMode = true
            directory = System.getProperty("user.home")
            filenameFilter = java.io.FilenameFilter { _, name ->
                name.substringAfterLast('.', "").lowercase() in DocumentReaderService.supportedExtensions
            }
        }
        dialog.isVisible = true
        return dialog.files?.toList().orEmpty()
    }
}
