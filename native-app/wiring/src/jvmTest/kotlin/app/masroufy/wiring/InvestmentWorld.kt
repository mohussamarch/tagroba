package app.masroufy.wiring

import app.masroufy.core.Currency
import app.masroufy.core.Space
import app.masroufy.core.UserProfile
import app.masroufy.core.Wallet
import app.masroufy.memory.MemoryAccount
import app.masroufy.memory.MemoryHttpText
import app.masroufy.ui.screens.investment.InvestmentDeps
import app.masroufy.usecase.FEEDS_BASE_URL
import app.masroufy.usecase.LoadOnlineFeeds

/**
 * بلد واحدة بمستودعات الذاكرة لاختبار شاشات «الاستثمار» على التجميع الحقيقي (`SpaceGraph` ⇒ `InvestmentGraph`) — نفس اللي الجوال بيعمله.
 * ملف الأسعار وهمي: ذهب صافي وفضة بالريال وبالجنيه + سطر عيار ٢١ للربط. **كل الأسماء والأرقام مخترعة.**
 */
class InvestmentWorld(
    val space: Space,
    wallets: List<Wallet> = emptyList(),
    profile: UserProfile? = null,
    today: String = "2026-10-09",
    pricesAsOf: String = "2026-10-08",
    withPrices: Boolean = true,
) {
    val repos = memorySpaceRepositories(wallets, profile = profile)
    private val http = MemoryHttpText().also {
        if (withPrices) it[FEEDS_BASE_URL + "prices.json"] = pricesJson(pricesAsOf)
    }
    val env = memoryEnv(today = today, http = http)
    private val session = object : SessionLinks {
        override val account = MemoryAccount()
        override fun spaces() = listOf(space to repos)
        override fun switchSpace(spaceId: String): Boolean = false
    }
    val graph = SpaceGraph(space, repos, env, session, LoadOnlineFeeds(env.http, env.feedCache, env.clock, env.nowMillis))
    val deps: InvestmentDeps get() = graph.investment

    companion object {
        val SAUDI = Space("sa", "السعودية", "SA", Currency.SAR, "2026-01-01T00:00:00.000Z")
        val EGYPT = Space("eg", "مصر", "EG", Currency.EGP, "2026-02-01T00:00:00.000Z")

        /** الجرام الصافي ٣٠٠ ر.س والفضة ٣٫٥٠ ⇒ نصاب السعودية = فضة ٥٩٥ جم = 2,082.50 · الجنيه: ٤٬٠٠٠ للجرام الصافي. عيار ٢١ = ٢٦٢٫٥٠. */
        fun pricesJson(asOf: String) = """{"generatedAt":"${asOf}T05:00:00Z","baseCurrency":"SAR","prices":{
            "GOLD_24K_GRAM":{"name":"ذهب صافي","unit":"جرام","pricePerUnitMinor":30000,"asOf":"$asOf","source":"مصدر وهمي"},
            "SILVER_GRAM":{"name":"فضة","unit":"جرام","pricePerUnitMinor":350,"asOf":"$asOf","source":"مصدر وهمي"},
            "GOLD_21K_GRAM":{"name":"ذهب عيار ٢١","unit":"جرام","pricePerUnitMinor":26250,"asOf":"$asOf","source":"مصدر وهمي"},
            "GOLD_24K_GRAM_EGP":{"name":"ذهب صافي","unit":"جرام","pricePerUnitMinor":400000,"asOf":"$asOf","source":"مصدر وهمي","currency":"EGP"}
        }}"""
    }
}
