package io.searchvpn.app

import android.app.Application
import android.util.Log
import io.searchvpn.app.data.ConfigStore
import io.searchvpn.app.vpn.VpnController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {

    /** Created lazily on first use (VPN screen), so the app can start even if
     *  the WireGuard backend fails to initialize on the device. */
    val vpnController: VpnController by lazy { VpnController(this) }

    override fun onCreate() {
        super.onCreate()

        // Record any uncaught crash to a readable file under
        // /Android/data/io.searchvpn.app/files/crash.txt  (open with Samsung My Files).
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                appendDiag("crash.txt", throwable.javaClass.simpleName + ": " + throwable.message + "\n" +
                    (Log.getStackTraceString(throwable) ?: "no stack trace"))
                Log.e("SearchVPN", "Uncaught crash on thread " + thread.name, throwable)
            } catch (ignored: Throwable) {
            }
        }

        // Boot marker: proves the app process started and how far it got.
        writeDiag("boot.txt", "boot " + now() + " pid=" + android.os.Process.myPid())
        instance = this

        // Preload user's WireGuard VPN config so it is immediately ready to connect
        ConfigStore.importUserConfig(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private fun diagDir(): File? {
        val external = getExternalFilesDir(null) ?: return filesDir
        return external
    }

    fun appendDiag(name: String, text: String) {
        try {
            val dir = diagDir()
            dir?.let { File(it, name).appendText(now() + "\n" + text + "\n==========\n") }
        } catch (_: Throwable) {
        }
    }

    fun writeDiag(name: String, text: String) {
        try {
            val dir = diagDir()
            dir?.mkdirs()
            dir?.let { File(it, name).writeText(text + "\n") }
        } catch (_: Throwable) {
        }
    }
}