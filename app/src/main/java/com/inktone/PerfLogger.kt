package com.inktone

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log

/**
 * Logger de performance — instrumentation Google-grade.
 *
 * Sortie : adb logcat -s InkTonePerf:*
 * En release : tout le code est éliminé par R8 (BuildConfig.PERF_LOGGING = false).
 */
object PerfLogger {
    private const val TAG = "InkTonePerf"

    private var appStartTime: Long = 0L
    private var ttsPlayRequestTime: Long = 0L
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Cold Start ──────────────────────────────────

    fun markAppStart() {
        if (!BuildConfig.PERF_LOGGING) return
        appStartTime = SystemClock.elapsedRealtime()
        Log.i(TAG, "App start recorded")
    }

    fun markFirstFrame(label: String) {
        if (!BuildConfig.PERF_LOGGING || appStartTime == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - appStartTime
        val status = if (elapsed < 1000) "OK" else "SLOW >1s"
        Log.i(TAG, "First frame ($label): ${elapsed}ms $status")
    }

    // ── TTS Latence ─────────────────────────────────

    fun markTtsPlayRequest() {
        if (!BuildConfig.PERF_LOGGING) return
        ttsPlayRequestTime = SystemClock.elapsedRealtime()
        Log.d(TAG, "TTS play requested")
    }

    fun markFirstAudioOutput(sentenceIdx: Int) {
        if (!BuildConfig.PERF_LOGGING || ttsPlayRequestTime == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - ttsPlayRequestTime
        val status = if (elapsed < 500) "OK" else "SLOW >500ms"
        Log.i(TAG, "First audio (sentence $sentenceIdx): ${elapsed}ms $status")
        ttsPlayRequestTime = 0L
    }

    // ── Mémoire (JVM + Native) ──────────────────────

    fun logMemorySnapshot(tag: String) {
        if (!BuildConfig.PERF_LOGGING) return
        val ctx = appContext ?: return
        val runtime = Runtime.getRuntime()
        val usedJvmMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576
        val nativeMB = Debug.getNativeHeapAllocatedSize() / 1048576
        val memInfo = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memInfo)
        val deviceTotalMB = memInfo.totalMem / 1048576
        val status = if (usedJvmMB + nativeMB < 200) "OK" else "HIGH >200MB"
        Log.i(TAG, "Memory [$tag]: JVM=${usedJvmMB}MB + Native=${nativeMB}MB = ${usedJvmMB + nativeMB}MB / Device=${deviceTotalMB}MB $status")
    }

    // ── Jank (appelé depuis le callback JankStats) ──

    fun reportJank(frameDurationMs: Long) {
        if (!BuildConfig.PERF_LOGGING) return
        Log.w(TAG, "JANK: ${frameDurationMs}ms frame")
    }
}
