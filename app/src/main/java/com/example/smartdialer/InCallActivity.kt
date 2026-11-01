package com.example.smartdialer

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            MaterialTheme {
                InCallScreen(onCallEnded = { finish() })
            }
        }
    }
}

@Composable
fun InCallScreen(onCallEnded: () -> Unit) {
    val callerInfo by CallManager.callerInfo.collectAsState()
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isOnHold by remember { mutableStateOf(false) }
    var callSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(callerInfo.callState) {
        if (callerInfo.callState == Call.STATE_DISCONNECTED) {
            onCallEnded()
        }
        if (callerInfo.callState == Call.STATE_ACTIVE) {
            while (true) {
                delay(1000L)
                callSeconds++
            }
        }
    }

    val formattedTimer = remember(callSeconds) {
        val mins = callSeconds / 60
        val secs = callSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    val statusText = when (callerInfo.callState) {
        Call.STATE_RINGING -> "Incoming Call..."
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling..."
        Call.STATE_HOLDING -> "Call on Hold • $formattedTimer"
        Call.STATE_ACTIVE -> "$formattedTimer • HD (Auto-Recording)"
        else -> "Ending Call..."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF111318)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF283141)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFFA8C7FA),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = callerInfo.displayName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                if (!callerInfo.cnapName.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF0F5223),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "CNAP",
                                tint = Color(0xFF6DD58C),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "CNAP Verified: ${callerInfo.cnapName}",
                                color = Color(0xFF6DD58C),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = callerInfo.number,
                    color = Color.LightGray,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusText,
                    color = Color(0xFFA8C7FA),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (callerInfo.callState == Call.STATE_ACTIVE ||
                    callerInfo.callState == Call.STATE_DIALING ||
                    callerInfo.callState == Call.STATE_HOLDING
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    CallManager.toggleMute(isMuted)
                                },
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(if (isMuted) Color.White else Color(0xFF283141))
                            ) {
                                Icon(
                                    if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = if (isMuted) Color.Black else Color.White
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Mute", color = Color.LightGray, fontSize = 12.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    isOnHold = !isOnHold
                                    CallManager.holdCall(isOnHold)
                                },
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnHold) Color.White else Color(0xFF283141))
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "Hold",
                                    tint = if (isOnHold) Color.Black else Color.White
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Hold", color = Color.LightGray, fontSize = 12.sp)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    isSpeakerOn = !isSpeakerOn
                                    CallManager.toggleSpeaker(isSpeakerOn)
                                },
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(if (isSpeakerOn) Color.White else Color(0xFF283141))
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Speaker",
                                    tint = if (isSpeakerOn) Color.Black else Color.White
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Speaker", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { CallManager.endCall() },
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDB372D))
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End", tint = Color.White)
                    }

                    if (callerInfo.callState == Call.STATE_RINGING) {
                        Button(
                            onClick = { CallManager.answerCall() },
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1EA446))
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
