package com.valenzine.whisperdroid.history

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(indices = [Index(value = ["audioFingerprint"], unique = true)])
data class TranscriptEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val audioFingerprint: String,
    val displayName: String,
    val rawText: String,
    val formattedText: String = "",
    @ColumnInfo(defaultValue = "''") val summary: String = "",
    @ColumnInfo(defaultValue = "'Audio'") val sourceOrigin: String = "Audio",
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface TranscriptHistoryDao {
    @Query("SELECT * FROM TranscriptEntry ORDER BY updatedAt DESC, id DESC")
    fun entries(): Flow<List<TranscriptEntry>>

    @Query("SELECT * FROM TranscriptEntry ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun latest(): TranscriptEntry?

    @Query("SELECT * FROM TranscriptEntry WHERE id = :id")
    fun entry(id: Long): Flow<TranscriptEntry?>

    @Query("SELECT * FROM TranscriptEntry WHERE audioFingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): TranscriptEntry?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: TranscriptEntry): Long

    @Query("UPDATE TranscriptEntry SET formattedText = :formattedText, summary = :summary, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFormattedText(id: Long, formattedText: String, summary: String, updatedAt: Long)

    @Query("UPDATE TranscriptEntry SET displayName = :displayName, rawText = :rawText, formattedText = '', summary = '', updatedAt = :updatedAt WHERE id = :id")
    suspend fun replaceTranscription(id: Long, displayName: String, rawText: String, updatedAt: Long)

    @Query("DELETE FROM TranscriptEntry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM TranscriptEntry")
    suspend fun clear()
}

@Database(entities = [TranscriptEntry::class], version = 2, exportSchema = false)
abstract class TranscriptHistoryDatabase : RoomDatabase() {
    abstract fun transcriptHistoryDao(): TranscriptHistoryDao

    companion object {
        fun create(context: Context): TranscriptHistoryDatabase = Room.databaseBuilder(
            context.applicationContext,
            TranscriptHistoryDatabase::class.java,
            "transcript-history.db"
        ).addMigrations(MIGRATION_1_2).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE TranscriptEntry ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE TranscriptEntry ADD COLUMN sourceOrigin TEXT NOT NULL DEFAULT 'Audio'")
            }
        }
    }
}

class TranscriptHistoryRepository(private val dao: TranscriptHistoryDao) {
    val entries: Flow<List<TranscriptEntry>> = dao.entries()

    fun entry(id: Long) = dao.entry(id)

    suspend fun latest() = dao.latest()

    suspend fun find(fingerprint: String) = dao.findByFingerprint(fingerprint)

    suspend fun saveRaw(fingerprint: String, displayName: String, rawText: String, sourceOrigin: String, importedAt: Long): TranscriptEntry {
        val entry = TranscriptEntry(
            audioFingerprint = fingerprint,
            displayName = displayName,
            rawText = rawText,
            sourceOrigin = sourceOrigin,
            createdAt = importedAt,
            updatedAt = importedAt
        )
        return entry.copy(id = dao.insert(entry))
    }

    suspend fun saveFormatted(id: Long, formattedText: String, summary: String) =
        dao.updateFormattedText(id, formattedText, summary, System.currentTimeMillis())

    suspend fun replaceTranscription(id: Long, displayName: String, rawText: String) =
        dao.replaceTranscription(id, displayName, rawText, System.currentTimeMillis())

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()
}
