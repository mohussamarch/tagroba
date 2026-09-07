import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { HomeScreen } from '../presentation/screens/HomeScreen'
import { TransactionsScreen } from '../presentation/screens/TransactionsScreen'
import { ImportSheet } from '../presentation/screens/ImportSheet'
import { KindsSheet } from '../presentation/screens/KindsSheet'
import { useTheme } from '../presentation/theme/useTheme'
import { DEFAULT_PAYDAY, periodForDate, type Period } from '../domain/period'
import type { HomeScreenData } from '../application/useCases/loadHomeScreen'
import type { TransactionsScreenData } from '../application/useCases/loadTransactionsScreen'
import type { Container, UserContainer } from './container'
import './AppShell.css'

type Tab = 'home' | 'transactions'

/**
 * قشرة التطبيق بعد الدخول: التنقل والفترة والتحميل.
 *
 * الفترة **حالة واحدة مشتركة** بين الشاشتين: تغييرها من الرئيسية
 * ينعكس على العمليات، وهو ما يطلبه spec/04 («تطبيق الاختيار على
 * الشاشة ذات الصلة»). لو كانت لكل شاشة فترتها لاختلفت الأرقام
 * بين شاشتين تعرضان نفس البيانات — وهذا مصدر واحد للحقيقة.
 */
export function AppShell({
  container,
  uid,
  onSignOut,
}: {
  container: Container
  uid: string
  onSignOut: () => void
}) {
  const user: UserContainer = useMemo(() => container.forUser(uid), [container, uid])
  const { theme, toggleTheme } = useTheme()

  const today = useMemo(() => new Date().toISOString().slice(0, 10), [])
  const payday = DEFAULT_PAYDAY

  const [tab, setTab] = useState<Tab>('home')
  const [period, setPeriod] = useState<Period>(() => periodForDate(today, payday))
  const [amountsHidden, setAmountsHidden] = useState(false)

  const [home, setHome] = useState<HomeScreenData | null>(null)
  const [txnData, setTxnData] = useState<TransactionsScreenData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [recovery, setRecovery] = useState<string | null>(null)

  const [importOpen, setImportOpen] = useState(false)
  const [kindsOpen, setKindsOpen] = useState(false)

  const requestId = useRef(0)
  const bootstrapped = useRef(false)

  const load = useCallback(async () => {
    const id = ++requestId.current
    setLoading(true)
    setError(null)
    try {
      /*
       * تجهيز لمرة واحدة قبل أي قراءة:
       * الزرع أولًا (القواعد لازمة للتصنيف)، ثم تنظيف الدفعات المعلّقة
       * (ما تحتها غير معتمد فلا يجوز أن يظهر كبيانات حقيقية).
       */
      if (!bootstrapped.current) {
        bootstrapped.current = true
        await user.seedUserReferences()
        const outcomes = await user.resumeStagedBatch.cleanupAll()
        if (outcomes.length > 0) {
          const total = outcomes.reduce((sum, o) => sum + o.deletedTransactions, 0)
          setRecovery(
            `فيه استيراد سابق ماكملش (${outcomes.map((o) => o.fileName).join('، ')}) ` +
              `فشلناه بالكامل عشان مايسيبش بيانات ناقصة. ` +
              `${total > 0 ? `اتشال ${total} سطر. ` : ''}تقدر تستورد الملف تاني.`,
          )
        }
      }

      const [homeData, transactionsData] = await Promise.all([
        user.loadHomeScreen({ period, today, payday }),
        user.loadTransactionsScreen({ period }),
      ])
      if (id !== requestId.current) return
      setHome(homeData)
      setTxnData(transactionsData)
    } catch (cause) {
      if (id !== requestId.current) return
      setError(cause instanceof Error ? cause.message : String(cause))
    } finally {
      if (id === requestId.current) setLoading(false)
    }
  }, [user, period, today, payday])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="shell">
      <header className="shell__head">
        <h1 className="shell__title">{tab === 'home' ? 'مصروفي' : 'العمليات'}</h1>
        <div className="shell__actions">
          <button
            type="button"
            className="iconBtn iconBtn--primary"
            onClick={() => setImportOpen(true)}
            aria-label="استيراد كشف حساب"
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
        {recovery && (
          <div className="notice" role="status">
            {recovery}
            <br />
            <button
              type="button"
              className="btn btn--quiet"
              onClick={() => setRecovery(null)}
              style={{ marginTop: 10 }}
            >
              تمام، فهمت
            </button>
          </div>
        )}

        {tab === 'home' ? (
          <HomeScreen
            data={home}
            loading={loading}
            error={error}
            payday={payday}
            amountsHidden={amountsHidden}
            onPeriodChange={setPeriod}
            onRetry={() => void load()}
            onOpenTransactions={() => setTab('transactions')}
            onFixKinds={() => setKindsOpen(true)}
          />
        ) : (
          <TransactionsScreen
            loading={loading}
            error={error}
            data={txnData}
            amountsHidden={amountsHidden}
            period={period}
            payday={payday}
            onPeriodChange={setPeriod}
            onImport={() => setImportOpen(true)}
            onFixKinds={() => setKindsOpen(true)}
            onRetry={() => void load()}
          />
        )}
      </main>

      {/* الشريط السفلي — spec/01 */}
      <nav className="shell__tabs" aria-label="التنقل الرئيسي">
        <TabButton label="الرئيسية" icon="⌂" active={tab === 'home'} onClick={() => setTab('home')} />
        <TabButton
          label="العمليات"
          icon="☰"
          active={tab === 'transactions'}
          onClick={() => setTab('transactions')}
        />
      </nav>

      {importOpen && (
        <ImportSheet
          user={user}
          onClose={() => setImportOpen(false)}
          onImported={() => {
            setImportOpen(false)
            void load()
          }}
        />
      )}

      {kindsOpen && txnData && (
        <KindsSheet
          user={user}
          transactions={txnData.transactions}
          onClose={() => setKindsOpen(false)}
          onDone={() => void load()}
        />
      )}
    </div>
  )
}

function TabButton({
  label,
  icon,
  active,
  onClick,
}: {
  label: string
  icon: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className={`tab${active ? ' tab--on' : ''}`}
      onClick={onClick}
      aria-current={active ? 'page' : undefined}
    >
      <span aria-hidden="true" className="tab__icon">
        {icon}
      </span>
      <span className="tab__label">{label}</span>
    </button>
  )
}
