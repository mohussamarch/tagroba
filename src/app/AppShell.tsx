import { SmsImportFlow } from '../presentation/screens/SmsImportFlow'
import { SmsInboxAccess } from '../presentation/components/SmsInboxAccess'
import { ShellOverlays } from './ShellOverlays'
import { useState } from 'react'
import { HomeScreen } from '../presentation/screens/HomeScreen'
import { TransactionsScreen } from '../presentation/screens/TransactionsScreen'
import { BudgetScreen } from '../presentation/screens/BudgetScreen'
import { PeopleScreen } from '../presentation/screens/PeopleScreen'
import { InvestmentScreen } from '../presentation/screens/InvestmentScreen'
import { SettingsScreen } from '../presentation/screens/SettingsScreen'
import { ImportSheet } from '../presentation/screens/ImportSheet'
import { AddTransactionSheet } from '../presentation/screens/AddTransactionSheet'
import { TransactionSheet } from '../presentation/screens/TransactionSheet'
import { AddMenu } from '../presentation/components/AddMenu'
import { ShellTabs } from '../presentation/components/ShellTabs'
import { ShellHeader } from '../presentation/components/ShellHeader'
import { MoreScreen } from '../presentation/screens/MoreScreen'
import { useTheme } from '../presentation/theme/useTheme'
import { useAppData } from './useAppData'
import type { Transaction } from '../domain/entities/types'
import type { Container } from './container'
import './AppShell.css'
type Tab = 'home' | 'budget' | 'transactions' | 'people' | 'invest' | 'settings' | 'more'
const TAB_TITLES: Record<Tab, string> = {
  home: 'الرئيسية',
  budget: 'الميزانية',
  transactions: 'العمليات',
  people: 'الأشخاص',
  invest: 'الاستثمار',
  settings: 'الإعدادات',
  more: 'المزيد',
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
  const [tab, setTab] = useState<Tab>('home')
  const app = useAppData(container, uid, tab)
  const { theme, toggleTheme } = useTheme()
  const [amountsHidden, setAmountsHidden] = useState(false)
  const [addMenuOpen, setAddMenuOpen] = useState(false)
  const [addTxOpen, setAddTxOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [smsOpen, setSmsOpen] = useState(false)
  const [kindsOpen, setKindsOpen] = useState(false)
  const [notifOpen, setNotifOpen] = useState(false)
  const [linking, setLinking] = useState<Transaction | null>(null)
  /** العملية المفتوحة للتفاصيل — التصنيف والملاحظة والوسوم. */
  const [opened, setOpened] = useState<Transaction | null>(null)
  const [rulesOpen, setRulesOpen] = useState(false)
  const [recurringOpen,setRecurringOpen] = useState(false)
  const [historyOpen,setHistoryOpen] = useState(false)
  const [categoriesOpen,setCategoriesOpen] = useState(false)
  const reload = () => void app.reload()
  const unseenCount = app.notifications?.unseen.length ?? 0
  const signOut = () => {
    void (async () => {
      if (app.user.smsInbox.available) await app.user.smsInbox.disable()
      await app.clearSnapshot()
      onSignOut()
    })().catch(() => window.alert('تعذر إيقاف قراءة الرسائل. جرّب تسجيل الخروج تاني.'))
  }
  /* السطر ده كان بيظهر دايمًا فوق المحتوى. بقى يظهر بس لما يكون فيه فعلًا
     عرض محفوظ قديم أو تحديث شغال أو خطأ — إعادة تصميم 2026-09-11. */
  const syncNote = app.snapshotAt
    ? 'آخر عرض محفوظ: ' + new Date(app.snapshotAt).toLocaleString('ar-SA') +
      (app.pending.home ? ' — بيتم تحديثه…' : ' — تعذر تحديثه')
    : app.activePending
      ? 'بيتم تحديث البيانات…'
      : Object.values(app.errors).some(Boolean)
        ? 'تعذر تحديث بعض البيانات'
        : null
  return (
    <div className="shell">
      <ShellHeader
        title={TAB_TITLES[tab]}
        unseenCount={unseenCount}
        amountsHidden={amountsHidden}
        onOpenNotifications={() => {setNotifOpen(true);void app.ensure('notifications')}}
        onToggleAmounts={() => setAmountsHidden((v) => !v)}
      />
      <main className="shell__body">
        <SmsInboxAccess user={app.user} wallets={app.wallets} onImported={reload} showWhenEmpty={tab === 'settings'}/>
        {syncNote && (
          <div className="syncStatus" role="status">
            <span>{syncNote}</span>
            <button className="btn btn--quiet" onClick={reload} disabled={app.activePending}>تحديث</button>
          </div>
        )}
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
            loading={app.pending.home}
            error={app.errors.home}
            payday={app.payday}
            amountsHidden={amountsHidden}
            onPeriodChange={app.setPeriod}
            onRetry={reload}
            onOpenTransactions={() => setTab('transactions')}
            onFixKinds={() => {setKindsOpen(true);void app.ensure('transactions')}}
          />
        )}
        {tab === 'budget' && (
          <BudgetScreen
            data={app.budgetData}
            loading={app.pending.budget}
            error={app.errors.budget}
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
            loading={app.pending.transactions}
            error={app.errors.transactions}
            data={app.txnData}
            amountsHidden={amountsHidden}
            period={app.period}
            payday={app.payday}
            onPeriodChange={app.setPeriod}
            onImport={() => setImportOpen(true)}
            onFixKinds={() => setKindsOpen(true)}
            onOpenHistory={() => setHistoryOpen(true)}
            onOpenTransaction={setOpened}
            onRetry={reload}
          />
        )}
        {tab === 'people' && (
          <PeopleScreen
            user={app.user}
            rows={app.people}
            loading={app.pending.people}
            error={app.errors.people}
            amountsHidden={amountsHidden}
            onChanged={reload}
            onRetry={reload}
          />
        )}
        {tab === 'invest' && (
          <InvestmentScreen
            user={app.user}
            data={app.portfolio}
            loading={app.pending.portfolio}
            error={app.errors.portfolio}
            amountsHidden={amountsHidden}
            onChanged={reload}
            onRetry={reload}
          />
        )}
        {tab === 'more' && (
          <MoreScreen
            periods={app.home?.recentPeriods ?? []}
            periodsLoading={app.pending.history}
            periodsError={app.errors.history}
            amountsHidden={amountsHidden}
            onRetry={reload}
            onOpenPeople={() => setTab('people')}
            onOpenInvest={() => setTab('invest')}
            onOpenSettings={() => setTab('settings')}
            onOpenRecurring={() => setRecurringOpen(true)}
            onOpenCategories={() => setCategoriesOpen(true)}
          />
        )}
        {tab === 'settings' && (
          <SettingsScreen
            user={app.user}
            wallets={app.wallets}
            today={app.today}
            payday={app.payday}
            onWalletsChanged={reload}
            onRestored={reload}
            onOpenRules={() => setRulesOpen(true)}
            theme={theme}
            onToggleTheme={toggleTheme}
            onSignOut={signOut}
          />
        )}
      </main>
      {/* الشريط السفلي — خمس خانات وزر الإضافة في النص (OVERRIDES §20) */}
      <ShellTabs tab={tab} onChange={setTab} onAdd={() => setAddMenuOpen(true)}/>
      {addMenuOpen && (
        <AddMenu onSms={() => { setAddMenuOpen(false); setSmsOpen(true) }}
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
      {smsOpen && <SmsImportFlow user={app.user} wallets={app.wallets} today={app.today} onClose={()=>setSmsOpen(false)} onImported={()=>{setSmsOpen(false);void app.reload()}}/>}
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
      <ShellOverlays
        app={app}
        amountsHidden={amountsHidden}
        reload={reload}
        open={{
          categories: categoriesOpen,
          history: historyOpen,
          recurring: recurringOpen,
          rules: rulesOpen,
          notifications: notifOpen,
          kinds: kindsOpen,
        }}
        close={(key) => {
          const setters = { categories: setCategoriesOpen, history: setHistoryOpen,
            recurring: setRecurringOpen, rules: setRulesOpen,
            notifications: setNotifOpen, kinds: setKindsOpen }
          setters[key](false)
        }}
        linking={linking}
        onCloseLinking={(linked) => {
          setLinking(null)
          if (linked) reload()
        }}
      />
    </div>
  )
}
