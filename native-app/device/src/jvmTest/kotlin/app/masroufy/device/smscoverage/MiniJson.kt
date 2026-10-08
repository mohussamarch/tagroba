package app.masroufy.device.smscoverage

/**
 * قارئ JSON صغير لملفات البحث (`research/banks (saudi|egypt)-sms-formats.json`) — موديول `:device` مالوش مكتبة JSON في اختباراته،
 * والملفات بسيطة (نصوص وأرقام وقوايم وكائنات)، فمش مستاهلة مكتبة جديدة (قاعدة 8).
 * بيرجع: Map / List / String / Double / Boolean / null.
 */
internal class MiniJson private constructor(private val text: String) {
    private var i = 0

    companion object {
        fun parse(text: String): Any? = MiniJson(text).run {
            val value = readValue()
            skipSpace()
            check(i == text.length) { "trailing JSON at $i" }
            value
        }
    }

    private fun skipSpace() {
        while (i < text.length && text[i] in " \t\r\n﻿") i++
    }

    private fun readValue(): Any? {
        skipSpace()
        check(i < text.length) { "unexpected end of JSON" }
        return when (text[i]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> readNumber()
        }
    }

    private fun literal(word: String, value: Any?): Any? {
        check(text.startsWith(word, i)) { "bad literal at $i" }
        i += word.length
        return value
    }

    private fun readObject(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        i++
        skipSpace()
        if (text[i] == '}') return out.also { i++ }
        while (true) {
            skipSpace()
            val key = readString()
            skipSpace()
            check(text[i] == ':') { "expected ':' at $i" }
            i++
            out[key] = readValue()
            skipSpace()
            when (text[i++]) {
                ',' -> continue
                '}' -> return out
                else -> error("expected ',' or '}' at ${i - 1}")
            }
        }
    }

    private fun readArray(): List<Any?> {
        val out = mutableListOf<Any?>()
        i++
        skipSpace()
        if (text[i] == ']') return out.also { i++ }
        while (true) {
            out += readValue()
            skipSpace()
            when (text[i++]) {
                ',' -> continue
                ']' -> return out
                else -> error("expected ',' or ']' at ${i - 1}")
            }
        }
    }

    private fun readString(): String {
        check(text[i] == '"') { "expected string at $i" }
        i++
        val out = StringBuilder()
        while (true) {
            val c = text[i++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    when (val e = text[i++]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            out.append(text.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> out.append(e)
                    }
                }
                else -> out.append(c)
            }
        }
    }

    private fun readNumber(): Double {
        val start = i
        while (i < text.length && (text[i].isDigit() || text[i] in "+-.eE")) i++
        return text.substring(start, i).toDouble()
    }
}
