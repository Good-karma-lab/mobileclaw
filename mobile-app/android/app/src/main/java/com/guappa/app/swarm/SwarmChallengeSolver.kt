package com.guappa.app.swarm

import android.util.Log

/**
 * Parses and solves garbled arithmetic challenges from the WWSP anti-bot system.
 *
 * The connector sends challenges with mixed-case, leetspeak-style obfuscation.
 * Examples:
 *   "wHAt 1S 64 pLus 33?" -> 64 + 33 = 97
 *   "WhAT iS 12 PlUS 45?" -> 12 + 45 = 57
 *   "wH4t 1s thr33 + f1ve?" -> 33 + 5 = 38
 *   "WHAT IS 100 m1nus 42?" -> 100 - 42 = 58
 *   "what is 7 t1m3s 6?" -> 7 * 6 = 42
 */
class SwarmChallengeSolver {
    private val TAG = "SwarmChallengeSolver"

    companion object {
        // Leet-speak number word patterns: maps obfuscated words to digits
        private val WORD_NUMBERS = mapOf(
            "zero" to 0, "z3r0" to 0, "z3ro" to 0, "zer0" to 0,
            "one" to 1, "0ne" to 1, "on3" to 1,
            "two" to 2, "tw0" to 2,
            "three" to 3, "thr33" to 3, "thr3e" to 3, "thre3" to 3,
            "four" to 4, "f0ur" to 4, "f0u4" to 4, "fo4r" to 4,
            "five" to 5, "f1ve" to 5, "fiv3" to 5, "f1v3" to 5,
            "six" to 6, "s1x" to 6,
            "seven" to 7, "s3ven" to 7, "sev3n" to 7, "s3v3n" to 7,
            "eight" to 8, "e1ght" to 8, "eigh7" to 8, "e1gh7" to 8,
            "nine" to 9, "n1ne" to 9, "nin3" to 9, "n1n3" to 9,
            "ten" to 10, "t3n" to 10,
            "eleven" to 11,
            "twelve" to 12,
            "thirteen" to 13,
            "fourteen" to 14,
            "fifteen" to 15,
            "sixteen" to 16,
            "seventeen" to 17,
            "eighteen" to 18,
            "nineteen" to 19,
            "twenty" to 20,
            "thirty" to 30,
            "forty" to 40,
            "fifty" to 50,
            "sixty" to 60,
            "seventy" to 70,
            "eighty" to 80,
            "ninety" to 90,
            "hundred" to 100,
        )

        // Operator patterns (case-insensitive, leet-speak variants)
        private val ADDITION_PATTERNS = setOf(
            "plus", "p1us", "plu5", "p1u5",
            "add", "4dd",
            "sum",
            "+"
        )

        private val SUBTRACTION_PATTERNS = setOf(
            "minus", "m1nus", "minu5", "m1nu5",
            "subtract",
            "sub",
            "-"
        )

        private val MULTIPLICATION_PATTERNS = setOf(
            "times", "t1mes", "tim3s", "t1m3s",
            "multiply", "mult1ply",
            "multiplied",
            "*", "x"
        )

        private val DIVISION_PATTERNS = setOf(
            "divided", "d1vided", "divid3d",
            "div",
            "/"
        )
    }

    /**
     * Solve a garbled arithmetic challenge.
     * Returns the computed integer answer.
     * Throws IllegalArgumentException if the challenge cannot be parsed.
     */
    fun solve(challenge: String): Int {
        Log.d(TAG, "Solving challenge: $challenge")

        val normalized = challenge.lowercase().trim()

        // Extract all numbers (both digit sequences and word numbers)
        val numbers = extractNumbers(normalized)
        if (numbers.isEmpty()) {
            throw IllegalArgumentException("No numbers found in challenge: $challenge")
        }

        // Detect the operator
        val operator = detectOperator(normalized)
        Log.d(TAG, "Parsed: numbers=$numbers, operator=$operator")

        val result = when (operator) {
            Operator.ADD -> numbers.sum()
            Operator.SUBTRACT -> numbers.reduce { a, b -> a - b }
            Operator.MULTIPLY -> numbers.reduce { a, b -> a * b }
            Operator.DIVIDE -> {
                if (numbers.size < 2 || numbers[1] == 0) {
                    throw IllegalArgumentException("Division by zero or insufficient numbers")
                }
                numbers.reduce { a, b -> a / b }
            }
        }

        Log.d(TAG, "Solution: $result")
        return result
    }

    /**
     * Extract all numbers from the challenge text.
     * Handles both raw digits ("64") and obfuscated word numbers ("thr33").
     */
    private fun extractNumbers(text: String): List<Int> {
        val numbers = mutableListOf<Int>()
        val tokens = text.replace(Regex("[?!.,;:]"), " ").split(Regex("\\s+"))

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            // Try raw integer
            val rawInt = token.toIntOrNull()
            if (rawInt != null) {
                numbers.add(rawInt)
                i++
                continue
            }

            // Try word number (exact match)
            val wordNum = WORD_NUMBERS[token]
            if (wordNum != null) {
                numbers.add(wordNum)
                i++
                continue
            }

            // Try extracting embedded digits from leet-speak tokens
            // e.g. "thr33" -> could contain "33"
            val embeddedDigits = Regex("\\d+").find(token)
            if (embeddedDigits != null && token.length > embeddedDigits.value.length) {
                // Token has both letters and digits — it's a leet-speak number
                // Check if it's a known word number first
                val asWordNum = WORD_NUMBERS[token]
                if (asWordNum != null) {
                    numbers.add(asWordNum)
                } else {
                    // Fall back to extracting the digit portion
                    numbers.add(embeddedDigits.value.toInt())
                }
                i++
                continue
            }

            i++
        }

        // If no numbers found through tokens, try a global digit extraction
        if (numbers.isEmpty()) {
            Regex("\\d+").findAll(text).forEach {
                numbers.add(it.value.toInt())
            }
        }

        return numbers
    }

    /**
     * Detect the arithmetic operator from the challenge text.
     * Defaults to addition if no operator is detected.
     */
    private fun detectOperator(text: String): Operator {
        val tokens = text.replace(Regex("[?!.,;:]"), " ").split(Regex("\\s+"))

        for (token in tokens) {
            if (token in ADDITION_PATTERNS) return Operator.ADD
            if (token in SUBTRACTION_PATTERNS) return Operator.SUBTRACT
            if (token in MULTIPLICATION_PATTERNS) return Operator.MULTIPLY
            if (token in DIVISION_PATTERNS) return Operator.DIVIDE
        }

        // Check for operator symbols between numbers
        if ("+" in text) return Operator.ADD
        if ("-" in text && "minus" !in text) return Operator.SUBTRACT
        if ("*" in text) return Operator.MULTIPLY
        if ("/" in text) return Operator.DIVIDE

        // Default to addition (matches WWSP behavior)
        return Operator.ADD
    }

    private enum class Operator {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }
}
