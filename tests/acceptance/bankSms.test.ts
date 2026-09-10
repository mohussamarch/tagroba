import {it,expect,vi} from 'vitest'
import {parseBankSms,redactSms} from '../../src/infrastructure/import/bankSmsParser'
import {makeReadBankSms} from '../../src/application/useCases/readBankSms'
const message={sender:'AlRajhiBank',receivedAt:'2026-09-10T01:00:00Z',body:'شراء عبر نقاط البيع\nبمبلغ: 29.99 SAR\nلدى: ALBAIK\nفي: 2026-09-09\nبطاقة: 1234567812345678\nالرصيد: 1500 SAR'}
it('extracts the transaction amount rather than balance and preserves exact halalas',()=>{
 const result=parseBankSms(message,1)
 expect(result.ok).toBe(true)
 if(!result.ok)return
 expect(result.row.amountMinor).toBe(2999)
 expect(result.row.direction).toBe('out')
 expect(result.row.date).toBe('2026-09-09')
 expect(result.row.merchantName).toBe('ALBAIK')
 expect(result.row.raw).not.toContain('1234567812345678')
})
it.each(['رمز التحقق 123456','OTP: 123456','كلمة المرور 123456'])('rejects sensitive texts even when they contain a purchase: %s',prefix=>{
 expect(parseBankSms({...message,body:prefix+'\n'+message.body},1).ok).toBe(false)
})
it.each(['عملية شراء مرفوضة بمبلغ 20 SAR في 2026-09-09','Purchase amount: 20 USD on 2026-09-09','شراء بمبلغ 20 SAR','عرض شراء بمبلغ 20 SAR','شراء بمبلغ 20 في 2026-09-09'])('skips incomplete or unsupported text %s',body=>{
 expect(parseBankSms({...message,body},1).ok).toBe(false)
})
it('supports Arabic digits and incoming transfer without assigning an economic type',()=>{
 const result=parseBankSms({...message,body:'حوالة واردة\nبمبلغ: ١٢٣٫٤٥ ريال\nفي: ٢٠٢٦/٠٩/٠٩'},1)
 expect(result.ok&&result.row.amountMinor).toBe(12345)
 expect(result.ok&&result.row.direction).toBe('in')
 expect(result.ok&&('economicKind' in result.row)).toBe(false)
})
it('redacts IBANs and long account identifiers',()=>{
 const text=redactSms('SA0380000000608010167519 حساب 1234567890')
 expect(text).not.toContain('SA038000')
 expect(text).not.toContain('1234567890')
})
it('does not read the device before an explicit call and validates the time window',async()=>{
 const read=vi.fn(async()=>({messages:[message],truncated:false}))
 const usecase=makeReadBankSms({available:true,read},parseBankSms)
 expect(read).not.toHaveBeenCalled()
 await expect(usecase.read({from:'2020-01-01',to:'2026-09-09',senders:['AlRajhiBank']})).rejects.toThrow()
 expect(read).not.toHaveBeenCalled()
 const result=await usecase.read({from:'2026-09-01',to:'2026-09-10',senders:['AlRajhiBank']})
 expect(result.rows).toHaveLength(1)
 expect(result.content).not.toContain('1234567812345678')
})

import { makeImportStatement, type ImportRequest } from '../../src/application/useCases/importStatement'
import { MemoryTransactionRepository,MemorySourceRecordRepository,MemoryImportBatchRepository,MemoryCategoryRepository,MemoryMerchantRepository,MemoryRuleRepository,PassthroughUnitOfWork,SequentialIdGenerator,FixedClock } from '../../src/infrastructure/memory/memoryRepositories'
function system(){
 const txns=new MemoryTransactionRepository()
 const imports=makeImportStatement({txns,sources:new MemorySourceRecordRepository(),batches:new MemoryImportBatchRepository(),categories:new MemoryCategoryRepository(),merchants:new MemoryMerchantRepository(),rules:new MemoryRuleRepository(),uow:new PassthroughUnitOfWork(),ids:new SequentialIdGenerator(),clock:new FixedClock('2026-09-10T00:00:00Z')})
 return {txns,imports}
}
function smsRequest():ImportRequest{
 const parsed=parseBankSms(message,1)
 if(!parsed.ok)throw Error('fixture')
 return {fileName:'sms.json',content:JSON.stringify([parsed.row]),parsedRows:[parsed.row],schema:'sms',sourceType:'sms',accountIdentity:'bank',walletId:'w-bank'}
}
const csv:ImportRequest={fileName:'statement.csv',content:'date,name,amount,type,source,reference\n2026-09-09,AL BAIK SA,29.99,expense,bank,BANK001',sourceType:'csv_preview',accountIdentity:'bank',walletId:'w-bank'}
it('flags statement then SMS as a review candidate despite different merchant names',async()=>{
 const {imports}=system()
 await imports.commit(csv,await imports.preview(csv))
 const preview=await imports.preview(smsRequest())
 expect(preview.lines[0].state).toBe('similar')
 expect(preview.lines[0].selectedByDefault).toBe(false)
})
it('flags SMS then statement as a review candidate despite different references',async()=>{
 const {imports}=system(),sms=smsRequest()
 await imports.commit(sms,await imports.preview(sms))
 expect((await imports.preview(csv)).lines[0].state).toBe('similar')
})
it('repeated confirmation of the same preview does not create another transaction',async()=>{
 const {imports,txns}=system(),sms=smsRequest()
 const preview=await imports.preview(sms)
 const first=await imports.commit(sms,preview)
 const second=await imports.commit(sms,preview)
 expect(second.id).toBe(first.id)
 expect(await txns.listByDateRange('2026-09-01','2026-09-30')).toHaveLength(1)
})
it('two rapid confirmations do not duplicate the same SMS batch',async()=>{
 const {imports,txns}=system(),sms=smsRequest(),preview=await imports.preview(sms)
 await Promise.allSettled([imports.commit(sms,preview),imports.commit(sms,preview)])
 expect(await txns.listByDateRange('2026-09-01','2026-09-30')).toHaveLength(1)
})

it('redacts account and card digits separated by spaces or hyphens',()=>{
 const text=redactSms('بطاقة 1234 5678 9012 3456 وحساب 1234-5678-9012-3456');
 expect(text).not.toContain('1234');expect(text).toContain('••••3456');
})
