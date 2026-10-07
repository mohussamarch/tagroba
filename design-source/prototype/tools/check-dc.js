// Static + logic check for .dc.html artboards: tag balance, data-props JSON,
// renderVals() for every enum combination, and every {{hole}} resolving.
const fs = require('fs');
const path = require('path');

const VOID = new Set(['area','base','br','col','embed','hr','img','input','link','meta','source','track','wbr']);
const SVG_SELF_OK = true;

function decodeAttr(s) { return s.replace(/&#39;/g, "'").replace(/&quot;/g, '"').replace(/&amp;/g, '&'); }

function checkTags(html, file, errs) {
  const body = html.replace(/<script[\s\S]*?<\/script>/g, '').replace(/<style[\s\S]*?<\/style>/g, '').replace(/<!--[\s\S]*?-->/g, '');
  const re = /<\/?([a-zA-Z][\w-]*)([^>]*)>/g;
  const stack = [];
  let m;
  while ((m = re.exec(body))) {
    const tag = m[1].toLowerCase();
    const closing = m[0][1] === '/';
    const selfClose = /\/\s*>$/.test(m[0]);
    if (tag === '!doctype') continue;
    if (!closing) {
      const attrs = m[2];
      const unq = attrs.match(/\s[\w:-]+=(?!["'])[^\s>]+/);
      if (unq) errs.push(`${file}: unquoted attribute in <${tag}${unq[0]}>`);
    }
    if (closing) {
      const top = stack.pop();
      if (top !== tag) { errs.push(`${file}: closing </${tag}> but open <${top}>`); if (top) stack.push(top); }
    } else if (!VOID.has(tag) && !selfClose) {
      stack.push(tag);
    } else if (selfClose && !VOID.has(tag)) {
      errs.push(`${file}: self-closed non-void <${tag}/>`);
    }
  }
  if (stack.length) errs.push(`${file}: unclosed tags: ${stack.join(',')}`);
}

function lookup(obj, p) {
  return p.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj);
}

function run(file) {
  const errs = [];
  const html = fs.readFileSync(file, 'utf8');
  const name = path.basename(file);
  if (!html.includes('<script src="./support.js"></script>')) errs.push(`${name}: missing support.js line`);
  checkTags(html, name, errs);
  const sm = html.match(/<script type="text\/x-dc" data-dc-script data-props='([^']*)'>([\s\S]*?)<\/script>/);
  if (!sm) { errs.push(`${name}: no dc script`); return errs; }
  let props;
  try { props = JSON.parse(decodeAttr(sm[1])); } catch (e) { errs.push(`${name}: data-props JSON: ${e.message}`); return errs; }
  const code = sm[2];
  class DCLogic {
    constructor(p) { this.props = p || {}; this.state = this.state || {}; }
    setState(s) { this.state = Object.assign({}, this.state, typeof s === 'function' ? s(this.state) : s); }
    forceUpdate() {}
  }
  global.window = { matchMedia: () => ({ matches: false }) };
  global.requestAnimationFrame = () => 0; global.cancelAnimationFrame = () => {};
  global.performance = { now: () => 0 };
  let Component;
  try { Component = new Function('DCLogic', code + '\nreturn Component;')(DCLogic); } catch (e) { errs.push(`${name}: class parse: ${e.message}`); return errs; }
  const tpl = html.slice(html.indexOf('<x-dc>'), html.indexOf('</x-dc>'));
  const holes = [...tpl.matchAll(/\{\{\s*([^}]+?)\s*\}\}/g)].map((x) => x[1]);
  const loops = [...tpl.matchAll(/<sc-for list="\{\{\s*([\w.$]+)\s*\}\}" as="(\w+)"/g)].map((x) => ({ list: x[1], as: x[2] }));
  const enums = Object.entries(props).filter(([k, v]) => v && v.editor === 'enum');
  const combos = enums.reduce((acc, [k, v]) => acc.flatMap((c) => v.options.map((o) => ({ ...c, [k]: o }))), [{}]);
  const extraStates = [{}];
  for (const combo of combos) {
    let inst;
    try { inst = new Component(combo); if (!inst.state) inst.state = {}; if (inst.componentDidMount) inst.componentDidMount(); } catch (e) { errs.push(`${name} ${JSON.stringify(combo)}: ctor/mount: ${e.message}`); continue; }
    let vals;
    try { vals = inst.renderVals(); } catch (e) { errs.push(`${name} ${JSON.stringify(combo)}: renderVals: ${e.message}`); continue; }
    const scope = { ...vals };
    for (const l of loops) {
      const arr = lookup(scope, l.list);
      if (!Array.isArray(arr)) { errs.push(`${name} ${JSON.stringify(combo)}: loop list ${l.list} not array`); continue; }
      if (arr.length) scope[l.as] = arr[0];
    }
    for (const h of holes) {
      if (/^(true|false|\d+)$/.test(h) || h.startsWith('$')) continue;
      if (/[^\w.$]/.test(h)) { errs.push(`${name}: hole is an expression: {{${h}}}`); continue; }
      const first = h.split('.')[0];
      if (!(first in scope)) { errs.push(`${name} ${JSON.stringify(combo)}: hole {{${h}}} root missing`); continue; }
      const v = lookup(scope, h);
      if (v === undefined && !h.match(/\.i\.\w+$/)) errs.push(`${name} ${JSON.stringify(combo)}: hole {{${h}}} undefined`);
    }
    // invoke handlers found in vals (one level and inside arrays)
    const fns = [];
    for (const v of Object.values(vals)) {
      if (typeof v === 'function') fns.push(v);
      if (Array.isArray(v)) v.forEach((it) => it && typeof it === 'object' && Object.values(it).forEach((x) => typeof x === 'function' && fns.push(x)));
    }
    for (const f of fns) { try { f({ preventDefault() {}, stopPropagation() {}, target: { value: '' }, currentTarget: {} }); inst.renderVals(); } catch (e) { errs.push(`${name} ${JSON.stringify(combo)}: handler: ${e.message}`); } }
  }
  return errs;
}

const files = process.argv.slice(2);
let total = 0;
for (const f of files) {
  const errs = run(f);
  total += errs.length;
  console.log(errs.length ? errs.join('\n') : `OK ${path.basename(f)}`);
}
process.exit(total ? 1 : 0);
