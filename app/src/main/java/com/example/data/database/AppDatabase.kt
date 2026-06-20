package com.example.data.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.EditedPhoto
import com.example.data.model.UserPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM edited_photos ORDER BY lastModifiedAt DESC")
    fun getAllPhotosFlow(): Flow<List<EditedPhoto>>

    @Query("SELECT * FROM edited_photos WHERE id = :id")
    suspend fun getPhotoById(id: Int): EditedPhoto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: EditedPhoto): Long

    @Update
    suspend fun updatePhoto(photo: EditedPhoto)

    @Delete
    suspend fun deletePhoto(photo: EditedPhoto)
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM user_presets ORDER BY createdAt DESC")
    fun getAllPresetsFlow(): Flow<List<UserPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: UserPreset): Long

    @Delete
    suspend fun deletePreset(preset: UserPreset)
}

@Database(entities = [EditedPhoto::class, UserPreset::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun presetDao(): PresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumina_photo_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
