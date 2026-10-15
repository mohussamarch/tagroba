package app.masroufy.core

/**
 * «العمليات» — شكل **التحويل بين محافظك** (طلب المالك HANDOVER §6.5-10: «ليه الحوالة بين حساباتي بتظهر سالب احمر؟ المفروض يكون في
 * سيمبول … انه منه فيه»): «من ← إلى» في الصف والتفاصيل · «إلى الكاش» / «من الراجحي» جوه سجل محفظة واحدة · «منه» / «إليه» في زون
 * التحويلات للطرف اللي اتقرر إنه حسابك التاني · وصف رمز «منه فيه» لقارئ الشاشة. الاستعمال في `ui/components/MoveLook.kt`.
 * العربي من اليمين للشمال ⇒ «←» في اتجاه القراية (زي `OPERATIONS_ROW_MOVE`)، والإنجليزي «→». المصري بـ«لـ» زي «من {0} لـ{1}» في
 * باقي جداوله، والفصحى بـ«إلى». الإنجليزي كتابة Claude — مستني مراجعة المالك (§40).
 */
internal val MSA_OPERATIONS_MOVE_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.MOVE_FROM_TO to "من {0} ← إلى {1}",
    UiKey.MOVE_OTHER_ACCOUNT to "حسابك الآخر",
    UiKey.MOVE_SIDE_FROM to "من {0}",
    UiKey.MOVE_SIDE_TO to "إلى {0}",
    UiKey.MOVE_PARTY_IN to "وارد منه",
    UiKey.MOVE_PARTY_OUT to "صادر إليه",
    UiKey.MOVE_SYMBOL to "تحويل بين محافظك",
    UiKey.MOVE_DATED to "{0}، {1}",
)

internal val EGYPTIAN_OPERATIONS_MOVE_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.MOVE_FROM_TO to "من {0} ← لـ{1}",
    UiKey.MOVE_OTHER_ACCOUNT to "حسابك التاني",
    UiKey.MOVE_SIDE_FROM to "من {0}",
    UiKey.MOVE_SIDE_TO to "لـ{0}",
    UiKey.MOVE_PARTY_IN to "جاي منه",
    UiKey.MOVE_PARTY_OUT to "رايح له",
    UiKey.MOVE_SYMBOL to "تحويل بين محافظك",
    UiKey.MOVE_DATED to "{0}، {1}",
)

internal val ENGLISH_OPERATIONS_MOVE_TEXTS: Map<UiKey, String> = mapOf(
    UiKey.MOVE_FROM_TO to "From {0} → {1}",
    UiKey.MOVE_OTHER_ACCOUNT to "your other account",
    UiKey.MOVE_SIDE_FROM to "From {0}",
    UiKey.MOVE_SIDE_TO to "To {0}",
    UiKey.MOVE_PARTY_IN to "Received from it",
    UiKey.MOVE_PARTY_OUT to "Sent to it",
    UiKey.MOVE_SYMBOL to "Transfer between your wallets",
    UiKey.MOVE_DATED to "{0}, {1}",
)
