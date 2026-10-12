package com.tele.child

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat

object Permissions {
    /** Requested in the setup flow, each through a normal Android system dialog. */
    val SETUP = buildList {
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Precise location, asked first in the location step. */
    const val LOCATION = Manifest.permission.ACCESS_FINE_LOCATION

    /** "Allow all the time". Asked second. Not a separate runtime permission before Android 10. */
    const val BACKGROUND_LOCATION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    fun granted(ctx: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocation(ctx: Context): Boolean = granted(ctx, LOCATION)

    /** Real-time sharing needs precise location, plus background access on Android 10+. */
    fun hasLocationAlways(ctx: Context): Boolean =
        granted(ctx, LOCATION) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(ctx, BACKGROUND_LOCATION))

    /** Sent to the parent dashboard so the parent can see what Tele is allowed to read. */
    fun status(ctx: Context): Map<String, Boolean> = mapOf(
        "callLog" to granted(ctx, Manifest.permission.READ_CALL_LOG),
        "sms" to granted(ctx, Manifest.permission.READ_SMS),
        "sendSms" to granted(ctx, Manifest.permission.SEND_SMS),
        "contacts" to granted(ctx, Manifest.permission.READ_CONTACTS),
        "location" to hasLocation(ctx),
        "backgroundLocation" to hasLocationAlways(ctx)
    )
}

object SmsRole {
    /** True only when Tele is the system default SMS app. This is the ONLY condition under which SMS text is read. */
    fun isDefault(ctx: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    fun requestIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            rm.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)
        }
}
