package uk.nktnet.webviewkiosk.utils.webview

import java.util.ArrayDeque

/**
 * Conservative bound on the size of RE2/J's expanded program. RE2/J avoids
 * catastrophic matching backtracking, but nested counted repetitions can expand into
 * an enormous program *during compilation*.
 *
 * This is deliberately a cheap lexical upper estimate, not another regex parser.
 * False positives fail closed; malformed syntax is left to RE2/J.
 */
internal object UrlRegexComplexity {
    private const val MAX_COST = 8192
    private const val OVER_BUDGET = MAX_COST + 1
    private const val MAX_GROUP_DEPTH = 64

    private class Frame {
        private var branches = 0
        private var prefix = 0
        var atom = 0
            private set

        fun addAtom(cost: Int) {
            prefix = add(prefix, atom)
            atom = cost
        }

        fun repeat(count: Int) {
            atom = cap(atom.toLong() * count)
        }

        fun optional() {
            if (atom == 0) {
                addAtom(1)
            } else {
                atom = add(atom, 1)
            }
        }

        fun alternative() {
            branches = add(branches, add(add(prefix, atom), 1))
            prefix = 0
            atom = 0
        }

        fun cost(): Int = add(branches, add(prefix, atom))
    }

    private fun cap(value: Long): Int =
        value.coerceAtMost(OVER_BUDGET.toLong()).toInt()

    private fun add(a: Int, b: Int): Int = cap(a.toLong() + b)

    /** Returns the index after a counted quantifier and its maximum expansion. */
    private fun repetition(pattern: String, start: Int): Pair<Int, Int>? {
        var index = start + 1
        if (index >= pattern.length || pattern[index] !in '0'..'9') {
            return null
        }
        var min = 0
        while (index < pattern.length && pattern[index] in '0'..'9') {
            min = cap(min.toLong() * 10 + (pattern[index] - '0'))
            index++
        }
        var max = min
        if (index < pattern.length && pattern[index] == ',') {
            index++
            if (index < pattern.length && pattern[index] == '}') {
                max = add(min, 1) // {min,} needs a loop after the fixed prefix.
            } else {
                if (index >= pattern.length || pattern[index] !in '0'..'9') {
                    return null
                }
                max = 0
                while (index < pattern.length && pattern[index] in '0'..'9') {
                    max = cap(max.toLong() * 10 + (pattern[index] - '0'))
                    index++
                }
            }
        }
        return if (index < pattern.length && pattern[index] == '}') {
            (index + 1) to max.coerceAtLeast(1)
        } else {
            null
        }
    }

    fun isWithinBudget(pattern: String): Boolean = estimatedCost(pattern) != null

    fun estimatedCost(pattern: String): Int? {
        val stack = ArrayDeque<Frame>()
        var frame = Frame()
        var i = 0
        while (i < pattern.length) {
            when (pattern[i]) {
                '\\' -> {
                    // Escaped punctuation is an atom, not a quantifier or group.
                    frame.addAtom(1)
                    i++
                    if (i < pattern.length) {
                        i++
                    }
                }
                '[' -> {
                    // Account for class contents, including large explicit ranges.
                    val start = i++
                    var escaped = false
                    while (i < pattern.length) {
                        val char = pattern[i++]
                        if (!escaped && char == ']') {
                            break
                        }
                        if (!escaped && char == '\\') {
                            escaped = true
                        } else {
                            escaped = false
                        }
                    }
                    frame.addAtom(cap((i - start).toLong()))
                }
                '(' -> {
                    if (stack.size == MAX_GROUP_DEPTH) {
                        return null
                    }
                    stack.addLast(frame)
                    frame = Frame()
                    i++
                }
                ')' -> {
                    if (stack.isEmpty()) {
                        return null
                    }
                    val groupCost =
                        add(frame.cost(), 2) // Capture or group overhead.
                    frame = stack.removeLast()
                    frame.addAtom(groupCost)
                    i++
                }
                '|' -> {
                    frame.alternative()
                    i++
                }
                '*',
                '+',
                '?' -> {
                    frame.optional()
                    i++
                }
                '{' -> {
                    val repeat =
                        if (frame.atom != 0) {
                            repetition(pattern, i)
                        } else {
                            null
                        }
                    if (repeat == null) {
                        frame.addAtom(1)
                        i++
                    } else {
                        frame.repeat(repeat.second)
                        i = repeat.first
                    }
                }
                else -> {
                    frame.addAtom(1)
                    i++
                }
            }
            if (frame.cost() > MAX_COST) {
                return null
            }
        }
        return frame.cost().takeIf { stack.isEmpty() && it <= MAX_COST }
    }
}
