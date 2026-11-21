package com.example

import android.app.Application
import com.example.security.AppCheckInstaller
import com.example.security.LocalVaultCrypto
import com.google.firebase.FirebaseApp

class PhasesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 1. Eagerly initialize & verify the hardware-backed local vault (fail-closed on KEK loss)
        LocalVaultCrypto.initializeAndVerify(this)

        // 2. Initialize Firebase App Check via build-variant installer:
        //    - debug builds: DebugAppCheckProviderFactory (isolated to src/debug + debugImplementation)
        //    - release builds: PlayIntegrityAppCheckProviderFactory (src/release + releaseImplementation)
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            AppCheckInstaller.install()
        }
    }
}
