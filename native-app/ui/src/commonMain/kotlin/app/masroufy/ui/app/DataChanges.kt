package app.masroufy.ui.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * «البيانات اتغيرت»: عدّاد بيزيد بعد كل كتابة ناجحة من حاجة مفتوحة **فوق** شاشة لسه ظاهرة (لوحة «+» فوق الرئيسية · تأكيد في المراجعة).
 * الشاشة اللي بتعرض أرقام بتحط [version] في مفتاح قرايتها (`LaunchedEffect(deps, changes.version)`) ⇒ بتنده حالات الاستخدام تاني
 * من غير ما المستخدم يسيب التبويب — الشاشة ما بتحسبش حاجة بنفسها (CLAUDE.md #4). عايش في `ShellState` (طول ما التطبيق مفتوح).
 */
@Stable
class DataChanges {
    var version by mutableIntStateOf(0)
        private set

    /** كتابة نجحت ⇒ اللي بيعرض أرقام يقرا تاني. */
    fun changed() {
        version++
    }
}

val LocalDataChanges = staticCompositionLocalOf { DataChanges() }

/**
 * 0.5.2: الرجوع للتطبيق من الخلفية (مش أول فتح) ⇒ «البيانات اتغيرت»: اللي اتسجل في الخلفية (رسالة بنك — العامل بيكتب من غير ما الشاشة
 * تعرف) يبان، ولوحة «ادفع بعدين» (`BnplHost`) تتفحص تاني. أول ظهور ما بيعملش حاجة (الشاشات لسه بتقرا أصلًا).
 */
class ResumeRefresh(private val changes: DataChanges) {
    private var shownOnce = false

    fun onVisible() {
        if (shownOnce) changes.changed() else shownOnce = true
    }
}
