export interface BankSmsMessage {
  sender: string
  receivedAt: string
  body: string
}
export interface BankSmsPort {
  readonly available: boolean
  read(input: { from: string; to: string; senders: string[] }): Promise<{ messages: BankSmsMessage[]; truncated: boolean }>
}

export interface SmsRow {
 lineNumber:number;date:string;amountMinor:number;direction:'in'|'out';merchantName:string
 reference:string|null;sourceName:string;description:string;raw:string
}
export type SmsParseResult = {ok:true;row:SmsRow} | {ok:false;reason:string}
export type BankSmsParser = (message:BankSmsMessage,lineNumber:number)=>SmsParseResult
