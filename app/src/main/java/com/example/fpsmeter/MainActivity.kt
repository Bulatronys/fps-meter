package com.example.fpsmeter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val info = TextView(this).apply {
            text = "1) Разреши «Поверх других окон».\n" +
                "2) Один раз выполни на ПК:\nadb shell pm grant $packageName android.permission.DUMP\n" +
                "3) Нажми «Старт» и открой игру."
            setPadding(48, 96, 48, 48)
        }
        val start = Button(this).apply {
            text = "Старт"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } else {
                    startForegroundService(Intent(this@MainActivity, FpsService::class.java))
                }
            }
        }
        val stop = Button(this).apply {
            text = "Стоп"
            setOnClickListener { stopService(Intent(this@MainActivity, FpsService::class.java)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info); addView(start); addView(stop)
        })
    }
}
