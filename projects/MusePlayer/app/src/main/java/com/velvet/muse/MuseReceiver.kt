package com.velvet.muse

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class MuseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val query = intent.getStringExtra("query")
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())

        MuseEngine.log("=== CMD=$cmd ===")
        try {
            MuseEngine.connect(context.applicationContext) {
                try {
                    when (cmd) {
                        "probe" -> MuseEngine.log("probe: " + MuseEngine.describe())
                        "play" -> { MuseEngine.play(); MuseEngine.log("play -> " + MuseEngine.describe()) }
                        "pause" -> { MuseEngine.pause(); MuseEngine.log("pause -> " + MuseEngine.describe()) }
                        "toggle" -> { MuseEngine.toggle(); MuseEngine.log("toggle -> " + MuseEngine.describe()) }
                        "next" -> { MuseEngine.next(); MuseEngine.log("next -> " + MuseEngine.describe()) }
                        "prev" -> { MuseEngine.prev(); MuseEngine.log("prev -> " + MuseEngine.describe()) }
                        "stop" -> { MuseEngine.stop(); MuseEngine.log("stop") }
                        "shuffle" -> { MuseEngine.toggleShuffle(); MuseEngine.log("shuffle=" + MuseEngine.shuffleOn) }
                        "repeat" -> { MuseEngine.cycleRepeat(); MuseEngine.log("repeat=" + MuseEngine.repeatMode) }
                        "playfromsearch" -> {
                            MuseEngine.playFromSearch(query ?: "")
                            MuseEngine.log("playFromSearch(\"" + query + "\")")
                        }
                        "browse" -> MuseEngine.browse(query ?: "root")
                        "skipqueue" -> MuseEngine.skipToIndex((query ?: "0").toIntOrNull() ?: 0)
                        else -> MuseEngine.log("unknown cmd: " + cmd)
                    }
                } catch (e: Exception) {
                    MuseEngine.log("cmd error: " + e.message)
                }
            }
        } catch (e: Exception) {
            MuseEngine.log("connect error: " + e.message)
        }

        handler.postDelayed({
            MuseEngine.log("=== END $cmd: " + MuseEngine.describe() + " ===")
            pending.finish()
        }, 4000)
    }
}
