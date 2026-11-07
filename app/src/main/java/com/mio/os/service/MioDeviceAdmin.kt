package com.mio.os.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Optional device administrator used only for "Lock device" (force-lock).
 * It never touches other device policies.
 */
class MioDeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "MIO OS can now lock the device", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device lock permission removed", Toast.LENGTH_SHORT).show()
    }
}
