package com.example.lifesaiver.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.lifesaiver.core.database.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: ProfileEntity): Long

    @Query(
        """
        UPDATE profiles
        SET name = :name,
            gender = :gender,
            birthDate = :birthDate,
            notes = :notes,
            updatedAt = :updatedAt,
            sourcePeerId = :sourcePeerId,
            lastSeenAt = :lastSeenAt
        WHERE peerId = :peerId
          AND updatedAt < :updatedAt
        """
    )
    suspend fun updateIfNewer(
        peerId: String,
        name: String,
        gender: String,
        birthDate: String,
        notes: String,
        updatedAt: Long,
        sourcePeerId: String,
        lastSeenAt: Long
    ): Int

    @Query("SELECT * FROM profiles ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE peerId = :peerId LIMIT 1")
    suspend fun getByPeerId(peerId: String): ProfileEntity?

    @Transaction
    suspend fun upsertIfNewer(profile: ProfileEntity): Boolean {
        val inserted = insert(profile)
        if (inserted != -1L) return true
        val updated = updateIfNewer(
            peerId = profile.peerId,
            name = profile.name,
            gender = profile.gender,
            birthDate = profile.birthDate,
            notes = profile.notes,
            updatedAt = profile.updatedAt,
            sourcePeerId = profile.sourcePeerId,
            lastSeenAt = profile.lastSeenAt
        )
        return updated > 0
    }
}
