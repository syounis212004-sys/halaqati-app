# RC12.2 FIX1 build fix

This source contains the post-CI compiler correction for the certificate renderer.

GitHub run `98411847630` reached `compileDebugKotlin` through the targeted Gradle unit-test step and failed because the `drawCertificateOrnaments` / `drawCertificateCanvas` functions have a parameter named `style`, while `Paint(...).apply { style = Paint.Style.STROKE }` was resolved by Kotlin to the outer immutable `NativeCertificateStyle` value.

Correction: every affected Android `Paint` block now uses explicit `setStyle(Paint.Style.STROKE)`, eliminating the shadowing ambiguity without changing certificate layout, data, Supabase behavior, or visual design.

A regression assertion was added to `scripts/rc12-2-certificates-studio-check.py` so this exact source pattern cannot return unnoticed.
