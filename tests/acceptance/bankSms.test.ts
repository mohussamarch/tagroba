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
/*
 * 2026-09-18: رسايل المالك الحقيقية كلها اترفضت بـ«مبلغ العملية غير واضح». الحالات دي بنفس أشكال رسايل البنوك
 * (كلمة «بـ» أو العملة قبل الرقم من غير «مبلغ»، وسنة برقمين) **بأرقام وأسماء وهمية** — المستودع عام.
 */
const at = (receivedAt:string, body:string) => ({ sender:'AlRajhiBank', receivedAt, body })
it('reads a purchase whose amount has the currency first and a two digit year',()=>{
 const result=parseBankSms(at('2026-09-16T10:56:00Z','شراء إنترنت\nبطاقة:4321;مدى-ابل باي\nمن:TEST SHOP\nمبلغ:SAR 199\nفي:26-9-16 13:55'),1)
 expect(result).toMatchObject({ok:true,row:{amountMinor:19900,date:'2026-09-16',direction:'out',merchantName:'TEST SHOP'}})
})
it('reads «بـ» without the word amount and a day-first two digit year',()=>{
 const result=parseBankSms(at('2026-09-16T11:00:00Z','شراء عبر نقاط البيع\nعبر:*4321\nبـ:25.50 SAR\nلدى:STORE ONE\nفي:16/9/26 13:55'),1)
 expect(result).toMatchObject({ok:true,row:{amountMinor:2550,date:'2026-09-16',merchantName:'STORE ONE'}})
})
it('ignores balance and fee lines and reads an incoming transfer sender',()=>{
 const incoming=parseBankSms(at('2026-09-16T08:00:00Z','حوالة واردة\nمن: TEST PERSON\nالمبلغ: 1,250.00 ر.س\nالرصيد: 3,000 ر.س\nفي: 26/9/15'),1)
 expect(incoming).toMatchObject({ok:true,row:{amountMinor:125000,date:'2026-09-15',direction:'in',merchantName:'TEST PERSON'}})
 const bill=parseBankSms(at('2026-09-11T08:00:00Z','سداد فاتورة\nمبلغ: 300 SAR\nرسوم: 1.15 SAR\nفي: 2026-09-10'),1)
 expect(bill).toMatchObject({ok:true,row:{amountMinor:30000}})
})
/*
 * نفس شكل رسايل الراجحي اللي بعتها المالك (صورة، 2026-09-18) **بأرقام بطاقة وأسماء محلات وهمية**:
 * «بـSR 24» من غير «مبلغ»، والمحل في سطر «لـاسم»، والتاريخ بسنة برقمين: «26/9/18 09:35» (سنة أول)
 * و«16:47 17/9/26» (يوم أول). رسالة الشراء بالدولار بتترفض لأن المبلغ بالريال مش مكتوب فيها.
 */
it('reads the owner bank point-of-sale layout: «بـSR», «لـ» merchant and a year-first date',()=>{
 const body='شراء PoS\nعبر1111;مدى-سامسونج باي\nبـSR 24\nلـTEST STORE\n26/9/18 09:35'
 expect(parseBankSms(at('2026-09-18T06:35:00Z',body),1)).toMatchObject({ok:true,row:{amountMinor:2400,date:'2026-09-18',direction:'out',merchantName:'TEST STORE'}})
 const cents=parseBankSms(at('2026-09-18T06:37:00Z','شراء PoS\nعبر1111;مدى-سامسونج باي\nبـSR 12.75\nلـTEST PERSON\n26/9/18 09:37'),1)
 expect(cents).toMatchObject({ok:true,row:{amountMinor:1275,merchantName:'TEST PERSON'}})
})
it('reads a day-first date after the time and skips an account number as merchant',()=>{
 const result=parseBankSms(at('2026-09-17T13:47:00Z','شراء\nعبر:1111;مدى\nمن:2222\nبـSR 50\nلـTEST WALLET\n16:47 17/9/26'),1)
 expect(result).toMatchObject({ok:true,row:{amountMinor:5000,date:'2026-09-17',merchantName:'TEST WALLET'}})
 const noName=parseBankSms(at('2026-09-17T13:47:00Z','شراء\nمن:2222\nبـSR 50\n16:47 17/9/26'),1)
 expect(noName.ok&&noName.row.merchantName).toBe('')
})
it('ignores hidden direction marks around numbers and refuses a dollar-only internet purchase',()=>{
 const marked=parseBankSms(at('2026-09-18T06:35:00Z','شراء PoS\nبـ‏SR‎ 24\nلـ‎TEST STORE\n‎26/9/18 09:35'),1)
 expect(marked).toMatchObject({ok:true,row:{amountMinor:2400,merchantName:'TEST STORE'}})
 const dollars=parseBankSms(at('2026-09-18T07:15:00Z','شراء انترنت\nبطاقة:1111;مدى\nمن:2222\nمبلغ:USD 5.30\nلدى:TEST AI\nفي:26/9/18 10:15'),1)
 expect(dollars).toMatchObject({ok:false,reason:expect.stringContaining('الريال')})
})
it.each([
 ['two different amounts','شراء\nبـ:25 SAR\nمبلغ:30 SAR\nفي:26-9-16','أكثر من مبلغ'],
 ['balance only','شراء\nالرصيد: 500 SAR\nفي: 2026-09-10','مبلغ العملية غير واضح'],
 ['a two digit date far from arrival','شراء\nبـ:25 SAR\nفي:10-1-1','تاريخ العملية غير واضح'],
])('still refuses unclear messages: %s',(_,body,reason)=>{
 const result=parseBankSms(at('2026-09-16T10:00:00Z',body),1)
 expect(result.ok).toBe(false)
 expect(!result.ok&&result.reason).toContain(reason)
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
