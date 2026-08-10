package com.malarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmStoreTest {

    private lateinit var store: AlarmStore

    @Before
    fun setUp() {
        store = AlarmStore(RuntimeEnvironment.getApplication())
    }

    @Test
    fun initiallyEmpty() {
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun saveAndGet() {
        val alarm = Alarm(1, 8, 0, label = "wake up")
        store.save(alarm)
        assertEquals(alarm, store.get(1))
    }

    @Test
    fun allSortedByTime() {
        store.save(Alarm(1, 10, 0))
        store.save(Alarm(2, 8, 30))
        assertEquals(listOf(2L, 1L), store.all().map { it.id })
    }

    @Test
    fun updateExistingById() {
        store.save(Alarm(1, 8, 0))
        store.save(Alarm(1, 9, 0, label = "changed"))
        assertEquals(1, store.all().size)
        assertEquals(9, store.get(1)!!.hour)
        assertEquals("changed", store.get(1)!!.label)
    }

    @Test
    fun deleteRemovesAlarm() {
        store.save(Alarm(1, 8, 0))
        store.delete(1)
        assertNull(store.get(1))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun importAllReplacesExistingAlarms() {
        store.save(Alarm(1, 8, 0))
        val imported = store.importAll(listOf(Alarm(7, 8, 0)))
        assertEquals(1, imported.size)
        assertEquals(listOf(2L), store.all().map { it.id })
    }

    @Test
    fun importAllAssignsDistinctFreshIds() {
        val imported = store.importAll(listOf(Alarm(7, 8, 0), Alarm(3, 9, 0)))
        assertEquals(listOf(1L, 2L), imported.map { it.id })
        assertEquals(2, imported.map { it.id }.toSet().size)
    }

    @Test
    fun nextIdIncrementsFromPersistedValue() {
        assertEquals(1L, store.nextId())
        assertEquals(2L, store.nextId())
        store = AlarmStore(RuntimeEnvironment.getApplication())
        assertEquals(3L, store.nextId())
    }
}
