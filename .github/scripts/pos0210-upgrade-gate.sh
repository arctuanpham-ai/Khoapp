#!/usr/bin/env bash
set -euo pipefail

PKG="vn.ecohome.pos0210"
ACT="vn.ecohome.pos0210/.MainActivity"

echo '=== Install alpha50 baseline ==='
adb install -r /tmp/alpha50.apk | tee /tmp/install50.txt
grep -q Success /tmp/install50.txt
adb shell am start -W -n "$ACT"
SDK_LEVEL=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$SDK_LEVEL" -le 28 ]; then sleep 20; else sleep 6; fi
adb shell pidof "$PKG"
adb shell am force-stop "$PKG"
sleep 2

echo '=== Extract alpha50 database including WAL if present ==='
adb exec-out run-as "$PKG" cat databases/pos0210.db > /tmp/pre.db
adb exec-out run-as "$PKG" cat databases/pos0210.db-wal > /tmp/pre.db-wal 2>/dev/null || rm -f /tmp/pre.db-wal
adb exec-out run-as "$PKG" cat databases/pos0210.db-shm > /tmp/pre.db-shm 2>/dev/null || rm -f /tmp/pre.db-shm
sqlite3 /tmp/pre.db 'PRAGMA wal_checkpoint(TRUNCATE); PRAGMA integrity_check;' | tee /tmp/pre_integrity.txt
grep -q '^ok$' /tmp/pre_integrity.txt
rm -f /tmp/pre.db-wal /tmp/pre.db-shm

echo '=== Select representative legacy rows and inject markers ==='
python3 - <<'PY'
import sqlite3, json
p='/tmp/pre.db'
db=sqlite3.connect(p)
def first_id(table):
    r=db.execute(f'SELECT id FROM "{table}" ORDER BY rowid LIMIT 1').fetchone()
    if not r:
        raise SystemExit(f'No rows in {table}; baseline seed missing')
    return r[0]
ids={'menu': first_id('MenuItemEntity'),'employee': first_id('EmployeeEntity'),'table': first_id('DiningTableEntity')}
db.execute("UPDATE MenuItemEntity SET name='QA_ALPHA50_MENU', price=123456 WHERE id=?",(ids['menu'],))
db.execute("UPDATE EmployeeEntity SET name='QA_ALPHA50_EMPLOYEE' WHERE id=?",(ids['employee'],))
db.execute("UPDATE DiningTableEntity SET name='QA_ALPHA50_TABLE' WHERE id=?",(ids['table'],))
db.execute("INSERT OR REPLACE INTO AppSettingEntity(key,value) VALUES('qa_upgrade_marker','ALPHA50_PRESERVE_ME')")
db.execute("INSERT OR REPLACE INTO CustomerEntity(id,phone,name,tier,points,totalSpend,visitCount,lastVisitAt,active,tierManual,address) VALUES(?,?,?,?,?,?,?,?,?,?,?)",('qa_customer','0900000050','QA Alpha50 Customer','VIP',321,987654,7,1700000000000,1,1,'QA Address'))
db.commit()
tables=[r[0] for r in db.execute("select name from sqlite_master where type='table' and name not like 'sqlite_%' order by name")]
counts={t:db.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0] for t in tables}
markers={'ids':ids,'menu':db.execute("select name,price from MenuItemEntity where id=?",(ids['menu'],)).fetchone(),'employee':db.execute("select name from EmployeeEntity where id=?",(ids['employee'],)).fetchone(),'table':db.execute("select name from DiningTableEntity where id=?",(ids['table'],)).fetchone(),'setting':db.execute("select value from AppSettingEntity where key='qa_upgrade_marker'").fetchone(),'customer':db.execute("select phone,name,tier,points,totalSpend,visitCount,address from CustomerEntity where id='qa_customer'").fetchone(),'user_version':db.execute('pragma user_version').fetchone()[0]}
json.dump({'counts':counts,'markers':markers},open('/tmp/before.json','w'),indent=2)
print(json.dumps({'counts':counts,'markers':markers},indent=2))
db.close()
PY
sqlite3 /tmp/pre.db 'PRAGMA integrity_check;' | tee /tmp/pre_integrity_after_markers.txt
grep -q '^ok$' /tmp/pre_integrity_after_markers.txt

echo '=== Put marked alpha50 DB back and validate baseline opens ==='
adb shell run-as "$PKG" rm -f databases/pos0210.db-wal databases/pos0210.db-shm
adb push /tmp/pre.db /data/local/tmp/pos0210-pre.db >/dev/null
adb shell chmod 644 /data/local/tmp/pos0210-pre.db
adb shell run-as "$PKG" cp /data/local/tmp/pos0210-pre.db databases/pos0210.db
adb shell run-as "$PKG" chmod 600 databases/pos0210.db
adb shell rm -f /data/local/tmp/pos0210-pre.db
adb exec-out run-as "$PKG" cat databases/pos0210.db > /tmp/roundtrip.db
sqlite3 /tmp/roundtrip.db "SELECT name || '|' || price FROM MenuItemEntity WHERE name='QA_ALPHA50_MENU' AND price=123456" | grep -q 'QA_ALPHA50_MENU|123456'
adb shell am start -W -n "$ACT"
sleep 5
adb shell pidof "$PKG"
adb shell am force-stop "$PKG"
sleep 2

echo '=== Install candidate IN PLACE (no uninstall) ==='
adb install -r /tmp/alpha51.apk | tee /tmp/install51.txt
grep -q Success /tmp/install51.txt
adb shell dumpsys package "$PKG" | grep -E 'versionName=1.0.0-alpha52-candidate11|versionCode=72'

echo '=== Launch candidate to execute Room migration to 15 ==='
adb logcat -c
adb shell am start -W -n "$ACT"
sleep 8
adb shell pidof "$PKG"
if adb logcat -d | grep -E 'FATAL EXCEPTION|Room cannot verify the data integrity|A migration from 10 to 11 was required but not found'; then
  echo 'Migration/runtime fatal detected'
  adb logcat -d > /tmp/logcat.txt
  exit 1
fi
adb logcat -d > /tmp/logcat.txt

echo '=== UI smoke: login surface exists ==='
adb shell uiautomator dump /sdcard/window.xml >/dev/null || true
adb pull /sdcard/window.xml /tmp/window.xml >/dev/null || true
grep -Eq 'PIN NHÂN VIÊN|ĐĂNG NHẬP|0210' /tmp/window.xml

adb shell am force-stop "$PKG"
sleep 2

echo '=== Extract post-upgrade database ==='
adb exec-out run-as "$PKG" cat databases/pos0210.db > /tmp/post.db
adb exec-out run-as "$PKG" cat databases/pos0210.db-wal > /tmp/post.db-wal 2>/dev/null || rm -f /tmp/post.db-wal
adb exec-out run-as "$PKG" cat databases/pos0210.db-shm > /tmp/post.db-shm 2>/dev/null || rm -f /tmp/post.db-shm
sqlite3 /tmp/post.db 'PRAGMA integrity_check;' | tee /tmp/post_integrity.txt
grep -q '^ok$' /tmp/post_integrity.txt

echo '=== DATA SURVIVAL assertions ==='
python3 - <<'PY'
import sqlite3, json, sys
before=json.load(open('/tmp/before.json')); ids=before['markers']['ids']; db=sqlite3.connect('/tmp/post.db'); errors=[]
after_counts={t:db.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0] for t in before['counts']}
for t,n in before['counts'].items():
    if after_counts.get(t)!=n: errors.append(f'count changed {t}: {n} -> {after_counts.get(t)}')
def one(sql,args=()): return db.execute(sql,args).fetchone()
checks={'menu':one("select name,price from MenuItemEntity where id=?",(ids['menu'],)),'employee':one("select name from EmployeeEntity where id=?",(ids['employee'],)),'table':one("select name from DiningTableEntity where id=?",(ids['table'],)),'setting':one("select value from AppSettingEntity where key='qa_upgrade_marker'"),'customer':one("select phone,name,tier,points,totalSpend,visitCount,address from CustomerEntity where id='qa_customer'")}
for k,v in checks.items():
    exp=tuple(before['markers'][k]) if isinstance(before['markers'][k],list) else before['markers'][k]
    if v != exp: errors.append(f'marker changed {k}: expected {exp}, got {v}')
uv=one('pragma user_version')[0]
if uv != 15: errors.append(f'user_version expected 15 got {uv}')
tables={r[0] for r in db.execute("select name from sqlite_master where type='table'")}
for required in ('PaymentSessionEntity','BankNotificationEventEntity','AssetCategoryEntity','AssetEntity','AssetValuationEntity','FinancialMovementEntity','OpeningCashAdjustmentEntity'):
    if required not in tables: errors.append(f'missing additive table {required}')
cols={r[1] for r in db.execute('pragma table_info(MenuItemEntity)')}
for col in ('productCode','description'):
    if col not in cols: errors.append(f'missing MenuItemEntity.{col}')
combo_cols={r[1] for r in db.execute('pragma table_info(ComboEntity)')}
if 'description' not in combo_cols: errors.append('missing ComboEntity.description')
purchase_cols={r[1] for r in db.execute('pragma table_info(PurchaseEntity)')}
if 'expenseCategory' not in purchase_cols: errors.append('missing PurchaseEntity.expenseCategory')
old_classified=one("select count(*) from PurchaseEntity where expenseCategory!='UNCLASSIFIED'")[0]
if old_classified: errors.append(f'{old_classified} legacy purchases were unexpectedly classified')
for table in ('MonthlyAccountingEntity','ProfitPartnerEntity'):
    if not one("select count(*) from sqlite_master where type='table' and name=?",(table,))[0]: errors.append(f'missing {table}')
blank=one("select count(*) from MenuItemEntity where productCode is null or trim(productCode)='' ")[0]
dup=one("select count(*) from (select productCode,count(*) c from MenuItemEntity group by productCode having c>1)")[0]
if blank: errors.append(f'{blank} menu rows have blank productCode')
if dup: errors.append(f'{dup} duplicate productCode groups')
result={'before_counts':before['counts'],'after_counts':after_counts,'checks':checks,'user_version':uv,'blank_codes':blank,'duplicate_code_groups':dup,'legacy_non_unclassified':old_classified,'errors':errors}
json.dump(result,open('/tmp/after.json','w'),indent=2,default=list); print(json.dumps(result,indent=2,default=list)); db.close()
if errors: sys.exit('\n'.join(errors))
PY

echo '=== RESPONSIVE TABLE CARD UI TESTS ==='
adb install -r /tmp/alpha51-androidTest.apk | tee /tmp/install_android_test.txt
grep -q Success /tmp/install_android_test.txt
adb shell am instrument -w "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee /tmp/table_card_tests.txt
grep -Eq '^OK \((7|[8-9]|[1-9][0-9]+) tests?\)' /tmp/table_card_tests.txt

echo '=== UPGRADE GATE PASS ==='
