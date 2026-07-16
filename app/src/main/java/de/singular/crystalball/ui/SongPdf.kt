package de.singular.crystalball.ui

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import de.singular.crystalball.Capo
import de.singular.crystalball.NameStyle
import de.singular.crystalball.songs.Song
import de.singular.crystalball.songs.SongChord
import java.io.OutputStream
import kotlin.math.min

/**
 * A song as a chord sheet you can print.
 *
 * The page is the [SongScreen] song view on paper, deliberately: same order, a diagram under every
 * chord rather than a legend of shapes, the comment at the foot. What you looked at is what comes
 * out, which is only true because both draw with [drawChordBox] — there is one chord-box renderer
 * in this app, and the PDF is a second surface for it rather than a second copy of it.
 *
 * No dependency: [PdfDocument] is in the platform. The bridge is [CanvasDrawScope], which will run
 * a `DrawScope` block against any canvas — including a PDF page's — so the painter neither knows
 * nor cares that it is drawing for print.
 */
object SongPdf {

    /**
     * Write [song] to [out] as a PDF. Does file I/O; call it off the main thread.
     *
     * [nameStyle] comes from the live setting because it is a preference about reading, not a fact
     * about the song — the sheet should name chords the way its reader wants them named.
     */
    fun write(context: Context, song: Song, nameStyle: NameStyle, out: OutputStream) {
        val doc = PdfDocument()
        val density = Density(1f)
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(context),
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        val writer = PageWriter(doc, density, measurer)
        try {
            writer.startPage()
            writer.text(song.title.ifBlank { "Song" }, TITLE)
            writer.gap(2f)
            writer.text(if (song.capo == 0) "No capo" else "Capo ${song.capo}", SUBTITLE)
            writer.gap(18f)

            song.parts.forEach { part ->
                // Never leave a part's name stranded at the foot of a page with its chords overleaf.
                writer.reserve(PART_NAME_HEIGHT + CELL_HEIGHT)
                writer.text(part.name, HEADING)
                writer.gap(6f)
                writer.chords(part.chords, song.capo, nameStyle)
                writer.gap(14f)
            }

            if (song.comment.isNotBlank()) {
                writer.reserve(PART_NAME_HEIGHT + 20f)
                writer.text("Comment", HEADING)
                writer.gap(4f)
                writer.text(song.comment, BODY)
            }
            writer.endPage()
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** A cursor down a page, starting another when it runs out of room. */
    private class PageWriter(
        private val doc: PdfDocument,
        private val density: Density,
        private val measurer: TextMeasurer,
    ) {
        private var page: PdfDocument.Page? = null
        private var number = 0
        private var y = MARGIN

        private val contentWidth = PAGE_WIDTH - MARGIN * 2

        fun startPage() {
            endPage()
            number++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), number).create(),
            )
            y = MARGIN
        }

        fun endPage() {
            page?.let { doc.finishPage(it) }
            page = null
        }

        fun gap(height: Float) {
            y += height
        }

        /** Break the page if [height] would not fit below the cursor. */
        fun reserve(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN) startPage()
        }

        fun text(value: String, style: TextStyle) {
            val layout = measure(value, style, contentWidth)
            reserve(layout.size.height.toFloat())
            draw(MARGIN, y, contentWidth, layout.size.height.toFloat()) {
                drawText(layout, topLeft = Offset.Zero)
            }
            y += layout.size.height
        }

        /** A part's chords, wrapping across as many rows as it takes. */
        fun chords(chords: List<SongChord>, capo: Int, nameStyle: NameStyle) {
            val perRow = ((contentWidth + CELL_GAP) / (DIAGRAM_WIDTH + CELL_GAP)).toInt().coerceAtLeast(1)
            var index = 0
            while (index < chords.size) {
                val row = chords.subList(index, min(index + perRow, chords.size))
                reserve(CELL_HEIGHT)
                row.forEachIndexed { column, chord ->
                    cell(MARGIN + column * (DIAGRAM_WIDTH + CELL_GAP), y, chord, capo, nameStyle)
                }
                y += CELL_HEIGHT
                index += row.size
            }
        }

        /** One chord: its name, its shape, and where on the neck to play it. */
        private fun cell(x: Float, top: Float, chord: SongChord, capo: Int, nameStyle: NameStyle) {
            val name = measure(
                Capo.shortName(chord.sounding, capo, nameStyle),
                CHORD_NAME,
                DIAGRAM_WIDTH,
            )
            draw(x, top, DIAGRAM_WIDTH, name.size.height.toFloat()) {
                drawText(name, topLeft = Offset.Zero)
            }

            val boxTop = top + PART_NAME_HEIGHT
            val boxHeight = DIAGRAM_WIDTH * BOX_ASPECT
            draw(x, boxTop, DIAGRAM_WIDTH, boxHeight) {
                drawChordBox(
                    voicing = chord.voicing,
                    capo = capo,
                    lineColor = INK,
                    dotColor = INK,
                    markerColor = INK,
                    // On paper the capo is in the heading, and a nut that is merely a different
                    // shade of black says nothing. The heavy bar already marks it.
                    capoColor = INK,
                    measurer = measurer,
                    labelStyle = TextStyle(
                        color = INK,
                        fontSize = (DIAGRAM_WIDTH * LABEL_TEXT_RATIO).sp,
                    ),
                )
            }

            val caption = measure(chord.voicing.label, CAPTION, DIAGRAM_WIDTH)
            draw(x, boxTop + boxHeight + 3f, DIAGRAM_WIDTH, caption.size.height.toFloat()) {
                drawText(caption, topLeft = Offset.Zero)
            }
        }

        private fun measure(value: String, style: TextStyle, maxWidth: Float): TextLayoutResult =
            measurer.measure(
                text = value,
                style = style,
                constraints = Constraints(maxWidth = maxWidth.toInt()),
            )

        /**
         * Run [block] with the origin at [x], [y] and the drawing size set to [width] × [height].
         *
         * The translate is what makes [drawChordBox] usable here unchanged: it lays itself out from
         * `size`, so each box is given a scope exactly its own size, wherever it sits on the page.
         */
        private fun draw(x: Float, y: Float, width: Float, height: Float, block: DrawScope.() -> Unit) {
            val canvas = Canvas(page!!.canvas)
            canvas.save()
            canvas.translate(x, y)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(width, height), block)
            canvas.restore()
        }
    }

    /** A4 at 72 points to the inch, which is what [PdfDocument] measures in. */
    private const val PAGE_WIDTH = 595f
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN = 40f

    private const val DIAGRAM_WIDTH = 62f
    private const val CELL_GAP = 10f
    private const val PART_NAME_HEIGHT = 13f
    private const val CAPTION_HEIGHT = 18f
    private const val CELL_HEIGHT =
        PART_NAME_HEIGHT + DIAGRAM_WIDTH * BOX_ASPECT + 3f + CAPTION_HEIGHT

    /** Print is black on white; the screen's palette has no meaning on paper. */
    private val INK = Color(0xFF000000)
    private val MUTED_INK = Color(0xFF555555)

    private val TITLE = TextStyle(color = INK, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    private val SUBTITLE = TextStyle(color = MUTED_INK, fontSize = 11.sp)
    private val HEADING = TextStyle(color = INK, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    private val BODY = TextStyle(color = INK, fontSize = 10.sp)
    private val CHORD_NAME =
        TextStyle(color = INK, fontSize = 9.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    private val CAPTION = TextStyle(color = MUTED_INK, fontSize = 6.5.sp, textAlign = TextAlign.Center)
}
