import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import { createContainer } from './app/container'
import './presentation/theme/tokens.css'
import './presentation/theme/base.css'

const root = document.getElementById('root')
if (!root) throw new Error('عنصر #root مش موجود في index.html')

createRoot(root).render(
  <StrictMode>
    <App container={createContainer()} />
  </StrictMode>,
)
