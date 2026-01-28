package com.example.lifesaiver.core.database.converter

import androidx.room.TypeConverter
import java.util.Date

class DateConverter {
    // DB에서 데이터를 가져올 때: Long -> Date 변환
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    // DB에 데이터를 저장할 때: Date -> Long 변환
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
