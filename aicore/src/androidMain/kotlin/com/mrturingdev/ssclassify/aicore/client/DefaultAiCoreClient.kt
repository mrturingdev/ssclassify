package com.mrturingdev.ssclassify.aicore.client

import android.content.Context
import android.os.Build
import com.mrturingdev.ssclassify.aicore.status.AiCoreStatus
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.generationConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Production implementation of [AiCoreClient] binding to Google Play Services Android AICore.
 *
 * @param context Android Application Context.
 * @param temperature Sampling temperature (default 0.2f for factual semantic extraction).
 * @param maxOutputTokens Maximum tokens to generate (default 1024).
 */
class DefaultAiCoreClient(
    private val context: Context,
    private val temperature: Float = 0.2f,
    private val maxOutputTokens: Int = 1024,
) : AiCoreClient {

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow<AiCoreStatus>(AiCoreStatus.Unchecked)
    override val status: StateFlow<AiCoreStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private var model: GenerativeModel? = null
    private var totalBytesToDownload: Long = -1L

    private val downloadCallback = object : DownloadCallback {
        override fun onDownloadStarted(bytesToDownload: Long) {
            totalBytesToDownload = bytesToDownload
            _status.value = AiCoreStatus.Downloading(
                bytesDownloaded = 0,
                totalBytes = bytesToDownload,
            )
        }

        override fun onDownloadProgress(totalBytesDownloaded: Long) {
            _status.value = AiCoreStatus.Downloading(
                bytesDownloaded = totalBytesDownloaded,
                totalBytes = totalBytesToDownload,
            )
        }

        override fun onDownloadCompleted() {
            _status.value = AiCoreStatus.Ready
        }

        override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
            _status.value = AiCoreStatus.Error("Download failed ($failureStatus): ${e.message}", e)
        }

        override fun onDownloadPending() {
            _status.value = AiCoreStatus.Downloading(
                bytesDownloaded = 0,
                totalBytes = totalBytesToDownload,
            )
        }

        override fun onDownloadDidNotStart(e: GenerativeAIException) {
            _status.value = mapExceptionToStatus(e)
        }
    }

    override suspend fun checkAvailability(): AiCoreStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_status.value is AiCoreStatus.Ready) {
                return@withContext _status.value
            }

            // Check OS level: AICore requires API 31+
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val unsupported = AiCoreStatus.Unsupported(
                    "Device Android API level ${Build.VERSION.SDK_INT} is below required Android 12 (API 31)."
                )
                _status.value = unsupported
                return@withContext unsupported
            }

            // Check if AICore system package is present
            if (!isAiCoreServiceInstalled()) {
                val unsupported = AiCoreStatus.Unsupported(
                    "Android AICore system package ('com.google.android.aicore') is not installed on this device."
                )
                _status.value = unsupported
                return@withContext unsupported
            }

            _status.value = AiCoreStatus.Checking
            val initialized = initModelInternal()
            if (initialized) {
                prepareInternal()
            }
            return@withContext _status.value
        }
    }

    override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_status.value is AiCoreStatus.Ready) return@withContext true
            val initialized = initModelInternal()
            if (!initialized) return@withContext false
            return@withContext prepareInternal()
        }
    }

    override suspend fun generateContent(prompt: String): String = withContext(Dispatchers.IO) {
        val currentModel = getOrInitModel()
            ?: throw IllegalStateException("AICore model could not be initialized. Status: ${_status.value}")

        try {
            val response = currentModel.generateContent(prompt)
            response.text ?: ""
        } catch (e: GenerativeAIException) {
            _status.value = mapExceptionToStatus(e)
            throw e
        }
    }

    override fun generateContentStream(prompt: String): Flow<String> {
        val currentModel = model
            ?: throw IllegalStateException("AICore model is not initialized. Call prepare() or checkAvailability() first.")

        return currentModel.generateContentStream(prompt)
            .map { it.text ?: "" }
            .flowOn(Dispatchers.IO)
    }

    override fun close() {
        mutex.tryLock()
        try {
            model?.close()
            model = null
        } finally {
            if (mutex.isLocked) {
                mutex.unlock()
            }
        }
    }

    private fun initModelInternal(): Boolean {
        if (model != null) return true

        return try {
            val config = generationConfig {
                context = appContext
                temperature = this@DefaultAiCoreClient.temperature
                maxOutputTokens = this@DefaultAiCoreClient.maxOutputTokens
            }

            val downloadConfig = DownloadConfig(downloadCallback)
            model = GenerativeModel(config, downloadConfig)
            true
        } catch (e: GenerativeAIException) {
            _status.value = mapExceptionToStatus(e)
            false
        } catch (e: Throwable) {
            _status.value = AiCoreStatus.Error("Failed to instantiate AICore GenerativeModel: ${e.message}", e)
            false
        }
    }

    private suspend fun prepareInternal(): Boolean {
        val currentModel = model ?: return false

        return try {
            currentModel.prepareInferenceEngine()
            _status.value = AiCoreStatus.Ready
            true
        } catch (e: GenerativeAIException) {
            _status.value = mapExceptionToStatus(e)
            false
        } catch (e: Throwable) {
            _status.value = AiCoreStatus.Error("prepareInferenceEngine failed: ${e.message}", e)
            false
        }
    }

    private suspend fun getOrInitModel(): GenerativeModel? {
        mutex.withLock {
            if (model == null) {
                initModelInternal()
                prepareInternal()
            }
            return model
        }
    }

    private fun isAiCoreServiceInstalled(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo("com.google.android.aicore", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun mapExceptionToStatus(e: GenerativeAIException): AiCoreStatus {
        return when (e.errorCode) {
            GenerativeAIException.ErrorCode.NOT_AVAILABLE ->
                AiCoreStatus.Unavailable("Gemini Nano model is not available: ${e.message}")

            GenerativeAIException.ErrorCode.NEEDS_SYSTEM_UPDATE ->
                AiCoreStatus.NeedsUpdate("Android AICore system components require an update: ${e.message}")

            GenerativeAIException.ErrorCode.NOT_ENOUGH_DISK_SPACE ->
                AiCoreStatus.Unavailable("Not enough storage space to download Gemini Nano: ${e.message}")

            GenerativeAIException.ErrorCode.BUSY ->
                AiCoreStatus.Unavailable("AICore inference service is currently busy.")

            GenerativeAIException.ErrorCode.SERVICE_DISCONNECTED,
            GenerativeAIException.ErrorCode.BINDING_DIED,
            GenerativeAIException.ErrorCode.BINDING_FAILURE ->
                AiCoreStatus.Error("AICore service binding disconnected.", e)

            else ->
                AiCoreStatus.Error("AICore exception (code ${e.errorCode}): ${e.message}", e)
        }
    }
}
