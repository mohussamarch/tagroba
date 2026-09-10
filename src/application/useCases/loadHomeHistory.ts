import {buildPeriod,type Period} from '../../domain/period'
import {assessCoverage} from '../../domain/analytics'
import {computePeriodTotals} from '../../domain/ledger'
import type {AllocationRepository,TransactionRepository} from '../ports/repositories'
import type {HomeScreenData,PeriodSummary} from './loadHomeScreen'
export function makeLoadHomeHistory(deps:{txns:TransactionRepository;allocations:AllocationRepository}){
 return async ({period,payday,current}:{period:Period;payday:number;current:HomeScreenData}):Promise<PeriodSummary[]>=>{
  const older=await Promise.all(Array.from({length:5},async(_,index)=>{
   const [year,month]=period.key.split('-').map(Number)
   const total=year*12+month-2-index
   const p=buildPeriod(Math.floor(total/12),total%12+1,payday)
   const rows=await deps.txns.listByDateRange(p.start,p.end)
   const allocations=await deps.allocations.listByTransactionIds(rows.map(row=>row.id))
   const totals=computePeriodTotals(rows,allocations),coverage=assessCoverage(rows)
   const unknown=coverage.total>0&&coverage.unclassified===coverage.total
   return {period:p,expenseMinor:unknown?null:totals.personalExpenseMinor,incomeMinor:unknown?null:totals.incomeMinor,transactionCount:rows.length}
  }))
  return [{period,expenseMinor:current.expenseMinor,incomeMinor:current.incomeMinor,transactionCount:current.transactionCount},...older]
 }
}
