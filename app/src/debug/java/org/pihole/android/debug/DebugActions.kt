package org.pihole.android.debug

/**
 * Broadcast actions for [DebugDnsReceiver] (debug APK only). Use with:
 *
 * ```
 * adb shell am broadcast -a <ACTION> -n org.pihole.android/.debug.DebugDnsReceiver
 * ```
 *
 * Or start the DNS foreground service via the activity (API 34+ safe path used by verify-harness):
 *
 * ```
 * adb shell am start -n org.pihole.android/.debug.DebugStartDnsActivity
 * ```
 *
 * Open the normal UI:
 *
 * ```
 * adb shell am start -n org.pihole.android/.MainActivity
 * ```
 */
object DebugActions {
    const val START_DNS: String = "org.pihole.android.debug.START_DNS"
    const val STOP_DNS: String = "org.pihole.android.debug.STOP_DNS"
    const val OPEN_MAIN: String = "org.pihole.android.debug.OPEN_MAIN"
}
