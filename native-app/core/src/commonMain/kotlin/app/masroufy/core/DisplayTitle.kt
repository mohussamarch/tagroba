package app.masroufy.core

/**
 * r041 A4 — اسم العملية للعرض في كل مكان (صف «العمليات» · التفاصيل · كارت الكاش · «افهم رصيدك» · لوحة المسح): التاجر المعروف [merchant]
 * ⇒ الاسم من المصدر ⇒ ملاحظتك ⇒ **طرف التحويل** (`transferPartyOf`) ⇒ **أول سطر** من الوصف ⇒ null.
 * قبل كده الوصف كله كان بيبقى العنوان، فتحويل رسالة بنك من غير تاجر كان عنوانه نص الرسالة كلها بسطورها (وفيها إنجليزي).
 * عرض بس: ما بيكتبش في `rawMerchantName` — ذاكرة التجار و`sharedMerchants` عمرها ما تشيل أسامي ناس (CLAUDE.md الأمان 7).
 */
fun displayTitleOrNull(tx: Transaction, merchant: String? = null): String? =
    (clean(merchant) ?: clean(tx.rawMerchantName))?.let { cardTitle(tx, it) } ?: clean(tx.note) ?: partyTitleOf(tx) ?: firstLineOf(tx.rawDescription)

/** r06-darahim §12-4: اسم الكارت منضّف للعرض — على الخصم اللي مالوش طرف تحويل بس (اسم الشخص ما بيتلمسش). */
private fun cardTitle(tx: Transaction, name: String): String =
    if (tx.observedDirection == Direction.OUT && partyTitleOf(tx) == null) cleanCardName(name) else name

/** زي [displayTitleOrNull] وآخرها اسم نوع العملية. */
fun displayTitleOf(tx: Transaction, merchant: String? = null): String = displayTitleOrNull(tx, merchant) ?: ruleFor(tx.economicKind).label

/** العنوان هو اسم الطرف نفسه (مفيش تاجر ولا ملاحظة) ⇒ السطر التاني ما يكررش «إلى <الاسم>» ويعرض النوع أو التصنيف. */
fun titleIsPartyOf(tx: Transaction, merchant: String? = null): Boolean =
    clean(merchant) == null && clean(tx.rawMerchantName) == null && clean(tx.note) == null && partyTitleOf(tx) != null

/** أول سطر فيه كلام من [text] (null لو فاضي). */
fun firstLineOf(text: String?): String? = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

private fun partyTitleOf(tx: Transaction): String? = transferPartyOf(tx)?.label?.let(::clean)

private fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }
