package app.masroufy.device.smscoverage

import java.io.File

/**
 * تقرير القياس: `device/build/reports/sms-template-coverage/report.json` (كل سطر وكل نسخة ونصها المملوء) و`summary.txt`.
 * في `build/` بس (مش في Git) — النصوص كلها مخترعة.
 */
internal object Report {
    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (c in value) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c < ' ' || c in '‎'..'‏' || c == '؜' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> json(value.toString())
    }

    private fun rowJson(r: RowResult): Map<String, Any?> = linkedMapOf(
        "file" to r.file, "index" to r.index, "bank" to r.bank, "type" to r.type, "lang" to r.lang, "verified" to r.verified,
        "title" to r.isTitle, "keywordOnly" to r.keywordOnly, "outcome" to r.outcome.wire, "problems" to r.problems,
        "variants" to r.variants.map { v ->
            linkedMapOf(
                "label" to v.variant.label, "outcome" to v.outcome.wire, "problems" to v.problems, "detail" to v.detail,
                "manualReadOutcome" to v.manualOutcome.wire, "crossLane" to v.crossLane, "fragileIgnore" to v.fragileIgnore,
                "latentWithDate" to v.latent,
                "expect" to linkedMapOf(
                    "tx" to v.variant.expect.tx, "dir" to v.variant.expect.dir.name, "amountKey" to v.variant.expect.amountKey,
                    "merchant" to v.variant.expect.merchant, "partyKeys" to v.variant.expect.partyKeys, "foreign" to v.variant.expect.foreign,
                ),
                "body" to v.variant.body,
            )
        },
    )

    private fun counts(rows: List<RowResult>): Map<String, Int> = Outcome.entries.associate { o -> o.wire to rows.count { it.outcome == o } }

    fun write(dir: File, rows: List<RowResult>): File {
        dir.mkdirs()
        val byBank = rows.groupBy { it.file + " | " + it.bank }.map { (k, list) ->
            linkedMapOf(
                "file" to k.substringBefore(" | "), "bank" to k.substringAfter(" | "), "total" to list.size,
                "handledRight" to list.count { it.outcome == Outcome.CORRECT || it.outcome == Outcome.CORRECTLY_IGNORED },
                "outcomes" to counts(list),
            )
        }
        val nonTitle = rows.filterNot { it.isTitle }
        val titles = rows.filter { it.isTitle }
        val variants = rows.flatMap { it.variants }
        val root = linkedMapOf(
            "generatedFrom" to listOf("research/banks/saudi-sms-formats.json", "research/banks/egypt-sms-formats.json"),
            "txDate" to Fill.TX_DATE, "receivedAt" to Fill.RECEIVED_AT,
            "total" to rows.size, "byOutcome" to counts(rows),
            "byOutcomeExcludingSamaTitles" to counts(nonTitle), "samaTitles" to counts(titles),
            "unverifiedRows" to counts(rows.filter { it.verified == false }),
            "variants" to variants.size,
            "manualReadDiffers" to variants.count { it.manualOutcome != it.outcome },
            "crossLaneAccepted" to variants.count { it.crossLane != null },
            "fragileIgnores" to variants.count { it.fragileIgnore },
            "byBank" to byBank,
            "rows" to rows.map(::rowJson),
        )
        val file = File(dir, "report.json")
        file.writeText(json(root))
        File(dir, "summary.txt").writeText(summaryText(rows))
        return file
    }

    fun summaryText(rows: List<RowResult>): String = buildString {
        appendLine("SMS template coverage — ${rows.size} rows, ${rows.sumOf { it.variants.size }} message variants")
        appendLine("all rows:            ${counts(rows)}")
        appendLine("without SAMA titles: ${counts(rows.filterNot { it.isTitle })}")
        appendLine("SAMA titles only:    ${counts(rows.filter { it.isTitle })}")
        appendLine()
        for ((bank, list) in rows.groupBy { it.file.substringBefore('-') + " · " + it.bank }) {
            val right = list.count { it.outcome == Outcome.CORRECT || it.outcome == Outcome.CORRECTLY_IGNORED }
            appendLine("$right/${list.size}  $bank")
        }
        appendLine()
        for (r in rows.filter { it.outcome.severity > 0 }) {
            val tag = if (r.verified == false) " [verified=false]" else ""
            appendLine("${r.file}#${r.index} ${r.outcome.wire}$tag (${r.type}): ${r.problems.joinToString(" | ")}")
            r.variants.mapNotNull { it.latent }.distinct().forEach { appendLine("    latent: $it") }
        }
        val fragile = rows.filter { r -> r.variants.any { it.fragileIgnore } }
        if (fragile.isNotEmpty()) {
            appendLine()
            appendLine("ignored only by accident (no OTP/declined/offer guard fired):")
            fragile.forEach { r -> appendLine("  ${r.file}#${r.index} (${r.type}): " + r.variants.filter { it.fragileIgnore }.joinToString(" | ") { "[${it.variant.label}] ${it.detail}" }) }
        }
    }
}
