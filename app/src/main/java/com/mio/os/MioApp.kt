package com.mio.os

import android.app.Application
import com.mio.os.core.DeviceStats
import com.mio.os.core.Mio
import com.mio.os.core.MioNav
import com.mio.os.core.MioScreen

class MioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Mio.init(this)
        DeviceStats.start(this)
        MioNav.start(
            if (Mio.prefs.current.onboarded) MioScreen.HOME else MioScreen.WELCOME
        )
    }
}
