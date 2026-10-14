#!/usr/bin/env node
// «افهم رصيدك» من نسخة احتياطية شاملة (بند المالك 3) — نفس قواعد `core/BalanceWhy.kt` بالظبط، بس على ملف JSON محلي.
// الاستخدام:  node scripts/balance-diagnose.mjs "<مسار ملف النسخة.json>" [آخر يوم YYYY-MM-DD]
// بيطبع **مجاميع بس** لكل محفظة (رصيد البداية · المجاميع بالمصدر · الرصيد المحسوب · عدد «حاجات تستاهل تتراجع» وأثر المكرر).
// مفيش أي بيانات جوه السكريبت، ومش بيطبع أسامي تجار ولا أشخاص ولا محافظ (المحفظة بنوعها وعملتها وآخر 4 حروف من معرّفها).
// المبالغ أعداد صحيحة بالهللة (BigInt) — التحويل للريال في الطباعة بس.
import { readFileSync } from 'node:fs';

const [, , file, asOfArg] = process.argv;
if (!file) {
  console.error('usage: node scripts/balance-diagnose.mjs <backup.json> [YYYY-MM-DD]');
  process.exit(2);
}
const asOf = asOfArg ?? new Date().toISOString().slice(0, 10);
const WINDOW_DAYS = 2; // CROSS_SOURCE_WINDOW_DAYS
const root = JSON.parse(readFileSync(file, 'utf8'));

/** مجموعات كل بلد: `data` (البلد الأساسي) + `spaces[].data`. */
function spacesOf(f) {
  const out = [];
  if (f.data) out.push({ label: 'default', data: f.data });
  for (const s of f.spaces ?? []) {
    const data = s.data ?? s;
    out.push({ label: s.countryCode ?? s.space?.countryCode ?? s.id ?? s.space?.id ?? '?', data });
  }
  return out;
}

const big = (v) => BigInt(v ?? 0);
const day = (iso) => String(iso ?? '').slice(0, 10);
const daysBetween = (a, b) => Math.round((Date.UTC(...ymd(a)) - Date.UTC(...ymd(b))) / 86_400_000);
function ymd(iso) {
  const [y, m, d] = day(iso).split('-').map(Number);
  return [y, m - 1, d];
}
const sar = (minor) => {
  const neg = minor < 0n;
  const a = neg ? -minor : minor;
  return `${neg ? '-' : ''}${(a / 100n).toLocaleString('en-US')}.${String(a % 100n).padStart(2, '0')}`;
};

/** مصدر كل عملية: كشف/رسالة من نوع دفعتها (أو مرجع «SMS:» لو الدفعة مش معروفة) — زي `sourcesOf`. */
function sourcesOf(data) {
  const kinds = new Map((data.importBatches ?? []).map((b) => [b.id, b.sourceType]));
  const out = new Map();
  for (const r of data.sourceRecords ?? []) {
    if (!r.transactionId) continue;
    const type = kinds.get(r.batchId);
    const sms = type != null ? type === 'sms' : String(r.sourceReference ?? '').startsWith('SMS:');
    const now = out.get(r.transactionId) ?? { statement: false, sms: false };
    out.set(r.transactionId, sms ? { ...now, sms: true } : { ...now, statement: true });
  }
  return out;
}

function sourceOf(t, incoming, sources) {
  if (incoming) return 'TRANSFER';
  if (t.transferToWalletId != null || t.economicKind === 'internal_transfer') return 'TRANSFER';
  const s = sources.get(t.id);
  if (!s) return 'MANUAL';
  if (s.statement && s.sms) return 'BOTH';
  return s.statement ? 'STATEMENT' : s.sms ? 'SMS' : 'MANUAL';
}

function explain(wallet, txns, sources, known) {
  const opening = big(wallet.openingBalanceMinor);
  const openingAt = day(wallet.openingAt);
  const totals = {};
  const review = { before: 0, otherCurrency: 0, foreign: 0, reversals: 0, oneSided: 0, unassigned: 0 };
  let inSum = 0n;
  let outSum = 0n;
  const counted = [];
  const sorted = [...txns].sort((a, b) => (day(a.occurredAt) + String(a.sourceOrder ?? 0).padStart(9, '0') + a.id).localeCompare(day(b.occurredAt) + String(b.sourceOrder ?? 0).padStart(9, '0') + b.id));
  for (const t of sorted) {
    const date = day(t.occurredAt);
    if (t.walletId == null && t.transferToWalletId == null) {
      if (t.currency === wallet.currency && date <= asOf) review.unassigned++;
      continue;
    }
    for (const incoming of [false, true]) {
      const mine = incoming ? t.transferToWalletId === wallet.id : t.walletId === wallet.id;
      if (!mine || date > asOf) continue;
      if (date < openingAt) { review.before++; continue; }
      const dir = incoming ? 'in' : t.observedDirection;
      const src = sourceOf(t, incoming, sources);
      const row = (totals[src] ??= { count: 0, in: 0n, out: 0n });
      row.count++;
      if (dir === 'in') { row.in += big(t.amountMinor); inSum += big(t.amountMinor); } else { row.out += big(t.amountMinor); outSum += big(t.amountMinor); }
      if (!incoming) counted.push(t);
      if (t.currency !== wallet.currency) review.otherCurrency++;
      if (t.foreignCurrency != null && t.currency === wallet.currency) review.foreign++;
      if (!incoming && t.suggestedKind != null) review.reversals++;
      if (!incoming && t.transferToWalletId != null && !known.has(t.transferToWalletId)) review.oneSided++;
    }
  }
  const dupes = duplicates(counted, sources);
  const effect = dupes.reduce((s, p) => s + (p.sms.observedDirection === 'in' ? big(p.sms.amountMinor) : -big(p.sms.amountMinor)), 0n);
  const open = openingAt <= asOf;
  return { opening, openingAt, totals, inSum, outSum, computed: open ? opening + inSum - outSum : null, review: { ...review, duplicates: dupes.length, duplicateEffect: effect } };
}

/** «مكرر محتمل»: رسالة بس ⇄ كشف بس، نفس المبلغ (الأصلي لو موجود) والاتجاه والعملة والمحفظة، ±يومين — أقرب زوج الأول، وكل عملية في زوج واحد. */
function duplicates(own, sources) {
  const eligible = (t) => t.transferToWalletId == null;
  const only = (t, want) => { const s = sources.get(t.id); return s && (want === 'sms' ? s.sms && !s.statement : s.statement && !s.sms); };
  const key = (t) => [t.originalAmountMinor ?? t.amountMinor, t.observedDirection, t.currency, t.walletId].join('|');
  const byKey = new Map();
  for (const s of own.filter((t) => eligible(t) && only(t, 'statement'))) byKey.set(key(s), [...(byKey.get(key(s)) ?? []), s]);
  const edges = [];
  for (const m of own.filter((t) => eligible(t) && only(t, 'sms'))) {
    for (const s of byKey.get(key(m)) ?? []) {
      const days = Math.abs(daysBetween(m.occurredAt, s.occurredAt));
      if (days <= WINDOW_DAYS) edges.push({ days, sms: m, statement: s });
    }
  }
  edges.sort((a, b) => a.days - b.days || day(a.sms.occurredAt).localeCompare(day(b.sms.occurredAt)) || a.sms.id.localeCompare(b.sms.id) || a.statement.id.localeCompare(b.statement.id));
  const used = new Set();
  const out = [];
  for (const e of edges) {
    if (used.has(e.sms.id) || used.has(e.statement.id)) continue;
    used.add(e.sms.id).add(e.statement.id);
    out.push(e);
  }
  return out;
}

for (const space of spacesOf(root)) {
  const wallets = space.data.wallets ?? [];
  const txns = space.data.transactions ?? [];
  const sources = sourcesOf(space.data);
  const known = new Set(wallets.map((w) => w.id));
  console.log(`\n=== space ${space.label} · as of ${asOf} · ${txns.length} transactions ===`);
  for (const w of wallets) {
    const touching = txns.filter((t) => t.walletId === w.id || t.transferToWalletId === w.id || (t.walletId == null && t.transferToWalletId == null));
    const x = explain(w, touching, sources, known);
    console.log(`\n[wallet …${String(w.id).slice(-4)} · ${w.type ?? '?'} · ${w.currency}]`);
    console.log(`  opening ${sar(x.opening)} @ ${x.openingAt}`);
    for (const [src, r] of Object.entries(x.totals)) console.log(`  ${src.padEnd(10)} ${String(r.count).padStart(6)} items · in ${sar(r.in)} · out ${sar(r.out)}`);
    console.log(`  computed ${x.computed == null ? 'n/a (opens after as-of)' : sar(x.computed)}  (= opening + in ${sar(x.inSum)} - out ${sar(x.outSum)})`);
    const r = x.review;
    console.log(`  review: duplicates ${r.duplicates} (effect ${sar(r.duplicateEffect)}) · before opening ${r.before} · other currency ${r.otherCurrency} · foreign ${r.foreign} · reversals ${r.reversals} · one-sided transfers ${r.oneSided} · no wallet ${r.unassigned}`);
  }
}
