package app.masroufy.core

/**
 * قايمة المالك §6.5 بند 8 — الاسم اللي **البنك نفسه** قصّه أو حجب جزء منه (`PartyNames.kt` — [NameCut]): بيتعرض زي ما جه ومعاه
 * السطر ده، والمالك يكمّله مرة واحدة بربطه بشخص (الطرف بيتفتكر بمفتاحه). فصحى للسعودية والافتراضي · مصري لمصر (OVERRIDES §66) ·
 * إنجليزي (كتابة Claude — مستني مراجعة المالك §40). متضموم لنصوص منطقة «العمليات» (`TextsAreaOperations.kt`).
 */
internal val MSA_NAME_CUT_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.NAME_CUT_SMS to "الاسم ناقص من رسالة البنك",
    UiKey.NAME_CUT_STATEMENT to "الاسم ناقص من كشف البنك",
    UiKey.NAME_CUT_FIX to "اربطه بشخص مرة واحدة واكتب اسمه كاملًا، وسنتذكّره بعد ذلك.",
)

internal val EGYPTIAN_NAME_CUT_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.NAME_CUT_SMS to "الاسم ناقص من رسالة البنك",
    UiKey.NAME_CUT_STATEMENT to "الاسم ناقص من كشف البنك",
    UiKey.NAME_CUT_FIX to "اربطه بشخص مرة واحدة واكتب اسمه كامل، وهنفتكره بعد كده.",
)

internal val ENGLISH_NAME_CUT_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.NAME_CUT_SMS to "The bank's message cut this name short",
    UiKey.NAME_CUT_STATEMENT to "The bank statement cut this name short",
    UiKey.NAME_CUT_FIX to "Link it to a person once and type the full name — we'll remember it next time.",
)

/** السطر تحت الاسم: من الرسالة ولا من الكشف — null لو الاسم مش مقصوص. */
fun nameCutText(cut: NameCut?): String? = when (cut) {
    NameCut.SMS -> uiText(UiKey.NAME_CUT_SMS)
    NameCut.STATEMENT -> uiText(UiKey.NAME_CUT_STATEMENT)
    null -> null
}
