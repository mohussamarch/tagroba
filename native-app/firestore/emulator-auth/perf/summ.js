// summarize an MPERF logcat dump per step: tap->loaded ms, span ms, fs reads/bytes, mirror reads/ms, main-thread read ms
const fs = require('fs')
const file = process.argv[2]
const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/)
const jankFile = file.replace(/\.log$/, '-jank.log')
const jank = fs.existsSync(jankFile) ? fs.readFileSync(jankFile, 'utf8').split(/\r?\n/).filter(Boolean) : []
const ts = (l) => { const m = l.match(/^\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d\d\d)/); return m ? ((+m[1] * 60 + +m[2]) * 60 + +m[3]) * 1000 + +m[4] : null }
const steps = []
let cur = null
for (const l of lines) {
  const t = ts(l)
  if (t == null) continue
  let m
  if ((m = l.match(/step (\S+) begin/))) { cur = { name: m[1], begin: t, spans: [], fs: 0, fsBytes: 0, fsMs: 0, mir: 0, mirMs: 0, mainMs: 0, last: t }; steps.push(cur); continue }
  if ((m = l.match(/step (\S+) end/))) { if (cur) cur.end = t; cur = null; continue }
  if (!cur) continue
  if ((m = l.match(/span (screen:\S+|sheet:\S+) ms=(\d+)/))) { cur.spans.push(`${m[1]}=${m[2]}`); cur.loaded = t }
  const ms = +((l.match(/ ms=(\d+)/) || [])[1] || 0)
  const main = / th=main/.test(l)
  if (/read src=fs/.test(l) && !/listen:/.test(l)) { cur.fs++; cur.fsBytes += +((l.match(/bytes=(\d+)/) || [])[1] || 0); cur.fsMs += ms; if (main) cur.mainMs += ms }
  if (/read src=mirror/.test(l)) { cur.mir++; cur.mirMs += ms; if (main) cur.mainMs += ms }
}
const jankIn = (s) => jank.filter((l) => { const t = ts(l); return t >= s.begin && t <= (s.end ?? s.begin + 1e9) })
  .reduce((a, l) => a + +((l.match(/Skipped (\d+) frames/) || [])[1] || 0), 0)
console.log('step       tap->loaded  spans                                   fsReads fsKB fsMs  mirReads mirMs mainReadMs skippedFrames')
for (const s of steps) {
  const lat = s.loaded ? s.loaded - s.begin : 'n/a'
  console.log([s.name.padEnd(10), String(lat).padStart(11), s.spans.join(',').padEnd(40), String(s.fs).padStart(7), String(Math.round(s.fsBytes / 1024)).padStart(4), String(s.fsMs).padStart(5), String(s.mir).padStart(8), String(s.mirMs).padStart(5), String(s.mainMs).padStart(10), String(jankIn(s)).padStart(13)].join(' '))
}
const listen = lines.filter((l) => /listen:/.test(l))
const lb = listen.reduce((a, l) => a + +((l.match(/bytes=(\d+)/) || [])[1] || 0), 0)
console.log(`listener deliveries: ${listen.length}, ${Math.round(lb / 1024)} KB (cache+server); session ready at +${(() => { const a = lines.find((l) => /step cold begin/.test(l)); const b = lines.find((l) => /session ready/.test(l)); return a && b ? ts(b) - ts(a) : 'n/a' })()} ms`)
