package com.example.douqiuqiu

import android.app.Application
import com.example.douqiuqiu.ui.GameMusic

class DouQiuQiuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GameMusic.install(this)
    }
}
