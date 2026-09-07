import { collection, doc, getDoc, getDocs, setDoc, type Firestore } from 'firebase/firestore'
import type { Id, Wallet } from '../../domain/entities/types'
import type { WalletRepository } from '../../application/ports/repositories'

/**
 * المحافظ على Firestore.
 *
 * ⚠️ `accountLast4` هو الحقل الوحيد المسموح لرقم الحساب —
 * **ممنوع تخزين رقم حساب كامل** (CLAUDE.md #11 و OVERRIDES §2).
 * الحماية عند حدود الطبقة لا بالاعتماد على المستدعي.
 */

function userPath(uid: string, ...segments: string[]): string {
  return ['users', uid, ...segments].join('/')
}

/** يقص أي تتابع من خمسة أرقام فأكثر إلى آخر أربعة. */
function last4Only(value: string | undefined): string | undefined {
  if (!value) return undefined
  const digits = value.replace(/\D/g, '')
  return digits.length === 0 ? undefined : digits.slice(-4)
}

export class FirestoreWalletRepository implements WalletRepository {
  constructor(
    private readonly db: Firestore,
    private readonly uid: string,
  ) {}

  async listAll(): Promise<Wallet[]> {
    const snap = await getDocs(collection(this.db, userPath(this.uid, 'wallets')))
    return snap.docs.map((d) => d.data() as Wallet)
  }

  async findById(id: Id): Promise<Wallet | null> {
    const snap = await getDoc(doc(this.db, userPath(this.uid, 'wallets', id)))
    return snap.exists() ? (snap.data() as Wallet) : null
  }

  async save(wallet: Wallet): Promise<void> {
    const safe: Record<string, unknown> = {
      id: wallet.id,
      name: wallet.name,
      currency: wallet.currency,
      kind: wallet.kind,
      openingBalanceMinor: wallet.openingBalanceMinor,
      openingAt: wallet.openingAt,
    }
    // آخر أربعة فقط، ولا يُكتب الحقل أصلًا لو لم يوجد رقم
    const last4 = last4Only(wallet.accountLast4)
    if (last4) safe.accountLast4 = last4

    await setDoc(doc(this.db, userPath(this.uid, 'wallets', wallet.id)), safe)
  }
}
