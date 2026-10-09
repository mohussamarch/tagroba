package app.masroufy.core

/**
 * **كلمات مش اسم** في الخانة الحرة (الجولة السابعة — المراجعة العدائية التالتة لفرع الجولة السادسة): اسم المحل · اسم الطرف · الخدمة ·
 * السبب. الجولة السادسة حصرت الخانة بالطول والعلامات، بس جوه الحد كانت أي جملة بتعدّي لو مفيهاش كلمة من قايمة الحالة: «From: YAZEED
 * SAMPLE wasn't completed» · «Transaction: on queue» · «At: OKAPI FUEL 14 ESTIMATED» · «المسجل باسم مازن التجريبي محتاج موافقتك» ·
 * «من PROBE STORE بمجرد استلام المبلغ من التاجر» · «Service: payable next week».
 *
 * الحل هنا **مش** كلمات حالة زيادة بس: الخانة لازم تبقى **كلمات اسم**، والكلام ده نوع **محدود** (closed class) عمره ما بيبقى جزء
 * من اسم شخص أو محل:
 * - حروف الجر والعطف والأدوات والضماير المتصلة بالحساب («في · من · خلال · حين · حسابك · بياناتك» · to · for · by · within · your).
 * - الأفعال المساعدة والنفي (be · is · was · wasn't · did · not · shall · will · لم · لن · لسه).
 * - الوقت والمدة (يومين · ساعة · اسبوع · بكره · next · week · days · hours · tomorrow).
 * - المستقبل والمضارع والماضي المصري من أفعال الفلوس («سيضاف · يتم · سترجع · هيوصلك · هيتفعل · اتحجزت»).
 * - كلام الإجراء والحالة (موافقة · تحقق · توثيق · تفعيل · تقديري · تأمين · approval · kyc · aml · hold · auth · validation · estimated …).
 * الكلمة بتتقارن **كلها** على [shapeKey] (حروف صغيرة · «ا» مكان «أ/إ/آ» · «ي» مكان «ى»). «علي» مش هنا (اسم — «على» بعد التوحيد).
 * أي كلمة من دول في الخانة ⇒ الخانة مش اسم ⇒ الشكل مش معروف ⇒ الرسالة **تستنى** تأكيد المالك (§72: الانتظار مقبول).
 */

/** حروف جر وعطف وأدوات وضماير بالحساب — عربي. */
private const val AR_FUNCTION =
    "في|فى|من|الي|الى|عن|ل|لحد|لحين|حين|حتي|بعد|قبل|خلال|عند|لدي|لما|لو|اذا|ان|انه|او|ثم|غير|بدون|ما|مش|مو|لسه|لسة|لسا|عشان|علشان" +
        "|لكي|كي|لم|لن|لا|قد|سوف|تم|تمت|يتم|سيتم|هيتم|برجاء|يرجي|الرجاء|رجاء|فضلك|فضلكم|منك|منكم|لك|لكم|عليك|بمجرد|حال|حالة|الحالة" +
        "|حسابك|حسابكم|لحسابك|لحسابكم|بحسابك|محفظتك|لمحفظتك|بطاقتك|لبطاقتك|رصيدك|بياناتك|موافقتك|المبلغ|مبلغ|التحويل|الحوالة|العملية|الدفعة|التاجر|المستفيد"

/** الوقت والمدة والحالة والإجراء — عربي (بعد توحيد الحروف). */
private const val AR_STATE =
    "يوم|يومين|ايام|ساعه|ساعة|ساعتين|ساعات|دقيقه|دقيقة|دقائق|اسبوع|اسبوعين|اسابيع|بكره|بكرة|بكرا|غدا|الصبح|قريب|قريبا|لاحقا|الجاي|القادم|التالي" +
        "|المتوقع|متوقع|المقرر|مقرر|تحصيل|التحصيل|استكمال|اكتمال|التحقق|تحقق|التوثيق|توثيق|التفعيل|تفعيل|موافقه|موافقة|الموافقة|القبول|قبول" +
        "|رفض|متاح|للصرف|الصرف|تقديري|تقديريه|تقديرية|مبدئي|مبدئيه|مبدئية|مؤقت|مؤقته|مؤقتة|تامين|ضمان|الكشف|كشف|تحديث|حدث|استلام|استلامه" +
        "|اضافه|اضافة|الاضافة|اضافته|اضافتها|ظهور|انتظار|بانتظار|تدقيق|التدقيق|مراجعه|مراجعة|المراجعة|متاكدش|ماتاكدش|متاكد|مؤكد|مؤكده|مؤكدة" +
        "|جديد|جديده|جديدة|صادر|صادره|صادرة|مستحق|مستحقه|مستحقة"

/** أول الكلمة: «محتاج/محتاجة» · «اقبله» · «ارفضه» · «مستني» · «منتظر» · «معلق» — بأي آخر. */
private const val AR_STEMS = "(?:محتاج|تحتاج|يحتاج|اقبل|ارفض|مستني|منتظر|معلق|مراجع)\\S*"

/** جذور أفعال الفلوس والحالة (بعد بادئة مستقبل أو مضارع أو «اتـ»). */
private const val AR_ROOTS =
    "(?:تم|ضاف|تضاف|ودع|رجع|رد|عاد|حول|تحول|خصم|صرف|نفذ|قيد|ظهر|وصل|دخل|نزل|سجل|تسجل|فعل|تفعل|كمل|اكد|تاكد|ضيف|حجز|علق|لغي|لغا|عكس|سحب|فك)"

/**
 * أفعال: «سيضاف · ستودع · سترجع» (مستقبل فصحى) · «يضاف · يتم · تودع» (مضارع) · «هيوصلك · هيتفعل · هتتحول · حيتم» (مستقبل مصري) ·
 * «اتحجزت · اتعلق · اتلغت» (ماضي مصري) — بـ«و/ف» قبلها أو من غيرها. الجذر لازم يبقى من [AR_ROOTS] («سيف · يوسف · هيثم · تامر» أسامي).
 */
private const val AR_VERB = "[وف]?(?:س[يتن]|[يت]|[هح][يتن]ت?|ات)$AR_ROOTS\\S*"

/** إنجليزي — أدوات ومساعدات ونفي ووقت وحالة. */
private const val EN_WORDS =
    "be|been|being|is|are|was|were|will|shall|would|should|may|might|must|can|could|cannot|not|did|didn't|does|doesn't|wasn't|isn't" +
        "|aren't|weren't|won't|hasn't|haven't|has|have|had|to|for|on|at|by|with|within|until|till|after|before|upon|once|when|if|unless" +
        "|in|into|your|you|our|we|please|pending|awaiting|awaits|awaited|await|approval|approve|approved|acceptance|accept|accepted|confirm" +
        "|confirmed|confirmation|unconfirmed|unverified|verify|verified|verification|validation|validate|kyc|aml|check|checks|cardcheck|review" +
        "|reviewed|queue|queued|hold|held|auth|cardauth|authorization|authorisation|preauth|pre-auth|incremental|estimated|estimate|expected" +
        "|provisional|temporary|draft|scheduled|uncleared|cleared|clearing|funds|release|released|reflect|reflected|posted|posting|receive|update" +
        "|updated|sent|issued|waived|stopped|completed|complete|incomplete|successful|failed|declined|rejected|reversed|refunded|returned" +
        "|cancelled|canceled|expired|blocked|frozen|suspended|needs|need|requires|required|require|working|tomorrow|later|soon|payable|due"

/** كلمة واحدة مش اسم (على [shapeKey]) — بتتقارن كلها. */
internal const val SLOT_STOP = "(?:$AR_FUNCTION|$AR_STATE|$AR_STEMS|$AR_VERB|$EN_WORDS)"

private val STOP_WORD = Regex("^$SLOT_STOP$")

/** كلام حالة لازق في رقم («778812REVERSED» · «14ESTIMATED») — الحد بين الرقم والحرف بيبقى مسافة قبل الفحص. */
private val DIGIT_LETTER = Regex("(?<=\\d)(?=[A-Za-z\\u0600-\\u06FF])|(?<=[A-Za-z\\u0600-\\u06FF])(?=\\d)")

/** كل كلمات [value] (بعد [shapeKey]) كلمات اسم — مفيش ولا كلمة من [SLOT_STOP]. */
internal fun slotWordsOk(value: String): Boolean =
    shapeKey(value, dropColons = false).split(' ').map { it.trim('.', ',', '،', ';', ':', '\'', '"') }.none { it.isNotEmpty() && STOP_WORD.matches(it) }

/** نفس النص والحد بين الرقم والحرف مسافة — عشان كلمة الحالة اللازقة في رقم تبان لـ[hasShapeDoubt]. */
internal fun splitDigitLetter(text: String): String = DIGIT_LETTER.replace(text, " ")
