package com.segawa.os.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "segawa_preferences")

class SegawaPreferences(private val context: Context) {
    private val accentKey = stringPreferencesKey("accent")
    private val modeKey = stringPreferencesKey("mode")

    val accent: Flow<String> = context.dataStore.data.map { it[accentKey] ?: "Crimson" }
    val mode: Flow<String> = context.dataStore.data.map { it[modeKey] ?: "Dark" }

    suspend fun setAccent(value: String) {
        context.dataStore.edit { it[accentKey] = value }
    }
    suspend fun setMode(value: String) {
        context.dataStore.edit { it[modeKey] = value }
    }
}

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val durationMinutes: Int,
    val completed: Boolean
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun sessions(): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insert(session: FocusSessionEntity)
}

@Database(entities = [FocusSessionEntity::class], version = 1, exportSchema = false)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile private var INSTANCE: FocusDatabase? = null
        fun get(context: Context): FocusDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusDatabase::class.java,
                    "segawa_focus.db"
                ).build().also { INSTANCE = it }
            }
    }
}
