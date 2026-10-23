package com.studenthub.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

private const val PREFS = "student_hub_data"
private const val BLUE = 0xFF1769E0.toInt()
private const val NAVY = 0xFF10294B.toInt()
private const val PALE = 0xFFF3F7FC.toInt()

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val tasks = mutableListOf<String>()
    private val done = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 20)
        }
        loadTasks(); showHome()
    }

    private fun base(title: String): LinearLayout {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PALE); setPadding(20, 18, 20, 18) }
        val scroller = ScrollView(this); scroller.addView(root); setContentView(scroller)
        root.addView(TextView(this).apply { text = title; textSize = 28f; setTextColor(NAVY); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, 4, 0, 8) })
        return root
    }
    private fun label(text: String, size: Float = 16f): TextView = TextView(this).apply { this.text = text; textSize = size; setTextColor(NAVY); setPadding(0, 8, 0, 8) }
    private fun button(text: String, action: () -> Unit): Button = Button(this).apply { this.text = text; setOnClickListener { action() } }
    private fun card(text: String) { root.addView(TextView(this).apply { this.text = text; textSize = 16f; setTextColor(NAVY); setBackgroundColor(Color.WHITE); setPadding(16, 14, 16, 14) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }) }
    private fun daysLeft(date: Long): Int = ceil((date - System.currentTimeMillis()).toDouble() / 86400000.0).toInt()
    private fun countdown(date: Long): String {
        var millis = (date - System.currentTimeMillis()).coerceAtLeast(0L)
        val days = millis / 86400000L; millis %= 86400000L
        val hours = millis / 3600000L; millis %= 3600000L
        val minutes = millis / 60000L
        return "$days days • $hours hours • $minutes minutes remaining"
    }

    private fun showHome() {
        base("Student Hub")
        root.addView(label("PLAN  •  STUDY  •  ACHIEVE", 15f))
        val total = tasks.size; val completed = tasks.indices.count { done.contains(it) }
        card("TODAY'S PROGRESS\n$completed of $total tasks completed\n${if (total > 0) completed * 100 / total else 0}% complete")
        val targetDate = prefs.getLong("target_date", 0L)
        card(if (targetDate > 0) "🎯 ${prefs.getString("target_name", "My Mission")}\n${countdown(targetDate)}" else "🎯 YOUR NEXT TARGET\nSet an exam or goal date to start your countdown.")
        root.addView(button("✓  To-Do List") { showTasks() })
        root.addView(button("📚  Smart Study Planner") { showPlanner() })
        root.addView(button("🎯  Missions & Goals") { showMission() })
        root.addView(button("⏰  One-time Study Reminder") { showReminder() })
        root.addView(button("🔔  Daily Recurring Reminder") { showRecurringReminder() })
        root.addView(button("＋  Add Task") { addTaskDialog() })
        root.addView(label("Small steps make big progress.", 14f))
    }

    private fun loadTasks() {
        tasks.clear(); done.clear()
        val raw = prefs.getString("tasks", "") ?: ""
        if (raw.isNotBlank()) tasks.addAll(raw.split("\n"))
        val completed = prefs.getString("done", "") ?: ""
        if (completed.isNotBlank()) completed.split(",").mapNotNull { it.toIntOrNull() }.forEach { done.add(it) }
    }
    private fun saveTasks() { prefs.edit().putString("tasks", tasks.joinToString("\n")).putString("done", done.joinToString(",")).apply() }

    private fun showTasks() {
        base("To-Do List"); root.addView(button("＋ Add a task") { addTaskDialog() })
        if (tasks.isEmpty()) root.addView(label("No tasks yet. Add one small step to get started."))
        tasks.forEachIndexed { i, task ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8, 4, 8, 4) }
            row.addView(CheckBox(this).apply { text = task; textSize = 16f; setTextColor(NAVY); isChecked = done.contains(i); setOnCheckedChangeListener { _, checked -> if (checked) done.add(i) else done.remove(i); saveTasks(); showTasks() } }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("×") { tasks.removeAt(i); val old = done.toList(); done.clear(); old.filter { it < i }.forEach { done.add(it) }; old.filter { it > i }.forEach { done.add(it - 1) }; saveTasks(); showTasks() })
            root.addView(row)
        }
        root.addView(button("← Home") { showHome() })
    }
    private fun addTaskDialog() {
        val input = EditText(this).apply { hint = "e.g. Revise Physics chapter 3" }
        AlertDialog.Builder(this).setTitle("Add task").setView(input).setPositiveButton("Save") { _, _ ->
            val value = input.text.toString().trim(); if (value.isNotEmpty()) { tasks.add(value); saveTasks(); showTasks() }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showPlanner() {
        base("Smart Study Planner")
        root.addView(label("Create a simple plan by entering your subjects and how many study sessions you want for each."))
        root.addView(button("＋ Generate a study plan") { plannerDialog() })
        root.addView(label("Generated sessions are added to your To-Do List. You can edit or complete them there."))
        root.addView(button("View my tasks") { showTasks() }); root.addView(button("← Home") { showHome() })
    }
    private fun plannerDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val subjects = EditText(this).apply { hint = "Subjects separated by commas" }
        val sessions = EditText(this).apply { hint = "Sessions per subject (e.g. 3)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        box.addView(subjects); box.addView(sessions)
        AlertDialog.Builder(this).setTitle("Build study plan").setView(box).setPositiveButton("Generate") { _, _ ->
            val list = subjects.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val count = sessions.text.toString().toIntOrNull()?.coerceIn(1, 10) ?: 2
            if (list.isEmpty()) { Toast.makeText(this, "Enter at least one subject", Toast.LENGTH_LONG).show(); return@setPositiveButton }
            list.forEach { subject -> for (i in 1..count) tasks.add("$subject — Study session $i/$count") }
            saveTasks(); Toast.makeText(this, "Added ${list.size * count} study sessions", Toast.LENGTH_LONG).show(); showTasks()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showMission() {
        base("Missions & Goals")
        val name = prefs.getString("target_name", "") ?: ""; val date = prefs.getLong("target_date", 0L)
        if (date > 0) card("CURRENT MISSION\n$name\n\n${countdown(date)}\n\n${done.size.coerceAtMost(tasks.size)} of ${tasks.size} tasks completed") else card("Create a mission with a target date. Your live countdown will appear here.")
        root.addView(button("＋ Set / Edit Mission") { missionDialog() }); root.addView(button("← Home") { showHome() })
    }
    private fun missionDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val title = EditText(this).apply { hint = "Goal name (e.g. Final exam)"; setText(prefs.getString("target_name", "")) }
        val date = EditText(this).apply { hint = "Target date: YYYY-MM-DD"; inputType = android.text.InputType.TYPE_CLASS_DATETIME; setText(prefs.getString("target_date_text", "")) }
        box.addView(title); box.addView(date)
        AlertDialog.Builder(this).setTitle("Set your target").setView(box).setPositiveButton("Save") { _, _ ->
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date.text.toString().trim())!!
                val calendar = Calendar.getInstance().apply { time = parsed; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }
                prefs.edit().putString("target_name", title.text.toString().trim().ifBlank { "My Goal" }).putString("target_date_text", date.text.toString().trim()).putLong("target_date", calendar.timeInMillis).apply(); showMission()
            } catch (_: Exception) { Toast.makeText(this, "Enter date as YYYY-MM-DD", Toast.LENGTH_LONG).show() }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showReminder() {
        base("Study Reminder"); root.addView(label("Set a one-time reminder for your next study session."))
        root.addView(button("Remind me in 25 minutes") { scheduleReminder(25 * 60 * 1000L, 101) })
        root.addView(button("Remind me in 1 hour") { scheduleReminder(60 * 60 * 1000L, 101) })
        root.addView(label("Allow notifications in Android settings so reminders can alert you.", 14f)); root.addView(button("← Home") { showHome() })
    }
    private fun showRecurringReminder() {
        base("Daily Study Reminder")
        root.addView(label("Choose a time for a daily study notification. Android may deliver repeating alarms a little later to save battery."))
        val saved = prefs.getString("daily_reminder", "") ?: ""
        root.addView(label(if (saved.isNotBlank()) "Current daily reminder: $saved" else "No daily reminder is set."))
        root.addView(button("Set daily reminder time") {
            TimePickerDialog(this, { _, hour, minute ->
                val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }
                val manager = getSystemService(ALARM_SERVICE) as AlarmManager
                val intent = Intent(this, ReminderReceiver::class.java).putExtra("daily", true)
                val pending = PendingIntent.getBroadcast(this, 202, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
                val shown = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
                prefs.edit().putString("daily_reminder", shown).apply(); Toast.makeText(this, "Daily reminder set for $shown", Toast.LENGTH_LONG).show(); showRecurringReminder()
            }, 18, 0, false).show()
        })
        root.addView(button("Turn off daily reminder") {
            val intent = Intent(this, ReminderReceiver::class.java).putExtra("daily", true)
            val pending = PendingIntent.getBroadcast(this, 202, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(pending); prefs.edit().remove("daily_reminder").apply(); showRecurringReminder()
        })
        root.addView(button("← Home") { showHome() })
    }
    private fun scheduleReminder(delay: Long, requestCode: Int) {
        val manager = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val trigger = System.currentTimeMillis() + delay
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            else manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            Toast.makeText(this, "Study reminder set", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { manager.set(AlarmManager.RTC_WAKEUP, trigger, pending); Toast.makeText(this, "Reminder set", Toast.LENGTH_LONG).show() }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "study_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(channelId, "Study reminders", NotificationManager.IMPORTANCE_HIGH))
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, channelId) else Notification.Builder(context)
        val daily = intent?.getBooleanExtra("daily", false) == true
        notification.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(if (daily) "Your daily study time" else "Time to study!").setContentText(if (daily) "Start your planned study session. Keep making progress!" else "Your study reminder is here. One small step at a time.").setAutoCancel(true)
        manager.notify(if (daily) 202 else 101, notification.build())
    }
}
