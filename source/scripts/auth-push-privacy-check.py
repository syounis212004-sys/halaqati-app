#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors=[]

def read(rel):
    p=root/rel
    if not p.exists():
        errors.append(f'missing {rel}')
        return ''
    return p.read_text(encoding='utf-8', errors='ignore')

build=read('app/build.gradle.kts')
api=read('app/src/main/java/com/mohammedsamir/halaqati/data/SupabaseApi.kt')
auth_callback=read('app/src/main/java/com/mohammedsamir/halaqati/data/AuthCallbackRules.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
vm=read('app/src/main/java/com/mohammedsamir/halaqati/ui/AppViewModel.kt')
auth=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/AuthScreens.kt')
push=read('app/src/main/java/com/mohammedsamir/halaqati/push/NotificationHelper.kt')
service=read('app/src/main/java/com/mohammedsamir/halaqati/push/HalaqatiMessagingService.kt')
manifest=read('app/src/main/AndroidManifest.xml')
backup=read('app/src/main/res/xml/backup_rules.xml')
extract=read('app/src/main/res/xml/data_extraction_rules.xml')
store=read('app/src/main/java/com/mohammedsamir/halaqati/data/SecureSessionStore.kt')
app=read('app/src/main/java/com/mohammedsamir/halaqati/HalaqatiApplication.kt')

for token in (
    'const val APP_DEEP_LINK = "com.mohammedsamir.halaqati://auth/callback"',
    'recover?redirect_to=$redirect',
    'URLEncoder.encode(APP_DEEP_LINK',
    'AuthCallbackRules.parse(url, APP_DEEP_LINK)',
    'fun updatePassword(newPassword: String)',
    '"${BuildConfig.SUPABASE_URL}/auth/v1/user"',
    'fun callbackType(url: String)',
):
    if token not in api: errors.append(f'native auth recovery contract missing: {token}')
for token in ('data class Recovery(', 'AuthState.Recovery', 'completePasswordRecovery', 'cancelPasswordRecovery', 'FirebaseMessaging.getInstance().deleteToken()'):
    if token not in vm: errors.append(f'AppViewModel auth/logout contract missing: {token}')
for token in ('fun PasswordRecoveryScreen(', 'تعيين كلمة مرور جديدة', 'كلمتا المرور غير متطابقتين'):
    if token not in auth: errors.append(f'password recovery UI missing: {token}')
for token in ('"halaqati-notify"', '"register_device"', '"unregister_device"', 'store.savePushToken(token)', 'store.clearPushToken()'):
    if token not in repo: errors.append(f'push registration lifecycle missing: {token}')
for token in ('fun savePushToken(', 'fun loadPushToken()', 'fun clearPushToken()', '.remove("a")', '.remove("r")'):
    if token not in store: errors.append(f'secure session/device token state missing: {token}')
if 'prefs.edit().clear()' in store: errors.append('SecureSessionStore.clear() must not wipe device-token state implicitly')
for token in ('const val CHANNEL_ID = "halaqati_general"', 'fun ensureChannel(', 'ic_stat_halaqati'):
    if token not in push: errors.append(f'notification channel/icon contract missing: {token}')
for token in ('val currentUser = AppGraph.repo.currentUserId() ?: return', 'message.data["recipient_id"]', 'intendedUser != currentUser'):
    if token not in service: errors.append(f'FCM account-binding guard missing: {token}')
for token in ('NotificationHelper.ensureChannel(this)',):
    if token not in app: errors.append(f'application push initialization missing: {token}')
for token in (
    'com.google.firebase.messaging.default_notification_channel_id',
    'halaqati_general',
    'com.google.firebase.messaging.default_notification_icon',
    '@drawable/ic_stat_halaqati',
):
    if token not in manifest: errors.append(f'FCM manifest contract missing: {token}')
if 'GOOGLE_REDIRECT_URL' in build or 'GOOGLE_REDIRECT_URL' in api:
    errors.append('stale web GOOGLE_REDIRECT_URL coupling remains in Native auth build/runtime')
if '.put("redirect_to", APP_DEEP_LINK)' in api:
    errors.append('password recovery redirect_to must be an encoded /recover query parameter, not a JSON body field')
for token in ('object AuthCallbackRules', 'uri.rawQuery', 'uri.rawFragment', 'access_token', 'refresh_token', 'error_description'):
    if token not in auth_callback: errors.append(f'auth callback parser missing: {token}')
for token in ('android:scheme="com.mohammedsamir.halaqati"', 'android:host="auth"', 'android:path="/callback"', 'android:launchMode="singleTop"'):
    if token not in manifest: errors.append(f'Android auth deep-link filter missing: {token}')

for text,name in ((backup,'backup_rules.xml'),(extract,'data_extraction_rules.xml')):
    for token in ('halaqati_secure.xml','halaqati_native_report_cache.xml','halaqati_native_certificates.xml','halaqati-offline.db'):
        if token not in text: errors.append(f'{name} does not exclude sensitive local data: {token}')

if errors:
    print('Auth/push/privacy check FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('Auth/push/privacy check passed (strict recovery callback, account-bound FCM guard, lifecycle/channel, sensitive backup exclusions)')
