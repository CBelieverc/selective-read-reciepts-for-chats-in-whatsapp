package com.whatsapp.selectivereads

import android.app.Application
import com.whatsapp.selectivereads.data.AppDatabase

class WhatsAppSelectiveReadsApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: WhatsAppSelectiveReadsApp
            private set
    }
}
