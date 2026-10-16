package app.masroufy.ui.screens.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.masroufy.core.Category
import app.masroufy.core.Currency
import app.masroufy.core.Direction
import app.masroufy.core.EconomicKind
import app.masroufy.core.ReviewState
import app.masroufy.core.Transaction
import app.masroufy.core.UiKey
import app.masroufy.memory.MemoryAllocationRepository
import app.masroufy.memory.MemoryCategoryRepository
import app.masroufy.memory.MemoryTransactionRepository
import app.masroufy.ui.screens.common.ScreenHeader
import app.masroufy.ui.text.BasicText
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.MasroufyTheme
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.LoadHomeScreen
import app.masroufy.usecase.LoadHomeScreenDeps
import app.masroufy.usecase.LoadTopCategories
import app.masroufy.usecase.TopSpendView
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * «أكتر ما صرفت عليه» مرسومة لصور PNG (بس لو `TOPSPEND_PNG_DIR` متظبط) عشان المقارنة بصورة النموذج (`masroufy-design-shots/TopSpending.png`).
 * نفس أرقام النموذج المخترعة (أكل 1,840 · مواصلات 920 · قهوة 640 …، والمجموع 5,555)، والأرقام كلها طالعة من حالة الاستخدام نفسها.
 */
class TopSpendPngTest {
    private var seq = 0

    // الاسم · الأيقونة · اللون · [المبلغ بالهللة، العدد] للشهر ده · اللي فات · اللي قبله (النموذج)
    private val design = listOf(
        listOf("أكل", "utensils", "#D15D1A", 184_000L to 23, 162_000L to 21, 175_000L to 22),
        listOf("مواصلات", "bus-front", "#2073B7", 92_000L to 14, 98_000L to 15, 91_000L to 14),
        listOf("قهوة", "coffee", "#935C25", 64_000L to 18, 51_000L to 15, 55_000L to 14),
        listOf("بقالة وسوبرماركت", "shopping-basket", "#278643", 61_000L to 9, 74_000L to 11, 70_000L to 11),
        listOf("التسوق", "shopping-bag", "#A5276F", 54_000L to 4, 12_000L to 1, 30_000L to 2),
        listOf("اتصالات", "wifi", "#11869A", 33_000L to 3, 33_000L to 3, 33_000L to 3),
        listOf("صحة وصيدليات", "heart-pulse", "#BF2722", 28_000L to 5, 9_500L to 2, 15_000L to 2),
        listOf("ترفيه", "party-popper", "#6A7E1B", 19_000L to 2, 26_000L to 3, 16_000L to 2),
        listOf("اشتراكات رقمية", "repeat", "#A43DB8", 12_000L to 3, 12_000L to 3, 12_000L to 3),
        listOf("متفرقات", "ellipsis", "#6A737C", 8_500L to 6, 11_000L to 7, 8_000L to 6),
    )

    private fun txn(date: String, amount: Long, category: String?, kind: EconomicKind = EconomicKind.PURCHASE, confirmed: Boolean = true) = Transaction(
        id = "png-${seq++}", occurredAt = date, datePrecision = "day", sourceOrder = seq, economicKind = kind, economicKindConfirmed = confirmed,
        observedDirection = Direction.OUT, amountMinor = amount, currency = Currency.SAR, categoryConfirmed = true, excludedFromBudget = false,
        reviewState = ReviewState.CONFIRMED, isCashTagged = false, createdAt = "x", updatedAt = "x", categoryId = category,
    )

    /** المبلغ مقسوم على العدد بالهللة (الباقي على أول عملية) — كل عملية في يوم من الشهر. */
    private fun month(prefix: String, id: String, total: Long, count: Int): List<Transaction> =
        (0 until count).map { i -> txn("$prefix-%02d".format(1 + i % 9), total / count + if (i == 0) total % count else 0L, id) }

    private val cats = design.mapIndexed { i, d -> Category("c-$i", null, d[0] as String, d[1] as String, d[2] as String, d[2] as String, true, i) }

    @Suppress("UNCHECKED_CAST")
    private val txns = design.indices.flatMap { i ->
        listOf("2026-10", "2026-09", "2026-08").flatMapIndexed { m, ym -> (design[i][3 + m] as Pair<Long, Int>).let { (a, c) -> month(ym, "c-$i", a, c) } }
    }

    private fun view(list: List<Transaction>, back: Int, months: Int): TopSpendView = runBlocking {
        LoadTopCategories(LoadHomeScreen(LoadHomeScreenDeps(MemoryTransactionRepository(list), MemoryCategoryRepository(cats), MemoryAllocationRepository())))
            .loadAll("2026-10-10", 28, back, months)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun png(out: File, name: String, heightDp: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(390 * 2, heightDp * 2, Density(2f)) {
            MasroufyTheme(reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFAF9F3), Color(0xFFEFF3ED), Color(0xFFDCE9DF))))) { content() }
            }
        }
        try {
            File(out, "$name.png").writeBytes(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Screen(v: TopSpendView, period: FilterPeriod) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenHeader(t(UiKey.TOP_SPEND_TITLE), onBack = {}, subtitle = t(UiKey.TOP_SPEND_CRUMB))
            TopSpendPeriodChips(period) {}
            TopSpendTotalCard(v, Currency.SAR)
            if (v.rows.isNotEmpty()) {
                TopSpendRanks(v.rows, Currency.SAR) {}
                BasicText(t(UiKey.TOP_SPEND_FOOT), style = Type.of(12, lineHeight = 1.6).copy(color = Ink.muted))
            }
        }
    }

    @Test
    fun rendersTopSpendingToPng() {
        val out = System.getenv("TOPSPEND_PNG_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        out.mkdirs()
        val now = view(txns, 0, 1)
        png(out, "TopSpending", 844) { Screen(now, FilterPeriod.THIS) }
        png(out, "TopSpending-full", 1500) { Screen(now, FilterPeriod.THIS) }
        png(out, "TopSpending-last", 1500) { Screen(view(txns, 1, 1), FilterPeriod.LAST) }
        png(out, "TopSpending-three", 1500) { Screen(view(txns, 0, 3), FilterPeriod.THREE) }
        val unknown = listOf(txn("2026-10-02", 5_000, null, EconomicKind.UNCLASSIFIED, confirmed = false).copy(suggestedKind = EconomicKind.INTERNAL_TRANSFER))
        png(out, "TopSpending-unknown", 520) { Screen(view(unknown, 0, 1), FilterPeriod.THIS) }
        png(out, "TopSpending-empty", 520) { Screen(view(emptyList(), 0, 1), FilterPeriod.THIS) }
        assertTrue(File(out, "TopSpending.png").isFile)
    }
}
