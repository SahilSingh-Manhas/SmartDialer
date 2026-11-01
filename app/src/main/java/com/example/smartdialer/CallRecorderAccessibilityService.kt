package com.example.smartdialer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecorderAccessibilityService : AccessibilityService() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordType = ""
    private var lastWhatsAppCaller = "Unknown"

    private val ignoredWaLabels = setOf(
        "whatsapp", "end-to-end encrypted", "ongoing voice call", "ongoing video call",
        "voice call", "video call", "ringing", "calling", "connected", "reconnecting",
        "mute", "speaker", "message", "add participant", "switch to video call",
        "incoming voice call", "incoming video call", "answer", "decline", "end call"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        setupSimCallListener()
    }

    private fun setupSimCallListener() {
        val prefs = getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleSimState(state, prefs.getBoolean("auto_sim_rec", true))
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleSimState(state, prefs.getBoolean("auto_sim_rec", true))
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleSimState(state: Int, isEnabled: Boolean) {
        if (!isEnabled) return
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isRecording) startRecording("SIM_Call")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRecording && currentRecordType.startsWith("SIM")) {
                    stopRecording()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_wa_rec", true)) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            val rawTexts = event.text.mapNotNull { it?.toString()?.trim() }
            val eventText = rawTexts.joinToString(" ").lowercase(Locale.ROOT)
            val className = event.className?.toString()?.lowercase(Locale.ROOT) ?: ""

            extractWhatsAppCallerName(rootInActiveWindow, rawTexts)?.let { detectedName ->
                lastWhatsAppCaller = detectedName
            }

            val isVoipScreen = className.contains("voip") || className.contains("call")
            val isOngoingCallText = eventText.contains("ongoing voice call") ||
                    eventText.contains("ongoing video call") ||
                    eventText.contains("whatsapp call")

            val isCallEndedText = eventText.contains("call ended") ||
                    eventText.contains("declined") ||
                    eventText.contains("unanswered")

            if ((isVoipScreen || isOngoingCallText) && !isRecording && !isCallEndedText) {
                startRecording("WhatsApp_Call")
            } else if (isCallEndedText && isRecording && currentRecordType.startsWith("WhatsApp")) {
                stopRecording()
                lastWhatsAppCaller = "Unknown"
            }
        }
    }

    private fun extractWhatsAppCallerName(
        rootNode: AccessibilityNodeInfo?,
        eventTexts: List<String>
    ): String? {
        val candidates = mutableListOf<String>()
        candidates.addAll(eventTexts)

        if (rootNode != null) {
            collectNodeTexts(rootNode, candidates)
        }

        val timeRegex = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")
        for (item in candidates) {
            val clean = item.trim()
            val lower = clean.lowercase(Locale.ROOT)
            if (clean.length in 2..35 &&
                !ignoredWaLabels.contains(lower) &&
                !lower.contains("encrypted") &&
                !lower.contains("ongoing") &&
                !lower.contains("whatsapp") &&
                !timeRegex.matches(clean)
            ) {
                return clean
            }
        }
        return null
    }

    private fun collectNodeTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            list.add(it)
        }
        for (i in 0 until node.childCount) {
            collectNodeTexts(node.getChild(i), list)
        }
    }

    private fun startRecording(prefix: String) {
        try {
            val folder = File(getExternalFilesDir(null), "CallRecordings")
            if (!folder.exists()) folder.mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            val fileTag = if (prefix.startsWith("SIM")) {
                val info = CallManager.callerInfo.value
                val cleanName = (info.cnapName ?: info.displayName)
                    .replace(Regex("[^a-zA-Z0-9]"), "_")
                    .trim('_')
                    .takeIf { it.isNotBlank() } ?: "Unknown"
                val cleanNum = info.number.replace(Regex("[^0-9+]"), "")
                "${prefix}_${cleanName}_${cleanNum}"
            } else {
                val cleanWaName = lastWhatsAppCaller
                    .replace(Regex("[^a-zA-Z0-9+]"), "_")
                    .trim('_')
                    .takeIf { it.isNotBlank() } ?: "Contact"
                "${prefix}_${cleanWaName}"
            }

            val outputFile = File(folder, "${fileTag}_$timeStamp.m4a")

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVol, 0)

            val recorder: MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            currentRecordType = prefix
            Log.d("CallRecorder", "Recording started: ${outputFile.name}")
        } catch (e: Exception) {
            Log.e("CallRecorder", "Failed to start recording: ${e.message}")
            isRecording = false
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("CallRecorder", "Stop error: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
            currentRecordType = ""
        }
    }

    override fun onInterrupt() {
        stopRecording()
    }
}
