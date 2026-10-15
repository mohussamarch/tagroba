package app.masroufy.usecase

import app.masroufy.core.CROSS_SOURCE_WINDOW_DAYS
import app.masroufy.core.Currency
import app.masroufy.core.ImportSourceType
import app.masroufy.core.SAUDI_TIME_ZONE
import app.masroufy.core.SchemaId
import app.masroufy.core.Wallet
import app.masroufy.core.dayNumberToIso
import app.masroufy.core.parseIsoDate
import app.masroufy.core.toDayNumber
import app.masroufy.memory.FixedClock
import app.masroufy.memory.MemorySmsInbox
import app.masroufy.memory.MemoryUserSettingsStore
import app.masroufy.port.QueuedSms

/**
 * عالم §83 (قراية تاريخ رسايل البنك وتكملة الناقص) — **كل الرسايل والمحلات والمبالغ مخترعة** بنفس أشكال `SmsAutoFixture` (مفيش بيانات من
 * ملفات المالك). بنك وهمي رصيد بدايته 2025-01-01 (زي حساب المالك) + كاش، والدمج مع الكشف شغال (±2 يوم — §75-10).
 */
internal val HISTORY_BANK = Wallet("w-bank", "بنك وهمي", Currency.SAR, "bank", 500_000, "2025-01-01")

/**
 * إجابة المالك 2026-10-15 (OVERRIDES §91-1): الحوالة الواردة «إلى:1111» من غير محفظة عليها الأرقام دي بتستنى «الحساب ده بتاعك؟». البلد دي
 * محفظة بنكها عليها 1111 — زي ما المالك بيرد «أيوه، ده حسابي» — للاختبارات اللي عن حاجة تانية (الصفحات · التاريخ الطويل).
 */
internal fun digitsSpace(window: Int? = CROSS_SOURCE_WINDOW_DAYS) = SmsSpace(wallets = listOf(CASH, HISTORY_BANK.copy(accountLast4 = "1111")), window = window)
internal const val HISTORY_NOW = "2026-07-01T12:00:00Z"

internal fun day(iso: String, plus: Int): String = dayNumberToIso(toDayNumber(parseIsoDate(iso)) + plus)

/** «25/03/05» من «2025-03-05» — شكل التاريخ في رسالة الشراء المخترعة. */
internal fun smsDay(iso: String) = iso.substring(2, 4) + "/" + iso.substring(5, 7) + "/" + iso.substring(8, 10)

internal fun money(minor: Long) = "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

/** عملية في التاريخ المخترع: رسالة (يومها) + سطر كشف (بعدها بـ[statementLag] يوم — جوه نافذة الدمج). */
internal data class HistoryTxn(val id: String, val date: String, val minor: Long, val incoming: Boolean, val party: String, val statementLag: Int) {
    val sms: QueuedSms
        get() = QueuedSms(
            id, "TESTBANK", "${date}T10:00:00Z",
            if (incoming) "حوالة محلية واردة\nمن:$party\nبـSR ${money(minor)}\nإلى:1111\n${smsDay(date)}" else "شراء\nبـSR ${money(minor)}\nلدى:$party\n${smsDay(date)}",
        )
}

internal class HistoryWorld(
    window: Int = 200,
    perSync: Int = 500,
    val memory: MemorySmsInbox = MemorySmsInbox(available = true, now = HISTORY_NOW, window = window, perSync = perSync),
    val settings: MemoryUserSettingsStore = MemoryUserSettingsStore(),
    val space: SmsSpace = SmsSpace(wallets = listOf(CASH, HISTORY_BANK), window = CROSS_SOURCE_WINDOW_DAYS),
    /** منطقة وقت البلد (§88) — الافتراضي السعودية زي `SmsSpace`. */
    val zone: String = SAUDI_TIME_ZONE,
) {
    val clock = FixedClock("2026-07-01T12:30:00.000Z")

    private fun lane(inbox: MemorySmsInbox) =
        SmsLane.of(space.spaceId, space.importDeps(space.smsEffects(inbox)), ManageSmsInbox(inbox, space.parse), space.wallets, zone)

    fun deps(inbox: MemorySmsInbox = memory) = AutoRecordSmsDeps(inbox, listOf(lane(inbox)))

    fun history(inbox: MemorySmsInbox = memory) = SmsHistory(SmsHistoryDeps(deps(inbox), settings, clock))

    fun catchUp(inbox: MemorySmsInbox = memory) = SmsCatchUp(inbox, settings, listOf(space.spaceId), clock)

    fun cycle(inbox: MemorySmsInbox = memory) = RunBackgroundCycle(BackgroundCycleDeps(sms = AutoRecordSms(deps(inbox)), smsCatchUp = catchUp(inbox)))

    /** كشف الحساب (CSV القديم بعمود الرصيد) للعمليات دي — سطر لكل عملية بتاريخ الكشف، والرصيد بيتحسب من رصيد البداية. */
    fun statement(txns: List<HistoryTxn>, file: String = "statement.csv"): ImportRequest {
        var balance = HISTORY_BANK.openingBalanceMinor
        val lines = txns.sortedWith(compareBy({ day(it.date, it.statementLag) }, { it.id })).map { t ->
            balance += if (t.incoming) t.minor else -t.minor
            val debit = if (t.incoming) "0.00" else money(t.minor)
            val credit = if (t.incoming) money(t.minor) else "0.00"
            "${day(t.date, t.statementLag)},$debit,$credit,${money(balance)},${t.party},,,${if (t.incoming) "حوالة واردة" else "شراء نقاط بيع"}"
        }
        val csv = (listOf(MW_HEADER) + lines).joinToString("\n")
        return ImportRequest(file, csv, HISTORY_BANK.name, ImportSourceType.CSV_LEGACY, walletId = HISTORY_BANK.id, schema = SchemaId.LEGACY)
    }

    suspend fun importStatement(request: ImportRequest) {
        val importer = ImportStatement(space.importDeps())
        importer.commit(request, importer.preview(request))
    }

    suspend fun closing(until: String): Long =
        ReconcileBalance(ReconcileDeps(space.txnStore, space.wallets)).run(HISTORY_BANK.id, until, 28).result.closingMinor

    suspend fun mismatches(until: String) = ReconcileBalance(ReconcileDeps(space.txnStore, space.wallets)).run(HISTORY_BANK.id, until, 28).result.mismatches
}

/**
 * 18 شهر (2025-01-01 ⇒ 2026-06-30) من عمليات مخترعة: شراء كل يوم بمبلغ مختلف (من 4 محلات وهمية) + راتب وهمي يوم 27 كل شهر. سطر الكشف
 * بعد الرسالة بـ0 أو 1 أو 2 يوم بالدور. [twins] = كل 45 يوم شراء تاني **بنفس المبلغ** اليوم اللي بعده (نفس الحركة ممكن تبقى أي واحد فيهم ⇒
 * «شبه عملية» ويسأل — عمرها ما بتتسجل مرتين).
 */
internal fun eighteenMonths(twins: Boolean = true): List<HistoryTxn> {
    val shops = listOf("TEST CAFE", "TEST MART", "TEST BAKERY", "TEST FUEL")
    val out = mutableListOf<HistoryTxn>()
    var i = 0
    var date = "2025-01-01"
    while (date <= "2026-06-30") {
        val minor = 1_000L + i * 7L
        out += HistoryTxn("p$i", date, minor, false, shops[i % shops.size], i % 3)
        if (twins && i % 45 == 10 && day(date, 1) <= "2026-06-30") out += HistoryTxn("p${i}twin", day(date, 1), minor, false, shops[(i + 1) % shops.size], 0)
        if (date.endsWith("-27")) out += HistoryTxn("s$i", date, 800_000L + i, true, "TEST EMPLOYER", 1)
        date = day(date, 1)
        i++
    }
    return out
}
