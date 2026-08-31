package dev.structlayout

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.ide.model.LayoutResult
import com.jetbrains.rd.ide.model.LayoutRow
import com.jetbrains.rd.ide.model.LayoutRowKind
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.TexturePaint
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ToolTipManager

class StructLayoutPanel : JPanel(BorderLayout()) {

    private val header = JBLabel().apply { border = JBUI.Borders.empty(4, 8) }
    private val grid = LayoutGrid()

    private val alignmentChoice = ComboBox(arrayOf("Struct", "1", "2", "4", "8", "16", "32")).apply {
        toolTipText = "Bytes per row: the struct's own alignment, or a fixed width"
        addActionListener { grid.rowWidthChoice = selectedItem as String }
    }

    private val offsetBaseChoice = ComboBox(arrayOf("Hex", "Decimal")).apply {
        toolTipText = "Offset display base"
        addActionListener { grid.hexOffsets = selectedItem == "Hex" }
    }

    init {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            add(JBLabel("Display alignment:"))
            add(alignmentChoice)
            add(JBLabel("Offsets:"))
            add(offsetBaseChoice)
            add(JButton("Expand All").apply { addActionListener { grid.setAllExpanded(true) } })
            add(JButton("Collapse All").apply { addActionListener { grid.setAllExpanded(false) } })
        }

        val top = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(header, BorderLayout.SOUTH)
        }

        add(top, BorderLayout.NORTH)
        add(JBScrollPane(grid).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        showEmpty()
    }

    fun showMessage(message: String) {
        header.text = message
        grid.setLayoutResult(null)
    }

    fun showEmpty() = showMessage("Put the caret on a class, struct or union and invoke Show Struct Layout.")

    fun show(layout: LayoutResult) {
        val warning = if (layout.interrupted) "   [!] walk interrupted, rows are partial" else ""
        header.text = "${layout.typeName}   -   size ${layout.totalSize} B, align ${layout.totalAlignment} B, " +
                "padding ${layout.totalPadding} B$warning"
        grid.setLayoutResult(layout)
    }
}

// A byte matrix: one grid row per `rowWidth` bytes, offset in the left gutter, byte index across the top,
// each member a box spanning the bytes it actually occupies. Empty space stays grey, so padding shows as
// a hole rather than as a member.
//
// Two layout rules carry meaning:
//  - depth insets a box on all sides, so an enclosing aggregate's border stays visible around its children
//  - members sharing bytes (unions, bitfields in one byte) are stacked into lanes inside the row
private class LayoutGrid : JPanel() {

    private var layout: LayoutResult? = null
    private var painted: List<Painted> = emptyList()

    var rowWidthChoice: String = "Struct"
        set(value) { field = value; rebuild() }

    private val openState = mutableMapOf<String, Boolean>()
    private var expandAll: Boolean? = null

    var hexOffsets: Boolean = true
        set(value) { field = value; repaint() }

    init {
        ToolTipManager.sharedInstance().registerComponent(this)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val hit = painted.lastOrNull { it.rect.contains(e.point) && it.key != null } ?: return
                toggle(hit.key!!, hit.open)
            }
        })
    }

    fun setLayoutResult(layout: LayoutResult?) {
        this.layout = layout
        painted = emptyList()
        rebuild()
    }

    private fun rebuild() {
        val current = layout
        if (current == null || current.totalSize <= 0) {
            preferredSize = Dimension(JBUI.scale(400), JBUI.scale(40))
        } else {
            val cols = rowWidth(current)
            val rows = (current.totalSize + cols - 1) / cols
            preferredSize = Dimension(
                JBUI.scale(GUTTER_W) + cols * JBUI.scale(CELL_W) + JBUI.scale(2 * PAD),
                JBUI.scale(HEADER_H + PAD) + rows * JBUI.scale(CELL_H) + JBUI.scale(PAD))
        }
        revalidate()
        repaint()
    }

    private fun rowWidth(layout: LayoutResult): Int =
        if (rowWidthChoice == "Struct") layout.totalAlignment.coerceIn(1, 64)
        else rowWidthChoice.toIntOrNull() ?: 8

    override fun getToolTipText(event: MouseEvent): String? =
        // painted last is on top (children after parents), so the innermost box under the cursor wins
        painted.lastOrNull { it.rect.contains(event.point) }?.tooltip

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val current = layout ?: return
        if (current.totalSize <= 0 || current.rows.isEmpty()) return

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.font = UIUtil.getLabelFont()
        val fm = g2.fontMetrics

        val cols = rowWidth(current)
        val cellW = ((width - JBUI.scale(GUTTER_W + 2 * PAD)) / cols).coerceAtLeast(JBUI.scale(12))
        val originX = JBUI.scale(GUTTER_W)
        val originY = JBUI.scale(HEADER_H + PAD)
        val rows = (current.totalSize + cols - 1) / cols

        val maxDepth = current.rows.maxOfOrNull { it.depth } ?: 0
        val maxLanes = maxLaneCount(current, cols, rows)
        val cellH = cellHeight(maxDepth, fm.height, maxLanes)

        val wanted = JBUI.scale(HEADER_H + PAD) + rows * cellH + JBUI.scale(PAD)
        if (preferredSize.height != wanted) {
            preferredSize = Dimension(preferredSize.width, wanted)
            revalidate()
        }

        drawColumnHeader(g2, fm, cols, cellW, originX)
        drawCells(g2, fm, current, cols, rows, cellW, cellH, originX, originY)

        val collected = mutableListOf<Painted>()
        val model = buildModel(current)
        val visible = model.entries

        // The struct's own extent, drawn tight to the cell edges. Without it, choosing a display
        // alignment wider than the struct's makes trailing padding indistinguishable from the bytes
        // past the end of the object.
        drawStructBox(g2, fm, current, cols, rows, cellW, cellH, originX, originY, collected)
        drawPadding(g2, current, model.padding, cols, rows, cellW, cellH, originX, originY, collected)

        // groups first and at full cell height: their border must remain visible around inset children
        visible.filter { it.isGroup }.forEach { entry ->
            val inset = insetFor(entry.depth)
            val pieces = mutableListOf<Triple<Int, Int, Int>>()
            forEachSegment(entry, cols, rows, current.totalSize) { row, startCol, byteCount ->
                pieces.add(Triple(row, startCol, byteCount))
            }
            val occupied = pieces.map { it.first }.toSet()

            // Same join rule as members: drop the inset where the group continues onto the next row, so
            // a base spanning the whole type is one frame rather than a frame per row.
            val rects = pieces.map { (row, startCol, byteCount) ->
                val top = if ((row - 1) in occupied) 0 else inset
                val bottom = if ((row + 1) in occupied) 0 else inset
                Rectangle(originX + startCol * cellW + inset, originY + row * cellH + top,
                    byteCount * cellW - 2 * inset, cellH - top - bottom)
            }

            g2.color = groupBorder()
            g2.draw(merged(rects))
            rects.forEach { collected.add(Painted(it, tooltip(entry), entry.key, true)) }
        }

        val segments = laneSegments(visible.filter { !it.isGroup }, cols, rows, current.totalSize)
        segments.groupBy { it.entry }.forEach { (entry, segs) ->
            collected.addAll(drawMember(g2, fm, entry, segs, cellW, cellH, originX, originY))
        }

        painted = collected
    }

    // deepest stack of byte-sharing members, so a union row also gets the height it needs
    private fun maxLaneCount(layout: LayoutResult, cols: Int, rows: Int): Int {
        val entries = visibleEntries(layout).filter { !it.isGroup }
        return laneSegments(entries, cols, rows, layout.totalSize).maxOfOrNull { it.laneCount } ?: 1
    }

    private fun merged(rects: List<Rectangle>): java.awt.geom.Area {
        val area = java.awt.geom.Area()
        rects.forEach { area.add(java.awt.geom.Area(it)) }
        return area
    }


    // Adjacent segments of the SAME element are merged into one shape, so a member that wraps to the
    // next row (or spans many cells) is filled and outlined once instead of per row.
    private fun drawMember(g2: Graphics2D, fm: FontMetrics, entry: Entry, segs: List<Segment>,
                           cellW: Int, cellH: Int, originX: Int, originY: Int): List<Painted> {
        val occupiedRows = segs.map { it.row }.toSet()

        val rects = segs.map { seg ->
            val inset = insetFor(entry.depth)
            val gap = JBUI.scale(2)
            val available = cellH - 2 * inset - (seg.laneCount - 1) * gap
            val laneH = (available / seg.laneCount).coerceAtLeast(JBUI.scale(6))

            // A member wider than a row wraps. Dropping the inset on the edge where it continues makes
            // the two segments share an edge, so the Area union below merges them into ONE outlined
            // shape instead of two boxes that look like unrelated members.
            val joinsAbove = seg.laneCount == 1 && (seg.row - 1) in occupiedRows
            val joinsBelow = seg.laneCount == 1 && (seg.row + 1) in occupiedRows

            val top = if (joinsAbove) 0 else inset + seg.lane * (laneH + gap)
            val bottom = if (joinsBelow) 0 else inset
            val height = if (seg.laneCount == 1) cellH - top - bottom else laneH

            Rectangle(originX + seg.startCol * cellW + inset,
                originY + seg.row * cellH + top,
                seg.byteCount * cellW - 2 * inset,
                height)
        }

        val area = merged(rects)
        g2.color = colorFor(entry.kind)
        g2.fill(area)
        g2.color = if (entry.unknownSize) unknownBorder() else boxBorder()
        g2.draw(area)

        // label once, in the largest segment: the merged outline already shows the pieces are one member
        val text = labelFor(entry)
        val target = rects.maxByOrNull { it.width.toLong() * it.height }
            ?: return rects.map { Painted(it, tooltip(entry), entry.key, entry.isGroup) }

        val small = if (target.height >= fm.height) null else g2.font.deriveFont(
            (g2.font.size2D * target.height / fm.height.toFloat()).coerceAtLeast(8f))
        val metrics = if (small == null) fm else g2.getFontMetrics(small)

        if (metrics.stringWidth(text) + JBUI.scale(6) <= target.width) {
            val oldFont = g2.font
            if (small != null) g2.font = small
            g2.color = boxText()
            g2.drawString(text, target.x + (target.width - metrics.stringWidth(text)) / 2,
                target.y + (target.height + metrics.ascent) / 2 - JBUI.scale(1))
            g2.font = oldFont
        }

        val tip = tooltip(entry)
        return rects.map { Painted(it, tip, if (entry.hasChildren) entry.key else null, entry.isGroup) }
    }

    private fun drawStructBox(g2: Graphics2D, fm: FontMetrics, layout: LayoutResult, cols: Int, rows: Int,
                              cellW: Int, cellH: Int, originX: Int, originY: Int,
                              collected: MutableList<Painted>) {
        val inset = JBUI.scale(1)
        val tip = "<html><b>${layout.typeName}</b><br>size: ${layout.totalSize} B" +
                "<br>alignment: ${layout.totalAlignment} B<br>padding: ${layout.totalPadding} B</html>"

        val rects = mutableListOf<Rectangle>()
        for (row in 0 until rows) {
            val rowStart = row * cols
            val byteCount = minOf(cols, layout.totalSize - rowStart)
            if (byteCount <= 0) continue
            rects.add(Rectangle(originX + inset, originY + row * cellH + inset,
                byteCount * cellW - 2 * inset, cellH - 2 * inset))
        }

        g2.color = structBorder()
        g2.draw(merged(rects))
        rects.forEach { collected.add(Painted(it, tip)) }
    }

    // Padding is not a member, so it gets no box - it is hatched empty space that still answers a hover.
    private fun drawPadding(g2: Graphics2D, layout: LayoutResult, paddingRows: List<LayoutRow>,
                            cols: Int, rows: Int,
                            cellW: Int, cellH: Int, originX: Int, originY: Int,
                            collected: MutableList<Painted>) {
        paddingRows.forEach { row ->
                val entry = Entry(row.offset!!, row.size, row.depth, row.kind, "", false, row)
                val tip = "<html><b>padding</b><br>${row.size} B at 0x%X (%d)".format(row.offset, row.offset) +
                        "<br>inserted to satisfy alignment</html>"

                val rects = mutableListOf<Rectangle>()
                forEachSegment(entry, cols, rows, layout.totalSize) { r, startCol, byteCount ->
                    val inset = JBUI.scale(4)
                    rects.add(Rectangle(originX + startCol * cellW + inset, originY + r * cellH + inset,
                        byteCount * cellW - 2 * inset, cellH - 2 * inset))
                }

                // A TexturePaint keeps the hatch even and continuous; hand-drawn diagonals bend at the
                // clamped edges and change density with the segment width.
                val old = g2.paint
                g2.paint = hatchPaint()
                g2.fill(merged(rects))
                g2.paint = old

                rects.forEach { collected.add(Painted(it, tip)) }
            }
    }

    private inline fun forEachSegment(entry: Entry, cols: Int, rows: Int, total: Int,
                                      action: (row: Int, startCol: Int, byteCount: Int) -> Unit) {
        val end = (entry.offset + entry.size).coerceAtMost(total)
        var byte = entry.offset
        while (byte < end) {
            val row = byte / cols
            val take = minOf(cols - byte % cols, end - byte)
            if (row in 0 until rows) action(row, byte % cols, take)
            byte += take
        }
    }

    // Lanes are local to a CLUSTER of byte-overlapping members, not to the whole grid row: a union at
    // bytes 4..7 must not halve an unrelated member sitting at bytes 0..3 in the same row.
    private fun laneSegments(entries: List<Entry>, cols: Int, rows: Int, total: Int): List<Segment> {
        val perRow = Array(rows) { mutableListOf<Pair<Entry, IntRange>>() }
        for (entry in entries) {
            forEachSegment(entry, cols, rows, total) { row, startCol, byteCount ->
                val first = row * cols + startCol
                perRow[row].add(entry to (first until first + byteCount))
            }
        }

        fun clashes(taken: List<IntRange>, range: IntRange) =
            taken.any { it.first <= range.last && range.first <= it.last }

        val out = mutableListOf<Segment>()
        val laneOfEntry = mutableMapOf<Entry, Int>()

        for (row in 0 until rows) {
            val segs = perRow[row].sortedBy { it.second.first }
            var i = 0
            while (i < segs.size) {
                var end = segs[i].second.last
                var j = i + 1
                while (j < segs.size && segs[j].second.first <= end) {
                    end = maxOf(end, segs[j].second.last)
                    j++
                }
                val cluster = segs.subList(i, j)

                val taken = mutableListOf<MutableList<IntRange>>()
                val laneOfIndex = IntArray(cluster.size)

                cluster.forEachIndexed { index, (entry, range) ->
                    // keep an entry in the same lane across rows when that lane is free, so a wide
                    // member stays a straight band instead of zig-zagging
                    val preferred = laneOfEntry[entry]
                    val lane = if (preferred != null && (preferred >= taken.size || !clashes(taken[preferred], range)))
                        preferred
                    else
                        (0..taken.size).first { it >= taken.size || !clashes(taken[it], range) }

                    while (taken.size <= lane) taken.add(mutableListOf())
                    taken[lane].add(range)
                    laneOfIndex[index] = lane
                    laneOfEntry[entry] = lane
                }

                val laneCount = taken.size.coerceAtLeast(1)
                cluster.forEachIndexed { index, (entry, range) ->
                    out.add(Segment(entry, row, range.first % cols, range.count(), laneOfIndex[index], laneCount))
                }
                i = j
            }
        }

        return out
    }

    private fun drawColumnHeader(g2: Graphics2D, fm: FontMetrics, cols: Int, cellW: Int, originX: Int) {
        g2.color = UIUtil.getInactiveTextColor()
        for (col in 0 until cols) {
            val text = col.toString()
            g2.drawString(text, originX + col * cellW + (cellW - fm.stringWidth(text)) / 2,
                JBUI.scale(HEADER_H) - JBUI.scale(4))
        }
    }

    private fun drawCells(g2: Graphics2D, fm: FontMetrics, layout: LayoutResult, cols: Int, rows: Int,
                          cellW: Int, cellH: Int, originX: Int, originY: Int) {
        for (row in 0 until rows) {
            val y = originY + row * cellH
            val rowStart = row * cols

            val label = if (hexOffsets) "0x%X".format(rowStart) else rowStart.toString()
            g2.color = UIUtil.getInactiveTextColor()
            g2.drawString(label, originX - fm.stringWidth(label) - JBUI.scale(8), y + (cellH + fm.ascent) / 2)

            for (col in 0 until cols) {
                val byte = rowStart + col
                g2.color = if (byte < layout.totalSize) emptyCell() else outsideCell()
                g2.fillRect(originX + col * cellW, y, cellW, cellH)
                g2.color = gridLine()
                g2.drawRect(originX + col * cellW, y, cellW, cellH)
            }
        }
    }

    // Per-element open/closed state, keyed by depth+offset+name so it survives a re-layout.
    // Bases and anonymous aggregates open by default; a member whose TYPE is a struct starts closed and
    // opens on double-click, which is what keeps a deep Unreal type readable.
    private fun keyOf(row: LayoutRow) = "${row.depth}:${row.offset}:${row.name}"

    private class Model(val entries: List<Entry>, val padding: List<LayoutRow>)

    private fun buildModel(layout: LayoutResult): Model {
        val rows = layout.rows
        val parents = IntArray(rows.size) { -1 }
        val stack = ArrayList<Int>()

        rows.forEachIndexed { i, row ->
            while (stack.isNotEmpty() && rows[stack[stack.size - 1]].depth >= row.depth) stack.removeAt(stack.size - 1)
            parents[i] = if (stack.isEmpty()) -1 else stack[stack.size - 1]
            stack.add(i)
        }

        fun hasChildren(i: Int) = i + 1 < rows.size && rows[i + 1].depth > rows[i].depth

        fun autoOpen(i: Int) = when (rows[i].kind) {
            LayoutRowKind.Base, LayoutRowKind.VirtualBase, LayoutRowKind.AnonymousAggregate -> true
            else -> false
        }

        fun isOpen(i: Int) =
            hasChildren(i) && (openState[keyOf(rows[i])] ?: (expandAll ?: autoOpen(i)))

        fun ancestorsOpen(i: Int): Boolean {
            var p = parents[i]
            while (p != -1) {
                if (!isOpen(p)) return false
                p = parents[p]
            }
            return true
        }

        val entries = mutableListOf<Entry>()
        val padding = mutableListOf<LayoutRow>()

        rows.forEachIndexed { i, row ->
            if (!ancestorsOpen(i)) return@forEachIndexed

            if (row.kind == LayoutRowKind.Padding) {
                if (row.offset != null && row.size > 0) padding.add(row)
                return@forEachIndexed
            }

            val offset = row.offset ?: return@forEachIndexed
            val unknownSize = row.bitWidth == null && row.size <= 0
            val size = when {
                row.bitWidth != null -> 1
                unknownSize -> 1
                else -> row.size
            }

            entries.add(Entry(offset, size, row.depth, row.kind, label(row), isOpen(i), row,
                unknownSize, hasChildren(i), keyOf(row)))
        }

        return Model(entries, padding)
    }

    private fun visibleEntries(layout: LayoutResult): List<Entry> = buildModel(layout).entries

    fun setAllExpanded(open: Boolean) {
        expandAll = open
        openState.clear()
        rebuild()
    }

    private fun toggle(key: String, currentlyOpen: Boolean) {
        openState[key] = !currentlyOpen
        rebuild()
    }

    private fun labelFor(entry: Entry): String {
        val marker = if (entry.hasChildren && !entry.isGroup) "> " else ""
        return if (entry.unknownSize) "$marker${entry.label}  [size ?]" else "$marker${entry.label}"
    }

    private fun label(row: LayoutRow): String = when {
        row.bitWidth != null -> "${row.name} : ${row.bitWidth}"
        row.kind == LayoutRowKind.VfPtr -> "VFTablePtr"
        row.kind == LayoutRowKind.VbPtr -> "VBTablePtr"
        else -> row.name
    }

    private fun tooltip(entry: Entry): String {
        val row = entry.source
        val kind = when (row.kind) {
            LayoutRowKind.Base -> "base class"
            LayoutRowKind.VirtualBase -> "virtual base class"
            LayoutRowKind.Field -> "member"
            LayoutRowKind.BitField -> "bitfield"
            LayoutRowKind.AnonymousAggregate -> "anonymous aggregate"
            LayoutRowKind.VfPtr -> "virtual function table pointer"
            LayoutRowKind.VbPtr -> "virtual base table pointer"
            LayoutRowKind.Padding -> "padding"
        }

        val lines = mutableListOf<String>()
        lines.add("<b>${row.name}</b>&nbsp;&nbsp;<i>$kind</i>")
        lines.add("offset: 0x%X (%d)".format(entry.offset, entry.offset))
        if (row.bitWidth != null) {
            lines.add("bits: ${row.bitWidth} starting at bit ${row.bitOffsetInByte} of that byte")
        } else {
            lines.add("size: ${row.size} B")
            if (row.alignment > 0) lines.add("alignment: ${row.alignment} B")
            lines.add("occupies bytes 0x%X..0x%X".format(entry.offset, entry.offset + entry.size - 1))
        }
        if (row.depth > 0) lines.add("nesting depth: ${row.depth}")
        if (entry.hasChildren) lines.add(
            if (entry.isGroup) "<i>double-click to collapse</i>" else "<i>double-click to expand its members</i>")
        if (entry.unknownSize) lines.add(
            "<b>size unavailable</b> - the C++ engine could not compute it (template-parented or " +
                    "incomplete type). Shown as one byte so the member is not hidden.")

        return "<html>" + lines.joinToString("<br>") + "</html>"
    }

    private fun insetFor(depth: Int) = depth * JBUI.scale(2)

    // Row height follows the nesting depth: with a fixed height, every extra level ate the innermost
    // box until it was a few pixels tall and its label had to be dropped. This keeps the deepest box at
    // least one line of text high, whatever the depth.
    private fun cellHeight(maxDepth: Int, fontHeight: Int, maxLanes: Int) =
        maxOf(JBUI.scale(CELL_H),
              maxLanes * (fontHeight + JBUI.scale(4)) + 2 * insetFor(maxDepth) + JBUI.scale(4))

    private class Entry(val offset: Int, val size: Int, val depth: Int, val kind: LayoutRowKind,
                        val label: String, val isGroup: Boolean, val source: LayoutRow,
                        val unknownSize: Boolean = false, val hasChildren: Boolean = false,
                        val key: String? = null)

    private class Segment(val entry: Entry, val row: Int, val startCol: Int, val byteCount: Int,
                          val lane: Int, val laneCount: Int)

    private class Painted(val rect: Rectangle, val tooltip: String,
                          val key: String? = null, val open: Boolean = false)

    private fun colorFor(kind: LayoutRowKind): Color = when (kind) {
        LayoutRowKind.VfPtr, LayoutRowKind.VbPtr -> JBColor(Color(0x0E, 0x7A, 0x3C), Color(0x0E, 0x7A, 0x3C))
        LayoutRowKind.Base, LayoutRowKind.VirtualBase,
        LayoutRowKind.AnonymousAggregate -> JBColor(Color(0x2E, 0x8B, 0x7D), Color(0x2E, 0x8B, 0x7D))
        LayoutRowKind.BitField -> JBColor(Color(0xC9, 0x8A, 0x0B), Color(0xC9, 0x8A, 0x0B))
        LayoutRowKind.Field -> JBColor(Color(0xB8, 0x86, 0x0B), Color(0xB8, 0x86, 0x0B))
        LayoutRowKind.Padding -> emptyCell()
    }

    // one sparse 45-degree stroke per tile, anchored at the origin so the pattern is continuous
    private var hatchCache: TexturePaint? = null

    private fun hatchPaint(): TexturePaint {
        hatchCache?.let { return it }

        val size = JBUI.scale(10)
        val tile = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val tg = tile.createGraphics()
        tg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        tg.color = paddingHatch()
        tg.drawLine(0, size, size, 0)
        tg.drawLine(-1, 1, 1, -1)
        tg.drawLine(size - 1, size + 1, size + 1, size - 1)
        tg.dispose()

        return TexturePaint(tile, Rectangle(0, 0, size, size)).also { hatchCache = it }
    }

    private fun structBorder() = JBColor(Color(0x30, 0x30, 0x30), Color(0xE0, 0xE0, 0xE0))
    private fun paddingHatch() = JBColor(Color(0x00, 0x00, 0x00, 0x55), Color(0xFF, 0xFF, 0xFF, 0x38))
    private fun emptyCell() = JBColor(Color(0x9A, 0x9A, 0x9A), Color(0x54, 0x54, 0x54))
    private fun outsideCell() = JBColor(Color(0xE8, 0xE8, 0xE8), Color(0x2B, 0x2B, 0x2B))
    private fun gridLine() = JBColor(Color(0x70, 0x70, 0x70), Color(0x3C, 0x3C, 0x3C))
    private fun boxBorder() = JBColor(Color.WHITE, Color.WHITE)
    private fun unknownBorder() = JBColor(Color(0xD9, 0x53, 0x4F), Color(0xE5, 0x73, 0x73))
    private fun boxText() = JBColor(Color.WHITE, Color.WHITE)
    private fun groupBorder() = JBColor(Color(0x7B, 0x1F, 0xA2), Color(0x8E, 0x24, 0xAA))

    companion object {
        private const val CELL_W = 56
        private const val CELL_H = 44
        private const val GUTTER_W = 52
        private const val HEADER_H = 22
        private const val PAD = 6
    }
}
