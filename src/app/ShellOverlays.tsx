import { CategoriesScreen } from '../presentation/screens/CategoriesScreen'
import { ProjectsScreen } from '../presentation/screens/ProjectsScreen'
import { HistoryReviewScreen } from '../presentation/screens/HistoryReviewScreen'
import { RecurringScreen } from '../presentation/screens/RecurringScreen'
import { RulesScreen } from '../presentation/screens/RulesScreen'
import { NotificationsSheet } from '../presentation/screens/NotificationsSheet'
import { KindsSheet } from '../presentation/screens/KindsSheet'
import { OnboardingFlow } from '../presentation/screens/OnboardingFlow'
import type { useAppData } from './useAppData'

export type OverlayKey =
  | 'categories'
  | 'projects'
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
}: {
  app: ReturnType<typeof useAppData>
  open: Record<OverlayKey, boolean>
  close: (key: OverlayKey) => void
  reload: () => void
  amountsHidden: boolean
}) {
  return (
    <>
      {open.categories && (
        <CategoriesScreen user={app.user} onClose={() => close('categories')} onChanged={reload} />
      )}
      {open.projects && (
        <ProjectsScreen user={app.user} categories={app.home?.categories ?? []} amountsHidden={amountsHidden} onClose={() => close('projects')} />
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
      {/* أسئلة البداية للحساب الجديد بس (OVERRIDES §26) — فوق أي حاجة تانية */}
      {app.needsOnboarding && <OnboardingFlow user={app.user} onDone={app.finishOnboarding} />}
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
