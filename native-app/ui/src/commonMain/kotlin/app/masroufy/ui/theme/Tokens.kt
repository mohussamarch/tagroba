package app.masroufy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * رموز نظام التصميم v0.4 (`design-source/design-system-v0.4/tokens.json` على فرع التصميم) — فاتح + غامق (قرار المالك 2026-10-22 «المظهر الليلي»).
 * كل لون هنا `mix(فاتح, غامق)` بيتقري من [Appearance.progress] ⇒ الشاشة بتتبدل في 300ms من غير ما تتعدّل.
 * الغامق (تصميم `NightPrivacy`): خلفية #0F1A17 · كروت #18241F · نص #E6EFEA · باهت #9AB0A7 · أخضر #3FBF8F · خطوط rgba(230,239,234,.08) · البطاقة البطلة #0B5A47→#083D31.
 * أي لون أو مقاس في شاشة لازم ييجي من هنا — ممنوع رقم جديد من غير قرار مكتوب.
 * ⚠️ ما تخزّنش لون من هنا في `val` على مستوى الملف ولا في `enum` — هيتجمّد على المظهر وقت التحميل؛ اقراه جوه الرسم.
 */
object Ink {
    val text get() = mix(0xFF193D33, 0xFFE6EFEA)
    val muted get() = mix(0xFF637570, 0xFF9AB0A7)
    val primary get() = mix(0xFF08634F, 0xFF3FBF8F)
    /** أبيض ثابت: على زرار أساسي وعلى البطاقة البطلة (بترولية في المظهرين). */
    val onPrimary = Color(0xFFFFFFFF)
    /** الخلفية الصلبة للشاشة. */
    val background get() = mix(0xFFEFF3ED, 0xFF0F1A17)
    /** الكارت الصلب. */
    val card get() = mix(0xFFFFFFFF, 0xFF18241F)
    /** سطح خفيف جوه كارت (شريحة · شريط تبويب): `rgba(25,61,51,.06)` ⇒ `rgba(230,239,234,.06)`. */
    val softSurface get() = mix(0x0F193D33, 0x0FE6EFEA)
    /** الخيار المختار في شريط تبويبات جوه كارت (أبيض ⇒ #24342E). */
    val segmentOn get() = mix(0xFFFFFFFF, 0xFF24342E)
    val heroStart get() = mix(0xFF064B40, 0xFF0B5A47)
    val heroEnd get() = mix(0xFF08705A, 0xFF083D31)
    val income get() = mix(0xFF13764D, 0xFF3FBF8F)
    val expense get() = mix(0xFFBE3D48, 0xFFFF8A93)
    val transfer get() = mix(0xFF2469BA, 0xFF7FB2F0)
    /** الفواصل: `#CCD8CC` بشفافية 55% ⇒ `rgba(230,239,234,.08)`. */
    val line get() = mix(0x8CCCD8CC, 0x14E6EFEA)
    val selected get() = mix(0xFFDCEBD6, 0xFF24342E)
    val focus get() = mix(0xFF956000, 0xFFF3C96B)
    val alertBg get() = mix(0xFFFBF0DD, 0xFF3A2F17)
    val surface get() = mix(0xFFFFFDF9, 0xFF18241F)
    /** نقطة الإشعار (KOTLIN-MAP §3): 6×6 من غير إطار. */
    val dot get() = mix(0xFFD93A47, 0xFFFF6B76)
    /** نص ثانوي على البطاقة البترولية (ثابت — البطاقة بترولية في المظهرين). */
    val onHeroMuted = Color(0xFFDCEBD6)
    val heroProgress = Color(0xFFA6DEC1)
    val heroTrack = Color(0x2EFFFFFF)
    val lensInk = Color(0xFFDAF8E9)
    val mint = Color(0xFFA6DEC1)
    val sky = Color(0xFF9BC5FF)
    val amber = Color(0xFFF3C96B)
    val rose = Color(0xFFFFA8AC)
    /** نص شرح أهدى من [text] وأتقل من [muted] — `#4F625C` في النموذج (مقدمة المساعد `AssistantStart` · تلميح «اسحب لفوق»). */
    val soft get() = mix(0xFF4F625C, 0xFFB7C8BF)
    /** نص ميت (يوم فات في التقويم). */
    val faded get() = mix(0xFF9AA8A3, 0xFF5F716A)
    /** حدود الحقول: `rgba(204,216,204,0.9)`. */
    val fieldEdge get() = mix(0xE6CCD8CC, 0x2EE6EFEA)

    /** [text] بشفافية [alpha] من 255 — بديل `Color(0x0A193D33)` (خطوط وأسطح خفيفة بتتبدل مع المظهر). */
    fun tint(alpha: Int): Color = text.copy(alpha = alpha / 255f)
}

/** ألوان التصنيفات — للأيقونة وخلفيتها بشفافية 12% بس. **المبلغ ما بيورثش لون التصنيف.** */
object CategoryInk {
    val restaurants = Color(0xFFA36A21)
    val shopping = Color(0xFF3866A7)
    val savings = Color(0xFF27785B)
    val transport = Color(0xFF4D747C)
    val gifts = Color(0xFFA55060)
}

/** سلّم الزوايا (DESIGN-SYSTEM «المقاسات والزوايا») — ممنوع زاوية من برّه السلّم. */
object Radius {
    val iconTile = 12.dp
    val lensSmall = 16.dp
    val control = 18.dp
    val card = 22.dp
    val nav = 26.dp
    val lensLarge = 26.dp
    val hero = 28.dp
    val menu = 28.dp
    val sheet = 32.dp
}

/** المسافات والتخطيط (tokens.json `layout`). */
object Space {
    val gutter = 20.dp
    val block = 14.dp
    val listGap = 8.dp
    val listGapWide = 12.dp
    val touch = 48.dp
    val navHeight = 70.dp
    /** v0.4.1: الشريط على خط المحتوى (OVERRIDES §73) — مش 12. */
    val navInset = 20.dp
    val navBottom = 14.dp
    /** شريط «اسأل مصروفي»: 44 مرئي جوه مساحة لمس 48، اللمس على بعد 6 بس فوق شريط التنقل (`14 + 70 + 6 = 90` — طلب المالك §76، KOTLIN-MAP §3). */
    val askHeight = 44.dp
    val askGap = 6.dp
    /** المسافة تحت آخر عنصر في قايمة جوه تبويب (الشريطين تحت) ≈ 156. */
    val tabContentBottom = 156.dp
    /** رأس الأقسام الأربعة: صف أول 48 والزراير 48×48 بزاوية 18. */
    val headerRow = 48.dp
}

/** مقاسات الخط (tokens.json `typography.size`). */
object TextSize {
    const val caption = 12
    const val body = 14
    const val section = 18
    const val title = 24
    const val amount = 38
    const val amountSmall = 34
}
