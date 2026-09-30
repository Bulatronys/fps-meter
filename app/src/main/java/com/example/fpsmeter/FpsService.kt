package com.example.fpsmeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class FpsService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var tv: TextView
    @Volatile private var running = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("fps", "FPS", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "fps")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("FPS-счётчик запущен").build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        wm = getSystemService(WindowManager::class.java)
        tv = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.GREEN)
            setBackgroundColor(0x99000000.toInt())
            setPadding(16, 8, 16, 8)
            text = "FPS: …"
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 48 }
        wm.addView(tv, lp)
        thread { loop() }
    }

    private fun loop() {
        var layer = ""
        var picked = 0L
        while (running) {
            val now = System.nanoTime()
            if (layer.isEmpty() || now - picked > 3_000_000_000L) {
                picked = now
                layer = pickLayer()
            }
            val f = if (layer.isEmpty()) null else fps(layer)
            tv.post {
                if (f == null) {
                    tv.text = "FPS: нет данных"
                    tv.setTextColor(Color.WHITE)
                } else {
                    tv.text = "FPS: $f"
                    tv.setTextColor(if (f >= 50) Color.GREEN else if (f >= 30) Color.YELLOW else Color.RED)
                }
            }
            Thread.sleep(1000)
        }
    }

    // Слой SurfaceFlinger приложения, которое сейчас на экране (SurfaceView — приоритетнее)
    private fun pickLayer(): String {
        val pkg = Regex("""ResumedActivity[=:] ?ActivityRecord\{\S+ u\d+ ([\w.]+)/""")
            .find(sh("dumpsys activity activities"))?.groupValues?.get(1) ?: return ""
        val c = sh("dumpsys SurfaceFlinger --list").lines()
            .filter { pkg in it && !it.startsWith("Background for") }
        return c.firstOrNull { "SurfaceView" in it } ?: c.firstOrNull() ?: ""
    }

    // FPS = число показанных кадров за последнюю секунду по таймстампам SurfaceFlinger
    private fun fps(layer: String): Int? {
        val ts = sh("dumpsys SurfaceFlinger --latency \"$layer\"").lines().drop(1)
            .mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() }
            .filter { it > 0 && it != Long.MAX_VALUE }
        if (ts.isEmpty()) return null
        val last = ts.max()
        if (System.nanoTime() - last > 1_500_000_000L) return 0
        val w = ts.filter { last - it <= 1_000_000_000L }
        return if (w.size < 2) 0 else ((w.size - 1) * 1e9 / (last - w.min())).roundToInt()
    }

    // Сначала обычный shell (нужен grant DUMP через adb), потом root
    private fun sh(cmd: String): String {
        for (p in listOf(arrayOf("sh", "-c", cmd), arrayOf("su", "-c", cmd))) try {
            val out = Runtime.getRuntime().exec(p).inputStream.bufferedReader().readText()
            if (out.isNotBlank() && "Permission Denial" !in out) return out
        } catch (_: Exception) {}
        return ""
    }

    override fun onDestroy() {
        running = false
        wm.removeView(tv)
        super.onDestroy()
    }
}
