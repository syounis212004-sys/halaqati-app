#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest_path = root / 'app/src/main/AndroidManifest.xml'
manifest = ET.parse(manifest_path).getroot()
android = '{http://schemas.android.com/apk/res/android}'

def attr(el, name):
    return el.attrib.get(android + name)

permissions = {attr(x, 'name') for x in manifest.findall('uses-permission')}
for required in {
    'android.permission.INTERNET',
    'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.POST_NOTIFICATIONS',
}:
    assert required in permissions, f'missing permission {required}'

app = manifest.find('application')
assert app is not None
assert attr(app, 'name') == '.HalaqatiApplication'
assert attr(app, 'supportsRtl') == 'true'
assert attr(app, 'fullBackupContent') == '@xml/backup_rules'
assert attr(app, 'dataExtractionRules') == '@xml/data_extraction_rules'

activities = {attr(a, 'name'): a for a in app.findall('activity')}
main = activities.get('.MainActivity')
assert main is not None, 'MainActivity missing'
assert attr(main, 'exported') == 'true'
assert attr(main, 'launchMode') == 'singleTop'

launcher = False
auth_callback = False
halaqati_scheme = False
for f in main.findall('intent-filter'):
    actions = {attr(a, 'name') for a in f.findall('action')}
    cats = {attr(c, 'name') for c in f.findall('category')}
    datas = f.findall('data')
    if 'android.intent.action.MAIN' in actions and 'android.intent.category.LAUNCHER' in cats:
        launcher = True
    for d in datas:
        scheme, host, path, path_prefix = attr(d, 'scheme'), attr(d, 'host'), attr(d, 'path'), attr(d, 'pathPrefix')
        if scheme == 'com.mohammedsamir.halaqati' and host == 'auth' and path == '/callback' and path_prefix is None:
            auth_callback = True
        if scheme == 'halaqati':
            halaqati_scheme = True
assert launcher, 'launcher intent filter missing'
assert auth_callback, 'exact native auth callback contract missing'
assert halaqati_scheme, 'halaqati navigation scheme missing'

services = {attr(s, 'name'): s for s in app.findall('service')}
fcm = services.get('.push.HalaqatiMessagingService')
assert fcm is not None, 'FCM service missing'
assert attr(fcm, 'exported') == 'false'

# Every manifest resource reference used by the release contract must exist.
required_files = [
    root / 'app/src/main/res/xml/backup_rules.xml',
    root / 'app/src/main/res/xml/data_extraction_rules.xml',
    root / 'app/src/main/res/drawable/ic_stat_halaqati.xml',
    root / 'app/src/main/res/values/styles.xml',
]
for p in required_files:
    assert p.is_file() and p.stat().st_size > 0, f'missing resource {p.relative_to(root)}'

# Parse XML resources now so malformed XML fails before Gradle.
for p in required_files:
    ET.parse(p)

print('Android manifest/resource contract passed (exact auth deep link, singleTop, FCM, backup rules)')
