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
import androidx.room.ForeignKey
import androidx.room.Transaction
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

const val PREVIOUS_RESULT_PROMPT_ID = "__previous_result__"

@Entity(
    primaryKeys = ["entryId", "promptId"],
    foreignKeys = [ForeignKey(
        entity = TranscriptEntry::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("entryId")]
)
data class TranscriptPromptResult(
    val entryId: Long,
    val promptId: String,
    val promptName: String,
    val formattedText: String,
    val summary: String,
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

    @Query("DELETE FROM TranscriptPromptResult WHERE entryId = :id")
    suspend fun clearResults(id: Long)

    @Query("DELETE FROM TranscriptEntry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM TranscriptEntry")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveResult(result: TranscriptPromptResult)

    @Query("SELECT * FROM TranscriptPromptResult WHERE entryId = :entryId ORDER BY updatedAt DESC, rowid DESC")
    fun results(entryId: Long): Flow<List<TranscriptPromptResult>>

    @Query("DELETE FROM TranscriptPromptResult WHERE entryId = :entryId AND promptId = :promptId")
    suspend fun deleteResult(entryId: Long, promptId: String): Int

    @Query("SELECT * FROM TranscriptPromptResult WHERE entryId = :entryId ORDER BY updatedAt DESC, rowid DESC LIMIT 1")
    suspend fun latestResult(entryId: Long): TranscriptPromptResult?

    @Transaction
    suspend fun replaceRawAndClearResults(id: Long, displayName: String, rawText: String, updatedAt: Long) {
        clearResults(id)
        replaceTranscription(id, displayName, rawText, updatedAt)
    }

    @Transaction
    suspend fun deleteResultAndProjectLatest(entryId: Long, promptId: String) {
        if (deleteResult(entryId, promptId) == 0) return
        val latest = latestResult(entryId)
        updateFormattedProjection(entryId, latest?.formattedText.orEmpty(), latest?.summary.orEmpty())
    }

    @Query("UPDATE TranscriptEntry SET formattedText = :formattedText, summary = :summary WHERE id = :id")
    suspend fun updateFormattedProjection(id: Long, formattedText: String, summary: String)

    @Transaction
    suspend fun saveResultAndProject(result: TranscriptPromptResult) {
        saveResult(result)
        updateFormattedText(result.entryId, result.formattedText, result.summary, result.updatedAt)
    }
}

@Database(entities = [TranscriptEntry::class, TranscriptPromptResult::class], version = 3, exportSchema = false)
abstract class TranscriptHistoryDatabase : RoomDatabase() {
    abstract fun transcriptHistoryDao(): TranscriptHistoryDao

    companion object {
        fun create(context: Context): TranscriptHistoryDatabase = Room.databaseBuilder(
            context.applicationContext,
            TranscriptHistoryDatabase::class.java,
            "transcript-history.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE TranscriptEntry ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE TranscriptEntry ADD COLUMN sourceOrigin TEXT NOT NULL DEFAULT 'Audio'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS TranscriptPromptResult (entryId INTEGER NOT NULL, promptId TEXT NOT NULL, promptName TEXT NOT NULL, formattedText TEXT NOT NULL, summary TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(entryId, promptId), FOREIGN KEY(entryId) REFERENCES TranscriptEntry(id) ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_TranscriptPromptResult_entryId ON TranscriptPromptResult(entryId)")
                db.execSQL("INSERT INTO TranscriptPromptResult (entryId, promptId, promptName, formattedText, summary, updatedAt) SELECT id, '$PREVIOUS_RESULT_PROMPT_ID', 'Previous result', formattedText, summary, updatedAt FROM TranscriptEntry WHERE formattedText != ''")
            }
        }
    }
}

class TranscriptHistoryRepository(private val dao: TranscriptHistoryDao) {
    val entries: Flow<List<TranscriptEntry>> = dao.entries()

    fun entry(id: Long) = dao.entry(id)
    fun results(entryId: Long): Flow<List<TranscriptPromptResult>> = dao.results(entryId)

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
        savePromptResult(id, PREVIOUS_RESULT_PROMPT_ID, "Previous result", formattedText, summary)

    suspend fun savePromptResult(id: Long, promptId: String, promptName: String, formattedText: String, summary: String) {
        val now = System.currentTimeMillis()
        dao.saveResultAndProject(TranscriptPromptResult(id, promptId, promptName, formattedText, summary, now))
    }

    suspend fun replaceTranscription(id: Long, displayName: String, rawText: String) {
        dao.replaceRawAndClearResults(id, displayName, rawText, System.currentTimeMillis())
    }

    suspend fun deletePromptResult(entryId: Long, promptId: String) {
        dao.deleteResultAndProjectLatest(entryId, promptId)
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear() = dao.clear()
}
