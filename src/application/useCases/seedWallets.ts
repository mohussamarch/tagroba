import type { Wallet } from '../../domain/entities/types'
import type { WalletRepository } from '../ports/repositories'

/**
 * SeedWallets — محفظتان فاضيتان (بنك وكاش) لأول تشغيل.
 *
 * OVERRIDES §121-5: الرصيد الافتتاحي **ما بقاش مكتوب في الكود** — كل مستخدم (المالك نفسه كمان) بيبدأ برصيد صفر
 * من النهارده ويكتب رصيده الافتتاحي وتاريخه بنفسه من الإعدادات. لا يجوز أن يرث أحد رصيد أحد.
 *
 * ⚠️ **الزرع مرة واحدة فقط.** لو وُجدت محافظ فالمستخدم عدّلها أو أضاف
 * غيرها، ولا يجوز إعادة الكتابة فوقها.
 */

/** معرّفات ثابتة: مطلوبة لربط العمليات بالمحفظة عبر الجلسات. */
export const BANK_WALLET_ID = 'wallet-bank'
export const CASH_WALLET_ID = 'wallet-cash'

export interface SeedWalletsOutcome {
  seeded: boolean
  wallets: Wallet[]
  reason: string
}

export function makeSeedWallets(deps: { wallets: WalletRepository }) {
  return async function seed(): Promise<SeedWalletsOutcome> {
    const existing = await deps.wallets.listAll()
    if (existing.length > 0) {
      return {
        seeded: false,
        wallets: existing,
        reason: 'المحافظ موجودة قبل كده. مش هنكتب فوق تعديلاتك.',
      }
    }

    const today = new Date().toISOString().slice(0, 10)
    const bank: Wallet = { id: BANK_WALLET_ID, name: 'الراجحي', currency: 'SAR', kind: 'bank', openingBalanceMinor: 0, openingAt: today }
    const cash: Wallet = { id: CASH_WALLET_ID, name: 'كاش', currency: 'SAR', kind: 'cash', openingBalanceMinor: 0, openingAt: today }

    await deps.wallets.save(bank)
    await deps.wallets.save(cash)

    return {
      seeded: true,
      wallets: [bank, cash],
      reason: 'اتزرعت محفظتان برصيد صفر — عدّل الرصيد الافتتاحي من الإعدادات.',
    }
  }
}
