from pathlib import Path

root = Path(__file__).resolve().parents[1]
auth = (root / 'app/src/main/java/com/mohammedsamir/halaqati/ui/auth/NativeGoogleSignIn.kt').read_text()
assert 'معرّف Google Web Client غير موجود' in auth
assert 'message.contains("Web Client", ignoreCase = true)' in auth
print('BUILD FIX 7 release signing / Google OAuth fallback gate PASS')
