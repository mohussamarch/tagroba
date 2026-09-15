import { describe, it, expect } from 'vitest'
import { decideBack } from '../../src/app/androidBack'

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
