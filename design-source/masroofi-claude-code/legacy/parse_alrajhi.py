#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
محلّل كشف حساب مصرف الراجحي  →  CSV مصنّف
الاستخدام:  python3 parse_alrajhi.py <ملف.pdf> [مجلد-الإخراج]
يتطلب:      pip install pdfplumber
"""
import sys, os, re, csv, json, unicodedata
from collections import Counter, defaultdict
import pdfplumber

# ─── أعمدة كشف الراجحي (إحداثيات أفقية بالنقاط، صفحة A4 عرض 595) ───
COL_BALANCE = (0, 110)      # الرصيد
COL_CREDIT  = (140, 200)    # دائن
COL_DEBIT   = (230, 300)    # مدين
COL_DETAIL  = (300, 560)    # تفاصيل العملية
NUM  = re.compile(r'^[\d,]+\.\d{2}$')
DATE = re.compile(r'^\d{4}/\d{2}/\d{2}$')


def ar(s):
    """يحوّل الأحرف العربية من صيغة العرض ويصلّح ترتيبها."""
    out = []
    for tok in s.split():
        n = unicodedata.normalize('NFKC', tok)
        out.append(n[::-1] if re.search(r'[\u0600-\u06FF]', n) else tok)
    return ' '.join(out)


def ltr(d):
    """يعكس ترتيب الكلمات اللاتينية لتُقرأ من اليسار لليمين."""
    return ' | '.join(' '.join(reversed(s.split())) for s in d.split(' | '))


def lines_of(page):
    buckets = {}
    for w in page.extract_words(use_text_flow=False):
        buckets.setdefault(round(w['top']), []).append(w)
    return [(k, sorted(buckets[k], key=lambda w: w['x0'])) for k in sorted(buckets)]


def col_text(words, lo, hi):
    sel = [w for w in words if w['x0'] >= lo and w['x1'] <= hi]
    return ' '.join(w['text'] for w in sorted(sel, key=lambda w: -w['x0'])) if sel else ''


def amounts(words):
    v = {}
    for w in words:
        if not NUM.match(w['text']):
            continue
        x = w['x0']
        if   COL_BALANCE[0] <= x < COL_BALANCE[1]: v['balance'] = w['text']
        elif COL_CREDIT[0]  <  x < COL_CREDIT[1]:  v['credit']  = w['text']
        elif COL_DEBIT[0]   <  x < COL_DEBIT[1]:   v['debit']   = w['text']
    return v


def extract(path):
    recs = []
    with pdfplumber.open(path) as pdf:
        for pno, page in enumerate(pdf.pages, 1):
            ls = lines_of(page)
            hits = [(i, t, a, w) for i, (t, w) in enumerate(ls)
                    if len(a := amounts(w)) == 3]
            for j, (i, top, a, words) in enumerate(hits):
                r = dict(page=pno, **a)
                d = next((w['text'] for w in words if DATE.match(w['text'])), None)
                if not d:                       # التاريخ أحياناً على سطر مجاور
                    for k in range(max(0, i - 3), min(len(ls), i + 6)):
                        d = next((w['text'] for w in ls[k][1] if DATE.match(w['text'])), None)
                        if d: break
                r['date'] = d
                typ = ''
                for k in range(i - 1, max(-1, i - 4), -1):
                    t = col_text(ls[k][1], *COL_DETAIL)
                    if t and not DATE.match(t.strip()):
                        typ = t; break
                r['type'] = typ
                stop = hits[j + 1][0] if j + 1 < len(hits) else len(ls)
                det = [t for k in range(i, stop)
                       if (t := col_text(ls[k][1], *COL_DETAIL)) and not DATE.match(t.strip())]
                r['detail'] = ' | '.join(det)
                recs.append(r)
    return recs


# ─── استخراج اسم التاجر: ثلاث صيغ مختلفة في كشوف الراجحي ───
MER  = re.compile(r"([A-Za-z0-9&.'\- ]{3,}?),\s*([A-Za-z\- ]+),\s*SA\b")   # محلي: الاسم، المدينة، SA
ONL  = re.compile(r"Online Purchase from ([A-Za-z0-9&.'\- ]+)")            # شراء إنترنت محلي
INTL = re.compile(r"([A-Za-z0-9*.'&\- ]{3,45}?)\s*:\s*\d{4}\*+\d+")        # دولي: الاسم : رقم البطاقة
AGMT = re.compile(r"payment_agreement_\w+,\s*([A-Za-z0-9&.'\- ]{2,45}?)\s*,")  # اتفاقية دفع: الاسم بعد المعرّف
SMSG = re.compile(r"^\s*([A-Za-z0-9&.'\- ]{2,45}?)\s*,\s*\(\d")               # سامسونج/أبل باي: الاسم ثم (رقم
RFND = re.compile(r"\)\s*([A-Za-z0-9&.'\- ]{2,45}?)\s*,\s*[A-Za-z]")           # استرداد: الاسم بعد قوس الإغلاق


def merchant(d):
    m = ONL.search(d)
    if m: return m.group(1).strip().upper()
    m = AGMT.search(d)
    if m: return m.group(1).strip().upper()[:40]
    m = SMSG.search(d)
    if m: return m.group(1).strip().upper()[:40]
    t = MER.findall(d)
    if t: return re.sub(r'^(TYB|Agmt\)?|ARBS\w+)\s*', '', t[0][0]).strip().upper()[:40]
    m = INTL.search(d)
    if m:
        n = re.sub(r'\s+', ' ', m.group(1)).strip().upper()
        n = re.sub(r'\s*\d{3}-\d+\s*', '', n)
        return re.sub(r'\s+(US|IE|GB|AE|NL|LU|CZ)$', '', n).strip() or None
    m = RFND.search(d)
    if m: return m.group(1).strip().upper()[:40]
    return None


def load_rules(csv_path):
    rules = defaultdict(list)
    if not os.path.exists(csv_path):
        return []
    with open(csv_path, encoding='utf-8-sig') as f:
        for row in csv.DictReader(f):
            rules[row['التصنيف']].append(row['كلمة مفتاحية'].upper())
    return list(rules.items())


TYPE_CAT = [("تحويلات", ["تحويل", "حوالة", "حوالات", "حواالت"]),
            ("رسوم بنكية", ["رسوم"]),
            ("سحب نقدي", ["سحب"]),
            ("إيداع", ["إيداع", "ايداع"]),
            ("سداد", ["سداد"]),
            ("ذهب", ["سبائك", "الذهب"]),
            ("حكومي ورسوم", ["المخالفات"]),
            ("استرداد", ["استرداد"])]


def main():
    if len(sys.argv) < 2:
        sys.exit("الاستخدام: python3 parse_alrajhi.py <ملف.pdf> [مجلد-الإخراج]")
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else '.'
    os.makedirs(out, exist_ok=True)

    recs = extract(src)
    print(f"تم استخراج {len(recs)} عملية")

    rules = load_rules(os.path.join(os.path.dirname(src) or '.', 'rules_categories.csv')) \
         or load_rules('rules_categories.csv')

    overrides = {}
    for base in (os.path.dirname(src) or '.', '.'):
        p_ov = os.path.join(base, 'merchants_classified.csv')
        if os.path.exists(p_ov):
            with open(p_ov, encoding='utf-8-sig') as fh:
                for row in csv.DictReader(fh):
                    c_ = row.get('التصنيف', '').strip()
                    if c_ and c_ != 'يحتاج تأكيد':
                        overrides[row['التاجر'].strip().upper()] = c_
            break

    def cat_m(n):
        if not n: return None
        if n.strip().upper() in overrides: return overrides[n.strip().upper()]
        for c, keys in rules:
            for k in keys:
                if k in n: return c
        return None

    def cat_t(t):
        for c, keys in TYPE_CAT:
            for k in keys:
                if k in t: return c
        return None

    rows, cats = [], Counter()
    for r in recs:
        f = lambda s: float(s.replace(',', ''))
        deb, cre, bal = f(r['debit']), f(r['credit']), f(r['balance'])
        t, d = ar(r['type']), ltr(r['detail'])
        n = merchant(d)
        c = cat_m(n) or cat_t(t) or 'غير مصنّف'
        cats[c] += 1
        rows.append([r['date'], deb, cre, bal, n or '', c, t, d[:160]])

    p = os.path.join(out, 'transactions.csv')
    with open(p, 'w', newline='', encoding='utf-8-sig') as fh:
        w = csv.writer(fh)
        w.writerow(['التاريخ', 'مدين', 'دائن', 'الرصيد', 'التاجر', 'التصنيف', 'نوع العملية', 'التفاصيل'])
        w.writerows(rows)
    print(f"تم الحفظ في {p}")
    for c, n in cats.most_common():
        print(f"  {n:5d}  {c}")


if __name__ == '__main__':
    main()
