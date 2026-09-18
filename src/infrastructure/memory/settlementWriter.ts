import type { SettlementWriter } from '../../application/ports/SettlementWriter'
import type { ObligationRepository, SettlementRepository } from '../../application/ports/repositories'
import { prepareSettlement } from '../../domain/settlementCommand'

const queues = new WeakMap<SettlementRepository, Promise<unknown>>()
export function memorySettlementWriter(obligations: ObligationRepository, settlements: SettlementRepository): SettlementWriter {
  return {
    settle(input) {
      const work = (queues.get(settlements) ?? Promise.resolve()).catch(() => {}).then(async () => {
        const obligation = (await obligations.listByPerson(input.personId)).find(row => row.id === input.obligationId)
        const rows = await settlements.listByObligations([input.obligationId])
        const result = prepareSettlement(input, obligation, rows)
        if (!rows.some(row => row.id === result.id)) await settlements.saveMany([result])
        return result
      })
      queues.set(settlements, work)
      return work
    },
  }
}
