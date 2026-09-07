import { useState } from 'react'
import { HomeScreen } from '../presentation/screens/HomeScreen'
import { TransactionsScreen } from '../presentation/screens/TransactionsScreen'
import { BudgetScreen } from '../presentation/screens/BudgetScreen'
import { PeopleScreen } from '../presentation/screens/PeopleScreen'
import { SettingsScreen } from '../presentation/screens/SettingsScreen'
import { ImportSheet } from '../presentation/screens/ImportSheet'
import { KindsSheet } from '../presentation/screens/KindsSheet'
import { AddTransactionSheet } from '../presentation/screens/AddTransactionSheet'
import { LinkPersonSheet } from '../presentation/screens/LinkPersonSheet'
import { AddMenu } from '../presentation/components/AddMenu'
import { TabButton } from '../presentation/components/TabButton'
import { useTheme } from '../presentation/theme/useTheme'
import { useAppData } from './useAppData'
import type { Transaction } from '../domain/entities/types'
import type { Container } from './container'
import './AppShell.css'

type Tab = 'home' | 'budget' | 'transactions' | 'people' | 'settings'

const TAB_TITLES: Record<Tab, string> = {
  home: 'مصروفي',
  budget: 'الميزانية',
  transactions: 'العمليات',
  people: 'الأشخاص',
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
  const [linking, setLinking] = useState<Transaction | null>(null)

  const reload = () => void app.reload()

  return (
    <div className="shell">
      <header className="shell__head">
        <h1 className="shell__title">{TAB_TITLES[tab]}</h1>
        <div className="shell__actions">
          <button
            type="button"
            className="iconBtn iconBtn--primary"
            onClick={() => setAddMenuOpen(true)}
            aria-label="إضافة"
          >
            <span aria-hidden="true">＋</span>
          </button>
          <button
            type="button"
            className="iconBtn"
            onClick={() => setAmountsHidden((v) => !v)}
            aria-label={amountsHidden ? 'إظهار المبالغ' : 'إخفاء المبالغ'}
            aria-pressed={amountsHidden}
          >
            <span aria-hidden="true">{amountsHidden ? '🙈' : '👁'}</span>
          </button>
          <button
            type="button"
            className="iconBtn"
            onClick={toggleTheme}
            aria-label={theme === 'light' ? 'تحويل للوضع الغامق' : 'تحويل للوضع الفاتح'}
          >
            <span aria-hidden="true">{theme === 'light' ? '🌙' : '☀️'}</span>
          </button>
          <button type="button" className="iconBtn" onClick={onSignOut} aria-label="تسجيل الخروج">
            <span aria-hidden="true">⎋</span>
          </button>
        </div>
      </header>

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
