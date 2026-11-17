package app.masroufy.device

import app.masroufy.core.SupportKind
import app.masroufy.core.SupportMessage
import app.masroufy.port.PendingSupport
import app.masroufy.port.SUPPORT_MAX_REFUSALS
import app.masroufy.port.SupportOutboxPort
import java.io.File

/**
 * صندوق «ابعتلنا» في ملفات خاصة بالتطبيق (أندرويد: `filesDir/support/` — مش بيتزامن):
 * - `outbox.log` = النسخة المحلية كلها: سطر لكل رسالة `النوع<TAB>اليوم<TAB>البلد<TAB>النص` — فواصل السطر والتبويب جوه النص بتتحول
 *   (`\n` ⇒ `\\n` · TAB ⇒ مسافة).
 * - `pending.log` (جنبه) = اللي لسه ما اتبعتش لفايربيز: `المعرّف<TAB>` + نفس السطر. بيتكتب من الأول لما رسالة تتبعت ([sent]).
 * ⚠️ مشترك بين أندرويد والكمبيوتر (`src/jvmShared` — `java.io` بس) عشان يتختبر بنفس الكود.
 */
class FileSupportOutbox(private val file: File) : SupportOutboxPort {
    private val lock = Any()
    private val pendingFile = File(file.parentFile, "pending.log")

    override fun add(message: SupportMessage, id: String) = synchronized(lock) {
        file.parentFile?.mkdirs()
        file.appendText(line(message) + "\n", Charsets.UTF_8)
        pendingFile.appendText(id.replace('\t', ' ').replace("\n", "") + "\t" + line(message) + "\n", Charsets.UTF_8)
    }

    override fun all(): List<SupportMessage> = synchronized(lock) {
        if (!file.isFile) emptyList() else file.readLines(Charsets.UTF_8).mapNotNull(::parse)
    }

    override fun pending(): List<PendingSupport> = synchronized(lock) { readPending() }

    override fun sent(id: String) = synchronized(lock) { writePending(readPending().filterNot { it.id == id }) }

    override fun resetRefusals(): Int = synchronized(lock) {
        val all = readPending()
        val n = all.count { it.refusals >= SUPPORT_MAX_REFUSALS }
        if (n > 0) writePending(all.map { if (it.refusals >= SUPPORT_MAX_REFUSALS) it.copy(refusals = 0) else it })
        n
    }

    override fun refused(id: String) = synchronized(lock) {
        writePending(readPending().map { if (it.id == id) it.copy(refusals = it.refusals + 1) else it })
    }

    /** السطر: `المعرّف<TAB>[R<عدد الرفض><TAB>]الرسالة` — الخانة دي بتتكتب بس لو فيه رفض (الملفات القديمة بتتقري زي ما هي). */
    private fun writePending(left: List<PendingSupport>) {
        if (left.isEmpty()) pendingFile.delete()
        else pendingFile.writeText(left.joinToString("") { it.id + "\t" + (if (it.refusals > 0) "R${it.refusals}\t" else "") + line(it.message) + "\n" }, Charsets.UTF_8)
    }

    private fun readPending(): List<PendingSupport> = if (!pendingFile.isFile) emptyList() else pendingFile.readLines(Charsets.UTF_8).mapNotNull { l ->
        val tab = l.indexOf('\t')
        if (tab <= 0) return@mapNotNull null
        var rest = l.substring(tab + 1)
        val next = rest.indexOf('\t')
        val refusals = if (next > 1 && rest[0] == 'R') rest.substring(1, next).toIntOrNull() else null
        if (refusals != null) rest = rest.substring(next + 1)
        parse(rest)?.let { PendingSupport(l.substring(0, tab), it, refusals ?: 0) }
    }

    companion object {
        internal fun line(m: SupportMessage): String =
            listOf(m.kind.name, m.day, m.countryCode.orEmpty(), m.text.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n").replace('\t', ' ')).joinToString("\t")

        internal fun parse(line: String): SupportMessage? {
            val p = line.split('\t', limit = 4)
            if (p.size < 4) return null
            val kind = SupportKind.entries.firstOrNull { it.name == p[0] } ?: return null
            val text = buildString {
                var i = 0
                val s = p[3]
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\' && i + 1 < s.length) {
                        append(if (s[i + 1] == 'n') '\n' else s[i + 1])
                        i += 2
                    } else {
                        append(c)
                        i++
                    }
                }
            }
            return SupportMessage(kind, text, p[1], p[2].ifEmpty { null })
        }
    }
}
