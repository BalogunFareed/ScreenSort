package com.screensort.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_items")
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val sourceApp: String? = null,
    val authorName: String? = null,
    val postDate: String? = null,
    val ocrText: String = "",
    val link: String? = null,
    val note: String = "",
    val savedAt: Long = System.currentTimeMillis(),
    val remindAt: Long? = null
)

@Dao
interface SavedItemDao {
    @Insert
    suspend fun insert(item: SavedItem): Long

    @Update
    suspend fun update(item: SavedItem)

    @Delete
    suspend fun delete(item: SavedItem)

    @Query("SELECT * FROM saved_items ORDER BY savedAt DESC")
    fun getAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_items WHERE id = :id")
    suspend fun getById(id: Long): SavedItem?

    @Query("SELECT * FROM saved_items WHERE remindAt IS NOT NULL AND remindAt > :now")
    suspend fun getUpcomingReminders(now: Long): List<SavedItem>
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_items ADD COLUMN remindAt INTEGER")
    }
}

@Database(entities = [SavedItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedItemDao(): SavedItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "screensort.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}