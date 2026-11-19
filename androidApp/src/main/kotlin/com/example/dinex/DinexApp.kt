package com.example.dinex

import android.app.Application

/** Prepara App Check antes de cualquier consulta a Dinex IA en la nube. */
class DinexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installDinexAppCheck(this)
    }
}
