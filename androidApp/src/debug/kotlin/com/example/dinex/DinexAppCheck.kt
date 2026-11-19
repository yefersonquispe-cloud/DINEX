package com.example.dinex

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Versión de prueba: App Check usa un token de depuración. El token aparece en Logcat
 * (etiqueta DebugAppCheckProvider) y se registra una vez en la consola de Firebase.
 */
internal fun installDinexAppCheck(app: Application) {
    if (FirebaseApp.getApps(app).isEmpty()) return
    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
