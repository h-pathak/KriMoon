package com.example

import android.app.Application
import com.example.security.LocalVaultCrypto
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class PhasesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Eagerly initialize & verify the hardware-backed local vault (fail-closed on KEK loss)
        LocalVaultCrypto.initializeAndVerify(this)

        // 2. Initialize Firebase App Check (Debug provider in debug builds; production Play Integrity
        //    provider must be linked & enforced in Firebase Console before production release).
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            if (BuildConfig.DEBUG) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            }
        }
    }
}
