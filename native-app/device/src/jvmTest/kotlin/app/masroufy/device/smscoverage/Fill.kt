package app.masroufy.device.smscoverage

import java.math.BigDecimal

/**
 * ملء القوالب بقيم **مخترعة** (المستودع عام — ممنوع أي اسم أو رقم أو محل من المصادر).
 *
 * صيغة القوالب في ملفات البحث: `{خانة}` · `[اختياري]` · `[بديل|بديل]` · و`(…)?` أو `(بديل|بديل)` على طريقة regex في ملف مصر.
 * القوس العادي من غير `|` ومن غير `?` بعده نص حرفي (زي `(SAR {amount})`). كل قالب بيطلع **نسختين**: (أ) أول بديل في كل مكان
 * والاختياري موجود، و(ب) آخر بديل والاختياري متشال — ولو الاتنين زي بعض نسخة واحدة.
 */
internal object Fill {
    const val TX_DATE = "2026-09-14"

    /** وقت وصول الرسالة زي `Instant.toString()` في `SmsInboxStore` — الساعة 14:22 في الرياض والقاهرة = 11:22 UTC. */
    const val RECEIVED_AT = "2026-09-14T11:22:30Z"

    private val PLACEHOLDER = Regex("\\{([A-Za-z0-9_]+)\\}")

    // ── التوسيع ──────────────────────────────────────────────────────────────

    private sealed interface Node
    private data class Lit(val text: String) : Node
    private data class Alt(val options: List<List<Node>>) : Node

    private class Parser(val t: String) {
        var i = 0

        fun seq(stops: Set<Char>): List<Node> {
            val out = mutableListOf<Node>()
            val lit = StringBuilder()
            fun flush() {
                if (lit.isNotEmpty()) out += Lit(lit.toString()).also { lit.clear() }
            }
            while (i < t.length) {
                val c = t[i]
                when {
                    c in stops -> break
                    c == '[' -> {
                        flush(); i++
                        val options = options(']')
                        out += Alt(if (options.size == 1) options + listOf(emptyList()) else options)
                    }
                    c == '(' && isGroup(i) -> {
                        flush(); i++
                        val options = options(')')
                        val optional = i < t.length && t[i] == '?'
                        if (optional) i++
                        out += Alt(if (optional) options + listOf(emptyList()) else options)
                    }
                    else -> { lit.append(c); i++ }
                }
            }
            flush()
            return out
        }

        private fun options(close: Char): List<List<Node>> {
            val all = mutableListOf<List<Node>>()
            while (true) {
                all += seq(setOf('|', close))
                check(i < t.length) { "unclosed '$close' in: $t" }
                if (t[i++] == close) return all
            }
        }

        /** `(` مجموعة لو بعد قفلتها `?` أو جواها `|` في نفس المستوى. */
        private fun isGroup(start: Int): Boolean {
            var depth = 0
            var bar = false
            var j = start
            while (j < t.length) {
                when (t[j]) {
                    '(', '[' -> depth++
                    ')', ']' -> {
                        depth--
                        if (depth == 0) return bar || (j + 1 < t.length && t[j + 1] == '?')
                    }
                    '|' -> if (depth == 1) bar = true
                }
                j++
            }
            return false
        }
    }

    private fun render(nodes: List<Node>, first: Boolean): String = nodes.joinToString("") { n ->
        when (n) {
            is Lit -> n.text
            is Alt -> render(if (first) n.options.first() else n.options.last(), first)
        }
    }

    /** النسخ: `A` و`B` (أو `-` لو القالب مالوش بدائل). */
    fun expand(template: String): List<Pair<String, String>> {
        val nodes = Parser(template).seq(emptySet())
        val a = render(nodes, true)
        val b = render(nodes, false)
        return if (a == b) listOf("-" to a) else listOf("A" to a, "B" to b)
    }

    fun fill(text: String, values: Map<String, String>): String = PLACEHOLDER.replace(text) { m ->
        values[m.groupValues[1]] ?: error("no invented value for {${m.groupValues[1]}} in: $text")
    }

    // ── القيم ────────────────────────────────────────────────────────────────

    /** مبلغ العملية حسب نوعها — فيه فاصلة آلاف أحيانًا عشان القارئ يتجرب عليها. */
    private val AMOUNT_BY_TYPE = listOf(
        "purchase" to "87.50", "salary" to "9,800.00", "topup" to "300.00", "top_up" to "300.00", "transfer" to "1,250.00", "wallet" to "1,250.00",
        "atm" to "500.00", "cash" to "500.00", "deposit" to "2,000.00", "bill" to "230.40", "government" to "230.40",
        "recharge" to "50.00", "loan" to "1,845.00", "installment" to "1,845.00", "card_payment" to "1,500.00",
        "refund" to "42.25", "cashback" to "42.25", "reversal" to "42.25", "credit" to "42.25", "fee" to "11.50",
    )

    private fun amountFor(type: String): String = AMOUNT_BY_TYPE.firstOrNull { it.first in type.lowercase() }?.second ?: "87.50"

    fun values(country: String, lang: String, type: String, style: DateStyle, overrides: Map<String, String>): Map<String, String> {
        val ar = lang.startsWith("ar")
        val sa = country == "SA"
        val base = mapOf(
            "amount" to amountFor(type), "currency" to if (sa) "SAR" else "EGP", "date" to style.date, "time" to style.time,
            "last4" to "4821", "acct" to "3307", "cpAcct" to "7719", "acct2" to "5530", "acctPrefix" to "30", "cpAcctFragment" to "771",
            "ownIbanMasked" to "SA**********3*******7", "acct_tail" to "482",
            "merchant" to if (sa) "ZEST BAKERY" else "NILE TEST MARKET",
            "name" to when {
                sa && ar -> "خالد عمر المختبر"
                sa -> "KHALID OMAR TESTER"
                ar -> "محمود سامي التجريبي"
                else -> "MAHMOUD SAMY TESTER"
            },
            "masked_name" to "محم** سام* التج****",
            "bank" to if (ar) "بنك الرياض" else "Riyad Bank", "bankCode" to "RIBL",
            "wallet" to if (ar) "ابل باي" else "Apple Pay", "location" to "OLAYA ATM RIYADH", "country" to if (sa) "SA" else "EG",
            "ccy" to "USD", "foreignAmount" to "23.40",
            "balance" to "3,412.60", "fee" to "5.75", "vat" to "0.86", "total" to "94.11", "rate" to "3.7612", "fxRate" to "3.7612",
            "remaining" to "48,200.00", "released" to "12.25",
            "ref" to "260914000427", "otp" to "482913", "code" to "777",
            "purpose" to if (ar) "شراء انترنت" else "Online purchase",
            "reason" to if (ar) "تجاوز الحد اليومي" else "Card replacement fee",
            "note" to "حساب المواطن",
            "billerCode" to "001", "biller" to if (ar) "شركة الكهرباء التجريبية" else "TEST ELECTRICITY CO",
            "service" to if (ar) "فاتورة كهرباء" else "Electricity bill", "billNumber" to "30012345678", "agency" to "الجوازات",
            "phone" to if (sa) "8000000000" else "01000000123", "counterparty_id" to "002010000004567",
            "cardProduct" to "فيزا بلاتينيوم", "hotline" to "19623", "terminal" to "0417",
            "installment" to "416.67", "n" to "12", "month" to "سبتمبر", "year" to "2026", "tz" to "2",
            "promo" to "تابع مصروفاتك من التطبيق", "rest" to "",
        )
        return base + overrides
    }

    /** المبلغ بالوحدة الصغرى — من النص المخترع نفسه (من غير float: `BigDecimal`). */
    fun minor(amount: String): Long = BigDecimal(amount.replace(",", "")).movePointRight(2).longValueExact()

    /**
     * شكل التاريخ المعتاد لكل بنك — من ملاحظات ملفات البحث لما تكون مكتوبة، وإلا اختيار معلن (DD/MM/YYYY) ومكتوب جنبه «مفترض».
     */
    fun defaultStyle(country: String, bank: String, index: Int, lang: String): DateStyle = when {
        country == "SA" -> when {
            bank.startsWith("Al Rajhi") -> when (index) {
                5, 6, 7, 15, 20, 32 -> DateStyle.YY_M_D_SLASH // الشكل الجديد 2026: YY/M/D H:MM
                26 -> DateStyle.YY_MM_DD_BACKSLASH // الملاحظة: «can use backslashes as date separators»
                else -> DateStyle.YY_MM_DD_DASH // الشكل القديم: YY-MM-DD
            }
            bank.startsWith("Unknown Saudi bank (possibly") -> DateStyle.YY_MM_DD_DASH
            bank.startsWith("Saudi National") -> DateStyle.DD_MM_YY_SLASH
            bank.startsWith("SAB") -> DateStyle.YYYY_MM_DD_SEC
            bank.startsWith("Banque Saudi Fransi") -> DateStyle.DD_MM_YYYY_DASH_SEC
            bank.startsWith("STC") -> DateStyle.YYYY_MM_DD_SEC // من السطر 112 (الوحيد اللي شكله مكتوب)
            bank.startsWith("Unknown Saudi bank (tidybox") -> DateStyle.D_M_YY
            bank.startsWith("SAMA") -> DateStyle.YYYY_MM_DD
            else -> DateStyle.DD_MM_YYYY_SLASH // مفترض: الإنماء · دي 360 · البلاد
        }
        bank.startsWith("National Bank of Egypt") -> if (index == 1 || index == 2) DateStyle.DD_MM_YY_SLASH else DateStyle.MM_DD
        bank.startsWith("Unknown bank via InstaPay") -> DateStyle.DD_MM_YYYY_DASH
        bank.startsWith("CIB") -> if (index == 25) DateStyle.DD_MM else DateStyle.DD_MM_YY_SLASH
        bank.startsWith("Kuwait Finance") -> DateStyle.DD_MM
        bank.startsWith("Breadfast") -> DateStyle.DD_DOT_MM_DOT_YY
        bank.startsWith("Vodafone") -> when {
            index == 38 -> DateStyle.MON_D_YYYY
            lang.startsWith("ar") -> DateStyle.YY_MM_DD_DASH // ملاحظة السطر 39: «YY-MM-DD in one test sample»
            else -> DateStyle.DD_MM_YYYY_SLASH
        }
        else -> DateStyle.DD_MM_YYYY_SLASH // مفترض: HSBC · البنك العربي · الباقي
    }
}
