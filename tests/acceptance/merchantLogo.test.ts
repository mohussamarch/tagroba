import { describe, it, expect } from 'vitest'
import { buildLogoIndex, logoSourceFor } from '../../src/domain/merchantLogo'
import { createMerchantLogos } from '../../src/infrastructure/logos/merchantLogos'

/** شعارات التجار — OVERRIDES §25.1. أسماء ودومينات وهمية. */

const entries = [
  { names: ['Demo Cafe', 'DEMO CAFE BR 2'], file: 'demo-cafe.png', domain: 'democafe.example' },
  { names: ['Online Only'], domain: 'onlineonly.example' },
  { names: ['Bad Domain'], domain: 'not a domain' },
  { names: ['demo cafe'], file: 'other.png' },
]

describe('مصدر شعار المحل', () => {
  const index = buildLogoIndex(entries)
  const files = new Set(['demo-cafe.png'])

  it('الملف اللي جوه التطبيق بيغلب، وبيتلاقى بأي شكل من أشكال الاسم', () => {
    expect(logoSourceFor('  demo-cafe ', index, { onlineEnabled: true, bundledFiles: files })).toEqual({ kind: 'bundled', file: 'demo-cafe.png' })
    expect(logoSourceFor('Demo Cafe Br 2', index, { onlineEnabled: false, bundledFiles: files })).toEqual({ kind: 'bundled', file: 'demo-cafe.png' })
  })

  it('الأونلاين مقفول من غير رقم التعريف، ودومين غلط ما يتبعتش', () => {
    expect(logoSourceFor('Online Only', index, { onlineEnabled: false, bundledFiles: files })).toBeUndefined()
    expect(logoSourceFor('Online Only', index, { onlineEnabled: true, bundledFiles: files })).toEqual({ kind: 'online', domain: 'onlineonly.example' })
    expect(logoSourceFor('Bad Domain', index, { onlineEnabled: true, bundledFiles: files })).toBeUndefined()
  })

  it('محل مش معروف أو ملف مش موجود فعلًا ⇒ مفيش شعار (رمز التصنيف)', () => {
    expect(logoSourceFor('Unknown Shop', index, { onlineEnabled: true, bundledFiles: files })).toBeUndefined()
    expect(logoSourceFor(undefined, index, { onlineEnabled: true, bundledFiles: files })).toBeUndefined()
    const missingFile = buildLogoIndex([{ names: ['Ghost'], file: 'ghost.png' }])
    expect(logoSourceFor('Ghost', missingFile, { onlineEnabled: true, bundledFiles: files })).toBeUndefined()
  })
})

describe('روابط الشعارات', () => {
  it('الملف ⇒ رابطه جوه التطبيق، والأونلاين ⇒ رابط براند فيتش المباشر بالدومين ورقم التعريف بس', () => {
    const logos = createMerchantLogos({ clientId: 'demo-id', entries, files: new Map([['demo-cafe.png', '/assets/demo-cafe.png']]) })
    expect(logos.urlFor('DEMO CAFE')).toBe('/assets/demo-cafe.png')
    expect(logos.urlFor('Online Only')).toBe('https://cdn.brandfetch.io/domain/onlineonly.example?c=demo-id')
  })

  it('من غير رقم التعريف: مفيش أي رابط برا التطبيق', () => {
    const logos = createMerchantLogos({ clientId: '  ', entries, files: new Map() })
    expect(logos.urlFor('Online Only')).toBeUndefined()
    expect(logos.urlFor('Demo Cafe')).toBeUndefined()
  })

  it('ملف البيان اللي في المشروع بيتقري من غير أخطاء', () => {
    expect(createMerchantLogos().urlFor('Anything')).toBeUndefined()
  })
})
