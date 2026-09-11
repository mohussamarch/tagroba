// مصروفي — يقرأ أخطاء ورسائل الـWebView داخل تطبيق أندرويد عبر بروتوكول DevTools (HANDOVER: بروتوكول المحاكي).
// Usage:
//   node scripts/webview-devtools.mjs --watch 20          collect console messages + exceptions for 20s
//   node scripts/webview-devtools.mjs --eval "location.href"   evaluate a READ-ONLY expression
// Needs a debuggable build (assembleDebug) running on the emulator/device. No npm dependency: Node 22 fetch + WebSocket.
// Privacy: never --eval expressions that dump financial data into a chat; read counts/states, not contents.
import { execFileSync } from 'node:child_process'

const ADB = process.env.ADB ?? `${process.env.USERPROFILE}/Documents/Codex/android-build-tools/sdk/platform-tools/adb.exe`
const PACKAGE = 'app.masroufy.personal'
const PORT = 9223
const args = process.argv.slice(2)
const watchSeconds = args.includes('--watch') ? Number(args[args.indexOf('--watch') + 1] ?? 15) : 0
const expression = args.includes('--eval') ? args[args.indexOf('--eval') + 1] : null
if (!watchSeconds && !expression) { console.error('Use --watch <seconds> or --eval "<expression>"'); process.exit(2) }

const adb = (...a) => execFileSync(ADB, a, { encoding: 'utf8' })
const pid = adb('shell', 'pidof', PACKAGE).trim()
if (!pid) throw new Error('App is not running. Run: scripts/android-emulator.ps1 -Action launch')
const sockets = adb('shell', 'cat', '/proc/net/unix').split('\n')
  .map((line) => line.trim().split(/\s+/).pop())
  .filter((name) => name?.startsWith('@webview_devtools_remote'))
const socket = sockets.find((name) => name.endsWith(`_${pid}`)) ?? sockets[0]
if (!socket) throw new Error('No WebView DevTools socket: the build is not debuggable or WebView debugging is off')
adb('forward', `tcp:${PORT}`, `localabstract:${socket.slice(1)}`)

const pages = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
const page = pages.find((p) => p.type === 'page') ?? pages[0]
if (!page) throw new Error('WebView has no inspectable page')
console.log(`page: ${page.url}`)

const ws = new WebSocket(page.webSocketDebuggerUrl)
let nextId = 1
const pending = new Map()
const send = (method, params = {}) => new Promise((resolve) => {
  const id = nextId++
  pending.set(id, resolve)
  ws.send(JSON.stringify({ id, method, params }))
})
const text = (arg) => arg.value ?? arg.description ?? arg.unserializableValue ?? arg.type

ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data)
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); return }
  if (msg.method === 'Runtime.consoleAPICalled') {
    console.log(`[console.${msg.params.type}] ${msg.params.args.map(text).join(' ')}`)
  } else if (msg.method === 'Runtime.exceptionThrown') {
    const d = msg.params.exceptionDetails
    console.log(`[exception] ${d.exception?.description ?? d.text} @ ${d.url ?? ''}:${d.lineNumber ?? ''}`)
  } else if (msg.method === 'Log.entryAdded') {
    const e = msg.params.entry
    console.log(`[log.${e.level}] ${e.text} ${e.url ?? ''}`)
  }
})
await new Promise((resolve, reject) => { ws.addEventListener('open', resolve); ws.addEventListener('error', reject) })

if (expression) {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true })
  const r = result.result
  console.log(r.exceptionDetails ? `[eval error] ${r.exceptionDetails.exception?.description ?? r.exceptionDetails.text}` : JSON.stringify(r.result.value, null, 2))
}
if (watchSeconds) {
  await send('Runtime.enable')
  await send('Log.enable') // replays buffered log entries, then streams new ones
  if (args.includes('--reload')) {
    // catch errors thrown during startup, which happen before any watcher can attach
    await send('Page.enable')
    await send('Page.reload', { ignoreCache: true })
    console.log('reloaded page')
  }
  console.log(`watching ${watchSeconds}s…`)
  await new Promise((resolve) => setTimeout(resolve, watchSeconds * 1000))
}
ws.close()
execFileSync(ADB, ['forward', '--remove', `tcp:${PORT}`])
