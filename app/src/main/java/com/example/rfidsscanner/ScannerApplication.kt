package com.example.rfidsscanner

import android.app.Application

/**
 * Custom application class. Triggers asynchronous dictionary initialization
 * on startup to ensure O(1) lookup tables are ready before NFC scans occur.
 */
class ScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DictionaryStore.initialize(this)
    }
}
