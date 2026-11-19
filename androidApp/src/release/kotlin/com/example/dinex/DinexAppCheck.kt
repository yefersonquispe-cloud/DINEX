package com.example.dinex

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Versión publicada: App Check confirma con Play Integrity que la app viene de Google Play. */
internal fun installDinexAppCheck(app: Application) {
    if (FirebaseApp.getApps(app).isEmpty()) return
    Firebase.appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
