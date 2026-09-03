package com.vitranslate.pdf.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.vitranslate.advancedengine.IAdvancedTranslationService
import com.vitranslate.advancedengine.ITranslationCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object AdvancedEngineManager {

    const val ADVANCED_ENGINE_PACKAGE = "com.vitranslate.advancedengine"
    const val SERVICE_ACTION = "com.vitranslate.advancedengine.BIND_SERVICE"

    fun isAddonInstalled(context: Context): Boolean {
        return try {
            val intent = Intent(SERVICE_ACTION).apply { setPackage(ADVANCED_ENGINE_PACKAGE) }
            val services = context.packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
            services.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun translatePdf(
        context: Context,
        inputFile: File,
        outputFile: File,
        targetLang: String,
        engineType: String,
        onProgress: (Int, Int, String) -> Unit
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        var service: IAdvancedTranslationService? = null
        var connection: ServiceConnection? = null

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = IAdvancedTranslationService.Stub.asInterface(binder)
                try {
                    val inputPfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val outputPfd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE)

                    service?.translatePdf(
                        inputPfd,
                        outputPfd,
                        targetLang,
                        engineType,
                        object : ITranslationCallback.Stub() {
                            override fun onProgress(currentPage: Int, totalPages: Int, logMessage: String?) {
                                onProgress(currentPage, totalPages, logMessage ?: "")
                            }

                            override fun onSuccess(resultPath: String?) {
                                inputPfd.close()
                                outputPfd.close()
                                connection?.let { context.unbindService(it) }
                                if (continuation.isActive) {
                                    continuation.resume(Result.success(resultPath ?: outputFile.absolutePath))
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                inputPfd.close()
                                outputPfd.close()
                                connection?.let { context.unbindService(it) }
                                if (continuation.isActive) {
                                    continuation.resume(Result.failure(Exception(errorMessage ?: "Unknown Advanced Engine Error")))
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    connection?.let { context.unbindService(it) }
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

        val intent = Intent(SERVICE_ACTION).apply { setPackage(ADVANCED_ENGINE_PACKAGE) }
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        if (!bound) {
            continuation.resume(Result.failure(Exception("Could not bind to Advanced Translation Engine. Please check if the addon is installed.")))
        }

        continuation.invokeOnCancellation {
            try {
                service?.cancel()
                context.unbindService(connection)
            } catch (_: Exception) {}
        }
    }
}
