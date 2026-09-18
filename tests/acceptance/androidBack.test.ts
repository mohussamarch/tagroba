import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { decideBack } from '../../src/app/androidBack'

function tsxFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    return statSync(full).isDirectory() ? tsxFiles(full) : full.endsWith('.tsx') ? [full] : []
  })
}

/** الرجوع بيدوّر على زرار «إغلاق» — نافذة من غيره كانت بتفضل مفتوحة (الاشتراكات، 2026-09-18). */
describe('كل نافذة ليها «إغلاق» يلاقيه زرار الرجوع', () => {
  it('إلا القفل وأسئلة البداية (مقصود إن الرجوع ما يعدّيهمش)', () => {
    const allowed = ['AppLockGate.tsx', 'OnboardingFlow.tsx']
    const missing = [...tsxFiles('src/presentation'), ...tsxFiles('src/app')]
      .filter((file) => readFileSync(file, 'utf8').includes('role="dialog"'))
      .filter((file) => !allowed.some((name) => file.endsWith(name)))
      .filter((file) => !readFileSync(file, 'utf8').includes('aria-label="إغلاق"'))
    expect(missing).toEqual([])
  })
})

/** زرار الرجوع بتاع أندرويد — HANDOVER بند 18. */
describe('قرار زرار الرجوع', () => {
  it('نافذة مفتوحة ليها «إغلاق» ⇒ تتقفل، مهما كان التبويب', () => {
    expect(decideBack({ topDialog: true, topDialogCanClose: true, tab: 'home' })).toBe('closeDialog')
    expect(decideBack({ topDialog: true, topDialogCanClose: true, tab: 'budget' })).toBe('closeDialog')
  })
  it('نافذة من غير «إغلاق» (القفل وأسئلة البداية) ⇒ الرجوع ما يعدّيهاش', () => {
    expect(decideBack({ topDialog: true, topDialogCanClose: false, tab: 'transactions' })).toBe('ignore')
  })
  it('مفيش نوافذ: تبويب تاني ⇒ الرئيسية، والرئيسية ⇒ الخلفية مش القفل', () => {
    expect(decideBack({ topDialog: false, topDialogCanClose: false, tab: 'settings' })).toBe('home')
    expect(decideBack({ topDialog: false, topDialogCanClose: false, tab: 'home' })).toBe('minimize')
  })
})
