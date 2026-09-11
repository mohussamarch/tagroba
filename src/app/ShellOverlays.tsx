import { CategoriesScreen } from '../presentation/screens/CategoriesScreen'
import { HistoryReviewScreen } from '../presentation/screens/HistoryReviewScreen'
import { RecurringScreen } from '../presentation/screens/RecurringScreen'
import { RulesScreen } from '../presentation/screens/RulesScreen'
import { LinkPersonSheet } from '../presentation/screens/LinkPersonSheet'
import { NotificationsSheet } from '../presentation/screens/NotificationsSheet'
import { KindsSheet } from '../presentation/screens/KindsSheet'
import type { Transaction } from '../domain/entities/types'
import type { useAppData } from './useAppData'

export type OverlayKey =
  | 'categories'
  | 'history'
  | 'recurring'
  | 'rules'
  | 'notifications'
  | 'kinds'

/**
 * الأوراق المنبثقة اللي بتتفتح من أي شاشة — مفصولة عن `AppShell`
 * لحد الملف 300 سطر (CLAUDE.md #7). مفيش منطق هنا: فتح وقفل وإعادة تحميل.
 */
export function ShellOverlays({
  app,
  open,
  close,
  reload,
  amountsHidden,
  linking,
  onCloseLinking,
}: {
  app: ReturnType<typeof useAppData>
  open: Record<OverlayKey, boolean>
  close: (key: OverlayKey) => void
  reload: () => void
  amountsHidden: boolean
  linking: Transaction | null
  onCloseLinking: (linked: boolean) => void
}) {
  return (
    <>
      {open.categories && (
        <CategoriesScreen user={app.user} onClose={() => close('categories')} onChanged={reload} />
      )}
      {open.history && (
        <HistoryReviewScreen
          user={app.user}
          today={app.today}
          categories={app.home?.categories ?? []}
          onClose={() => close('history')}
          onChanged={reload}
        />
      )}
      {open.recurring && (
        <RecurringScreen
          user={app.user}
          today={app.today}
          hidden={amountsHidden}
          onClose={() => close('recurring')}
        />
      )}
      {open.rules && (
        <RulesScreen
          user={app.user}
          categories={app.home?.categories ?? []}
          onClose={() => close('rules')}
        />
      )}
      {linking && (
        <LinkPersonSheet
          user={app.user}
          transaction={linking}
          people={app.people.map((row) => row.person)}
          loading={app.pending.people}
          loadError={app.errors.people}
          onClose={() => onCloseLinking(false)}
          onLinked={() => onCloseLinking(true)}
        />
      )}
      {open.notifications && (
        <NotificationsSheet
          user={app.user}
          view={app.notifications}
          loading={app.pending.notifications}
          error={app.errors.notifications}
          onClose={() => close('notifications')}
          onSeen={reload}
        />
      )}
      {open.kinds && app.txnData && (
        <KindsSheet
          user={app.user}
          transactions={app.txnData.transactions}
          onClose={() => close('kinds')}
          onDone={reload}
        />
      )}
    </>
  )
}
