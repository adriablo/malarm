package com.malarm

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EventType {
    SCHEDULED, CANCELLED, FIRED, SNOOZED, DISMISSED,
    ENABLED, DISABLED, DELETED, IMPORTED,
    BOOT_COMPLETED, TIMEZONE_CHANGED, PERIODIC_CHECK,
    RESCHEDULE_ALL, EXACT_ALARM_GRANTED, EXACT_ALARM_DENIED
}

@Entity(tableName = "event_log")
data class AlarmEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: EventType,
    val alarmId: Long?,
    val label: String?,
    val details: String?
)

class Converters {
    @TypeConverter fun fromEventType(value: EventType) = value.name
    @TypeConverter fun toEventType(value: String) = EventType.valueOf(value)
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: AlarmEvent)

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<AlarmEvent>

    @Query("DELETE FROM event_log WHERE id NOT IN (SELECT id FROM event_log ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trimOldEvents()

    @Query("DELETE FROM event_log")
    suspend fun deleteAll()
}

@Database(entities = [AlarmEvent::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}

object EventLog {
    private var db: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getDb(context: Context): AppDatabase {
        return db ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "alarm-events")
            .fallbackToDestructiveMigration()
            .build().also { db = it }
    }

    fun log(context: Context, type: EventType, alarmId: Long? = null, label: String? = null, details: String? = null) {
        val dao = getDb(context).eventDao()
        scope.launch {
            dao.insert(AlarmEvent(timestamp = System.currentTimeMillis(), type = type, alarmId = alarmId, label = label, details = details))
            dao.trimOldEvents()
        }
    }

    suspend fun getAll(context: Context) = getDb(context).eventDao().getAllEvents()
    suspend fun clear(context: Context) = getDb(context).eventDao().deleteAll()

    fun formatTimestamp(ts: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
    }
}
