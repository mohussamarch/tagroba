import type { BankSmsMessage, BankSmsPort, BankSmsParser, SmsRow } from '../ports/BankSmsPort'
import { isValidIsoDate } from '../../domain/period'
export function makeReadBankSms(port:BankSmsPort, parseBankSms:BankSmsParser) {
 function prepare(messages:BankSmsMessage[]) {
  const rows:SmsRow[]=[], skipped:{line:number;reason:string}[]=[]
  messages.forEach((message,index)=>{
   const parsed=parseBankSms(message,index+1)
   if(parsed.ok)rows.push(parsed.row)
   else skipped.push({line:index+1,reason:parsed.reason})
  })
  return {rows,skipped,content:JSON.stringify(rows)}
 }
 return {
  available:port.available,
  async read(input:{from:string;to:string;senders:string[]}) {
   if(!isValidIsoDate(input.from)||!isValidIsoDate(input.to)||input.from>input.to)throw Error('اختار فترة صحيحة')
   if(new Date(input.to).getTime()-new Date(input.from).getTime()>366*86400000)throw Error('اختار سنة واحدة بحد أقصى')
   if(!input.senders.length||input.senders.length>10||input.senders.some(x=>!x.trim()||x.length>50))throw Error('اكتب اسم مرسل البنك كما يظهر في الرسائل')
   const result=await port.read(input)
   return {...prepare(result.messages),truncated:result.truncated}
  },
  paste(body:string){ return {...prepare([{body,sender:'نص ملصق',receivedAt:''}]),truncated:false} },
 }
}
