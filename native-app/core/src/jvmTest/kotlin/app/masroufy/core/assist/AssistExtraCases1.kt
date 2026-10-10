package app.masroufy.core

/**
 * صيغ زيادة على أمثلة التصميم — الجزء ١: الإضافة بالكتابة (المبلغ بالوحدة الصغرى · الأرقام العربي · كلمات الأرقام · العملة) والكارت
 * المستني. كل الأسامي والمبالغ مخترعة.
 */
private val COFFEE = seedCategoryId("مطاعم وقهوة", "قهوة ومشروبات")
private val FOOD = seedCategoryId("مطاعم وقهوة", "مطاعم")
private val GROCERY = seedCategoryId("بقالة وسوبرماركت")
private val FUEL = seedCategoryId("السيارة", "وقود")
private val TRANSPORT = seedCategoryId("السيارة", "مواصلات عامة")
private val PHARMACY = seedCategoryId("صحة وصيدليات", "صيدلية")
private val STREAMING = seedCategoryId("اشتراكات رقمية", "بث وأفلام")
private val PARKING = seedCategoryId("السيارة", "مواقف وسايس")

internal val EXTRA_CASES_1: List<AssistCase> = listOf(
    AssistCase("قهوة ١٥", "action.quick_add", subject = COFFEE, amount = 1_500),
    AssistCase("قهوه 15", "action.quick_add", subject = COFFEE, amount = 1_500),
    AssistCase("قهوة 15.5", "action.quick_add", amount = 1_550),
    AssistCase("قهوة ١٥٫٥", "action.quick_add", amount = 1_550),
    AssistCase("قهوة ١٥٫٥٠ ريال", "action.quick_add", amount = 1_550),
    AssistCase("دفعت ١٬٢٥٠ ايجار", "action.quick_add", subject = "r-rent", amount = 125_000),
    AssistCase("دفعت 1,250 للإيجار", "action.quick_add", subject = "r-rent", amount = 125_000),
    AssistCase("ايجار 2.500", "action.quick_add", subject = "r-rent", amount = 250_000),
    AssistCase("بنزين ٨٠", "action.quick_add", subject = FUEL, amount = 8_000),
    AssistCase("بانزين 80 ريال", "action.quick_add", subject = FUEL, amount = 8_000),
    AssistCase("صرفت 200 بقالة كاش", "action.quick_add", subject = GROCERY, amount = 20_000),
    AssistCase("غدا ٣٥ امبارح", "action.quick_add", subject = FOOD, amount = 3_500),
    AssistCase("عشا بـ80 من الراجحي", "action.quick_add", subject = FOOD, amount = 8_000),
    AssistCase("حط ٥٠ مواصلات", "action.quick_add", subject = TRANSPORT, amount = 5_000),
    AssistCase("دفعت خمسه وعشرين ريال قهوة", "action.quick_add", subject = COFFEE, amount = 2_500),
    AssistCase("ميتين ريال بقالة", "action.quick_add", subject = GROCERY, amount = 20_000),
    AssistCase("٢ ألف إيجار", "action.quick_add", subject = "r-rent", amount = 200_000),
    AssistCase("ألفين ايجار", "action.quick_add", subject = "r-rent", amount = 200_000),
    AssistCase("الف وخمسميه بقاله", "action.quick_add", subject = GROCERY, amount = 150_000),
    AssistCase("مواقف ٥ ريال", "action.quick_add", subject = PARKING, amount = 500),
    AssistCase("coffee 15", "action.quick_add", subject = COFFEE, amount = 1_500),
    AssistCase("lunch 35 sar", "action.quick_add", subject = FOOD, amount = 3_500),
    AssistCase("صيدلية ٤٥", "action.quick_add", subject = PHARMACY, amount = 4_500),
    AssistCase("نتفلكس 45", "action.quick_add", subject = STREAMING, amount = 4_500),
    AssistCase("كهربا ٣٨٠", "action.quick_add", subject = "r-elec", amount = 38_000),
    AssistCase("دفعت فاتورة النت 230", "action.quick_add", subject = "r-net", amount = 23_000),
    AssistCase("قهوة في مقهى المرسى 23", "action.quick_add", subject = "m-marsa", amount = 2_300),
    AssistCase("المرسى ٢٣", "action.quick_add", subject = "m-marsa", amount = 2_300),
    AssistCase("قهوة 15 و 20", "action.quick_add", amount = 1_500),
    AssistCase("قهوه ١٥ دولار", "action.quick_add", amount = 1_500),
    AssistCase("قهوة بكرة 15", "action.quick_add", amount = 1_500),
    AssistCase("رسوم التحويل ١٥", "action.quick_add", amount = 1_500),
    AssistCase("شاورما ٢٢ امس", "action.quick_add", subject = FOOD, amount = 2_200),
    AssistCase("ابي اسجل عشاء ب80", "action.quick_add", subject = FOOD, amount = 8_000),
    AssistCase("ابغى اسجل قهوة 18", "action.quick_add", subject = COFFEE, amount = 1_800),
    AssistCase("سجل لي ٤٠ بنزين", "action.quick_add", subject = FUEL, amount = 4_000),
    AssistCase("اشتريت خضار ب٣٠", "action.quick_add", amount = 3_000),
    AssistCase("جبت عيش ١٠", "action.quick_add", amount = 1_000),
    AssistCase("50 هللة مواقف", "action.quick_add", amount = 50),
    AssistCase("تاكسي ٢٥", "action.quick_add", amount = 2_500),
    AssistCase("اوبر 32", "action.quick_add", amount = 3_200),
    AssistCase("هدية 150", "action.quick_add", subject = GiftCategories.ROOT, amount = 15_000),
    AssistCase("قهوة 15.555", "action.quick_add"),
    AssistCase("ححط 50 قهوه", "action.quick_add", amount = 5_000),
    AssistCase("قهوة كاااااش 15", "action.quick_add", amount = 1_500),
    // الكارت المستني: تعديل · تأكيد · إلغاء — وكلمة مصروف جديدة أو سؤال ⇒ مش تعديل
    AssistCase("لا خليها ٢٠", "action.edit_pending", pending = true),
    AssistCase("خليها 20", "action.edit_pending", pending = true),
    AssistCase("٢٠ مش ١٥", "action.edit_pending", pending = true),
    AssistCase("كاش", "action.edit_pending", pending = true),
    AssistCase("من الكاش", "action.edit_pending", pending = true),
    AssistCase("بالبطاقة", "action.edit_pending", pending = true),
    AssistCase("من الراجحي", "action.edit_pending", pending = true),
    AssistCase("خليها امبارح", "action.edit_pending", pending = true),
    AssistCase("التصنيف بقالة", "action.edit_pending", pending = true),
    AssistCase("غير التصنيف لصيدلية", "action.edit_pending", pending = true),
    AssistCase("أيوه", "action.confirm_pending", pending = true),
    AssistCase("ايوا سجلها", "action.confirm_pending", pending = true),
    AssistCase("تمام", "action.confirm_pending", pending = true),
    AssistCase("اوكي", "action.confirm_pending", pending = true),
    AssistCase("نعم احفظ", "action.confirm_pending", pending = true),
    AssistCase("زين", "action.confirm_pending", pending = true),
    AssistCase("لا", "action.cancel_pending", pending = true),
    AssistCase("لأ", "action.cancel_pending", pending = true),
    AssistCase("لا ما ابي", "action.cancel_pending", pending = true),
    AssistCase("الغيها", "action.cancel_pending", pending = true),
    AssistCase("مش عايز", "action.cancel_pending", pending = true),
    AssistCase("قهوة ١٥", "action.quick_add", pending = true, amount = 1_500),
    AssistCase("صرفت كام الشهر ده؟", "data.spend.total", pending = true),
    // التقسيم
    AssistCase("قسم ٣٦٠ مع احمد وساره", "action.split", subject = "p-ahmed", amount = 36_000),
    AssistCase("قسّم الحساب 300 بيني وبين خالد", "action.split", subject = "p-khaled", amount = 30_000),
    AssistCase("نقسم ١٢٠ على ٣", "action.split", amount = 12_000),
    AssistCase("split 90 with Sara", "action.split", amount = 9_000),
    AssistCase("قسم العشا مع فهد", "action.split", subject = "p-fahd"),
    // المحفظة الأساسية
    AssistCase("خلي الكاش هي الاساسية", "action.set_main_wallet", subject = "w-cash"),
    AssistCase("بصرف عادة من الراجحي", "action.set_main_wallet", subject = "w-bank"),
    AssistCase("اجعل البنك محفظتي الاساسيه", "action.set_main_wallet", subject = "w-bank"),
    // تسجيل فاتورة دورية من غير مبلغ
    AssistCase("سجل فاتورة الكهربا", "action.record_due_bill", subject = "r-elec"),
    AssistCase("سجّل الإيجار", "action.record_due_bill", subject = "r-rent"),
    AssistCase("دفعت فاتورة النت", "action.record_due_bill", subject = "r-net"),
)
