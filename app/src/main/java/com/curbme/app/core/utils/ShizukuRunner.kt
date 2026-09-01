package com.curbme.app.core.utils

import android.util.Log
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Utility for executing shell commands asynchronously or synchronously via Shizuku's IPC binder.
 */
object ShizukuRunner {
    private const val TAG = "ShizukuRunner"

    interface CommandListener {
        fun onSuccess(output: String) {}
        fun onError(error: String) {}
    }

    fun executeCommand(command: String, listener: CommandListener? = null) {
        Thread {
            try {
                Log.d(TAG, "Executing Shizuku command: $command")
                val binder = Shizuku.getBinder()
                if (binder == null) {
                    val err = "Shizuku binder is null. Is Shizuku running?"
                    Log.e(TAG, err)
                    listener?.onError(err)
                    return@Thread
                }

                val process = IShizukuService.Stub.asInterface(binder)
                    .newProcess(arrayOf("sh", "-c", command), null, null)

                val outputReader = BufferedReader(InputStreamReader(FileInputStream(process.inputStream.fileDescriptor)))
                val errorReader = BufferedReader(InputStreamReader(FileInputStream(process.errorStream.fileDescriptor)))

                val output = outputReader.readText()
                val error = errorReader.readText()

                process.waitFor()
                val exitCode = process.exitValue()

                if (error.isNotBlank() || exitCode != 0) {
                    Log.e(TAG, "Shizuku command failed (exit=$exitCode) [$command] -> Error: $error | Output: $output")
                    listener?.onError(error.ifBlank { "Exit code $exitCode" })
                } else {
                    Log.d(TAG, "Shizuku command success [$command] -> Output: $output")
                    listener?.onSuccess(output)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception executing Shizuku command [$command]", e)
                listener?.onError(e.message ?: "Failed executing Shizuku command")
            }
        }.start()
    }

    fun executeCommandSync(command: String, timeoutMs: Long = 1500L): Boolean {
        var success = false
        val thread = Thread {
            try {
                Log.d(TAG, "Executing Sync Shizuku command: $command")
                val binder = Shizuku.getBinder()
                if (binder == null) {
                    Log.e(TAG, "Sync Shizuku command failed: binder is null")
                    return@Thread
                }
                val process = IShizukuService.Stub.asInterface(binder)
                    .newProcess(arrayOf("sh", "-c", command), null, null)

                process.waitFor()
                val exitCode = process.exitValue()
                success = exitCode == 0
                if (success) {
                    Log.d(TAG, "Sync Shizuku command success (exit=0): $command")
                } else {
                    Log.e(TAG, "Sync Shizuku command failed (exit=$exitCode): $command")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in executeCommandSync [$command]", e)
                success = false
            }
        }
        thread.start()
        try {
            thread.join(timeoutMs)
        } catch (e: Exception) {
            Log.e(TAG, "Timeout waiting for Sync Shizuku command [$command]", e)
        }
        return success
    }
}
