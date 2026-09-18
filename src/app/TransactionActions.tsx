import { useState, type ReactNode } from 'react'
import type { Transaction } from '../domain/entities/types'
import { TransactionMenu, type TransactionMenuAction } from '../presentation/components/TransactionMenu'
import { TransactionSheet } from '../presentation/screens/TransactionSheet'
import { LinkPersonSheet } from '../presentation/screens/LinkPersonSheet'
import { SettleWithTransactionSheet } from '../presentation/screens/SettleWithTransactionSheet'
import { AddToProjectSheet } from '../presentation/screens/AddToProjectSheet'
import type { useAppData } from './useAppData'

/**
 * أوراق العملية الواحدة: قايمة «النقط التلاتة» (OVERRIDES §30)، والتفاصيل، والربط بشخص، والربط بدين موجود.
 * مفصولة عن `AppShell` عشان حد الـ300 سطر. مفيش منطق هنا: فتح وقفل وإعادة تحميل.
 */
export function useTransactionActions({ app, amountsHidden, reload }: {
  app: ReturnType<typeof useAppData>
  amountsHidden: boolean
  reload: () => void
}): { openMenu: (transaction: Transaction) => void; openDetails: (transaction: Transaction) => void; element: ReactNode } {
  const [menuFor, setMenuFor] = useState<Transaction | null>(null)
  const [opened, setOpened] = useState<Transaction | null>(null)
  const [linking, setLinking] = useState<Transaction | null>(null)
  const [settling, setSettling] = useState<Transaction | null>(null)
  const [projectFor, setProjectFor] = useState<Transaction | null>(null)

  function choose(action: TransactionMenuAction) {
    const transaction = menuFor
    setMenuFor(null)
    if (!transaction) return
    if (action === 'person') { void app.ensure('people'); setLinking(transaction) }
    else if (action === 'settle') { void app.ensure('people'); setSettling(transaction) }
    else if (action === 'project') setProjectFor(transaction)
    else setOpened(transaction)
  }

  const element = (
    <>
      {menuFor && (
        <TransactionMenu transaction={menuFor} amountsHidden={amountsHidden} onChoose={choose} onClose={() => setMenuFor(null)} />
      )}
      {opened && (
        <TransactionSheet
          user={app.user}
          transaction={opened}
          categories={app.home?.categories ?? []}
          onClose={() => setOpened(null)}
          onChanged={reload}
          onLinkPerson={() => {
            void app.ensure('people')
            setLinking(opened)
            setOpened(null)
          }}
        />
      )}
      {linking && (
        <LinkPersonSheet
          user={app.user}
          transaction={linking}
          people={app.people.map((row) => row.person)}
          loading={app.pending.people}
          loadError={app.errors.people}
          onClose={() => setLinking(null)}
          onLinked={() => { setLinking(null); reload() }}
        />
      )}
      {settling && (
        <SettleWithTransactionSheet
          user={app.user}
          transaction={settling}
          rows={app.people}
          loading={app.pending.people}
          onClose={() => setSettling(null)}
          onSettled={() => { setSettling(null); reload() }}
        />
      )}
      {projectFor && (
        <AddToProjectSheet user={app.user} transaction={projectFor} amountsHidden={amountsHidden} onClose={() => setProjectFor(null)} onChanged={reload} />
      )}
    </>
  )

  return { openMenu: setMenuFor, openDetails: setOpened, element }
}
