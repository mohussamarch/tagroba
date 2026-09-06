import { describe, it, expect } from 'vitest'
import { parseQuery, searchTransactions, type SearchableTransaction } from '../../src/domain/search'
import { parseMoney } from '../../src/domain/money'
import type { Transaction } from '../../src/domain/entities/types'

/** حالات البحث من spec/06. */

let n = 0
function make(
  merchant: string,
  amount: string,
  extra: Partial<Transaction> = {},
  categoryName?: string,
  tagNames?: string[],
): SearchableTransaction {
  n++
  const transaction: Transaction = {
    id: `t${n}`,
    occurredAt: '2026-09-20',
    datePrecision: 'day',
    sourceOrder: n,
    economicKind: 'purchase',
    economicKindConfirmed: false,
    observedDirection: 'out',
    amountMinor: parseMoney(amount),
    currency: 'SAR',
    categoryConfirmed: false,
    excludedFromBudget: false,
    reviewState: 'suggested',
    isCashTagged: false,
    rawMerchantName: merchant,
    rawDescription: merchant,
    createdAt: '', updatedAt: '',
    ...extra,
  }
  const item: SearchableTransaction = { transaction }
  if (categoryName) item.categoryName = categoryName
  if (tagNames) item.tagNames = tagNames
  return item
}

const items: SearchableTransaction[] = [
  make('البيك', '32.00', {}, 'مطاعم وقهوة'),
  make('Amazon', '720.00', { note: 'هدية لعبد الفتاح' }, 'تسوق إلكتروني', ['هدية']),
  make('مشتريات كاش', '100.00', { isCashTagged: true }, 'متفرقات'),
  make('NASAMAT ALBADIA CO', '85.99', {}, 'مطاعم وقهوة'),
  make('OTHAIM MARKETS', '89.00', {}, 'بقالة وسوبرماركت'),
  make('سوق آخر', '95.00', {}, 'بقالة وسوبرماركت'),
  make('عبدالفتاح تحويل', '200.00', {}, 'تحويلات'),
]

describe('البحث النصي — spec/06', () => {
  it('«كاش» يلاقي العملية المتوسمة بالكاش', () => {
    const hits = searchTransactions(items, parseQuery('كاش'))
    expect(hits).toHaveLength(1)
    expect(hits[0].transaction.rawMerchantName).toBe('مشتريات كاش')
    expect(hits[0].matchedFields).toContain('cash')
  })

  it('«هدية» يلاقي الوسم والملاحظة', () => {
    const hits = searchTransactions(items, parseQuery('هدية'))
    expect(hits).toHaveLength(1)
    expect(hits[0].transaction.rawMerchantName).toBe('Amazon')
    expect(hits[0].matchedFields).toEqual(expect.arrayContaining(['note', 'tag']))
  })

  it('«عبد الفتاح» يلاقي «عبدالفتاح» بلا مسافة، وبالعكس', () => {
    const withSpace = searchTransactions(items, parseQuery('عبد الفتاح'))
    expect(withSpace.length).toBeGreaterThanOrEqual(2) // الملاحظة + اسم التحويل

    const withoutSpace = searchTransactions(items, parseQuery('عبدالفتاح'))
    expect(withoutSpace.length).toBeGreaterThanOrEqual(2)
  })

  it('يبحث في التصنيف واللاتيني داخل RTL', () => {
    expect(searchTransactions(items, parseQuery('مطاعم'))).toHaveLength(2)
    expect(searchTransactions(items, parseQuery('amazon'))).toHaveLength(1) // بلا حساسية حالة
    expect(searchTransactions(items, parseQuery('AMAZON'))).toHaveLength(1)
  })

  it('نص فارغ لا يرجّع كل شيء', () => {
    expect(searchTransactions(items, parseQuery('   '))).toHaveLength(0)
  })
})

describe('البحث بالمبلغ ±5% — spec/06', () => {
  it('«85.99» يلاقي المبالغ داخل ٥٪ فقط', () => {
    const query = parseQuery('85.99')
    expect(query.amount).not.toBeNull()
    expect(query.amount!.targetMinor).toBe(parseMoney('85.99'))
    expect(query.amount!.tolerancePerThousand).toBe(50) // ٥٪ معلنة

    const hits = searchTransactions(items, query)
    const amounts = hits.map((h) => h.transaction.amountMinor).sort((a, b) => a - b)
    // ±5% من 85.99 = 81.69 إلى 90.29 ⇒ 85.99 و 89.00 فقط
    expect(amounts).toEqual([parseMoney('85.99'), parseMoney('89.00')])
    expect(amounts).not.toContain(parseMoney('95.00'))
  })

  it('يدعم الأرقام العربية ٨٥٫٩٩', () => {
    const arabic = parseQuery('٨٥٫٩٩')
    expect(arabic.amount!.targetMinor).toBe(parseMoney('85.99'))
    const hits = searchTransactions(items, arabic)
    expect(hits.map((h) => h.transaction.amountMinor).sort((a, b) => a - b)).toEqual([
      parseMoney('85.99'),
      parseMoney('89.00'),
    ])
  })

  it('بحث المبلغ مستقل عن البحث النصي — spec/06', () => {
    // «100» رقم ⇒ استعلام مبلغ، ويلاقي 100.00 بغض النظر عن الاسم
    const hits = searchTransactions(items, parseQuery('100'))
    expect(hits.some((h) => h.transaction.amountMinor === parseMoney('100.00'))).toBe(true)
    expect(hits.every((h) => h.matchedFields.includes('amount'))).toBe(true)
  })

  it('النص غير الرقمي ليس استعلام مبلغ', () => {
    expect(parseQuery('البيك').amount).toBeNull()
    expect(parseQuery('BTC').amount).toBeNull() // رمز أصل لا مبلغ
    expect(parseQuery('').amount).toBeNull()
  })

  it('الهامش قابل للتغيير ومعلن دائمًا', () => {
    const strict = parseQuery('85.99', 0) // بلا هامش
    const hits = searchTransactions(items, strict)
    expect(hits.map((h) => h.transaction.amountMinor)).toEqual([parseMoney('85.99')])
  })
})
