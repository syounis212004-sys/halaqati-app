-keep class com.google.firebase.** { *; }
-dontwarn org.conscrypt.**

# Credential Manager / Google native sign-in (recommended by Supabase Android guide)
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
