import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import { createContainer, type Container } from './app/container'
import './presentation/theme/tokens.css'
import './presentation/theme/base.css'
import './presentation/theme/feedback.css'

const root = document.getElementById('root')
if (!root) throw new Error('عنصر #root مش موجود في index.html')

/**
 * وضع المعاينة (?demo=1) **في التطوير فقط**.
 * الشرط `import.meta.env.DEV` يجعل Vite يحذف الفرع كله من بناء الإنتاج،
 * فلا يصل كود المعاينة ولا بياناته إلى التطبيق المنشور (spec/01).
 */
async function resolveContainer(): Promise<Container> {
  if (import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1') {
    const { createDemoContainer } = await import('./app/demoContainer')
    return createDemoContainer()
  }
  return createContainer()
}

void resolveContainer().then((container) => {
  createRoot(root).render(
    <StrictMode>
      <App container={container} />
    </StrictMode>,
  )
})
