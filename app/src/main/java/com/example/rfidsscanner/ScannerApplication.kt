package com.example.rfidsscanner

import android.app.Application

class ScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the DictionaryStore once when the app starts
        DictionaryStore.initialize(this)
    }
}
