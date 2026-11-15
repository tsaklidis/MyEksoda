// Port of CPython 3.13's difflib.SequenceMatcher, the parts ratio() uses.
package io.eksoda.engine.text

// The matching guard (backend/apps/products/matching.py, `_reused_code`) compares descriptions
// with `SequenceMatcher(None, a, b).ratio()`. Similar-looking libraries give different ratios,
// so this follows CPython's algorithm step by step (docs/android-standalone.md §9.3), including
// the autojunk heuristic: when b has 200 or more elements, elements that make up more than 1% of
// b (plus one) are "popular" and are not used to start a match.
//
// Python compares strings element by element on code points, so both strings are split into
// code points first. `isjunk` is always None here, so there is no junk set besides the popular
// elements.

/** A matching block: `a[a until a+size] == b[b until b+size]`. */
public data class Match(val a: Int, val b: Int, val size: Int)

/** `difflib.SequenceMatcher(None, a, b, autojunk=True)`. */
public class SequenceMatcher(a: String, b: String) {
    private val a: IntArray = a.codePoints().toArray()
    private val b: IntArray = b.codePoints().toArray()

    /** `b2j`: element → its indices in b, ascending, without the popular elements. */
    private val b2j: Map<Int, IntArray> = chainB()

    private fun chainB(): Map<Int, IntArray> {
        val indices = LinkedHashMap<Int, MutableList<Int>>()
        b.forEachIndexed { index, element ->
            indices.getOrPut(element) { mutableListOf() } += index
        }
        if (b.size >= AUTOJUNK_MIN_SIZE) {
            val ntest = b.size / AUTOJUNK_PERCENT + 1
            indices.entries.removeAll { (_, positions) -> positions.size > ntest }
        }
        return indices.mapValues { (_, positions) -> positions.toIntArray() }
    }

    /**
     * `find_longest_match(alo, ahi, blo, bhi)`: the longest matching block in a[alo, ahi) and
     * b[blo, bhi), the earliest in a and then in b among equally long ones, extended over equal
     * neighbors (popular elements may extend a match but not start one).
     */
    public fun findLongestMatch(alo: Int, ahi: Int, blo: Int, bhi: Int): Match {
        var bestI = alo
        var bestJ = blo
        var bestSize = 0
        var j2len: Map<Int, Int> = emptyMap()
        for (i in alo until ahi) {
            val newJ2len = HashMap<Int, Int>()
            for (j in b2j[a[i]] ?: EMPTY) {
                // Indices ascend. Python's `continue` for j < blo is the `if` below.
                if (j >= bhi) break
                if (j >= blo) {
                    val k = (j2len[j - 1] ?: 0) + 1
                    newJ2len[j] = k
                    if (k > bestSize) {
                        bestI = i - k + 1
                        bestJ = j - k + 1
                        bestSize = k
                    }
                }
            }
            j2len = newJ2len
        }
        return extend(Match(bestI, bestJ, bestSize), alo, ahi, blo, bhi)
    }

    /**
     * The extension loops at the end of `find_longest_match`. There is no junk (isjunk is None),
     * so only the "not junk" extensions apply.
     */
    private fun extend(match: Match, alo: Int, ahi: Int, blo: Int, bhi: Int): Match {
        var (bestI, bestJ, bestSize) = match
        while (bestI > alo && bestJ > blo && a[bestI - 1] == b[bestJ - 1]) {
            bestI -= 1
            bestJ -= 1
            bestSize += 1
        }
        while (bestI + bestSize < ahi && bestJ + bestSize < bhi &&
            a[bestI + bestSize] == b[bestJ + bestSize]
        ) {
            bestSize += 1
        }
        return Match(bestI, bestJ, bestSize)
    }

    /** `get_matching_blocks()`: non-adjacent blocks in order, ending with (len(a), len(b), 0). */
    public fun matchingBlocks(): List<Match> {
        val queue = ArrayDeque(listOf(Span(0, a.size, 0, b.size)))
        val blocks = mutableListOf<Match>()
        while (queue.isNotEmpty()) {
            val span = queue.removeLast()
            val alo = span.alo
            val blo = span.blo
            val match = findLongestMatch(alo, span.ahi, blo, span.bhi)
            val (i, j, k) = match
            if (k > 0) {
                blocks += match
                if (alo < i && blo < j) queue.addLast(Span(alo, i, blo, j))
                if (i + k < span.ahi &&
                    j + k < span.bhi
                ) {
                    queue.addLast(Span(i + k, span.ahi, j + k, span.bhi))
                }
            }
        }
        blocks.sortWith(compareBy<Match>({ it.a }, { it.b }, { it.size }))
        val nonAdjacent = mutableListOf<Match>()
        var current = Match(0, 0, 0)
        for (block in blocks) {
            if (current.a + current.size == block.a && current.b + current.size == block.b) {
                current = current.copy(size = current.size + block.size)
            } else {
                if (current.size > 0) nonAdjacent += current
                current = block
            }
        }
        if (current.size > 0) nonAdjacent += current
        nonAdjacent += Match(a.size, b.size, 0)
        return nonAdjacent
    }

    /**
     * `ratio()`: 2·M / T, where M is the number of matched elements and T the total length; 1.0
     * for two empty strings.
     */
    public fun ratio(): Double {
        val matches = matchingBlocks().sumOf { it.size }
        val length = a.size + b.size
        return if (length == 0) 1.0 else 2.0 * matches / length
    }

    /** A pair of ranges a[alo, ahi) and b[blo, bhi) still to search. */
    private data class Span(val alo: Int, val ahi: Int, val blo: Int, val bhi: Int)

    private companion object {
        const val AUTOJUNK_MIN_SIZE = 200
        const val AUTOJUNK_PERCENT = 100
        val EMPTY = IntArray(0)
    }
}
