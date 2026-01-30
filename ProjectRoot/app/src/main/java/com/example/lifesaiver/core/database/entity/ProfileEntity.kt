package com.example.lifesaiver.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val peerId: String,
    val name: String,
    val gender: String,
    val birthDate: String,
    val notes: String,
    val updatedAt: Long,
    val sourcePeerId: String,
    val lastSeenAt: Long
)
