package com.ar13x.jarvis.reminders.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * The device's mirror of the gateway's occurrence window (plan §7.1).
 *
 * **This is not a cache of record.** The gateway owns schedule and state
 * (parent plan §2.8); this table exists for one reason — an alarm has to be
 * armed from data that is on the device, because the phone may be offline or
 * asleep when it fires. Nothing reads it to draw a screen.
 */
@Entity(tableName = "upcoming_occurrence")
data class OccurrenceEntity(
    @PrimaryKey val occurrenceId: Long,
    val taskId: Long,
    val title: String,
    /** Epoch millis — what AlarmManager wants, so no conversion at fire time. */
    val scheduledForMillis: Long,
    val isPriority: Boolean,
)

@Dao
interface OccurrenceDao {

    @Query("SELECT * FROM upcoming_occurrence ORDER BY scheduledForMillis ASC")
    suspend fun all(): List<OccurrenceEntity>

    @Query("SELECT * FROM upcoming_occurrence WHERE occurrenceId = :id")
    suspend fun byId(id: Long): OccurrenceEntity?

    @Upsert
    suspend fun upsert(rows: List<OccurrenceEntity>)

    @Query("DELETE FROM upcoming_occurrence WHERE occurrenceId NOT IN (:keep)")
    suspend fun deleteNotIn(keep: List<Long>)

    @Query("DELETE FROM upcoming_occurrence")
    suspend fun clear()

    /**
     * **Replace the window, do not merge** (plan §7.1).
     *
     * A merge leaves rows for occurrences the server has since cancelled or
     * moved, and those rows still have alarms armed against them. gw03 hit
     * exactly this shape of bug on their side — a rescheduled task left its old
     * occurrence live and the window served both, which would have armed two
     * alarms for one reminder. Deleting whatever is no longer in the window is
     * what makes the device incapable of remembering a firing the server has
     * forgotten.
     */
    @Transaction
    suspend fun replaceWindow(rows: List<OccurrenceEntity>) {
        if (rows.isEmpty()) {
            clear()
            return
        }
        upsert(rows)
        deleteNotIn(rows.map { it.occurrenceId })
    }
}

@Database(entities = [OccurrenceEntity::class], version = 1, exportSchema = true)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun occurrences(): OccurrenceDao
}
