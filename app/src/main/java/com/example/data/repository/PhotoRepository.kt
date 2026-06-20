package com.example.data.repository

import com.example.data.database.PhotoDao
import com.example.data.database.PresetDao
import com.example.data.model.EditedPhoto
import com.example.data.model.UserPreset
import kotlinx.coroutines.flow.Flow

class PhotoRepository(
    private val photoDao: PhotoDao,
    private val presetDao: PresetDao
) {
    val allPhotos: Flow<List<EditedPhoto>> = photoDao.getAllPhotosFlow()
    val allPresets: Flow<List<UserPreset>> = presetDao.getAllPresetsFlow()

    suspend fun getPhotoById(id: Int): EditedPhoto? {
        return photoDao.getPhotoById(id)
    }

    suspend fun insertPhoto(photo: EditedPhoto): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun updatePhoto(photo: EditedPhoto) {
        photoDao.updatePhoto(photo)
    }

    suspend fun deletePhoto(photo: EditedPhoto) {
        photoDao.deletePhoto(photo)
    }

    suspend fun insertPreset(preset: UserPreset): Long {
        return presetDao.insertPreset(preset)
    }

    suspend fun deletePreset(preset: UserPreset) {
        presetDao.deletePreset(preset)
    }
}
