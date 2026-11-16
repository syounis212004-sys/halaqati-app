#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]

def read(p): return (root/p).read_text(encoding='utf-8')
app=read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt'))
shell=read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/components/GoldenShell.kt'))
auth=read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/AuthScreens.kt'))
theme=read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/Theme.kt'))
prefs=read(Path('app/src/main/java/com/mohammedsamir/halaqati/data/UiPreferencesStore.kt'))
policy=read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/navigation/RoleNavigationPolicy.kt'))
build=read(Path('app/build.gradle.kts'))
settings=read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt'))
core=read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt'))
checks={
 'right-side scrollable drawer': 'ModalNavigationDrawer' in app and 'LazyColumn' in shell and 'ModalBottomSheet' not in app,
 'golden primary token': '0xFF0D3B2F' in theme and '0xFF0D3B2F' in prefs,
 'golden accent token': '0xFF2F8B69' in Path('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/HalaqatiPalette.kt').read_text(encoding='utf-8'),
 'native credential manager deps': 'androidx.credentials:credentials' in build and 'googleid' in build,
 'minSdk23 compatible Google ID': 'minSdk = 23' in build and 'googleid:1.1.1' in build,
 'no external full-browser Google login': 'Intent.ACTION_VIEW' not in auth and 'CustomTabsIntent' in read(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/auth/NativeGoogleSignIn.kt')),
 'native Google ID token callback': 'onGoogleIdToken' in auth,
 'golden drawer labels': 'إدارة الحلقات والفئات' in policy and 'الجلسات اليومية' in policy and 'الخطط الذكية' in policy,
 'golden icon resources': (root/'app/src/main/res/drawable/ic_gm_home.xml').exists() and (root/'app/src/main/res/drawable/ic_gm_more.xml').exists(),
 'legacy appearance presets': all(token in settings for token in ['0xFF12345B','0xFF2D6CDF','0xFF5A3825','0xFFAD744F','0xFF342B5F','0xFF765BC4']),
 'settings theme density controls': all(token in settings for token in ['الوضع الداكن','مريح','متوازن','مضغوط','إعادة ضبط المظهر']),
 'rich student golden actions': all(token in core for token in ['تسميع اليوم','الملف الشخصي','تعديل البيانات','الخطة']),
 'golden session card': 'GoldenSessionCard' in core,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
if failed: raise SystemExit('Missing: '+', '.join(failed))
print('UI 2.44 restoration contract: PASS')
