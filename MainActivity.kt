package com.lpu.reminderalarmmanager

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lpu.reminderalarmmanager.ui.theme.ReminderAlarmManagerTheme
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReminderAlarmManagerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    // Request Notification Permission for Android 13+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val launcher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (!isGranted) {
                                Toast.makeText(
                                    this,
                                    "Notification permission required",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        LaunchedEffect(Unit) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    ReminderSchedulerScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ReminderSchedulerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf("Select Time") }
    var selectedTriggerTime by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {

        TextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Reminder Message") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pick Time Button
        Button(
            onClick = {
                val timePickerDialog = TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        selectedTriggerTime = calculateNextAlarmTime(hourOfDay, minute)
                        selectedTime = String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            hourOfDay,
                            minute
                        )
                    },
                    12,
                    0,
                    true
                )
                timePickerDialog.show()
            },
        ) {
            Text(selectedTime)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Set Reminder Button
        Button(
            onClick = {
                if (message.isNotEmpty() && selectedTriggerTime != null) {
                    val alarmManager =
                        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                    // Check exact alarm permission on Android 12+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !alarmManager.canScheduleExactAlarms()
                    ) {
                        Toast.makeText(
                            context,
                            "Enable exact alarm permission first",
                            Toast.LENGTH_LONG
                        ).show()
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                        return@Button
                    }

                    scheduleAlarm(context, selectedTriggerTime!!, message)
                    Toast.makeText(context, "Reminder Scheduled for $selectedTime", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "Please enter message and select time",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
        ) {
            Text("Set Reminder")
        }
    }
}

@SuppressLint("ScheduleExactAlarm")
fun scheduleAlarm(context: Context, timeInMillis: Long, message: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("REMINDER_MESSAGE", message)
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        message.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // setExactAndAllowWhileIdle fires even in Doze mode
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        timeInMillis,
        pendingIntent
    )
}

fun calculateNextAlarmTime(hourOfDay: Int, minute: Int): Long {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // If time already passed today, schedule for tomorrow
    if (calendar.timeInMillis <= System.currentTimeMillis()) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    return calendar.timeInMillis
}