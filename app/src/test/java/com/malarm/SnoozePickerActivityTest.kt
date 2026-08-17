package com.malarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.ListView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SnoozePickerActivityTest {

    private lateinit var context: Context
    private lateinit var store: AlarmStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        store = AlarmStore(context)
        context.getSharedPreferences("malarm", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { EventLog.clear(context) }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowOf(alarmManager).scheduledAlarms.forEach { alarmManager.cancel(it.operation!!) }
    }

    private fun launch(alarm: Alarm): ActivityController<SnoozePickerActivity> {
        return Robolectric.buildActivity(
            SnoozePickerActivity::class.java,
            SnoozePickerActivity.intent(context, alarm.id),
        ).setup()
    }

    private fun dialogList(controller: ActivityController<SnoozePickerActivity>): ListView {
        val dialogs = org.robolectric.shadows.ShadowDialog.getShownDialogs()
        assertNotNull("expected a snooze dialog", dialogs.lastOrNull())
        val dialog = dialogs.last()
        // The list lives somewhere in the dialog's decor view; search for it.
        val list = dialog.window?.decorView?.let { findList(it) } ?: dialog.findViewById(android.R.id.list)
        assertNotNull("expected a list in the dialog", list)
        return list
    }

    private fun findList(view: android.view.View): ListView? {
        if (view is ListView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findList(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun select(controller: ActivityController<SnoozePickerActivity>, index: Int) {
        val list = dialogList(controller)
        list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val scheduledAlarms
        get() = shadowOf(
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager,
        ).scheduledAlarms

    @Test
    fun showsAllSnoozePresets() {
        val alarm = Alarm(1, 8, 0)
        store.save(alarm)
        val controller = launch(alarm)
        val list = dialogList(controller)
        val labels = (0 until list.adapter.count).map { list.adapter.getItem(it).toString() }
        assertEquals(listOf("5 min", "10 min", "15 min", "30 min", "1 h", "2 h", "4 h", "8 h"), labels)
    }

    @Test
    fun selectingOptionSchedulesSnoozeWithMatchingDelay() {
        val alarm = Alarm(1, 8, 0, repeatDays = setOf(Calendar.MONDAY))
        store.save(alarm)
        val controller = launch(alarm)
        val before = System.currentTimeMillis()
        // index 4 == "1 h" (60 min)
        select(controller, 4)
        val after = System.currentTimeMillis()
        val alarms = scheduledAlarms
        assertEquals(1, alarms.size)
        val trigger = alarms[0].triggerAtMs
        assertTrue("snooze trigger ${trigger} outside [${before + 3_600_000L}, ${after + 3_600_000L}]", trigger in (before + 3_600_000L)..(after + 3_600_000L))
    }

    @Test
    fun selectingOptionFinishesActivity() {
        val alarm = Alarm(1, 8, 0)
        store.save(alarm)
        val controller = launch(alarm)
        select(controller, 0)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun missingAlarmFinishesImmediately() {
        val controller = Robolectric.buildActivity(
            SnoozePickerActivity::class.java,
            Intent(context, SnoozePickerActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, 999L),
        ).setup()
        assertTrue(controller.get().isFinishing)
    }
}
