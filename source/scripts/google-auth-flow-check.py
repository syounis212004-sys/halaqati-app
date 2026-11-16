#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
auth = (root/'app/src/main/java/com/mohammedsamir/halaqati/ui/auth/NativeGoogleSignIn.kt').read_text(encoding='utf-8')
screen = (root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/AuthScreens.kt').read_text(encoding='utf-8')
app = (root/'app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt').read_text(encoding='utf-8')
build = (root/'app/build.gradle.kts').read_text(encoding='utf-8')
checks = {
    'minSdk23 retained': 'minSdk = 23' in build,
    'compatible googleid retained': 'googleid:1.1.1' in build,
    'credential manager primary': 'GetGoogleIdOption' in auth and 'CredentialManager.create' in auth,
    'no incompatible explicit option': 'GetSignInWithGoogleOption' not in auth,
    'partial custom tab fallback dependency': 'androidx.browser:browser:' in build,
    'partial custom tab fallback implementation': 'CustomTabsIntent.Builder' in auth and 'setInitialActivityHeightPx' in auth,
    'oauth fallback connected to screen': 'onGoogleOAuthUrl' in screen and 'launchOAuthFallback' in screen,
    'oauth url wired from vm': 'onGoogleOAuthUrl = vm::googleOAuthUrl' in app,
    'no raw external browser intent': 'Intent.ACTION_VIEW' not in screen and 'Intent.ACTION_VIEW' not in auth,
}
bad=[]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL'), name)
    if not ok: bad.append(name)
if bad: raise SystemExit('Google auth flow missing: '+', '.join(bad))
print('Google auth flow contract: PASS')
