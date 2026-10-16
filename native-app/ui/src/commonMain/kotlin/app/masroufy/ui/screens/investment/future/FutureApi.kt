package app.masroufy.ui.screens.investment.future

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.masroufy.core.CountriesView
import app.masroufy.core.FutureChoice
import app.masroufy.core.FutureInputs
import app.masroufy.core.FutureMix
import app.masroufy.core.FuturePath
import app.masroufy.core.FutureProjection
import app.masroufy.core.FxRate
import app.masroufy.core.HoldingsSnapshot
import app.masroufy.core.InvestRatesState
import app.masroufy.core.normalizeDigits
import app.masroufy.core.stepAtLeast
import app.masroufy.core.uiText
import app.masroufy.core.InvestFutureKey
import app.masroufy.usecase.ManageInvestView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * حالة «هتبقى معاك كام» المرفوعة (مواصفة النقل §4.1): المدخلات بتتحمّل مرة لكل تحميل للشاشة (اللقطة والمتوسطات جايين من الشاشة)،
 * وكل حساب على `Dispatchers.Default` في كوروتين لوحده — مفيش جزء بيوقّف التاني، والتحديث ما بيمسحش الأرقام القديمة (شكوى التعليق 4-5 ثواني).
 */
@Stable
class InvestFutureState internal constructor(private val deps: InvestFutureDeps, val spaceId: String, private val scope: CoroutineScope) {
    var inputs by mutableStateOf<FutureInputs?>(null); private set
    var choice by mutableStateOf(FutureChoice()); private set
    var projection by mutableStateOf<FutureProjection?>(null); private set
    var countries by mutableStateOf<CountriesView?>(null); private set
    var failed by mutableStateOf(false); private set
    var wheelOpen by mutableStateOf(false); private set
    private var others: List<FutureInputs> = emptyList()
    private var fx: FxRate? = null
    private var restored = false
    private var job: Job? = null

    /** لسه بيحمّل أول مرة (مفيش أرقام قديمة نعرضها). */
    val loading: Boolean get() = inputs == null && !failed

    val thisYear: Int get() = deps.today().substring(0, 4).toInt()

    internal suspend fun load(holdings: HoldingsSnapshot, rates: InvestRatesState) {
        val today = deps.today()
        try {
            val loaded = withContext(Dispatchers.Default) {
                val mine = deps.inputs.load(today, holdings, rates)
                Triple(mine, runCatching { deps.inputs.otherSpaces(today) }.getOrElse { emptyList() }, runCatching { deps.inputs.fx() }.getOrNull())
            }
            if (!restored) {
                choice = runCatching { deps.view.load(spaceId) }.getOrElse { FutureChoice() }
                restored = true
            }
            others = loaded.second
            fx = loaded.third
            inputs = loaded.first
            failed = false
            recompute()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = inputs == null
        }
    }

    fun openWheel() { wheelOpen = true }
    fun closeWheel() { wheelOpen = false }
    fun pickYears(k: Int) = update(choice.copy(years = k), persist = false)
    fun pickPath(p: FuturePath) = update(choice.copy(path = p), persist = true)
    fun pickMix(m: FutureMix) = update(choice.copy(mix = m), persist = true)
    fun setTodayMoney(on: Boolean) = update(choice.copy(todayMoney = on), persist = false)

    fun plus() {
        val p = projection ?: return
        update(choice.copy(path = p.selected, amountMinor = minOf(p.maxAmountMinor, p.amountMinor + p.stepMinor)), persist = true)
    }

    fun minus() {
        val p = projection ?: return
        update(choice.copy(path = p.selected, amountMinor = maxOf(p.stepMinor, p.amountMinor - p.stepMinor)), persist = true)
    }

    /** «سجّلها علامة» من النص المكتوب (أرقام عربي هندي وفارسي مقبولة) — يرجّع رسالة الخطأ أو null لو اتسجلت. */
    fun addMark(typed: String): String? {
        val range = ManageInvestView.markRange(thisYear)
        val year = normalizeDigits(typed).trim().toIntOrNull()
        if (year == null || year !in range) return uiText(InvestFutureKey.FUT_WHEEL_ERR, range.first.toString(), range.last.toString())
        val marks = (choice.marks + year).distinct().sorted()
        val span = year - thisYear
        update(choice.copy(marks = marks, years = if (span > choice.years) stepAtLeast(span) else choice.years), persist = false)
        scope.launch { runCatching { deps.view.addMark(spaceId, year, thisYear) } }
        return null
    }

    fun removeMark(year: Int) {
        update(choice.copy(marks = choice.marks.filter { it != year }), persist = false)
        scope.launch { runCatching { deps.view.removeMark(spaceId, year) } }
    }

    private fun update(c: FutureChoice, persist: Boolean) {
        choice = c
        recompute()
        if (persist) scope.launch { runCatching { deps.view.save(spaceId, c) } }
    }

    private fun recompute() {
        val i = inputs ?: return
        val c = choice
        val o = others
        val rate = fx
        job?.cancel()
        job = scope.launch {
            val (p, cv) = withContext(Dispatchers.Default) { deps.project.project(i, c) to deps.project.countries(i, o, c, rate) }
            projection = p
            countries = cv
        }
    }
}

/** الحالة لبلد [spaceId]. [holdings] و[rates] من الشاشة (بتتحمّل مرة واحدة) — null = لسه بتتحمّل (الأرقام القديمة بتفضل). */
@Composable
fun rememberInvestFuture(deps: InvestFutureDeps, spaceId: String, holdings: HoldingsSnapshot?, rates: InvestRatesState?): InvestFutureState {
    val scope = rememberCoroutineScope()
    val state = remember(deps, spaceId) { InvestFutureState(deps, spaceId, scope) }
    LaunchedEffect(state, holdings, rates) {
        if (holdings != null && rates != null) state.load(holdings, rates)
    }
    return state
}

/** البطاقة البطلة «إنت كمان (10 سنين)» — مفتاح `future-hero`. */
fun LazyListScope.investFutureHero(state: InvestFutureState) {
    item(key = "future-hero") { FutureHero(state) }
}

/** «اختار طريقك» والتشارت والكروت التلاتة — بس لو التوقع متاح (المتوسطات موجودة). */
fun LazyListScope.investFutureBody(state: InvestFutureState) {
    if (state.projection == null) return
    item(key = "future-paths") { FuturePathsCard(state) }
    item(key = "future-chart") { FutureChartCard(state) }
    item(key = "future-cards") { FutureCards(state) }
}

/** بكرة السنين فوق الشاشة كلها. */
@Composable
fun InvestFutureOverlays(state: InvestFutureState) {
    if (state.wheelOpen) YearWheel(state)
}
