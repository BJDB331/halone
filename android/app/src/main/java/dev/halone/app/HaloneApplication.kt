package dev.halone.app

import android.app.Application

class HaloneApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReports.install(this)
    }
}
