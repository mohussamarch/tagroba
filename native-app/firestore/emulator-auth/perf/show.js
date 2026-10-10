// node show.js <log> <step...>: non-mirror lines of each step + mirror reads per group (count, ms)
const fs = require('fs')
const [file, ...names] = process.argv.slice(2)
const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/)
for (const name of names) {
  console.log('== ' + name)
  let on = false
  const groups = {}
  for (const l of lines) {
    if (l.includes(`step ${name} begin`)) on = true
    if (!on) continue
    if (/read src=mirror/.test(l)) {
      const g = (l.match(/group=(\S+)/) || [])[1]; const ms = +((l.match(/ ms=(\d+)/) || [])[1] || 0)
      groups[g] = groups[g] || [0, 0]; groups[g][0]++; groups[g][1] += ms
    } else if (!/listen:|synced group/.test(l)) console.log(l.slice(6, 175))
    if (l.includes(`step ${name} end`)) break
  }
  console.log('  mirror: ' + Object.entries(groups).sort((a, b) => b[1][1] - a[1][1] || b[1][0] - a[1][0]).map(([g, [c, ms]]) => `${g}x${c}/${ms}ms`).join(' '))
}
