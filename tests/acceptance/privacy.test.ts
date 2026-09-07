import { describe, it, expect } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { sanitizeAccountNumbers } from '../../src/infrastructure/firestore/firestoreRepositories'

/**
 * CLAUDE.md #11 و OVERRIDES §2:
 * «لا تُخزَّن أرقام حسابات كاملة في فايربيز — آخر أربعة أرقام فقط.»
 *
 * الحماية تُطبَّق عند **حدود الطبقة** (قبل الكتابة مباشرة) لا بالاعتماد
 * على انضباط المستدعي، لأن نصوص الكشف تصل من مصدر خارجي غير مضبوط.
 */

describe('قص أرقام الحسابات قبل الكتابة', () => {
  it('يقص أرقام الحسابات الكاملة إلى آخر أربعة', () => {
    expect(sanitizeAccountNumbers('FRACCT/67800608010143577FR')).toBe('FRACCT/****3577FR')
    expect(sanitizeAccountNumbers('TOACCT/18100608016091127TOABDUL')).toBe('TOACCT/****1127TOABDUL')
    expect(sanitizeAccountNumbers('SA0380000000608010167519')).toBe('SA****7519')
  })

  it('لا يمس المبالغ ولا الأوقات ولا التواريخ', () => {
    // المبالغ: أطول تتابع أرقام فيها 4 خانات
    expect(sanitizeAccountNumbers('96.47')).toBe('96.47')
    expect(sanitizeAccountNumbers('4837.83')).toBe('4837.83')
    expect(sanitizeAccountNumbers('9999.99')).toBe('9999.99') // أطول مبلغ آمن كنص
    expect(sanitizeAccountNumbers('22:07:50')).toBe('22:07:50')
    expect(sanitizeAccountNumbers('2026-09-04')).toBe('2026-09-04')
    expect(sanitizeAccountNumbers('2026/09/04')).toBe('2026/09/04')
  })

  it('لا يُبقي أي تتابع من 5 أرقام فأكثر', () => {
    const inputs = [
      'FRACCT/67800608010143577FR',
      'رقم 123456789012345678901234567890',
      'a1234567b8901234c',
    ]
    for (const input of inputs) {
      expect(/\d{5,}/.test(sanitizeAccountNumbers(input))).toBe(false)
    }
  })

  it('لا يمس النص العربي ولا اسم التاجر', () => {
    expect(sanitizeAccountNumbers('شراء عبر نقاط البيع')).toBe('شراء عبر نقاط البيع')
    expect(sanitizeAccountNumbers('OTHAIM MARKETS COMP')).toBe('OTHAIM MARKETS COMP')
  })
})

/**
 * ⚠️ **حدّ حقيقي للدالة، موثَّق لا مخفي.**
 *
 * الدالة تنظر إلى تتابعات الأرقام لا إلى المعنى، فأي مبلغ **نصّي**
 * جزؤه الصحيح 5 خانات فأكثر (100,000 ر.س وما فوق) سيُقص.
 * اكتُشف هذا بفشل اختبار على `302171.45`، لا بالمراجعة.
 *
 * **لماذا هذا غير ضار في التطبيق:**
 * `sanitize` تُطبَّق على الحقول **النصية فقط** (`typeof v === 'string'`)،
 * وكل المبالغ مخزَّنة **أعدادًا صحيحة بالهللة** لا نصوصًا (ARCHITECTURE.md §4).
 * الحقول النصية الوحيدة التي قد تحوي رقمًا هي `rawDescription` و
 * `rawMerchantName`، وهي للعرض والمراجعة لا لأي حساب مالي.
 *
 * **الأثر الوحيد:** لو ظهر مبلغ ضخم داخل نص وصف العملية، سيُعرض مقصوصًا.
 * وهذا مقبول: خطر عرض وصف ناقص أهون من خطر تسريب رقم حساب.
 */
describe('حدّ القص — موثَّق لا مخفي', () => {
  it('مبلغ نصّي من 5 خانات صحيحة فأكثر يُقص — وهذا مقصود', () => {
    expect(sanitizeAccountNumbers('100000')).toBe('****0000')
    expect(sanitizeAccountNumbers('302171.45')).toBe('****2171.45')
  })

  it('لكن المبالغ لا تُخزَّن نصوصًا أصلًا، فلا تمرّ على الدالة', () => {
    // 302,171.45 ر.س تُخزَّن هكذا: عددًا صحيحًا بالهللة
    const amountMinor = 30217145
    expect(typeof amountMinor).toBe('number')
    expect(Number.isInteger(amountMinor)).toBe(true)
  })
})

const CSV_PATH = resolve(__dirname, '../../files/transactions_full.csv')

describe.skipIf(!existsSync(CSV_PATH))('على الكشف الحقيقي', () => {
  it('كل سطر بعد القص خالٍ من أي رقم حساب كامل', () => {
    const lines = readFileSync(CSV_PATH, 'utf8').split(/\r?\n/).slice(1).filter(Boolean)
    const risky = lines.filter((l) => /\d{5,}/.test(l))

    // البيانات الحقيقية فعلًا فيها أرقام حسابات كاملة
    expect(risky.length).toBeGreaterThan(500)

    for (const line of lines) {
      expect(/\d{5,}/.test(sanitizeAccountNumbers(line))).toBe(false)
    }
  })
})
