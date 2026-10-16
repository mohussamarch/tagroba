package app.masroufy.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.masroufy.core.UiKey
import app.masroufy.ui.components.FieldLabel
import app.masroufy.ui.components.TextInput
import app.masroufy.ui.icons.Lucide
import app.masroufy.ui.text.t

/**
 * الكارت الأول «بماذا نناديك؟» (النموذج `Onboarding` خطوة 1 · character-spec §4.1): الاسم + رجل/امرأة — **الاتنين لازم** (قرار المالك
 * 2026-10-16): رد «أهلًا …» و«متابعة» بيظهروا بس لما الاسم مكتوب والنوع متختار، وبيختفوا لو الاسم اتمسح. صف النوع اختيار عادي (بيتعلّم
 * على طول)، والضغط عليه والاسم فاضي بيطلّع جملة الخطأ تحت الخانة. النوع بيحدد لبس «شخصيتك» (القوايم الرجالي أو الحريمي) ويتغير بعدين
 * من «ملفك»، والاسم بيظهر في التحية. **مفيش كتابة هنا** — الاتنين بيتكتبوا في الآخر مع الباقي (`finishInput`).
 */

/** أقصى طول للاسم في الكارت ده (`maxlength="24"` في النموذج). */
const val ONB_NAME_MAX = 24

const val GENDER_MALE = "male"
const val GENDER_FEMALE = "female"

/** الاسم بعد قصّ المسافات فاضي؟ */
fun nameMissing(name: String?): Boolean = name.orEmpty().trim().isEmpty()

/** الكتابة في الخانة: [ONB_NAME_MAX] حرف بالكتير. */
fun typedName(raw: String): String = raw.take(ONB_NAME_MAX)

/** الاسم والنوع الاتنين موجودين ⇒ الخطوة خلصت و«متابعة» تشتغل. */
fun nameStepDone(a: OnbAnswers): Boolean = !nameMissing(a.name) && (a.gender == GENDER_MALE || a.gender == GENDER_FEMALE)

/** الإجابات الجديدة + الرد «أهلًا …» لو الاتنين موجودين (وإلا مفيش رد ⇒ مفيش «متابعة»). */
internal fun named(s: OnbState, answers: OnbAnswers): OnbState =
    s.copy(answers = answers, reply = if (nameStepDone(answers)) OnbReply.NAMED else null)

/** الكتابة في خانة الاسم — الرد بيتحدّث مع الاسم. */
fun typeName(s: OnbState, raw: String): OnbState = named(s, s.answers.copy(name = typedName(raw)))

/** الضغط على «رجل»/«امرأة»: بيتعلّم على طول؛ الرد بييجي لما الاسم كمان يبقى مكتوب. */
fun pickGender(s: OnbState, gender: String): OnbState = named(s, s.answers.copy(gender = gender))

/** حساب قديم فيه اسم ونوع ⇒ الكارت بيبدأ بيهم (مرة واحدة: لو لسه ما اتكتبش حاجة). */
fun prefillName(s: OnbState, name: String?, gender: String?): OnbState {
    if (s.answers.name != null || s.answers.gender != null) return s
    val a = s.answers.copy(name = name?.let(::typedName), gender = gender?.takeIf { it == GENDER_MALE || it == GENDER_FEMALE })
    return if (s.step == OnbStep.NAME) named(s, a) else s.copy(answers = a)
}

/** صفين النوع (نفس سطر الاختيار بتاع البلد والمصدر). */
fun genderOptions(): List<OnbOption> = listOf(
    OnbOption(GENDER_MALE, t(UiKey.ONB_GENDER_MALE), t(UiKey.ONB_GENDER_MALE_SUB), null, off = false),
    OnbOption(GENDER_FEMALE, t(UiKey.ONB_GENDER_FEMALE), t(UiKey.ONB_GENDER_FEMALE_SUB), null, off = false),
)

@Composable
internal fun NameCard(s: OnbState, onChange: (OnbState) -> Unit) {
    var bad by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextInput(
            s.answers.name.orEmpty(), { onChange(typeName(s, it)); bad = false }, label = t(UiKey.ONB_NAME_LABEL),
            placeholder = t(UiKey.ONB_NAME_PLACEHOLDER), error = if (bad && nameMissing(s.answers.name)) t(UiKey.ONB_NAME_ERROR) else null,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(t(UiKey.ONB_GENDER_LABEL), Modifier)
            for (o in genderOptions()) {
                OnbOptionRow(o, s.answers.gender == o.id, Lucide.USER) {
                    if (nameMissing(s.answers.name)) bad = true
                    onChange(pickGender(s, o.id))
                }
            }
        }
    }
}
