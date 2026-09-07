import { useState } from 'react'
import { HomeScreen } from '../presentation/screens/HomeScreen'
import { TransactionsScreen } from '../presentation/screens/TransactionsScreen'
import { BudgetScreen } from '../presentation/screens/BudgetScreen'
import { PeopleScreen } from '../presentation/screens/PeopleScreen'
import { InvestmentScreen } from '../presentation/screens/InvestmentScreen'
import { SettingsScreen } from '../presentation/screens/SettingsScreen'
import { ImportSheet } from '../presentation/screens/ImportSheet'
import { KindsSheet } from '../presentation/screens/KindsSheet'
import { NotificationsSheet } from '../presentation/screens/NotificationsSheet'
import { AddTransactionSheet } from '../presentation/screens/AddTransactionSheet'
import { LinkPersonSheet } from '../presentation/screens/LinkPersonSheet'
import { AddMenu } from '../presentation/components/AddMenu'
import { TabButton } from '../presentation/components/TabButton'
import { ShellHeader } from '../presentation/components/ShellHeader'
import { useTheme } from '../presentation/theme/useTheme'
import { useAppData } from './useAppData'
import type { Transaction } from '../domain/entities/types'
import type { Container } from './container'
import './AppShell.css'

type Tab = 'home' | 'budget' | 'transactions' | 'people' | 'invest' | 'settings'

const TAB_TITLES: Record<Tab, string> = {
  home: 'مصروفي',
  budget: 'الميزانية',
  transactions: 'العمليات',
  people: 'الأشخاص',
  invest: 'الاستثمار',
  settings: 'الإعدادات',
}

/** قشرة التطبيق بعد الدخول: التنقل والأوراق. التحميل في `useAppData`. */
export function AppShell({
  container,
  uid,
  onSignOut,
}: {
  container: Container
  uid: string
  onSignOut: () => void
}) {
  const app = useAppData(container, uid)
  const { theme, toggleTheme } = useTheme()

  const [tab, setTab] = useState<Tab>('home')
  const [amountsHidden, setAmountsHidden] = useState(false)
  const [addMenuOpen, setAddMenuOpen] = useState(false)
  const [addTxOpen, setAddTxOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [kindsOpen, setKindsOpen] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)
  const [linking, setLinking] = useState<Transaction | null>(null)

  const reload = () => void app.reload()
  const unseenCount = app.notifications?.unseen.length ?? 0

  return (
    <div className="shell">
      <ShellHeader
        title={TAB_TITLES[tab]}
        unseenCount={unseenCount}
        amountsHidden={amountsHidden}
        theme={theme}
        onAdd={() => setAddMenuOpen(true)}
        onOpenNotifications={() => setNotifOpen(true)}
        onToggleAmounts={() => setAmountsHidden((v) => !v)}
        onToggleTheme={toggleTheme}
        onSignOut={onSignOut}
      />

      <main className="shell__body">
        {app.recovery && (
          <div className="notice" role="status">
            {app.recovery}
            <br />
            <button
              type="button"
              className="btn btn--quiet"
              onClick={app.dismissRecovery}
              style={{ marginTop: 10 }}
            >
              تمام، فهمت
            </button>
          </div>
        )}

        {tab === 'home' && (
          <HomeScreen
            data={app.home}
            loading={app.loading}
            error={app.error}
            payday={app.payday}
            amountsHidden={amountsHidden}
            onPeriodChange={app.setPeriod}
            onRetry={reload}
            onOpenTransactions={() => setTab('transactions')}
            onFixKinds={() => setKindsOpen(true)}
          />
        )}

        {tab === 'budget' && (
          <BudgetScreen
            data={app.budgetData}
            loading={app.loading}
            error={app.error}
            period={app.period}
            payday={app.payday}
            amountsHidden={amountsHidden}
            onPeriodChange={app.setPeriod}
            onSetTotal={async (limitMinor, threshold) => {
              await app.user.setBudget.setTotalLimit(app.period, limitMinor, threshold)
              reload()
            }}
            onClearTotal={async () => {
              await app.user.setBudget.clearTotalLimit(app.period)
              reload()
            }}
            onSetCategory={async (categoryId, limitMinor) => {
              await app.user.setBudget.setCategoryLimit(app.period, categoryId, limitMinor)
              reload()
            }}
            onClearCategory={async (categoryId) => {
              await app.user.setBudget.clearCategoryLimit(app.period, categoryId)
              reload()
            }}
            onRetry={reload}
          />
        )}

        {tab === 'transactions' && (
          <TransactionsScreen
            loading={app.loading}
            error={app.error}
            data={app.txnData}
            amountsHidden={amountsHidden}
            period={app.period}
            payday={app.payday}
            onPeriodChange={app.setPeriod}
            onImport={() => setImportOpen(true)}
            onFixKinds={() => setKindsOpen(true)}
            onOpenTransaction={setLinking}
            onRetry={reload}
          />
        )}

        {tab === 'people' && (
          <PeopleScreen
            user={app.user}
            rows={app.people}
            loading={app.loading}
            error={app.error}
            amountsHidden={amountsHidden}
            onChanged={reload}
            onRetry={reload}
          />
        )}

        {tab === 'invest' && (
          <InvestmentScreen
            user={app.user}
            data={app.portfolio}
            loading={app.loading}
            error={app.error}
            amountsHidden={amountsHidden}
            onChanged={reload}
            onRetry={reload}
          />
        )}

        {tab === 'settings' && (
          <SettingsScreen
            user={app.user}
            wallets={app.wallets}
            today={app.today}
            payday={app.payday}
            onWalletsChanged={reload}
          />
        )}
      </main>

      {/* الشريط السفلي — spec/01 */}
      <nav className="shell__tabs" aria-label="التنقل الرئيسي">
        <TabButton label="الرئيسية" icon="⌂" active={tab === 'home'} onClick={() => setTab('home')} />
        <TabButton label="الميزانية" icon="◱" active={tab === 'budget'} onClick={() => setTab('budget')} />
        <TabButton label="العمليات" icon="☰" active={tab === 'transactions'} onClick={() => setTab('transactions')} />
        <TabButton label="الأشخاص" icon="◎" active={tab === 'people'} onClick={() => setTab('people')} />
        <TabButton label="الاستثمار" icon="◈" active={tab === 'invest'} onClick={() => setTab('invest')} />
        <TabButton label="الإعدادات" icon="⚙" active={tab === 'settings'} onClick={() => setTab('settings')} />
      </nav>

      {addMenuOpen && (
        <AddMenu
          onClose={() => setAddMenuOpen(false)}
          onAddTransaction={() => {
            setAddMenuOpen(false)
            setAddTxOpen(true)
          }}
          onImport={() => {
            setAddMenuOpen(false)
            setImportOpen(true)
          }}
        />
      )}

      {addTxOpen && (
        <AddTransactionSheet
          user={app.user}
          wallets={app.wallets}
          categories={app.home?.categories ?? []}
          today={app.today}
          onClose={() => setAddTxOpen(false)}
          onAdded={() => {
            setAddTxOpen(false)
            reload()
          }}
        />
      )}

      {importOpen && (
        <ImportSheet
          user={app.user}
          wallets={app.wallets}
          onClose={() => setImportOpen(false)}
          onImported={() => {
            setImportOpen(false)
            reload()
          }}
        />
      )}

      {linking && (
        <LinkPersonSheet
          user={app.user}
          transaction={linking}
          people={app.people.map((row) => row.person)}
          onClose={() => setLinking(null)}
          onLinked={() => {
            setLinking(null)
            reload()
          }}
        />
      )}

      {notifOpen && (
        <NotificationsSheet
          user={app.user}
          view={app.notifications}
          onClose={() => setNotifOpen(false)}
          onSeen={reload}
        />
      )}

      {kindsOpen && app.txnData && (
        <KindsSheet
          user={app.user}
          transactions={app.txnData.transactions}
          onClose={() => setKindsOpen(false)}
          onDone={reload}
        />
      )}
    </div>
  )
}
