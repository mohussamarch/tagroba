# -*- coding: utf-8 -*-
"""كشف الاشتراكات والالتزامات المتكررة من transactions.csv"""
import csv,collections,statistics,sys,os
src=sys.argv[1] if len(sys.argv)>1 else 'out4/transactions.csv'
out=sys.argv[2] if len(sys.argv)>2 else '.'
NOW='2026/09'
rows=[r for r in csv.DictReader(open(src,encoding='utf-8-sig')) if float(r['مدين'])>0]

# استبعاد رسوم الخصم الدولي (خصم صغير ١.٢٪-٤.٥٪ من خصم آخر لنفس التاجر في نفس اليوم)
drop=set(); g=collections.defaultdict(list)
for i,r in enumerate(rows): g[(r['التاجر'],r['التاريخ'])].append(i)
for idx in g.values():
    if len(idx)<2: continue
    v=sorted(idx,key=lambda i:float(rows[i]['مدين'])); big=float(rows[v[-1]]['مدين'])
    for i in v[:-1]:
        if big and 0.012<=float(rows[i]['مدين'])/big<=0.045: drop.add(i)
fee_n,fee_s=len(drop),sum(float(rows[i]['مدين']) for i in drop)
rows=[r for i,r in enumerate(rows) if i not in drop]

# توحيد أسماء مزوّدي الخدمة المتشابهة
def norm(n):
    u=n.upper()
    if 'STC' in u and 'PAY' not in u and 'BANK' not in u: return 'STC'
    for k in ('GOOGLE ONE','DRAHIM','AWAED','MOBILY','ZAIN','VIRGIN'):
        if k in u: return k
    return n
NOT_EXPENSE={'استثمار','تحويلات','محافظ رقمية','سحب نقدي','تقسيط'}
by=collections.defaultdict(list)
for r in rows:
    if r['التاجر'].strip(): by[norm(r['التاجر'].strip())].append(r)

def mnum(m): return int(m[:4])*12+int(m[5:])
subs,savers,variables=[],[],[]
for n,tx in by.items():
    months=sorted({r['التاريخ'][:7] for r in tx})
    if len(months)<4: continue                                        # يمتد ٤ شهور فأكثر
    if statistics.mean(collections.Counter(r['التاريخ'][:7] for r in tx).values())>2: continue  # مرتين شهرياً كحد أقصى
    saving = tx[0]['التصنيف'] in NOT_EXPENSE
    if not saving and sum('نقاط البيع' in r['نوع العملية'] for r in tx)/len(tx)>0.2: continue   # مش نقاط بيع
    amts=collections.Counter(round(float(r['مدين']),2) for r in tx)
    top,cnt=amts.most_common(1)[0]
    days=[int(r['التاريخ'][8:10]) for r in tx]
    spread=statistics.pstdev(days) if len(days)>1 else 0
    span=mnum(months[-1])-mnum(months[0])+1
    fixed = cnt/len(tx)>=0.5 and (saving or spread<=10)
    if not fixed:
        # التزام متكرر بمبلغ متغيّر (فاتورة) — يحتاج امتداد أطول للتأكد إنه مش عادة شراء
        BILL_CATS={'اتصالات','فواتير ومرافق','تأمين','خدمات منزلية','حكومي ورسوم','اشتراكات رقمية','تعليم وتدريب'}
        if saving or len(months)<6 or tx[0]['التصنيف'] not in BILL_CATS: continue
        med=round(statistics.median(float(r['مدين']) for r in tx),2)
        variables.append({'الخدمة':n,'التصنيف':tx[0]['التصنيف'],'المبلغ الوسيط':med,
            'أقل':round(min(float(r['مدين']) for r in tx),2),'أعلى':round(max(float(r['مدين']) for r in tx),2),
            'عدد الخصومات':len(tx),'عدد الشهور':len(months),'أول خصم':months[0],'آخر خصم':months[-1],
            'نشط':'نعم' if mnum(NOW)-mnum(months[-1])<=2 else 'لا',
            'الإجمالي المدفوع':round(sum(float(r['مدين']) for r in tx),2),
            'التكلفة السنوية المقدّرة':round(sum(float(r['مدين']) for r in tx)/span*12,2)})
        continue
    rec={'الخدمة':n,'التصنيف':tx[0]['التصنيف'],'المبلغ المتكرر':top,'عدد الخصومات':len(tx),
         'عدد الشهور':len(months),'أول خصم':months[0],'آخر خصم':months[-1],
         'نشط':'نعم' if mnum(NOW)-mnum(months[-1])<=2 else 'لا','ثبات يوم الخصم':round(spread,1),
         'الإجمالي المدفوع':round(sum(float(r['مدين']) for r in tx),2),
         'التكلفة السنوية المقدّرة':round(top*len(tx)/span*12,2)}
    (savers if saving else subs).append(rec)

for lst,name in ((subs,'subscriptions.csv'),(savers,'recurring_savings.csv'),(variables,'variable_bills.csv')):
    lst.sort(key=lambda x:(x['نشط']!='نعم',-x['التكلفة السنوية المقدّرة']))
    if lst:
        with open(os.path.join(out,name),'w',encoding='utf-8-sig',newline='') as f:
            w=csv.DictWriter(f,fieldnames=list(lst[0].keys())); w.writeheader(); w.writerows(lst)
print(f'رسوم خصم دولي مستبعدة: {fee_n} عملية بمبلغ {round(fee_s,2)} ر.س\n')
print('=== اشتراكات والتزامات ===')
for x in subs: print(f"{x['نشط']:>3} | {x['التكلفة السنوية المقدّرة']:>8.2f}/سنة | {x['المبلغ المتكرر']:>7.2f}×{x['عدد الخصومات']:>2} | {x['أول خصم']}→{x['آخر خصم']} | {x['الخدمة']}")
a=[x for x in subs if x['نشط']=='نعم']
print('نشط:',len(a),'| تكلفة سنوية للنشط:',round(sum(x['التكلفة السنوية المقدّرة'] for x in a),2))
print('\n=== فواتير متكررة بمبلغ متغيّر ===')
for x in variables: print(f"{x['نشط']:>3} | {x['التكلفة السنوية المقدّرة']:>8.2f}/سنة | وسيط {x['المبلغ الوسيط']:>7.2f} ({x['أقل']}-{x['أعلى']}) ×{x['عدد الخصومات']:>2} | {x['أول خصم']}→{x['آخر خصم']} | {x['الخدمة']}")
print('\n=== ادخار/استثمار متكرر (مش مصروف) ===')
for x in savers: print(f"{x['نشط']:>3} | {x['المبلغ المتكرر']:>8.2f}×{x['عدد الخصومات']:>2} | {x['أول خصم']}→{x['آخر خصم']} | {x['الخدمة']}")
