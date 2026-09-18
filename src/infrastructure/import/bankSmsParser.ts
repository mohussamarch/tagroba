import { latinizeDigits } from '../../domain/normalize'
import { tryParseMoney } from '../../domain/money'
import { isValidIsoDate } from '../../domain/period'
import { hashContent } from '../../domain/dedupe'
import type { BankSmsMessage, SmsParseResult } from '../../application/ports/BankSmsPort'

const sensitive = /\bOTP\b|verification\s*code|one.time\s*(?:password|code)|رمز\s*(?:التحقق|التوثيق|التفعيل|الدخول)|كلمة\s*(?:المرور|السر)/i
export function redactSms(text:string):string {
 // Direct callers (including backup export) do not pass through parseBankSms.
 text=latinizeDigits(text)
 const redact=(value:string)=>value.replace(/SA[\d\s]{20,}/gi, value=>'••••'+value.replace(/\s/g,'').slice(-4))
  .replace(/\b(?:\d[ -]*){12,34}\b/g,value=>'••••'+value.replace(/\D/g,'').slice(-4))
  .replace(/\d{5,}/g,value=>'••••'+value.slice(-4))
 const amounts=/(?:بمبلغ|المبلغ|مبلغ|amount|الرصيد|balance)\s*[:：]?\s*(?:(?:SAR|ريال|ر\.?س\.?)\s*[\d,٬]+(?:[.٫]\d{1,2})?|[\d,٬]+(?:[.٫]\d{1,2})?\s*(?:SAR|ريال|ر\.?س\.?))/gi
 let result='',end=0
 for(const match of text.matchAll(amounts)){result+=redact(text.slice(end,match.index))+match[0];end=match.index!+match[0].length}
 return result+redact(text.slice(end))
}

/*
 * 2026-09-18: كل رسايل المالك الحقيقية اترفضت بـ«مبلغ العملية غير واضح» — الصيغ القديمة هنا كانت مبنية على
 * رسايل مخترعة (لازم كلمة «مبلغ» قبل الرقم، والسنة بأربع أرقام). دلوقتي:
 * - **المبلغ** = أي رقم جنبه عملة الريال (قبله أو بعده) في سطر مش رصيد ولا متاح ولا حد ولا رسوم.
 *   لازم يطلع مبلغ واحد بالظبط (أو نفس القيمة متكررة)؛ أكتر من قيمة ⇒ رفض زي الأول.
 * - **التاريخ** بسنة برقمين (`26-9-16` أو `16/9/26`) بيتفهم بتاريخ وصول الرسالة: لازم يطلع تاريخ واحد بس
 *   قبل الوصول بيوم أو أقل وبعد 60 يوم قبله على الأكتر، وإلا رفض. السنة بأربع أرقام زي الأول.
 * كل رسالة بتفضل محتاجة تأكيد المستخدم قبل ما تتحفظ.
 */
const CURRENCY = String.raw`(?:(?<![A-Za-z])(?:SAR|SR)(?![A-Za-z])|ر\.\s?س\.?|ريال)`
const NUMBER = String.raw`\d(?:[\d,٬]*\d)?(?:[.٫]\d{1,2})?`
const CURRENCY_AMOUNT = new RegExp(String.raw`${CURRENCY}\s*[:：]?\s*(${NUMBER})|(${NUMBER})\s*${CURRENCY}`, 'gi')
const NOT_TRANSACTION_AMOUNT = /الرصيد|رصيد|balance|المتاح|متاح|available|الحد|limit|رسوم|\bfees?\b|عمولة|المتبقي/i
const BARE_AMOUNT = /(?:بمبلغ|المبلغ|مبلغ|amount|بـ|قيمة)\s*[:：]?\s*\d/i
const DAY_MS = 86_400_000

type AmountResult = {ok:true;amountMinor:number} | {ok:false;reason:string}

function transactionAmount(body:string):AmountResult {
 const values=new Set<number>()
 for(const line of body.split('\n')){
  for(const match of line.matchAll(CURRENCY_AMOUNT)){
   if(NOT_TRANSACTION_AMOUNT.test(line.slice(0,match.index)))continue
   const amount=tryParseMoney((match[1]??match[2]!).replace(/٬/g,',').replace(/٫/g,'.'))
   if(amount===null||amount<=0)return {ok:false,reason:'المبلغ غير صالح'}
   values.add(amount)
  }
 }
 if(values.size===1)return {ok:true,amountMinor:[...values][0]!}
 if(values.size>1)return {ok:false,reason:'يوجد أكثر من مبلغ عملية في الرسالة'}
 return {ok:false,reason:BARE_AMOUNT.test(body)?'عملة المبلغ غير مذكورة بوضوح':'مبلغ العملية غير واضح'}
}

const iso=(y:number,m:number,d:number)=>`${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`

function transactionDate(body:string,receivedAt:string):string|null {
 const long=body.match(/(?<!\d)(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?!\d)/)
 if(long){const value=iso(+long[1]!,+long[2]!,+long[3]!);return isValidIsoDate(value)?value:null}
 const longDmy=body.match(/(?<!\d)(\d{1,2})[-/](\d{1,2})[-/](\d{4})(?!\d)/)
 if(longDmy){const value=iso(+longDmy[3]!,+longDmy[2]!,+longDmy[1]!);return isValidIsoDate(value)?value:null}
 const received=Date.parse(receivedAt)
 if(Number.isNaN(received))return null
 const candidates=new Set<string>()
 for(const match of body.matchAll(/(?<!\d)(\d{1,2})[-/](\d{1,2})[-/](\d{1,2})(?!\d)/g)){
  const [a,b,c]=[+match[1]!,+match[2]!,+match[3]!]
  for(const value of [iso(2000+a,b,c),iso(2000+c,b,a)]){
   if(!isValidIsoDate(value))continue
   const time=Date.parse(value+'T00:00:00Z')
   if(time<=received+DAY_MS&&time>=received-60*DAY_MS)candidates.add(value)
  }
 }
 return candidates.size===1?[...candidates][0]!:null
}

function merchantOf(body:string):string {
 const labeled=body.match(/(?:لدى|عند|تاجر|\bmerchant\b|\bat\b)\s*[:：]?\s*([^\n]+?)(?=\s+(?:في|بتاريخ|\bon\b|الرصيد|\bbalance\b)(?:\s|[:：])|$)/im)?.[1]?.trim()
 return labeled||body.match(/^\s*(?:من|إلى|الى|\bfrom\b|\bto\b)\s*[:：]\s*([^\n]+)$/im)?.[1]?.trim()||''
}

/** Conservative Saudi transaction templates; unknown formats require manual entry. */
export function parseBankSms(message:BankSmsMessage,lineNumber:number):SmsParseResult {
 const body=latinizeDigits(message.body).replace(/\r/g,'')
 if(/عرض|سيتم|عرض خاص|offer|will be|scheduled/i.test(body))return {ok:false,reason:'عرض أو حركة مستقبلية وليست عملية مكتملة'}
 if(sensitive.test(body))return {ok:false,reason:'رسالة تحقق أو كلمة سر؛ تم تجاهلها'}
 if(/مرفوض|رفض العملية|لم تتم|غير ناجح|declined|failed|unsuccessful/i.test(body))return {ok:false,reason:'عملية مرفوضة أو غير مكتملة'}
 const out=/شراء|سحب|خصم|سداد|مدفوعات|دفع|حوالة\s*(?:صادرة|محلية صادرة|دولية صادرة)|تحويل\s*صادر|purchase|withdrawal|outgoing transfer/i.test(body)
 const incoming=/حوالة\s*(?:واردة|داخلية واردة|محلية واردة)|تحويل\s*وارد|إيداع|ايداع|راتب|استرداد|مرتجع|incoming transfer|salary|deposit|refund/i.test(body)
 if(out===incoming)return {ok:false,reason:'اتجاه الحركة غير واضح؛ لم نفترض أنها دخل أو مصروف'}
 if(/\b(?:USD|EUR|EGP|AED|GBP)\b|دولار|يورو|جنيه/i.test(body))return {ok:false,reason:'النسخة الحالية تدعم رسائل الريال السعودي فقط'}
 const amount=transactionAmount(body)
 if(!amount.ok)return amount
 const date=transactionDate(body,message.receivedAt)
 if(!date)return {ok:false,reason:'تاريخ العملية غير واضح؛ تاريخ وصول الرسالة لا يكفي'}
 const safeBody=redactSms(body)
 return {ok:true,row:{lineNumber,date,amountMinor:amount.amountMinor,direction:incoming?'in':'out',merchantName:redactSms(merchantOf(body)),reference:'SMS:'+hashContent(message.sender+'|'+message.receivedAt+'|'+body),sourceName:message.sender,description:safeBody,raw:safeBody}}
}
