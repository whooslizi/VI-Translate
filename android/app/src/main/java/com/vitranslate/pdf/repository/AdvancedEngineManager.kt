package com.vitranslate.pdf.repository

import android.content.Context
import android.net.Uri
import java.io.File

enum class AdvancedEngineStatus {
    READY,
    NOT_INSTALLED,
    INCOMPATIBLE,
    UNAVAILABLE
}

object AdvancedEngineManager {

    fun getEngineStatus(context: Context): AdvancedEngineStatus {
        return AdvancedEngineStatus.READY
    }

    fun isAddonInstalled(context: Context): Boolean {
        return true
    }

    fun translatePdfAdvanced(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetLang: String,
        pageSelectionInput: String,
        customEngine: TranslateEngine?,
        onProgress: (Int, Int, String) -> Unit,
        onLog: (String) -> Unit,
        isCancelled: () -> Boolean
    ): Result<String> {
        return AdvancedPdfTranslator.translatePdfAdvanced(
            context = context,
            inputUri = inputUri,
            outputFile = outputFile,
            targetLang = targetLang,
            pageSelectionInput = pageSelectionInput,
            customEngine = customEngine,
            onProgress = onProgress,
            onLog = onLog,
            isCancelled = isCancelled
        )
    }
}
