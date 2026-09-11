import { SmsImportFlow } from '../presentation/screens/SmsImportFlow'
import { SmsInboxAccess } from '../presentation/components/SmsInboxAccess'
import { CategoriesScreen } from '../presentation/screens/CategoriesScreen'
import { HistoryReviewScreen } from '../presentation/screens/HistoryReviewScreen'
import { RecurringScreen } from '../presentation/screens/RecurringScreen'
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
import { TransactionSheet } from '../presentation/screens/TransactionSheet'
import { RulesScreen } from '../presentation/screens/RulesScreen'
import { AddMenu } from '../presentation/components/AddMenu'
import { ShellTabs } from '../presentation/components/ShellTabs'
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
  return (
    <div className="shell">
      <ShellHeader
        title={TAB_TITLES[tab]}
        unseenCount={unseenCount}
        amountsHidden={amountsHidden}
        theme={theme}
        onAdd={() => setAddMenuOpen(true)}
        onOpenNotifications={() => {setNotifOpen(true);void app.ensure('notifications')}}
        onToggleAmounts={() => setAmountsHidden((v) => !v)}
        onToggleTheme={toggleTheme}
        onSignOut={()=>{void (async()=>{if(app.user.smsInbox.available)await app.user.smsInbox.disable();await app.clearSnapshot();onSignOut()})().catch(()=>window.alert('تعذر إيقاف قراءة الرسائل. جرّب تسجيل الخروج تاني.'))}}
      />
      <main className="shell__body">
        <SmsInboxAccess user={app.user} wallets={app.wallets} onImported={reload} showWhenEmpty={tab === 'settings'}/>
        <div className="syncStatus" role="status"><span>{app.snapshotAt ? "آخر عرض محفوظ: "+new Date(app.snapshotAt).toLocaleString("ar-SA")+(app.pending.home ? " — بيتم تحديثه…" : " — تعذر تحديثه") : app.activePending ? "بيتم تحديث البيانات…" : Object.values(app.errors).some(Boolean) ? "تعذر تحديث بعض البيانات" : "بيانات الفترة جاهزة"}</span><button className="btn btn--quiet" onClick={reload} disabled={app.activePending}>تحديث</button></div>
        {tab === 'settings' && <button className="btn btn--quiet" onClick={()=>setCategoriesOpen(true)}>التصنيفات وألوانها</button>}
        {tab === 'home' && <button className="btn btn--quiet" onClick={()=>setRecurringOpen(true)}>الاشتراكات والفواتير</button>}
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
            historyLoading={app.pending.history}
            historyError={app.errors.history}
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
        {tab === 'settings' && (
          <SettingsScreen
            user={app.user}
            wallets={app.wallets}
            today={app.today}
            payday={app.payday}
            onWalletsChanged={reload}
            onRestored={reload}
            onOpenRules={() => setRulesOpen(true)}
          />
        )}
      </main>
      {/* الشريط السفلي — spec/01 */}
      <ShellTabs tab={tab} onChange={setTab}/>
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
      {categoriesOpen && <CategoriesScreen user={app.user} onClose={()=>setCategoriesOpen(false)} onChanged={reload}/>}
      {historyOpen && <HistoryReviewScreen user={app.user} today={app.today} categories={app.home?.categories??[]} onClose={()=>setHistoryOpen(false)} onChanged={reload}/>}
      {recurringOpen && <RecurringScreen user={app.user} today={app.today} hidden={amountsHidden} onClose={()=>setRecurringOpen(false)}/>}
      {rulesOpen && (
        <RulesScreen
          user={app.user}
          categories={app.home?.categories ?? []}
          onClose={() => setRulesOpen(false)}
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
          loading={app.pending.notifications}
          error={app.errors.notifications}
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
