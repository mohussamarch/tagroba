import { describe, expect, it } from 'vitest'
import { makeManageSmsInbox } from '../../src/application/useCases/manageSmsInbox'
import { memorySmsInbox } from '../../src/infrastructure/memory/smsInbox'
import { parseBankSms } from '../../src/infrastructure/import/bankSmsParser'

const message = {id:'one', sender:'AlRajhiBank', receivedAt:'2026-09-10T09:00:00Z', body:'شراء بمبلغ 25.00 SAR لدى ALBAIK في 2026-09-10'}
describe('رسائل معلقة — لا حفظ أو إزالة قبل اختيار المستخدم', () => {
  it('keeps the full amount after native-style redaction, including five-digit transfers', async () => {
    const {redactSms}=await import('../../src/infrastructure/import/bankSmsParser')
    const body=redactSms('حوالة واردة بمبلغ 15000.50 SAR في 2026-09-10 حساب 1234567890')
    expect(body).not.toContain('1234567890')
    const parsed=parseBankSms({...message,body},1)
    expect(parsed.ok&&parsed.row.amountMinor).toBe(1500050)
  })
  it('refresh parses but leaves the original queue pending across visits', async () => {
    const service = makeManageSmsInbox(memorySmsInbox([message]), parseBankSms)
    expect((await service.refresh()).items[0].parsed.ok).toBe(true)
    const parsed=(await service.refresh()).items[0].parsed
    expect(parsed.ok&&parsed.row.merchantName).toBe('ALBAIK')
    expect((await service.refresh()).count).toBe(1)
  })
  it('acknowledges only successfully confirmed line IDs; partial import preserves the rest', async () => {
    const service = makeManageSmsInbox(memorySmsInbox([message,{...message,id:'two'}]),parseBankSms)
    await service.imported([{id:'one',lineNumber:1},{id:'two',lineNumber:2}],[2])
    expect((await service.refresh()).messages.map(m=>m.id)).toEqual(['one'])
    await service.imported([{id:'one',lineNumber:1}],[])
    expect((await service.refresh()).count).toBe(1)
  })
  it('unsupported SMS stays visible with reason until explicitly dismissed', async () => {
    const service = makeManageSmsInbox(memorySmsInbox([{...message,body:'شراء بمبلغ 25 SAR'}]),parseBankSms)
    expect((await service.refresh()).items[0].parsed.ok).toBe(false)
    await service.dismiss(['one'])
    expect((await service.refresh()).count).toBe(0)
  })
  it('stopping collection preserves pending messages and validates enable input', async () => {
    const service = makeManageSmsInbox(memorySmsInbox([message]),parseBankSms)
    await expect(service.enable([])).rejects.toThrow()
    expect((await service.enable([' AlRajhiBank ','AlRajhiBank'])).senders).toEqual(['AlRajhiBank'])
    const stopped=await service.disable()
    expect(stopped.enabled).toBe(false); expect(stopped.count).toBe(1)
  })
})
