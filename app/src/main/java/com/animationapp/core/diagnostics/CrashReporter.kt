package com.animationapp.core.diagnostics

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-App Diagnostics & Crash Catcher.
 *
 * Since we build via GitHub Actions without local Android Studio / ADB Logcat,
 * any uncaught exception or logged error is written to disk and presented in a clean
 * dialog with a "Copy Error Report" button on next launch (or on demand).
 */
object CrashReporter {

    private const val CRASH_FILE_NAME = "last_crash_report.txt"
    private const val DIAGNOSTIC_LOG_MAX_ENTRIES = 100
    private val runtimeLogs = ArrayDeque<String>(DIAGNOSTIC_LOG_MAX_ENTRIES)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrashReport(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never fail inside crash handler
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun logInfo(tag: String, message: String) {
        synchronized(runtimeLogs) {
            if (runtimeLogs.size >= DIAGNOSTIC_LOG_MAX_ENTRIES) {
                runtimeLogs.removeFirst()
            }
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            runtimeLogs.addLast("[$time] [$tag] $message")
        }
    }

    fun logWarning(tag: String, message: String) {
        synchronized(runtimeLogs) {
            if (runtimeLogs.size >= DIAGNOSTIC_LOG_MAX_ENTRIES) {
                runtimeLogs.removeFirst()
            }
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            runtimeLogs.addLast("[$time] [WARN] [$tag] $message")
        }
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        synchronized(runtimeLogs) {
            if (runtimeLogs.size >= DIAGNOSTIC_LOG_MAX_ENTRIES) {
                runtimeLogs.removeFirst()
            }
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val errStr = throwable?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""
            runtimeLogs.addLast("[$time] [ERROR] [$tag] $message$errStr")
        }
    }

    private fun saveCrashReport(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        pw.println("=== PRO ANIMATION STUDIO CRASH REPORT ===")
        pw.println("Time: $timestamp")
        pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        pw.println("Thread: ${thread.name}")
        pw.println()
        pw.println("--- Stacktrace ---")
        throwable.printStackTrace(pw)
        pw.println()
        pw.println("--- Recent Runtime Logs ---")
        synchronized(runtimeLogs) {
            for (entry in runtimeLogs) {
                pw.println(entry)
            }
        }
        pw.flush()

        val crashFile = File(context.filesDir, CRASH_FILE_NAME)
        crashFile.writeText(sw.toString())
    }

    fun checkAndShowPendingCrashReport(activity: Activity) {
        val crashFile = File(activity.filesDir, CRASH_FILE_NAME)
        if (!crashFile.exists()) return

        val reportText = try {
            crashFile.readText()
        } catch (e: Exception) {
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Previous Crash Detected")
            .setMessage(reportText.take(1800))
            .setPositiveButton("Copy Full Report") { _, _ ->
                copyToClipboard(activity, reportText)
                crashFile.delete()
            }
            .setNegativeButton("Dismiss") { _, _ ->
                crashFile.delete()
            }
            .setCancelable(false)
            .show()
    }

    fun showLiveDiagnosticsDialog(activity: Activity, extraStats: String) {
        val sb = StringBuilder()
        sb.appendLine("=== STUDIO LIVE DIAGNOSTICS ===")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine(extraStats)
        sb.appendLine("\n--- Recent Breadcrumbs ---")
        synchronized(runtimeLogs) {
            if (runtimeLogs.isEmpty()) {
                sb.appendLine("(No warnings or errors recorded)")
            } else {
                for (entry in runtimeLogs) {
                    sb.appendLine(entry)
                }
            }
        }
        val report = sb.toString()

        AlertDialog.Builder(activity)
            .setTitle("Studio Diagnostics & Logs")
            .setMessage(report)
            .setPositiveButton("Copy Report") { _, _ ->
                copyToClipboard(activity, report)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("ProAnimationDiagnostics", text))
    }
}

