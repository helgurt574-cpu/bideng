package com.example.filiz.desktop.service

import org.apache.fontbox.cmap.CMap
import org.apache.fontbox.cmap.CMapParser
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDSimpleFont
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont
import org.apache.pdfbox.pdmodel.font.PDVectorFont
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.util.IdentityHashMap
import kotlin.math.ceil

// Letters that start ligatures in common text fonts (ff, fi, ft, fk, ti, tt, tı, st, ct, Th...).
private val LIGATURE_LEADS = setOf('f', 't', 's', 'c', 'F', 'T')
private val LIGATURE_TRIPLE_PREFIXES = listOf("ff", "ft", "tf", "tt")
private const val LIGATURE_MIN_WIDTH_RATIO = 0.82f
private const val LIGATURE_MAX_WIDTH_RATIO = 1.06f
private const val LIGATURE_RASTER_PX_PER_EM = 64f
private const val LIGATURE_MIN_MATCH_SCORE = 0.72f

/**
 * Some PDF producers (macOS Quartz with Calibri, for example) embed ligature glyphs such as "ti", "tı"
 * or "ft" without a ToUnicode entry. PDFBox then falls back to the font encoding and the ligature turns
 * into an unrelated letter ("Perspektif" -> "PerspekBf"). Subset fonts carry no glyph names either, so
 * the missing glyphs are recovered by comparing their outlines with combinations of the letters the same
 * font does map. Ligatures the CMap does map (U+FB00–FB06) need nothing here: PDFTextStripper already
 * expands them with NFKC.
 */
internal class LigatureRepairingTextStripper : PDFTextStripper() {
    private val fontRepairs = IdentityHashMap<COSDictionary, FontLigatureRepair?>()

    override fun processTextPosition(text: TextPosition) {
        super.processTextPosition(repair(text))
    }

    private fun repair(text: TextPosition): TextPosition {
        val code = text.characterCodes?.singleOrNull() ?: return text
        val replacement = repairFor(text.font)?.textFor(code) ?: return text
        return TextPosition(
            text.rotation,
            text.pageWidth,
            text.pageHeight,
            text.textMatrix,
            text.endX,
            text.endY,
            text.height,
            text.individualWidths?.firstOrNull() ?: text.width,
            text.widthOfSpace,
            replacement,
            text.characterCodes,
            text.font,
            text.fontSize,
            text.fontSizeInPt.toInt()
        )
    }

    private fun repairFor(font: PDFont?): FontLigatureRepair? {
        if (font !is PDSimpleFont || font !is PDVectorFont) return null
        val key = font.cosObject
        if (fontRepairs.containsKey(key)) return fontRepairs[key]
        val toUnicode = runCatching {
            (key.getDictionaryObject(COSName.TO_UNICODE) as? COSStream)
                ?.createInputStream()
                ?.use { CMapParser(true).parse(RandomAccessReadBuffer(it)) }
                ?.takeIf { it.hasUnicodeMappings() }
        }.getOrNull()
        val repair = toUnicode?.let { FontLigatureRepair(font, it) }
        fontRepairs[key] = repair
        return repair
    }
}

/**
 * Recovers text for codes a font's ToUnicode CMap leaves out. Only those codes are touched, so every glyph
 * the PDF maps explicitly (including real "B" or "P" letters) keeps its own text.
 */
private class FontLigatureRepair(private val font: PDSimpleFont, private val toUnicode: CMap) {
    private val vectorFont = font as PDVectorFont
    private val recovered = HashMap<Int, String?>()

    private val letterCodes: Map<Char, Int> by lazy {
        val codes = LinkedHashMap<Char, Int>()
        for (code in 0..255) {
            val text = toUnicode.toUnicode(code) ?: continue
            val char = text.singleOrNull() ?: continue
            if (char.isLetter() && char !in codes) codes[char] = code
        }
        codes
    }

    private val unitsPerEm: Float by lazy {
        runCatching {
            if (font is PDTrueTypeFont) font.trueTypeFont.unitsPerEm.toFloat()
            else 1f / font.fontMatrix.scaleX
        }.getOrDefault(1000f).takeIf { it > 0f } ?: 1000f
    }

    fun textFor(code: Int): String? {
        if (toUnicode.toUnicode(code) != null) return null
        if (recovered.containsKey(code)) return recovered[code]
        val text = runCatching { recognizeLigature(code) }.getOrNull()
        recovered[code] = text
        return text
    }

    private fun recognizeLigature(code: Int): String? {
        if (!vectorFont.hasGlyph(code)) return null
        val ligatureWidth = font.getWidth(code)
        if (ligatureWidth <= 0f) return null
        val ligaturePath = vectorFont.getPath(code)
        if (ligaturePath.bounds2D.isEmpty) return null

        val emPx = LIGATURE_RASTER_PX_PER_EM / unitsPerEm
        val left = -0.15f * unitsPerEm
        val top = 1.0f * unitsPerEm
        val widthPx = ceil((ligatureWidth / 1000f * unitsPerEm + 0.3f * unitsPerEm) * emPx).toInt()
        val heightPx = ceil(1.35f * unitsPerEm * emPx).toInt()
        val raster = GlyphRaster(widthPx, heightPx, left, top, emPx)
        val ligatureInk = raster.draw(listOf(ligaturePath to 0f))
        if (ligatureInk.none { it }) return null

        var best: String? = null
        var bestScore = 0f
        for (candidate in ligatureCandidates()) {
            val components = candidate.map { char -> letterCodes.getValue(char) }
            val widths = components.map { font.getWidth(it) }
            val totalWidth = widths.sum()
            if (totalWidth <= 0f) continue
            val ratio = ligatureWidth / totalWidth
            if (ratio !in LIGATURE_MIN_WIDTH_RATIO..LIGATURE_MAX_WIDTH_RATIO) continue
            // Ligatures are usually a little tighter than their letters; spread the letters to the
            // ligature's advance so the outlines line up.
            var offset = 0f
            val placed = components.mapIndexed { index, component ->
                val placement = vectorFont.getPath(component) to offset
                offset += widths[index] * ratio / 1000f * unitsPerEm
                placement
            }
            val score = overlap(ligatureInk, raster.draw(placed))
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best?.takeIf { bestScore >= LIGATURE_MIN_MATCH_SCORE }
    }

    private fun ligatureCandidates(): List<String> {
        val lowercase = letterCodes.keys.filter { it.isLowerCase() }
        val pairs = letterCodes.keys.filter { it in LIGATURE_LEADS }.flatMap { lead -> lowercase.map { "$lead$it" } }
        val triples = LIGATURE_TRIPLE_PREFIXES
            .filter { prefix -> prefix.all { it in letterCodes } }
            .flatMap { prefix -> lowercase.map { "$prefix$it" } }
        return pairs + triples
    }

    private fun overlap(a: BooleanArray, b: BooleanArray): Float {
        var both = 0
        var either = 0
        for (i in a.indices) {
            if (a[i] && b[i]) both++
            if (a[i] || b[i]) either++
        }
        return if (either == 0) 0f else both.toFloat() / either
    }
}

/** Rasterizes glyph outlines (font units, y up) into a fixed frame for shape comparison. */
private class GlyphRaster(
    private val widthPx: Int,
    private val heightPx: Int,
    private val left: Float,
    private val top: Float,
    private val scale: Float
) {
    private val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    private val pixels = IntArray(widthPx * heightPx)

    fun draw(paths: List<Pair<GeneralPath, Float>>): BooleanArray {
        val graphics = image.createGraphics()
        try {
            graphics.composite = java.awt.AlphaComposite.Clear
            graphics.fillRect(0, 0, widthPx, heightPx)
            graphics.composite = java.awt.AlphaComposite.SrcOver
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            graphics.color = java.awt.Color.BLACK
            for ((path, xOffset) in paths) {
                val transform = AffineTransform()
                transform.scale(scale.toDouble(), -scale.toDouble())
                transform.translate((xOffset - left).toDouble(), (-top).toDouble())
                graphics.fill(transform.createTransformedShape(path))
            }
        } finally {
            graphics.dispose()
        }
        image.getRGB(0, 0, widthPx, heightPx, pixels, 0, widthPx)
        return BooleanArray(pixels.size) { index -> (pixels[index] ushr 24) > 127 }
    }
}
