package com.example.smartdialer

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CallerInfo(
    val number: String = "Unknown",
    val displayName: String = "Unknown",
    val cnapName: String? = null,
    val isSavedContact: Boolean = false,
    val callState: Int = Call.STATE_NEW
)

object CallManager {
    var inCallService: InCallService? = null
    var currentCall: Call? = null

    private val _callerInfo = MutableStateFlow(CallerInfo())
    val callerInfo: StateFlow<CallerInfo> = _callerInfo

    fun updateCallerInfo(info: CallerInfo) {
        _callerInfo.value = info
    }

    fun answerCall() {
        currentCall?.answer(0)
    }

    fun endCall() {
        currentCall?.disconnect()
    }

    fun holdCall(hold: Boolean) {
        if (hold) currentCall?.hold() else currentCall?.unhold()
    }

    fun playDtmf(digit: Char) {
        currentCall?.playDtmfTone(digit)
        currentCall?.stopDtmfTone()
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker(enable: Boolean) {
        val route = if (enable) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        inCallService?.setAudioRoute(route)
    }

    fun toggleMute(mute: Boolean) {
        inCallService?.setMuted(mute)
    }
}

class DialerInCallService : InCallService() {

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            extractCallerAndCnapDetails(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details?) {
            super.onDetailsChanged(call, details)
            extractCallerAndCnapDetails(call)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.inCallService = this
        CallManager.currentCall = call
        call.registerCallback(callCallback)

        extractCallerAndCnapDetails(call)

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (CallManager.currentCall == call) {
            CallManager.currentCall = null
            CallManager.updateCallerInfo(CallerInfo(callState = Call.STATE_DISCONNECTED))
        }
    }

    private fun extractCallerAndCnapDetails(call: Call) {
        val details = call.details
        val rawNumber = details?.handle?.schemeSpecificPart ?: "Unknown"

        val networkCnap = if (details?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
            details.callerDisplayName?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        val savedName = getContactNameFromPhonebook(this, rawNumber)

        val finalDisplayName = when {
            !savedName.isNullOrBlank() -> savedName
            !networkCnap.isNullOrBlank() -> networkCnap
            else -> rawNumber
        }

        CallManager.updateCallerInfo(
            CallerInfo(
                number = rawNumber,
                displayName = finalDisplayName,
                cnapName = networkCnap,
                isSavedContact = !savedName.isNullOrBlank(),
                callState = call.state
            )
        )
    }

    private fun getContactNameFromPhonebook(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank() || phoneNumber == "Unknown") return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
