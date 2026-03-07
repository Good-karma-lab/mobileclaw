package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject
import kotlin.math.*

class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Evaluate mathematical expressions. Supports +, -, *, /, ^, %, sqrt, sin, cos, tan, log, abs, pi, e"
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": "The mathematical expression to evaluate (e.g. '2 + 3 * 4', 'sqrt(16)', 'sin(3.14)')"
                }
            },
            "required": ["expression"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val expression = params.optString("expression", "")
        if (expression.isEmpty()) {
            return ToolResult.Error("Expression is required.", "INVALID_PARAMS")
        }

        return try {
            val result = evaluate(expression)
            val data = JSONObject().apply {
                put("expression", expression)
                put("result", result)
            }
            val formatted = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                result.toString()
            }
            ToolResult.Success(content = "$expression = $formatted", data = data)
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to evaluate expression '$expression': ${e.message}",
                "EVAL_ERROR"
            )
        }
    }

    private fun evaluate(expr: String): Double {
        val tokens = tokenize(expr.trim().lowercase())
        val parser = Parser(tokens)
        val result = parser.parseExpression()
        if (parser.pos < tokens.size) {
            throw IllegalArgumentException("Unexpected token: ${tokens[parser.pos]}")
        }
        return result
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < expr.length && expr[i].isLetter()) i++
                    tokens.add(expr.substring(start, i))
                }
                c in "+-*/^%()" -> {
                    tokens.add(c.toString())
                    i++
                }
                else -> throw IllegalArgumentException("Unexpected character: $c")
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<String>) {
        var pos = 0

        private fun peek(): String? = tokens.getOrNull(pos)
        private fun consume(): String = tokens[pos++]

        fun parseExpression(): Double {
            var result = parseTerm()
            while (peek() == "+" || peek() == "-") {
                val op = consume()
                val right = parseTerm()
                result = if (op == "+") result + right else result - right
            }
            return result
        }

        private fun parseTerm(): Double {
            var result = parsePower()
            while (peek() == "*" || peek() == "/" || peek() == "%") {
                val op = consume()
                val right = parsePower()
                result = when (op) {
                    "*" -> result * right
                    "/" -> {
                        if (right == 0.0) throw ArithmeticException("Division by zero")
                        result / right
                    }
                    "%" -> result % right
                    else -> result
                }
            }
            return result
        }

        private fun parsePower(): Double {
            var result = parseUnary()
            while (peek() == "^") {
                consume()
                val right = parseUnary()
                result = result.pow(right)
            }
            return result
        }

        private fun parseUnary(): Double {
            if (peek() == "-") {
                consume()
                return -parseUnary()
            }
            if (peek() == "+") {
                consume()
                return parseUnary()
            }
            return parseAtom()
        }

        private fun parseAtom(): Double {
            val token = peek() ?: throw IllegalArgumentException("Unexpected end of expression")

            // Parenthesized expression
            if (token == "(") {
                consume()
                val result = parseExpression()
                if (peek() != ")") throw IllegalArgumentException("Missing closing parenthesis")
                consume()
                return result
            }

            // Number
            val number = token.toDoubleOrNull()
            if (number != null) {
                consume()
                return number
            }

            // Constants and functions
            consume()
            return when (token) {
                "pi" -> Math.PI
                "e" -> Math.E
                "sqrt" -> {
                    val arg = parseParenArg()
                    sqrt(arg)
                }
                "sin" -> sin(parseParenArg())
                "cos" -> cos(parseParenArg())
                "tan" -> tan(parseParenArg())
                "log" -> ln(parseParenArg())
                "log10" -> log10(parseParenArg())
                "abs" -> abs(parseParenArg())
                "ceil" -> ceil(parseParenArg())
                "floor" -> floor(parseParenArg())
                else -> throw IllegalArgumentException("Unknown function or constant: $token")
            }
        }

        private fun parseParenArg(): Double {
            if (peek() != "(") throw IllegalArgumentException("Expected '(' after function name")
            consume()
            val result = parseExpression()
            if (peek() != ")") throw IllegalArgumentException("Missing closing parenthesis")
            consume()
            return result
        }
    }
}
