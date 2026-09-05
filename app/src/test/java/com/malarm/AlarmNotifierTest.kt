package com.malarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Manual S11/11.5 (notification buttons fit): assert the ringing
 * notification actually carries the three expected actions. Layout
 * truncation itself stays a manual eyeball check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmNotifierTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun ringingNotificationHasSnoozeCustomDismissActions() {
        val notification = AlarmNotifier.build(context, Alarm(1, 8, 0, label = "Morning"))
        val actions = notification.actions
        assertNotNull(actions)
        assertEquals(3, actions!!.size)
        assertEquals(context.getString(R.string.snooze), actions[0].title.toString())
        assertEquals(context.getString(R.string.custom), actions[1].title.toString())
        assertEquals(context.getString(R.string.dismiss), actions[2].title.toString())
    }

    @Test
    fun ringingNotificationCarriesFullScreenContentIntent() {
        val notification = AlarmNotifier.build(context, Alarm(1, 8, 0))
        assertNotNull(notification.contentIntent)
    }
}
