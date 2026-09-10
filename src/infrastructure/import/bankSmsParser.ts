import { latinizeDigits } from '../../domain/normalize'
import { tryParseMoney } from '../../domain/money'
import { isValidIsoDate } from '../../domain/period'
import { hashContent } from '../../domain/dedupe'
import type { BankSmsMessage, SmsParseResult } from '../../application/ports/BankSmsPort'

const sensitive = /\bOTP\b|verification\s*code|one.time\s*(?:password|code)|رمز\s*(?:التحقق|التوثيق|التفعيل|الدخول)|كلمة\s*(?:المرور|السر)/i
export function redactSms(text:string):string {
 const redact=(value:string)=>value.replace(/SA[\d\s]{20,}/gi, value=>'••••'+value.replace(/\s/g,'').slice(-4))
  .replace(/\b(?:\d[ -]*){12,34}\b/g,value=>'••••'+value.replace(/\D/g,'').slice(-4))
  .replace(/\d{5,}/g,value=>'••••'+value.slice(-4))
 const amounts=/(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)\s*[:：]?\s*(?:(?:SAR|ريال|ر\.?س\.?)\s*[\d,٬]+(?:[.٫]\d{1,2})?|[\d,٬]+(?:[.٫]\d{1,2})?\s*(?:SAR|ريال|ر\.?س\.?))/gi
 let result='',end=0
 for(const match of text.matchAll(amounts)){result+=redact(text.slice(end,match.index))+match[0];end=match.index!+match[0].length}
 return result+redact(text.slice(end))
}
/** Conservative Saudi transaction templates; unknown formats require manual entry. */
export function parseBankSms(message:BankSmsMessage,lineNumber:number):SmsParseResult {
 const body=latinizeDigits(message.body).replace(/\r/g,'')
 if(/عرض|سيتم|عرض خاص|offer|will be|scheduled/i.test(body))return {ok:false,reason:'عرض أو حركة مستقبلية وليست عملية مكتملة'}
 if(sensitive.test(body))return {ok:false,reason:'رسالة تحقق أو كلمة سر؛ تم تجاهلها'}
 if(/مرفوض|رفض العملية|لم تتم|غير ناجح|declined|failed|unsuccessful/i.test(body))return {ok:false,reason:'عملية مرفوضة أو غير مكتملة'}
 const out=/شراء|سحب نقدي|حوالة\s*(?:صادرة|محلية صادرة|دولية صادرة)|تحويل\s*صادر|سداد\s*فاتورة|purchase|cash withdrawal|outgoing transfer/i.test(body)
 const incoming=/حوالة\s*(?:واردة|داخلية واردة)|تحويل\s*وارد|إيداع|ايداع|إضافة راتب|ايداع راتب|incoming transfer|salary deposit|cash deposit|تم استرداد|refund/i.test(body)
 if(out===incoming)return {ok:false,reason:'اتجاه الحركة غير واضح؛ لم نفترض أنها دخل أو مصروف'}
 if(/\b(?:USD|EUR|EGP|AED|GBP)\b|دولار|يورو|جنيه/i.test(body))return {ok:false,reason:'النسخة الحالية تدعم رسائل الريال السعودي فقط'}
 const money=[...body.matchAll(/(?:بمبلغ|مبلغ|المبلغ|amount)\s*[:：]?\s*(?:SAR|ر\.?س\.?|ريال)?\s*([\d,٬]+(?:[.٫]\d{1,2})?)\s*(SAR|ر\.?س\.?|ريال)?/gi)]
 if(money.length!==1)return {ok:false,reason:'مبلغ العملية غير واضح أو يوجد أكثر من مبلغ عملية'}
 if(!/SAR|ر\.?س\.?|ريال/i.test(money[0][0]))return {ok:false,reason:'عملة المبلغ غير مذكورة بوضوح'}
 const amount=tryParseMoney(money[0][1].replace(/٬/g,',').replace(/٫/g,'.'))
 if(amount===null||amount<=0)return {ok:false,reason:'المبلغ غير صالح'}
 const match=body.match(/(?:في|بتاريخ|التاريخ|on|date)\s*[:：]?\s*(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?!\d)/i)
 const short=body.match(/(?:في|بتاريخ|التاريخ|on|date)\s*[:：]?\s*(\d{1,2})[-/](\d{1,2})[-/](\d{4})(?!\d)/i)
 const date=match?match[1]+'-'+match[2].padStart(2,'0')+'-'+match[3].padStart(2,'0'):short?short[3]+'-'+short[2].padStart(2,'0')+'-'+short[1].padStart(2,'0'):''
 if(!isValidIsoDate(date))return {ok:false,reason:'تاريخ العملية غير واضح؛ تاريخ وصول الرسالة لا يكفي'}
 const merchant=body.match(/(?:لدى|عند|تاجر|merchant|at)\s*[:：]?\s*([^\n]+?)(?=\s+(?:في|بتاريخ|on|الرصيد|balance)(?:\s|[:：])|$)/im)?.[1]?.trim()??''
 const safeBody=redactSms(body)
 return {ok:true,row:{lineNumber,date,amountMinor:amount,direction:incoming?'in':'out',merchantName:redactSms(merchant),reference:'SMS:'+hashContent(message.sender+'|'+message.receivedAt+'|'+body),sourceName:message.sender,description:safeBody,raw:safeBody}}
}
