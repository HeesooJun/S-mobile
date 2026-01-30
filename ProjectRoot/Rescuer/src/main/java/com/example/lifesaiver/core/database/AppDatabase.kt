package com.example.lifesaiver.core.database // 패키지명은 실제 프로젝트에 맞게 수정해주세요

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.lifesaiver.core.database.converter.DateConverter
import com.example.lifesaiver.core.database.dao.MessageDao
import com.example.lifesaiver.core.database.dao.ProfileDao
import com.example.lifesaiver.core.database.entity.MessageEntity
import com.example.lifesaiver.core.database.entity.ProfileEntity

/**
 * entities: DB에 포함될 테이블(Entity)들을 배열로 명시합니다.
 * version: DB 스키마가 변경될 때마다 숫자를 올려야 합니다.
 * exportSchema: 빌드 시 스키마 정보를 파일로 뽑아낼지 여부입니다. (보통 false로 둡니다)
 */
@Database(
    entities = [
        MessageEntity::class,
        ProfileEntity::class,
        // 추가되는 Entity는 여기에 콤마(,)로 구분하여 계속 추가
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DateConverter::class) // 날짜 변환기 등이 필요할 때 추가
abstract class AppDatabase : RoomDatabase() {

    // DAO를 가져올 수 있는 추상 함수 선언 (Room이 구현체를 만들어줍니다)
    abstract fun messageDao(): MessageDao
    abstract fun profileDao(): ProfileDao

    companion object {
        // Volatile: 변수 값의 변경을 모든 스레드가 즉시 알 수 있게 함
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // 여러 스레드에서 동시에 접근할 때 중복 생성을 막기 위한 동기화 블록
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app-database.db" // 생성될 DB 파일의 이름
            )
                .addMigrations(MIGRATION_1_2)
                // .fallbackToDestructiveMigration() // 개발 초기에는 버전 변경 시 기존 데이터 날리고 새로 만드는 이 옵션이 편할 수 있음
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS profiles (
                        peerId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        gender TEXT NOT NULL,
                        birthDate TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        sourcePeerId TEXT NOT NULL,
                        lastSeenAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
