package com.example.filiz.desktop

import com.example.filiz.desktop.ui.FilePicker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FilePickerTest {
    @Test
    fun parsesPickerOutput() {
        val dir = File(System.getProperty("java.io.tmpdir"), "bideng-picker-test").apply { mkdirs() }
        val a = File(dir, "ilk belge.pdf").apply { writeText("x") }
        val b = File(dir, "ikinci.txt").apply { writeText("x") }
        // Boş satırlar, sondaki satır sonu ve artık var olmayan dosyalar atlanır
        val output = "${a.absolutePath}\n\n${b.absolutePath}\n${File(dir, "yok.pdf").absolutePath}\n"
        assertEquals(listOf(a, b), FilePicker.parseSelection(output))
        assertEquals(emptyList(), FilePicker.parseSelection(""))
    }
}
