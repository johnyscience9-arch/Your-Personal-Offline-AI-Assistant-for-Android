package com.yourgpt.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the on-device model, download and conversation on one worker thread.
 * The model is stored outside the APK in the app's private files directory.
 */
class OfflineAiEngine(context: Context) {
    interface Listener {
        fun onStatus(message: String)
        fun onProgress(percent: Int)
        fun onReady()
        fun onReplyChunk(text: String)
        fun onReplyFinished()
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val dispatcher = worker.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val modelFile = File(appContext.filesDir, MODEL_FILE)
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    @Volatile private var ready = false
    @Volatile private var busy = false

    fun prepare(listener: Listener) {
        scope.launch {
            try {
                if (!modelFile.exists() || modelFile.length() < MIN_MODEL_BYTES) {
                    ready = false
                    postStatus(listener, "Downloading offline intelligence")
                    downloadModel(listener)
                }

                if (engine == null) {
                    postStatus(listener, "Preparing local AI engine")
                    val loadedEngine = createEngineWithFallback()
                    engine = loadedEngine
                    conversation = loadedEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "You are Your GPT, a helpful, accurate and respectful " +
                                    "assistant. Reply in the same language as the user. " +
                                    "Support Bengali and English. Be clear and concise."
                            )
                        )
                    )
                }

                ready = true
                post(listener) { onReady() }
            } catch (error: Exception) {
                ready = false
                post(listener) { onError(error.message ?: "Could not prepare offline AI.") }
            }
        }
    }

    fun send(prompt: String, listener: Listener) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty() || !ready || busy) return

        scope.launch {
            if (!ready || busy) return@launch
            busy = true
            try {
                val activeConversation = conversation
                    ?: throw IllegalStateException("Offline model is not ready.")
                activeConversation.sendMessageAsync(cleanPrompt).collect { message ->
                    post(listener) { onReplyChunk(message.toString()) }
                }
                post(listener) { onReplyFinished() }
            } catch (error: Exception) {
                post(listener) { onError(error.message ?: "The model could not answer.") }
            } finally {
                busy = false
            }
        }
    }

    private fun createEngineWithFallback(): Engine {
        try {
            val gpuEngine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = appContext.cacheDir.absolutePath
                )
            )
            gpuEngine.initialize()
            return gpuEngine
        } catch (_: Exception) {
            // A failed initialize() instance is deliberately not closed.
            // Retry with CPU for devices without a compatible OpenCL driver.
        }

        val cpuEngine = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath
            )
        )
        cpuEngine.initialize()
        return cpuEngine
    }

    private fun downloadModel(listener: Listener) {
        val partialFile = File(appContext.filesDir, "$MODEL_FILE.part")
        if (partialFile.exists()) partialFile.delete()

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "YourGPT-Android/1.1")
        }

        try {
            connection.connect()
            val response = connection.responseCode
            if (response !in 200..299) {
                throw IllegalStateException("Model download failed (HTTP $response).")
            }

            val total = connection.contentLengthLong
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                post(listener) { onProgress(percent) }
                            }
                        }
                    }
                    output.fd.sync()
                }
            }

            if (partialFile.length() < MIN_MODEL_BYTES) {
                throw IllegalStateException("Downloaded model file is incomplete.")
            }
            if (modelFile.exists()) modelFile.delete()
            if (!partialFile.renameTo(modelFile)) {
                throw IllegalStateException("Could not save the downloaded model.")
            }
            postStatus(listener, "Model saved. Starting offline AI")
        } catch (error: Exception) {
            partialFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun postStatus(listener: Listener, value: String) {
        post(listener) { onStatus(value) }
    }

    private fun post(listener: Listener, action: Listener.() -> Unit) {
        mainHandler.post { listener.action() }
    }

    fun close() {
        scope.launch {
            ready = false
            try {
                if (busy) conversation?.cancelProcess()
                conversation?.close()
            } catch (_: Exception) {
            } finally {
                conversation = null
            }
            try {
                engine?.close()
            } catch (_: Exception) {
            } finally {
                engine = null
            }
            dispatcher.close()
            worker.shutdown()
        }
    }

    companion object {
        private const val MODEL_FILE = "yourgpt-qwen3-0.6b-int4.litertlm"
        private const val MIN_MODEL_BYTES = 250L * 1024L * 1024L
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true"
    }
}
