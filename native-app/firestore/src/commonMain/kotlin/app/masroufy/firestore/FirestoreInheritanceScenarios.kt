package app.masroufy.firestore

import app.masroufy.core.Id
import app.masroufy.core.InheritanceScenario
import app.masroufy.data.InheritanceCodecs
import app.masroufy.port.InheritanceScenarioRepository

/**
 * حسابات الورث المحفوظة (رد المالك §69.3 — OVERRIDES §69.4) — على مستوى الحساب (`users/{uid}/inheritanceScenarios`) جنب خطط الادخار،
 * فبتبان على كل الأجهزة ومن أي بلد. القاعدة العامة `users/{uid}/{document=**}` بتغطيها ⇒ مفيش تغيير في قواعد فايربيز ولا نشر.
 * الكتابة merge (`saveAll`) ⇒ الحقل الاختياري اللي اتشال (الوصية · الأسامي …) بيتمسح من المستند صراحة.
 */
class FirestoreInheritanceScenarioRepository(private val space: FirestoreSpace) : InheritanceScenarioRepository {
    private val codec = InheritanceCodecs.scenarios

    override suspend fun listAll(): List<InheritanceScenario> = space.select(codec)

    override suspend fun save(scenario: InheritanceScenario) = space.saveAll(codec, listOf(scenario))

    override suspend fun remove(id: Id) = space.deleteAll(codec.group, listOf(id))
}
