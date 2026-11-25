package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daw_projects")
data class DawProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val bpm: Long,
    val spatialPattern: String,
    val orbitSpeedHz: Double,
    val orbitRadiusMeters: Double,
    val reverbMix: Double,
    val bassBoostDb: Double,
    val eqBandsDb: List<Double>,
    val compressorEnabled: Boolean,
    val compressorThresholdDb: Double,
    val delayEnabled: Boolean,
    val delayMix: Double,
    val limiterEnabled: Boolean,
    val latencyFrames: Long,
    val youtubeVideoId: String,
    val youtubeTitle: String,
    val synthGain: Double,
    val clipGain: Double,
    val micGain: Double,
    val youtubeGain: Double,
    val masterGain: Double,
    val stepPattern: List<Long>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "project_notes",
    foreignKeys = [
        ForeignKey(
            entity = DawProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("projectId")],
)
data class ProjectNoteEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val message: String,
    val barPosition: Long,
    val createdAtMillis: Long,
)

class DawTypeConverters {
    @TypeConverter
    fun fromDoubleList(list: List<Double>): String = list.joinToString(",")

    @TypeConverter
    fun toDoubleList(raw: String): List<Double> {
        if (raw.isBlank()) return listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        return raw.split(",").mapNotNull { it.toDoubleOrNull() }
    }

    @TypeConverter
    fun fromLongList(list: List<Long>): String = list.joinToString(",")

    @TypeConverter
    fun toLongList(raw: String): List<Long> {
        if (raw.isBlank()) return List(16) { 0L }
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }
}

@Dao
interface DawDao {
    @Query("SELECT * FROM daw_projects ORDER BY updatedAtMillis DESC, createdAtMillis DESC")
    fun observeProjects(): Flow<List<DawProjectEntity>>

    @Query("SELECT * FROM project_notes WHERE projectId = :projectId ORDER BY createdAtMillis DESC")
    fun observeNotesForProject(projectId: String): Flow<List<ProjectNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: DawProjectEntity)

    @Query("DELETE FROM daw_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: ProjectNoteEntity)

    @Query("DELETE FROM project_notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: String)
}

@Database(
    entities = [DawProjectEntity::class, ProjectNoteEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(DawTypeConverters::class)
abstract class DawDatabase : RoomDatabase() {
    abstract fun dawDao(): DawDao

    companion object {
        @Volatile
        private var INSTANCE: DawDatabase? = null

        fun getInstance(context: Context): DawDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DawDatabase::class.java,
                    "spatial_daw_local.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
