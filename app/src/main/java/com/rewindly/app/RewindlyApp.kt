package com.rewindly.app

import android.app.Application
import com.rewindly.app.work.Notifications
import com.rewindly.app.work.Scheduler

class RewindlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        Scheduler.apply(this)
    }
}
