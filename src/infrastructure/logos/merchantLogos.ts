import type { MerchantLogoPort } from '../../application/ports/MerchantLogoPort'
import { buildLogoIndex, logoSourceFor, type MerchantLogoEntry } from '../../domain/merchantLogo'
import manifest from './manifest.json'

/**
 * الشعارات: ملفات جوه التطبيق من `files/` (المالك بيضيفها ويسجلها في `manifest.json`)،
 * والأونلاين من براند فيتش بالرابط المباشر بتاعهم — **من غير تحميل ولا حفظ** (شروطهم، OVERRIDES §25.1).
 * الأونلاين مقفول لحد ما `VITE_BRANDFETCH_CLIENT_ID` يتحط في `.env.local`.
 */
const bundled = import.meta.glob('./files/*.{png,svg,webp,jpg}', { eager: true, import: 'default' }) as Record<string, string>
const bundledUrls = new Map(Object.entries(bundled).map(([path, url]) => [path.replace('./files/', ''), url]))

export function createMerchantLogos(options: {
  clientId?: string | undefined
  entries?: readonly MerchantLogoEntry[]
  files?: ReadonlyMap<string, string>
} = {}): MerchantLogoPort {
  const files = options.files ?? bundledUrls
  const index = buildLogoIndex(options.entries ?? (manifest.entries as MerchantLogoEntry[]))
  const clientId = options.clientId?.trim()
  const bundledFiles = new Set(files.keys())
  return {
    urlFor(merchantName) {
      const source = logoSourceFor(merchantName, index, { onlineEnabled: !!clientId, bundledFiles })
      if (!source) return undefined
      if (source.kind === 'bundled') return files.get(source.file)
      return `https://cdn.brandfetch.io/domain/${encodeURIComponent(source.domain)}?c=${encodeURIComponent(clientId!)}`
    },
  }
}
