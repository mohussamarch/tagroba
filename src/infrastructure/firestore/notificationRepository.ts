import { collection, doc, getDocs, writeBatch, type Firestore } from 'firebase/firestore'
import type { NotificationReceipt } from '../../domain/notifications'
import type { NotificationReceiptRepository } from '../../application/ports/repositories'

/**
 * إيصالات التنبيه على Firestore.
 *
 * معرّف الوثيقة هو `eventKey` نفسه، فكتابة نفس الإيصال مرتين بتدهس
 * نفسها ومستحيل ينتج إيصالان لنفس الحدث. القراءة بالمجموعة كاملة
 * وبلا أي استعلام مركّب (ARCHITECTURE §12) — وعددها محدود بالفترة.
 *
 * ⚠️ `eventKey` بيتضمن معرّف تصنيف وعلامات `|` و`:`، وFirestore بيمنع
 * `/` في معرّف الوثيقة، فبيتشفّر قبل الكتابة ويترجع مع البيانات نفسها.
 */

const BATCH_LIMIT = 500

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

/** معرّف وثيقة آمن من مفتاح الحدث. الأصل محفوظ في الحقل نفسه. */
function docIdOf(eventKey: string): string {
  return encodeURIComponent(eventKey).replace(/\./g, '%2E')
}

function clean<T extends object>(value: T): T {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(value)) if (v !== undefined) out[k] = v
  return out as T
}

export class FirestoreNotificationReceiptRepository implements NotificationReceiptRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<NotificationReceipt[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'notificationReceipts')))
    return snap.docs.map((d) => d.data() as NotificationReceipt)
  }

  async saveMany(receipts: readonly NotificationReceipt[]): Promise<void> {
    for (let i = 0; i < receipts.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const receipt of receipts.slice(i, i + BATCH_LIMIT)) {
        batch.set(
          doc(this.db, userPath(this.uid, 'notificationReceipts', docIdOf(receipt.eventKey))),
          clean(receipt),
        )
      }
      await batch.commit()
    }
  }

  async deleteMany(eventKeys: readonly string[]): Promise<void> {
    for (let i = 0; i < eventKeys.length; i += BATCH_LIMIT) {
      const batch = writeBatch(this.db)
      for (const key of eventKeys.slice(i, i + BATCH_LIMIT)) {
        batch.delete(doc(this.db, userPath(this.uid, 'notificationReceipts', docIdOf(key))))
      }
      await batch.commit()
    }
  }
}
