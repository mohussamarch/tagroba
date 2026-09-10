import type {FullBackupData} from './fullBackup'
/** Validate financial relationships, including combinations formed by a merge. */
export function checkBackupFinance(data:FullBackupData){
  const transactions=new Map(data.transactions.map(row=>[row.id,row]))
  const obligations=new Map(data.obligations.map(row=>[row.id,row]))
  const allocations=new Map<unknown,number>(),settlements=new Map<unknown,number>()
  for(const row of data.allocations){
    const txn=transactions.get(row.transactionId)
    if(txn?.currency!==row.currency)throw Error('عملة تخصيص الشخص مختلفة عن العملية')
    const sum=(allocations.get(row.transactionId)??0)+Number(row.amountMinor)
    if(!Number.isSafeInteger(sum)||sum>Number(txn?.amountMinor))throw Error('تخصيصات الأشخاص تتجاوز مبلغ العملية')
    allocations.set(row.transactionId,sum)
  }
  for(const row of data.obligations){
    const txn=transactions.get(row.originTransactionId)
    if(txn?.currency!==row.currency||Number(row.originalMinor)>Number(txn?.amountMinor))throw Error('الدين غير متوافق مع العملية الأصلية')
  }
  for(const row of data.settlements){
    const obligation=obligations.get(row.obligationId),txn=transactions.get(row.transactionId)
    if(txn?.currency!==obligation?.currency||Number(row.amountMinor)>Number(txn?.amountMinor))throw Error('تسوية غير متوافقة مع عملة أو مبلغ العملية')
    const sum=(settlements.get(row.obligationId)??0)+Number(row.amountMinor)
    if(!Number.isSafeInteger(sum)||sum>Number(obligation?.originalMinor))throw Error('التسويات تتجاوز الدين الأصلي')
    settlements.set(row.obligationId,sum)
  }
}
