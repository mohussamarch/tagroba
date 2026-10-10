package app.masroufy.android

import android.content.Context
import app.masroufy.device.AndroidActiveSpaceStore
import app.masroufy.firestore.AccountSession
import app.masroufy.firestore.FirestoreCategoryRepository
import app.masroufy.firestore.FirestoreReferenceSeed
import app.masroufy.firestore.FirestoreRuleRepository
import app.masroufy.firestore.FirestoreSpace
import app.masroufy.firestore.FirestoreSpaceRegistry
import app.masroufy.port.SpaceSeedTargets
import app.masroufy.wiring.SpaceAdminLinks

/**
 * إدارة البلاد على فايربيز (§41 · §64 · رد المالك §88-8) للحساب الداخل [st]:
 * - السجل **بذاكرة الحساب** (`st.account` — نفس اللي الجلسة بتقرا منه، فاللي بيتكتب يبان لحظتها).
 * - البلد الشغالة من **نفس مخزن الجلسة** على الجهاز (أرشفة الشغالة ⇒ السعودية).
 * - تصنيفات وقواعد البلد الجديدة وعلامة تجهيزها في مكانها `users/{uid}/spaces/{id}` (التجار مشتركين على الحساب — ما بيتزرعوش).
 * - بعد أي تغيير ⇒ `AccountSession.refreshSpaces` (تتفتح الجديدة وتتقفل المؤرشفة من غير إعادة تشغيل).
 * نفس الكود للنوعين (`debug` مشروع المالك · `emulator` محاكي الكمبيوتر) — الفرق في `openFirebase` بس.
 */
internal fun spaceAdminLinks(context: Context, session: AccountSession, st: AccountSession.State.Ready) = SpaceAdminLinks(
    registry = FirestoreSpaceRegistry(st.account),
    active = AndroidActiveSpaceStore(context, st.user.uid),
    targets = { space ->
        val root = FirestoreSpace.forSpace(st.account.db, st.user.uid, space.id)
        SpaceSeedTargets(FirestoreCategoryRepository(root), FirestoreRuleRepository(root), FirestoreReferenceSeed(root, merchantsAt = st.account))
    },
    reopen = session::refreshSpaces,
)
