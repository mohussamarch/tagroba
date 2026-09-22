package app.masroufy.usecase

import app.masroufy.core.Direction
import app.masroufy.core.Id
import app.masroufy.core.LedgerMovement
import app.masroufy.core.ReconcileResult
import app.masroufy.core.Transaction
import app.masroufy.core.Wallet
import app.masroufy.core.daysBetween
import app.masroufy.core.periodForDate
import app.masroufy.core.reconcileBalance
import app.masroufy.port.TransactionRepository
import app.masroufy.port.WalletRepository

/**
 * ReconcileBalance — نقل `reconcileBalance.ts`: مطابقة الرصيد الجاري بالكشف (OVERRIDES §7-ب).
 * القراية **فترة فترة** من تاريخ الافتتاح (ARCHITECTURE §14.5) بسقف معلن، والتحويل الداخلي
 * الجاي **إلى** المحفظة بيتعامل دخولًا — من غيره رصيدها بيفضل ناقص للأبد.
 */

data class ReconcileOutcome(
    val wallet: Wallet,
    val result: ReconcileResult,
    /** عدد العمليات اللي مالهاش رصيد معلن فما بتتقارنش. */
    val withoutStatedBalance: Int,
    /** عمليات مش منسوبة لأي محفظة — بتتذكر ومش بتتنسب بالتخمين. */
    val unassignedCount: Int,
    val periodsRead: Int,
)

data class ReconcileDeps(
    val txns: TransactionRepository,
    val wallets: WalletRepository,
)

/** سقف الفترات المقروءة في مرة — حماية من قراية ما تخلصش. */
private const val MAX_PERIODS = 60

class ReconcileBalance(private val deps: ReconcileDeps) {
    suspend fun run(walletId: Id, until: String, payday: Int): ReconcileOutcome {
        val wallet = deps.wallets.findById(walletId) ?: throw IllegalStateException("محفظة غير موجودة: $walletId")

        val collected = mutableListOf<Transaction>()
        /** تحويلات داخلية جاية **إلى** المحفظة دي — بتتعامل دخولًا. */
        val incomingLegs = mutableListOf<Transaction>()
        var unassignedCount = 0
        var periodsRead = 0

        // الفترة اللي **بتحوي** تاريخ الافتتاح، مش اللي بتبدأ في شهره (ARCHITECTURE §14.4)
        var period = periodForDate(wallet.openingAt, payday)

        while (period.start <= until && periodsRead < MAX_PERIODS) {
            val rows = deps.txns.listByDateRange(period.start, period.end)
            periodsRead++
            for (row in rows) {
                if (row.occurredAt < wallet.openingAt || row.occurredAt > until) continue
                if (row.transferToWalletId == wallet.id) {
                    incomingLegs += row
                    continue
                }
                if (row.walletId == null) {
                    unassignedCount++
                    continue
                }
                if (row.walletId != wallet.id) continue
                collected += row
            }
            if (period.end >= until) break
            period = shiftPeriod(period, 1, payday)
        }

        // الطرف الداخل بيتضاف كحركة وارد قبل الترتيب، فبيدخل السلسلة بترتيبه
        val all = (collected.map { it to false } + incomingLegs.map { it to true })
            .sortedWith(compareBy({ it.first.occurredAt }, { it.first.sourceOrder }))

        val movements = all.map { (txn, incoming) ->
            LedgerMovement(
                date = txn.occurredAt,
                sourceOrder = txn.sourceOrder,
                // الطرف الداخل دايمًا دائن على المحفظة دي مهما كان اتجاه العملية
                debitMinor = if (!incoming && txn.observedDirection == Direction.OUT) txn.amountMinor else 0,
                creditMinor = if (incoming) txn.amountMinor else if (txn.observedDirection == Direction.IN) txn.amountMinor else 0,
                // الرصيد المعلن بيخص محفظة **المصدر** في كشفها — ما يتقارنش بيه الطرف الداخل
                statedBalanceMinor = if (!incoming) txn.statedBalanceMinor else null,
                label = (if (incoming) "وارد تحويل — " else "") +
                    (
                        txn.rawMerchantName?.takeIf { it.isNotEmpty() }
                            ?: txn.rawDescription?.takeIf { it.isNotEmpty() }
                            ?: txn.sourceCategory?.takeIf { it.isNotEmpty() }
                            ?: ""
                        ),
            )
        }

        return ReconcileOutcome(
            wallet = wallet,
            result = reconcileBalance(wallet.openingBalanceMinor, wallet.openingAt, movements),
            withoutStatedBalance = movements.count { it.statedBalanceMinor == null },
            unassignedCount = unassignedCount,
            periodsRead = periodsRead,
        )
    }
}

/** كام فترة بين تاريخين — لتقدير تكلفة القراية قبل تشغيلها. */
fun estimatePeriodsBetween(from: String, to: String): Int =
    maxOf(1, (daysBetween(from, to) + 29) / 30)
