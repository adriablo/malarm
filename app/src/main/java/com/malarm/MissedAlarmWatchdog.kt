package com.malarm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Detects OS-side non-delivery of armed alarms. Every [AlarmScheduler.schedule]
 * records the armed trigger in [AlarmStore]; every cancel clears it. On each
 * run this compares those expectations against the event log: an enabled alarm
 * whose trigger passed [GRACE_MS] ago with no FIRED since is logged as MISSED.
 *
 * Snooze instances are deliberately out of scope (elapsed-based, short-lived).
 */
object MissedAlarmWatchdog {

    /** Past-grace an expectation must be before it counts as missed. Covers
     * the 60 s inexact fallback window plus delivery slack. */
    internal const val GRACE_MS = 5 * 60 * 1000L

    /** FIRED search starts this far before the expectation, so a fire logged
     * a few seconds early (clock granularity) still counts. */
    internal const val FIRED_SEARCH_BACKOFF_MS = 60_000L

    private val scope = CoroutineScope(Dispatchers.IO)
    private val checkMutex = Mutex()

    /** Synchronous prefs read — snapshot this BEFORE any re-arm overwrites it. */
    fun snapshot(context: Context): Map<Long, Long> =
        AlarmStore(context).expectedTriggers()

    fun checkAsync(context: Context, snapshot: Map<Long, Long>) {
        if (snapshot.isEmpty()) return
        scope.launch {
            check(context.applicationContext, snapshot)
        }
    }

    internal suspend fun check(context: Context, snapshot: Map<Long, Long>) {
        checkMutex.withLock {
            val store = AlarmStore(context)
            val scheduler = AlarmScheduler(context)
            val dao = EventLog.getDb(context).eventDao()
            val now = System.currentTimeMillis()
            for ((alarmId, expected) in snapshot) {
                // A concurrent check may already have advanced this entry.
                if (store.expectedTriggers()[alarmId] != expected) continue
                if (now - expected < GRACE_MS) continue
                val alarm = store.get(alarmId)
                if (alarm == null || !alarm.enabled || scheduler.nextTrigger(alarm) == null) {
                    // Deleted, disabled, or expired since arming: stale entry.
                    store.clearExpectedTrigger(alarmId)
                    continue
                }
                val fired = dao.countFiredBetween(
                    alarmId,
                    expected - FIRED_SEARCH_BACKOFF_MS,
                    expected + GRACE_MS,
                )
                if (fired == 0) {
                    EventLog.log(
                        context, EventType.MISSED, alarmId, alarm.label,
                        "Expected ${AlarmFormatter.timestamp(expected)}, no fire",
                    )
                }
                // Advance the expectation so each trigger is examined once.
                scheduler.nextTrigger(alarm)?.let { store.setExpectedTrigger(alarmId, it) }
            }
        }
    }
}
