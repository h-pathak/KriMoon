package com.example.security

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-only App Check provider installer.
 * Compiled exclusively into debug builds (`debugImplementation` + `src/debug`) so the debug
 * App Check provider class is never packaged into release APKs or AABs.
 */
object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
