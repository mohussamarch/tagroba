package app.masroufy.ui.screens.investment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.masroufy.core.Currency
import app.masroufy.core.Id
import app.masroufy.core.TextKey
import app.masroufy.core.ZakatLineKind
import app.masroufy.core.currencySymbol
import app.masroufy.ui.app.LocalSpace
import app.masroufy.ui.components.AmountText
import app.masroufy.ui.components.AmountTone
import app.masroufy.ui.components.FieldError
import app.masroufy.ui.components.FloatingCard
import app.masroufy.ui.components.PrimaryButton
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.components.TonalButton
import app.masroufy.ui.components.pressScale
import app.masroufy.ui.components.rememberPress
import app.masroufy.ui.components.tap
import app.masroufy.ui.glass.insetRing
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.icons.LucideIcon
import app.masroufy.ui.text.t
import app.masroufy.ui.theme.Ink
import app.masroufy.ui.theme.Type
import app.masroufy.usecase.ZakatPaymentSource
import kotlinx.coroutines.launch


/**
 * «دفع زكاة السنة» (`ZakatPay`) بعد التثبيت: المطلوب · دُفع · الباقي (من `PayZakat.status`) · السطور بخانة «دُفع» · «دفعت الكل» ·
 * «كيف دفعتها؟» (عملية من الكشف ⇒ `pay` — أو كاش ⇒ `payCash`) · الدفعات بـ«فك» (`unlink`). التوزيع على السطور والصدقة الزيادة في حالة الاستخدام.
 */
@Composable
internal fun ZakatPaySection(pay: ZakatPayUi, currency: Currency, showTitle: Boolean = true, onChanged: () -> Unit) {
    val deps = LocalSpace.current.investment
    val scope = rememberCoroutineScope()
    var picked by remember(pay) { mutableStateOf(emptySet<ZakatLineKind>()) }
    var how by remember(pay) { mutableStateOf<Id?>(null) }
    var cash by remember(pay) { mutableStateOf("") }
    var error by remember(pay) { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var ops by remember { mutableStateOf<List<LinkableOp>>(emptyList()) }
    LaunchedEffect(pay.yearId) { ops = linkableOps(runCatching { deps.recentOperations() }.getOrNull(), outgoing = true, currency) }
    fun submit(block: suspend () -> Unit) {
        scope.launch {
            try { block(); status = t(TextKey.ZAKAT_PAY_RECORDED); error = null; onChanged() } catch (e: IllegalArgumentException) { error = e.message }
        }
    }
    fun record() {
        when (val r = zakatPayRequest(picked, how, cash, currency)) {
            is ZakatPayRequest.Invalid -> error = r.message
            is ZakatPayRequest.Cash -> submit { deps.payZakat.payCash(pay.yearId, r.lines, r.amountMinor, deps.today()) }
            is ZakatPayRequest.FromOperation -> submit { deps.payZakat.pay(pay.yearId, r.lines, listOf(ZakatPaymentSource(r.transactionId))) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            if (showTitle) BasicText(t(TextKey.ZAKAT_PAY_TITLE), style = Type.section())
            BasicText(pay.yearLabel, style = Type.caption().copy(color = Ink.muted))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SumTile(t(TextKey.ZAKAT_PAY_DUE), pay.dueMinor, currency, Ink.text, Modifier.weight(1f))
            SumTile(t(TextKey.ZAKAT_PAY_PAID), pay.paidMinor, currency, Ink.income, Modifier.weight(1f))
            SumTile(t(TextKey.ZAKAT_PAY_LEFT), pay.leftMinor, currency, if (pay.leftMinor > 0) Ink.expense else Ink.income, Modifier.weight(1f))
        }
        // الشريط بأوزان المبلغين نفسهم (من حالة الاستخدام) — من غير نسبة محسوبة
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Ink.text.copy(alpha = 0.08f))) {
            if (pay.paidMinor > 0) Box(Modifier.weight(pay.paidMinor.toFloat()).height(8.dp).background(Ink.income))
            if (pay.leftMinor > 0) Box(Modifier.weight(pay.leftMinor.toFloat()).height(8.dp))
        }
        pay.allPaidText?.let {
            BasicText(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.selected).padding(horizontal = 14.dp, vertical = 12.dp),
                style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary))
        }
        FloatingCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            pay.lines.forEachIndexed { i, line ->
                if (i > 0) RowGap()
                PayLineRow(line, line.done || line.kind in picked, currency) {
                    if (!line.done) { picked = if (line.kind in picked) picked - line.kind else picked + line.kind; error = null; status = null }
                }
            }
        }
        TonalButton(t(TextKey.ZAKAT_PAY_ALL), { picked = unpaidKinds(pay); error = null; status = null }, Modifier.fillMaxWidth(), enabled = unpaidKinds(pay).isNotEmpty())
        if (picked.isNotEmpty()) FloatingCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(t(TextKey.ZAKAT_PAY_HOW), style = Type.bodyBold())
                if (ops.isNotEmpty()) BasicText(t(TextKey.ZAKAT_PAY_FROM_STATEMENT), style = Type.caption().copy(color = Ink.muted))
                else BasicText(t(TextKey.ZAKAT_PAY_NO_OPS), style = Type.caption().copy(color = Ink.muted))
                for (op in ops) OpChoice(t(TextKey.INVEST_ROW_SUB, op.title, dateText(op.date)), op, how == op.id) { how = op.id; error = null }
                OpChoice(t(TextKey.ZAKAT_PAY_CASH), null, how == PAY_CASH) { how = PAY_CASH; error = null }
                if (how == PAY_CASH) {
                    BasicText(t(TextKey.ZAKAT_PAY_CASH_SUB), style = Type.caption().copy(color = Ink.muted))
                    TextInput(cash, { cash = it; error = null }, label = t(TextKey.ZAKAT_PAY_CASH_LABEL), placeholder = "0.00", ltr = true,
                        keyboard = KeyboardType.Decimal, trailing = { FieldUnit(currencySymbol(currency)) })
                }
                BasicText(t(TextKey.ZAKAT_PAY_NOTE), style = Type.caption().copy(color = Ink.muted))
                error?.let { FieldError(it) }
                PrimaryButton(t(TextKey.ZAKAT_PAY_RECORD), ::record, Modifier.fillMaxWidth())
            }
        }
        if (pay.payments.isNotEmpty()) {
            BasicText(t(TextKey.ZAKAT_PAY_PAYMENTS), style = Type.bodyBold())
            for (p in pay.payments) {
                QuietBox {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(p.label, style = Type.bodyBold())
                            BasicText(p.sub, style = Type.caption().copy(color = Ink.muted))
                        }
                        AmountText(p.amountMinor, currency, tone = AmountTone.EXPENSE, size = 14, showCurrency = false)
                        TonalButton(t(TextKey.ZAKAT_PAY_UNLINK), { submit { deps.payZakat.unlink(pay.yearId, p.id); t(TextKey.ZAKAT_PAY_UNLINKED) } }, height = 44.dp)
                    }
                }
            }
        }
        status?.let { BasicText(it, style = Type.of(13, FontWeight.Bold).copy(color = Ink.primary)) }
    }
}

@Composable
private fun SumTile(label: String, minor: Long, currency: Currency, ink: Color, modifier: Modifier) {
    FloatingCard(modifier, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(10.dp)) {
        BasicText(label, style = Type.of(11).copy(color = Ink.muted))
        AmountText(minor, currency, showCurrency = false, color = ink)
    }
}

/** سطر بخانة «دُفع» (مربع 24 بزاوية 8): المدفوع أخضر ومقفول، والمختار أخضر غامق. */
@Composable
private fun PayLineRow(line: PayLine, checked: Boolean, currency: Currency, onToggle: () -> Unit) {
    val press = rememberPress()
    val box = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(press, !line.done).tap(press, !line.done, role = Role.Checkbox, label = line.label, onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fill = when {
            line.done -> Modifier.clip(box).background(Ink.income)
            checked -> Modifier.clip(box).background(Ink.primary)
            else -> Modifier.clip(box).background(Color.White).insetRing(box, 1.5.dp, Ink.faded)
        }
        Box(Modifier.size(24.dp).then(fill), contentAlignment = Alignment.Center) {
            if (checked) LucideIcon(Lucide.CHECK, size = 14.dp, tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            BasicText(line.label, style = Type.bodyBold())
            BasicText(line.sub, style = Type.caption().copy(color = Ink.muted))
        }
        AmountText(line.dueMinor, currency, size = 14, showCurrency = false, color = if (line.done) Ink.income else Ink.text)
    }
}
