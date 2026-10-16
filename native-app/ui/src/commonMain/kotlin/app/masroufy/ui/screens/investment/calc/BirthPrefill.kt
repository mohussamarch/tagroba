package app.masroufy.ui.screens.investment.calc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.masroufy.core.IsoDate

/**
 * تاريخ الميلاد بين «حاسبة التقاعد» و«هتبقى معاك كام» (§87-8 «يتحفظ هناك»): الخانة الفاضية بتتملي من المحفوظ على الحساب مرة لما
 * اللوحة تفتح، وبعد أي حسبة ناجحة التاريخ بيتحفظ (لو اتغير) — فـ«عمرك وقتها» يظهر في «الاستثمار» من غير ما يتكتب تاني.
 */
@Composable
internal fun PrefillBirth(f: FieldsState, deps: CalculatorsDeps, field: String) {
    LaunchedEffect(deps) {
        if (f[field].isNotBlank()) return@LaunchedEffect
        val saved = runCatching { deps.birth.load() }.getOrNull() ?: return@LaunchedEffect
        if (f[field].isBlank()) f[field] = saved
    }
}

/** بيحفظ تاريخ الميلاد بعد حسبة ناجحة — فشل الحفظ ما بيوقفش الحاسبة. */
internal suspend fun saveBirth(deps: CalculatorsDeps, birth: IsoDate?) {
    if (birth == null) return
    runCatching { if (deps.birth.load() != birth) deps.birth.save(birth) }
}
