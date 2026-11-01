package com.ozon.books.ui.reader

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.TypefaceSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import com.ozon.books.data.PathSafety
import com.ozon.books.model.ReaderTypeface
import java.io.File

object NativeChapterParser {

    private val HEAD_STYLE_SCRIPT_REGEX = "(?is)<(head|style|script)[^>]*>.*?</\\1>".toRegex()
    private val IMG_REGEX = "(?i)<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>".toRegex()
    private val HEADING_OPEN_REGEX = "(?i)<h([1-6])[^>]*>".toRegex()
    private val HEADING_CLOSE_REGEX = "(?i)</h[1-6]>".toRegex()
    private val BLOCK_OPEN_REGEX = "(?i)<(p|div|blockquote|li|section|article)[^>]*>".toRegex()
    private val BLOCK_CLOSE_REGEX = "(?i)</(p|div|blockquote|li|section|article)>".toRegex()
    private val BR_REGEX = "(?i)<br\\s*/?>".toRegex()

    private val BLOCK_SPLIT_REGEX = Regex("\\s*###BLOCK_BREAK###\\s*")
    private val IMG_MARKER_REGEX = "###IMG_MARKER###(.*?)###END_MARKER###".toRegex()
    private val H_MARKER_REGEX = "^###H([1-6])_START###([\\s\\S]*)$".toRegex()

    fun parse(rawHtml: String, chapterFile: File, bookRoot: File): List<ChapterElement> {
        if (rawHtml.isBlank()) return emptyList()

        val elements = mutableListOf<ChapterElement>()
        val baseDir = chapterFile.parentFile ?: bookRoot
        var hasBodyParagraph = false

        // 1. Strip <head>, <style>, and <script> so metadata/CSS doesn't render as body text
        var processedHtml = rawHtml.replace(HEAD_STYLE_SCRIPT_REGEX, "")

        // 2. Inject explicit block & heading markers so we don't rely on HtmlCompat's newline quirks
        processedHtml = processedHtml
            .replace(IMG_REGEX) { match ->
                val src = match.groupValues[1]
                "###BLOCK_BREAK######IMG_MARKER###$src###END_MARKER######BLOCK_BREAK###"
            }
            .replace(HEADING_OPEN_REGEX) { match ->
                val level = match.groupValues[1]
                "###BLOCK_BREAK######H${level}_START###"
            }
            .replace(HEADING_CLOSE_REGEX, "###BLOCK_BREAK###")
            .replace(BLOCK_OPEN_REGEX, "###BLOCK_BREAK###")
            .replace(BLOCK_CLOSE_REGEX, "###BLOCK_BREAK###")
            .replace(BR_REGEX, "\n")

        val spanned: Spanned = HtmlCompat.fromHtml(processedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val fullText = spanned.toString()

        var searchIndex = 0
        val rawBlocks = fullText.split(BLOCK_SPLIT_REGEX)

        for (rawBlock in rawBlocks) {
            if (rawBlock.isEmpty()) continue

            val blockStart = fullText.indexOf(rawBlock, searchIndex).takeIf { it != -1 } ?: searchIndex
            val blockEnd = blockStart + rawBlock.length
            searchIndex = blockEnd

            val trimmed = rawBlock.trim()
            if (trimmed.isEmpty()) continue

            // Check for Image marker
            val imgMatch = IMG_MARKER_REGEX.find(trimmed)
            if (imgMatch != null) {
                val relPath = imgMatch.groupValues[1]
                val imageFile = PathSafety.resolveWithin(bookRoot, baseDir, relPath)
                if (imageFile != null &&
                    imageFile.exists() &&
                    imageFile.isFile &&
                    !imageFile.extension.equals("svg", ignoreCase = true)
                ) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
                    val ratio = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        bounds.outWidth.toFloat() / bounds.outHeight
                    } else 1f
                    elements.add(ChapterElement.Image(imageFile.absolutePath, ratio))
                }
                continue
            }

            // Check for explicit Heading marker (###H1_START### ... ###H6_START###)
            val hMatch = H_MARKER_REGEX.find(trimmed)
            if (hMatch != null) {
                val level = hMatch.groupValues[1].toIntOrNull() ?: 2
                val markerPrefix = "###H${level}_START###"
                val contentStartInRaw = rawBlock.indexOf(markerPrefix) + markerPrefix.length
                val actualStart = blockStart + contentStartInRaw

                val annotated = sliceSpannedToAnnotated(spanned, actualStart, blockEnd)
                if (annotated.text.isNotBlank()) {
                    elements.add(ChapterElement.Heading(annotated, level = level.coerceIn(1, 3)))
                }
                continue
            }

            // Standard paragraph or fallback styled heading (e.g., <p class="title"><b>Title</b></p>)
            val annotated = sliceSpannedToAnnotated(spanned, blockStart, blockEnd)
            if (annotated.text.isBlank()) continue

            val fallbackHeadingLevel = detectFallbackHeadingLevel(spanned, blockStart, blockEnd, annotated.text)
            if (fallbackHeadingLevel != null) {
                elements.add(ChapterElement.Heading(annotated, level = fallbackHeadingLevel))
            } else {
                elements.add(ChapterElement.Paragraph(annotated, isFirstLineIndented = hasBodyParagraph))
                hasBodyParagraph = true
            }
        }

        return elements
    }

    /**
     * Accurately slices [spanned] between [start] and [end], trimming leading/trailing
     * whitespace WITHOUT desynchronizing span indices.
     */
    private fun sliceSpannedToAnnotated(spanned: Spanned, start: Int, end: Int): AnnotatedString {
        var trimmedStart = start
        var trimmedEnd = end

        while (trimmedStart < trimmedEnd && spanned[trimmedStart].isWhitespace()) {
            trimmedStart++
        }
        while (trimmedEnd > trimmedStart && spanned[trimmedEnd - 1].isWhitespace()) {
            trimmedEnd--
        }

        if (trimmedStart >= trimmedEnd) return AnnotatedString("")

        val rawSlice = spanned.subSequence(trimmedStart, trimmedEnd).toString()
        val builder = AnnotatedString.Builder(rawSlice)

        val spans = spanned.getSpans(trimmedStart, trimmedEnd, Any::class.java)
        for (span in spans) {
            val sStart = (spanned.getSpanStart(span) - trimmedStart).coerceIn(0, rawSlice.length)
            val sEnd = (spanned.getSpanEnd(span) - trimmedStart).coerceIn(0, rawSlice.length)
            if (sStart < sEnd) {
                when (span) {
                    is android.text.style.StyleSpan -> {
                        when (span.style) {
                            Typeface.BOLD ->
                                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), sStart, sEnd)
                            Typeface.ITALIC ->
                                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), sStart, sEnd)
                            Typeface.BOLD_ITALIC ->
                                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), sStart, sEnd)
                        }
                    }
                    is android.text.style.UnderlineSpan -> {
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), sStart, sEnd)
                    }
                    is android.text.style.StrikethroughSpan -> {
                        builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), sStart, sEnd)
                    }
                }
            }
        }
        return builder.toAnnotatedString()
    }

    /**
     * Catches EPUBs that use <p><b>Chapter 1</b></p> instead of <h1>/<h2> tags.
     */
    private fun detectFallbackHeadingLevel(spanned: Spanned, start: Int, end: Int, text: String): Int? {
        if (text.length > 90 || text.contains('\n')) return null

        val relSpans = spanned.getSpans(start, end, android.text.style.RelativeSizeSpan::class.java)
        val maxRel = relSpans.maxOfOrNull { it.sizeChange }
        if (maxRel != null) {
            if (maxRel >= 1.4f) return 1
            if (maxRel >= 1.15f) return 2
        }

        val styleSpans = spanned.getSpans(start, end, android.text.style.StyleSpan::class.java)
        val isAllBold = styleSpans.any {
            (it.style == Typeface.BOLD || it.style == Typeface.BOLD_ITALIC) &&
                spanned.getSpanStart(it) <= start && spanned.getSpanEnd(it) >= end
        }
        return if (isAllBold && text.length < 70 && !text.endsWith(".")) 3 else null
    }

    fun elementOffsets(elements: List<ChapterElement>): List<Int> {
        val offsets = ArrayList<Int>(elements.size)
        var offset = 0
        for (element in elements) {
            offsets.add(offset)
            offset += elementTextLength(element)
        }
        return offsets
    }

    fun totalTextLength(elements: List<ChapterElement>): Int =
        elements.sumOf { elementTextLength(it) }

    private fun elementTextLength(element: ChapterElement): Int = when (element) {
        is ChapterElement.Paragraph -> element.text.length
        is ChapterElement.Heading -> element.text.length
        is ChapterElement.Image -> 1
    }

    fun elementIndexForOffset(elements: List<ChapterElement>, offset: Int): Int {
        if (elements.isEmpty()) return 0
        val offsets = elementOffsets(elements)
        val index = offsets.indexOfLast { it <= offset.coerceAtLeast(0) }
        return if (index >= 0) index else 0
    }

    fun pageIndexForOffset(pages: List<ChapterPage>, offset: Int): Int {
        if (pages.isEmpty()) return 0
        val index = pages.indexOfLast { it.startCharOffset <= offset.coerceAtLeast(0) }
        return if (index >= 0) index else 0
    }

    /**
     * Paginates [elements] using Android's native [StaticLayout].
     *
     * Paragraphs deliberately have no extra vertical gap: first-line indentation marks a
     * new paragraph, while line height stays identical throughout the text block.
     */
    fun paginate(
        elements: List<ChapterElement>,
        typography: ReaderTypography,
        layout: ReaderLayout,
        customTypeface: Typeface? = null,
        customItalicTypeface: Typeface? = null
    ): List<ChapterPage> {
        if (elements.isEmpty()) return listOf(ChapterPage(emptyList(), 0, 0))
        if (!layout.isValid) return listOf(ChapterPage(elements, 0, totalTextLength(elements)))

        val availableHeightPx = ((layout.heightDp - 16).coerceAtLeast(1) * layout.density)
        val availableWidthPx = (layout.widthDp.coerceAtLeast(1) * layout.density).toInt().coerceAtLeast(1)

        val bodyTextSizePx = typography.fontSizePx * layout.density * layout.fontScale
        val bodyLineHeightPx = bodyTextSizePx * typography.lineHeightMultiplier
        val paragraphIndentPx = (typography.fontSizePx * 1.5f) * layout.density * layout.fontScale
        val imageVerticalPaddingPx = 24f * layout.density

        val baseTypeface = when {
            customTypeface != null -> customTypeface
            typography.typeface == ReaderTypeface.MONO -> Typeface.MONOSPACE
            typography.typeface == ReaderTypeface.SERIF -> Typeface.SERIF
            else -> Typeface.SANS_SERIF
        }

        val bodyPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = bodyTextSizePx
            typeface = baseTypeface
            letterSpacing = 0f
        }

        val offsets = elementOffsets(elements)
        val totalChars = totalTextLength(elements)
        val pages = mutableListOf<ChapterPage>()
        var currentPageElements = mutableListOf<ChapterElement>()
        var currentHeightPx = 0f
        var currentPageStartOffset = 0

        fun addElement(element: ChapterElement, startOffset: Int) {
            if (currentPageElements.isEmpty()) currentPageStartOffset = startOffset
            currentPageElements.add(element)
        }

        fun commitPage() {
            if (currentPageElements.isNotEmpty()) {
                pages.add(ChapterPage(currentPageElements, currentPageStartOffset))
                currentPageElements = mutableListOf()
                currentHeightPx = 0f
            }
        }

        elements.forEachIndexed { elementIndex, element ->
            val elementOffset = offsets[elementIndex]
            when (element) {
                is ChapterElement.Heading -> {
                    val headingScale = when (element.level) {
                        1 -> 1.55f
                        2 -> 1.30f
                        else -> 1.15f
                    }
                    val headingPaint = TextPaint(bodyPaint).apply {
                        textSize = bodyTextSizePx * headingScale
                        typeface = Typeface.create(baseTypeface, Typeface.BOLD)
                    }
                    val headingLayout = buildStaticLayout(
                        text = buildStyledSlice(
                            annotated = element.text,
                            start = 0,
                            end = element.text.length,
                            indentPx = 0f,
                            italicTypeface = customItalicTypeface,
                            baseBold = true
                        ),
                        paint = headingPaint,
                        widthPx = availableWidthPx,
                        lineHeightPx = bodyTextSizePx * headingScale * 1.3f
                    )
                    val headingLineHeightPx = bodyTextSizePx * headingScale * 1.3f
                    val topPadPx = (if (element.level == 1) 12f else 6f) * layout.density
                    val bottomPadPx = 4f * layout.density
                    val totalHeadingHeightPx = topPadPx + (headingLayout.lineCount * headingLineHeightPx) + bottomPadPx

                    if (currentPageElements.isNotEmpty() && currentHeightPx + totalHeadingHeightPx > availableHeightPx) {
                        commitPage()
                    }
                    addElement(element, elementOffset)
                    currentHeightPx += totalHeadingHeightPx
                }

                is ChapterElement.Image -> {
                    val imgHeightPx = readerImageHeight(
                        width = (layout.imageWidthDp.takeIf { it > 0 } ?: layout.widthDp).toFloat(),
                        pageHeight = availableHeightPx / layout.density,
                        aspectRatio = element.aspectRatio
                    ) * layout.density
                    val totalImageHeightPx = imgHeightPx + imageVerticalPaddingPx

                    if (currentPageElements.isNotEmpty() && currentHeightPx + totalImageHeightPx > availableHeightPx) {
                        commitPage()
                    }
                    addElement(element, elementOffset)
                    currentHeightPx += totalImageHeightPx
                }

                is ChapterElement.Paragraph -> {
                    val fullAnnotated = element.text
                    val rawText = fullAnnotated.text
                    var remainingStart = 0
                    var isFirstSlice = element.isFirstLineIndented

                    while (remainingStart < rawText.length) {
                        while (remainingStart < rawText.length && rawText[remainingStart].isWhitespace()) {
                            remainingStart++
                        }
                        if (remainingStart >= rawText.length) break

                        val remainingHeightPx = availableHeightPx - currentHeightPx
                        val maxLinesCanFit = kotlin.math.floor(remainingHeightPx / bodyLineHeightPx).toInt()

                        if (maxLinesCanFit < 1 && currentPageElements.isNotEmpty()) {
                            commitPage()
                            continue
                        }

                        val sliceText = rawText.substring(remainingStart)
                        val charSequence = buildStyledSlice(
                            annotated = fullAnnotated,
                            start = remainingStart,
                            end = rawText.length,
                            indentPx = if (isFirstSlice) paragraphIndentPx else 0f,
                            italicTypeface = customItalicTypeface
                        )
                        val textLayout = buildStaticLayout(
                            text = charSequence,
                            paint = bodyPaint,
                            widthPx = availableWidthPx,
                            lineHeightPx = bodyLineHeightPx
                        )
                        val totalLines = textLayout.lineCount.coerceAtLeast(1)
                        val allowedLines = if (currentPageElements.isEmpty()) {
                            maxLinesCanFit.coerceAtLeast(1)
                        } else {
                            maxLinesCanFit
                        }

                        if (totalLines <= allowedLines) {
                            var subEnd = rawText.length
                            while (subEnd > remainingStart && rawText[subEnd - 1].isWhitespace()) {
                                subEnd--
                            }
                            if (subEnd > remainingStart) {
                                addElement(
                                    ChapterElement.Paragraph(
                                        text = fullAnnotated.subSequence(remainingStart, subEnd),
                                        isFirstLineIndented = isFirstSlice
                                    ),
                                    elementOffset + remainingStart
                                )
                                currentHeightPx += totalLines * bodyLineHeightPx
                            }
                            remainingStart = rawText.length
                        } else {
                            val lastFittingLineIndex = (allowedLines - 1).coerceIn(0, totalLines - 1)
                            var cutOffsetInSlice = textLayout.getLineEnd(lastFittingLineIndex)
                            if (cutOffsetInSlice <= 0) {
                                cutOffsetInSlice = sliceText.length
                            }

                            val cutPoint = (remainingStart + cutOffsetInSlice).coerceAtMost(rawText.length)
                            var subEnd = cutPoint
                            while (subEnd > remainingStart && rawText[subEnd - 1].isWhitespace()) {
                                subEnd--
                            }

                            if (subEnd > remainingStart) {
                                addElement(
                                    ChapterElement.Paragraph(
                                        text = fullAnnotated.subSequence(remainingStart, subEnd),
                                        isFirstLineIndented = isFirstSlice
                                    ),
                                    elementOffset + remainingStart
                                )
                            }

                            commitPage()
                            remainingStart = cutPoint
                            isFirstSlice = false
                        }
                    }
                }
            }
        }

        commitPage()
        if (pages.isEmpty()) return listOf(ChapterPage(emptyList(), 0, totalChars))
        return pages.mapIndexed { index, page ->
            page.copy(
                endCharOffset = pages.getOrNull(index + 1)?.startCharOffset ?: totalChars
            )
        }
    }

    private fun buildStyledSlice(
        annotated: AnnotatedString,
        start: Int,
        end: Int,
        indentPx: Float,
        italicTypeface: Typeface?,
        baseBold: Boolean = false
    ): CharSequence {
        val raw = annotated.text.substring(start, end)
        val spannable = SpannableString(raw)
        for (range in annotated.spanStyles) {
            if (range.end <= start || range.start >= end) continue
            val spanStart = (range.start - start).coerceIn(0, raw.length)
            val spanEnd = (range.end - start).coerceIn(0, raw.length)
            if (spanStart >= spanEnd) continue
            val style = range.item
            val isBold = baseBold || (style.fontWeight?.weight ?: 0) >= FontWeight.SemiBold.weight
            val isItalic = style.fontStyle == FontStyle.Italic
            if (isItalic && italicTypeface != null) {
                // Use the real bundled italic face so measured widths match Compose.
                val face = if (isBold) Typeface.create(italicTypeface, Typeface.BOLD) else italicTypeface
                spannable.setSpan(
                    TypefaceSpan(face),
                    spanStart,
                    spanEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                continue
            }
            val typefaceStyle = when {
                isBold && isItalic -> Typeface.BOLD_ITALIC
                isBold -> Typeface.BOLD
                isItalic -> Typeface.ITALIC
                else -> null
            } ?: continue
            spannable.setSpan(
                android.text.style.StyleSpan(typefaceStyle),
                spanStart,
                spanEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (indentPx > 0f) {
            spannable.setSpan(
                LeadingMarginSpan.Standard(indentPx.toInt(), 0),
                0,
                raw.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    private fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        lineHeightPx: Float
    ): StaticLayout {
        val metrics = paint.fontMetrics
        val defaultLineHeight = metrics.descent - metrics.ascent + metrics.leading
        val extraLineSpacing = (lineHeightPx - defaultLineHeight).coerceAtLeast(0f)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(extraLineSpacing, 1f)
            .build()
    }
}
