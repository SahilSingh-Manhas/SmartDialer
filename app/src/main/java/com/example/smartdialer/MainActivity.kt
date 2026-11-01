package com.example.smartdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0B57D0),
                    secondaryContainer = Color(0xFFD3E3FD),
                    surface = Color(0xFFF8FAFD)
                )
            ) {
                GoogleDialerApp()
            }
        }
    }
}

data class ContactEntry(
    val name: String,
    val number: String,
    val t9Signature: String
)

fun nameToT9(name: String): String {
    val sb = StringBuilder()
    for (ch in name.lowercase(Locale.ROOT)) {
        when (ch) {
            'a', 'b', 'c' -> sb.append('2')
            'd', 'e', 'f' -> sb.append('3')
            'g', 'h', 'i' -> sb.append('4')
            'j', 'k', 'l' -> sb.append('5')
            'm', 'n', 'o' -> sb.append('6')
            'p', 'q', 'r', 's' -> sb.append('7')
            't', 'u', 'v' -> sb.append('8')
            'w', 'x', 'y', 'z' -> sb.append('9')
            ' ' -> sb.append('0')
        }
    }
    return sb.toString()
}

fun loadPhoneContacts(context: Context): List<ContactEntry> {
    val list = mutableListOf<ContactEntry>()
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return list
    }
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )
    val seen = mutableSetOf<String>()
    cursor?.use {
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (it.moveToNext()) {
            val name = it.getString(nameIdx) ?: continue
            val rawNum = it.getString(numIdx)?.replace(Regex("[^0-9+]"), "") ?: continue
            val key = "$name|$rawNum"
            if (seen.add(key)) {
                list.add(ContactEntry(name, rawNum, nameToT9(name)))
            }
        }
    }
    return list
}

fun placeSimCall(context: Context, number: String) {
    if (number.isBlank()) return
    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
        == PackageManager.PERMISSION_GRANTED
    ) {
        context.startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDialerApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotEmpty() && selectedTab != 2) {
                                selectedTab = 2
                            }
                        },
                        placeholder = { Text("Search contacts or T9 keypad...", fontSize = 14.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFF0F4F9)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dialpad, contentDescription = null) },
                    label = { Text("Keypad") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Recents") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = null) },
                    label = { Text("Contacts") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text("Recordings") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Auto-Rec") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (selectedTab) {
                0 -> ResponsiveDialpadScreen(context)
                1 -> RecentsCallLogScreen(context)
                2 -> ContactsScreen(context, searchQuery)
                3 -> RecordingsListScreen(context)
                4 -> AutoRecordSettingsScreen(context)
            }
        }
    }
}

@Composable
fun ResponsiveDialpadScreen(context: Context) {
    var phoneNumber by remember { mutableStateOf("") }
    val allContacts = remember { mutableStateListOf<ContactEntry>() }

    val config = LocalConfiguration.current
    val isCompactHeight = config.screenHeightDp < 650
    val buttonSize = if (isCompactHeight) 58.dp else 68.dp

    LaunchedEffect(Unit) {
        allContacts.clear()
        allContacts.addAll(loadPhoneContacts(context))
    }

    val t9Matches = remember(phoneNumber, allContacts.size) {
        if (phoneNumber.isBlank()) {
            emptyList()
        } else {
            val cleanQuery = phoneNumber.replace(Regex("[^0-9+]"), "")
            allContacts.filter {
                it.t9Signature.contains(cleanQuery) || it.number.contains(cleanQuery)
            }.take(15)
        }
    }

    val dialKeys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (phoneNumber.isNotEmpty() && t9Matches.isEmpty()) {
                item {
                    Text(
                        text = "No T9 contact match • Tap green call button to dial",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(t9Matches) { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { placeSimCall(context, contact.number) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "${contact.number}  •  T9: ${contact.t9Signature}",
                                fontSize = 12.sp,
                                color = Color(0xFF0B57D0)
                            )
                        }
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Call Contact",
                            tint = Color(0xFF146C2E)
                        )
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = phoneNumber,
                    fontSize = if (phoneNumber.length > 12) 24.sp else 32.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (phoneNumber.isNotEmpty()) {
                    IconButton(onClick = { phoneNumber = phoneNumber.dropLast(1) }) {
                        Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                    }
                }
            }

            Divider(color = Color.LightGray.copy(alpha = 0.4f))
            Spacer(Modifier.height(6.dp))

            dialKeys.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(Color(0xFFE9EEF6))
                                .clickable { phoneNumber += digit },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(digit, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                if (letters.isNotEmpty()) {
                                    Text(letters, fontSize = 9.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { placeSimCall(context, phoneNumber) },
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF146C2E))
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun ContactsScreen(context: Context, searchQuery: String) {
    val allContacts = remember { mutableStateListOf<ContactEntry>() }

    LaunchedEffect(Unit) {
        allContacts.clear()
        allContacts.addAll(loadPhoneContacts(context))
    }

    val filtered = remember(searchQuery, allContacts.size) {
        if (searchQuery.isBlank()) {
            allContacts
        } else {
            val q = searchQuery.lowercase(Locale.ROOT)
            allContacts.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                        it.number.contains(q) ||
                        it.t9Signature.contains(q)
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                text = "Contacts (${filtered.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        items(filtered) { contact ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { placeSimCall(context, contact.number) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(contact.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(contact.number, color = Color.Gray, fontSize = 13.sp)
                    }
                    IconButton(onClick = { placeSimCall(context, contact.number) }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF0B57D0))
                    }
                }
            }
        }
    }
}

data class CallLogEntry(val number: String, val name: String, val date: String, val type: String)

@Composable
fun RecentsCallLogScreen(context: Context) {
    val logs = remember { mutableStateListOf<CallLogEntry>() }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                "${CallLog.Calls.DATE} DESC LIMIT 60"
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                while (it.moveToNext()) {
                    val num = it.getString(numIdx) ?: "Unknown"
                    val cachedName = it.getString(nameIdx)?.takeIf { n -> n.isNotBlank() }
                    val cnapColIdx = it.getColumnIndex("cnap_name")
                    val cnapName = if (cnapColIdx != -1) it.getString(cnapColIdx) else null

                    val displayLabel = when {
                        !cachedName.isNullOrBlank() -> cachedName
                        !cnapName.isNullOrBlank() -> "$cnapName (CNAP)"
                        else -> num
                    }

                    val rawDate = it.getLong(dateIdx)
                    val formattedDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(rawDate))
                    val callType = when (it.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Call"
                    }
                    logs.add(CallLogEntry(num, displayLabel, formattedDate, callType))
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Recent Calls", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        }
        items(logs) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${item.type} • ${item.date}", color = Color.Gray, fontSize = 13.sp)
                    }
                    IconButton(onClick = { placeSimCall(context, item.number) }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF0B57D0))
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsListScreen(context: Context) {
    val folder = File(context.getExternalFilesDir(null), "CallRecordings")
    var files by remember {
        mutableStateOf(folder.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList())
    }
    var playingFile by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("SIM & WhatsApp Recordings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Path: Android/data/com.example.smartdialer/files/CallRecordings",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(12.dp))
        }

        if (files.isEmpty()) {
            item {
                Text(
                    "Abhi tak koi recording nahi hai.",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }

        items(files) { file ->
            val isWhatsApp = file.name.startsWith("WhatsApp")
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isWhatsApp) Color(0xFFE8F5E9) else Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isWhatsApp) "WhatsApp Call" else "SIM Call (CNAP)",
                            fontWeight = FontWeight.Bold,
                            color = if (isWhatsApp) Color(0xFF1B5E20) else Color(0xFF0B57D0)
                        )
                        Text(file.name, fontSize = 12.sp, color = Color.DarkGray)
                    }

                    Row {
                        IconButton(onClick = {
                            if (playingFile == file.name) {
                                mediaPlayer.stop()
                                playingFile = null
                            } else {
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource(file.absolutePath)
                                mediaPlayer.prepare()
                                mediaPlayer.start()
                                playingFile = file.name
                                mediaPlayer.setOnCompletionListener { playingFile = null }
                            }
                        }) {
                            Icon(
                                if (playingFile == file.name) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Play"
                            )
                        }

                        IconButton(onClick = {
                            if (playingFile == file.name) {
                                mediaPlayer.stop()
                                playingFile = null
                            }
                            file.delete()
                            files = folder.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDB372D))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoRecordSettingsScreen(context: Context) {
    val prefs = remember { context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE) }
    var autoSim by remember { mutableStateOf(prefs.getBoolean("auto_sim_rec", true)) }
    var autoWa by remember { mutableStateOf(prefs.getBoolean("auto_wa_rec", true)) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Auto Call Recording Setup", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Record SIM Calls", fontWeight = FontWeight.SemiBold)
                        Text("Saves with CNAP or Contact Name", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = autoSim, onCheckedChange = {
                        autoSim = it
                        prefs.edit().putBoolean("auto_sim_rec", it).apply()
                    })
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Record WhatsApp Calls", fontWeight = FontWeight.SemiBold)
                        Text("Extracts WhatsApp caller name automatically", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = autoWa, onCheckedChange = {
                        autoWa = it
                        prefs.edit().putBoolean("auto_wa_rec", it).apply()
                    })
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                        !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                    ) {
                        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("1. Set as Default Phone Dialer")
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("2. Enable Accessibility Service (Required for Rec)")
        }
    }
}
