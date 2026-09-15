import { describe, it, expect } from 'vitest'
import { settleableKinds, suggestedSettlementMinor } from '../../src/domain/settlementSuggestion'
import { parseMoney } from '../../src/domain/money'

/** «اربطها بدين موجود» من قايمة النقط التلاتة — OVERRIDES §30. */
describe('ربط عملية بدين موجود', () => {
  it('الفلوس الداخلة بتحصّل دين ليك، والخارجة بتسدد دين عليك أو ترجّع أمانة', () => {
    expect(settleableKinds('in')).toEqual(['receivable'])
    expect(settleableKinds('out')).toEqual(['loan_payable', 'custody_payable'])
  })

  it('المبلغ المقترح هو الأقل بين العملية والمتبقي، بالهللة', () => {
    expect(suggestedSettlementMinor(parseMoney('150.00'), parseMoney('500.00'))).toBe(parseMoney('150.00'))
    expect(suggestedSettlementMinor(parseMoney('900.00'), parseMoney('200.50'))).toBe(parseMoney('200.50'))
    expect(suggestedSettlementMinor(parseMoney('75.25'), parseMoney('75.25'))).toBe(parseMoney('75.25'))
  })
})
