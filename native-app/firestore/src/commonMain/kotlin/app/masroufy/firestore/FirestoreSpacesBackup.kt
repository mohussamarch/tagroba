package app.masroufy.firestore

import app.masroufy.core.BackupRow
import app.masroufy.core.SPACE_GROUPS
import app.masroufy.core.Space
import app.masroufy.data.SpaceCodecs
import app.masroufy.port.FullBackupPort
import app.masroufy.port.SpacesBackupPort
import dev.gitlive.firebase.firestore.Source

/**
 * البلاد غير السعودية في النسخة الشاملة (الإصدار 3 — §41.1 · §64) على فايربيز — بنفس قواعد `FirestoreFullBackup`:
 * القراية **من السيرفر بس** (من غير نت بتفشل برسالة)، والكتابة **بتضيف الناقص بس** بمعاملات (عمرها ما تكتب فوق الموجود).
 * [spaceOf] بيرجّع مكان البلد بذاكرته من الجلسة لو موجودة (عشان اللي اتضاف يبان لحظتها).
 */
class FirestoreSpacesBackup(
    private val account: FirestoreSpace,
    private val spaceOf: (String) -> FirestoreSpace = { FirestoreSpace.forSpace(account.db, account.root.removePrefix("users/"), it) },
) : SpacesBackupPort {
    private val registry = FirestoreSpaceRegistry(account)

    override suspend fun registry(): List<Space> =
        account.collection(SpaceCodecs.spaces.group).get(Source.SERVER).documents.mapNotNull { it.rawData() }.map(SpaceCodecs.spaces::decode)

    override suspend fun addSpaceIfMissing(space: Space): Boolean = registry.addIfMissing(space)

    override fun dataOf(spaceId: String): FullBackupPort = FirestoreFullBackup(spaceOf(spaceId), groups = SPACE_GROUPS)

    override suspend fun readSpaceTransfers(): List<BackupRow> =
        account.collection(SpaceCodecs.spaceTransfers.group).get(Source.SERVER).documents.mapNotNull { it.rawData() }

    override suspend fun addMissingSpaceTransfers(rows: List<BackupRow>): Int {
        var added = 0
        for (chunk in rows.chunked(100)) {
            val written = account.db.runTransaction {
                val refs = chunk.map { account.collection(SpaceCodecs.spaceTransfers.group).document(it["id"] as String) }
                val current = refs.map { get(it) }
                val fresh = mutableListOf<Pair<String, BackupRow>>()
                current.forEachIndexed { i, snap ->
                    if (!snap.exists) {
                        set(refs[i], chunk[i])
                        fresh += refs[i].id to chunk[i]
                    }
                }
                fresh
            }
            account.mirror?.let { m -> written.forEach { (id, row) -> m.applySet(SpaceCodecs.spaceTransfers.group, id, row) } }
            added += written.size
        }
        return added
    }
}
