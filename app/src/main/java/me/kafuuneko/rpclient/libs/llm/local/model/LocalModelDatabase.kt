// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.llm.local.model

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabase

/**
 * A model file stored on this device.
 *
 * The file itself is named after [id], so names, origins and hashes, which can say something
 * about the user, live only in this table and never in file names.
 */
@Entity(tableName = "local_models")
data class LocalModelEntity(
    /** Random UUID; also the file name and the value stored in a provider's `model` field. */
    @PrimaryKey val id: String,
    /** Name shown to the user, taken from the GGUF header or the imported file name. */
    val displayName: String,
    /** Size of the stored file in bytes. */
    val sizeBytes: Long,
    /** Lowercase hex SHA-256 of the stored file. */
    val sha256: String,
    /** `general.architecture` from the GGUF header, for example "llama"; empty when missing. */
    val architecture: String,
    /** Context length the model was trained with; 0 when unknown. */
    val trainedContextTokens: Int,
    /** Whether the GGUF bundles a chat template. */
    val hasChatTemplate: Boolean,
    /** llama.cpp quantization id from `general.file_type`; -1 when missing. */
    val fileType: Int,
    /** How the file arrived. */
    val source: LocalModelSource,
    /** Creation time in milliseconds. */
    val createTime: Long
)

/** How a model file arrived on the device. The name is persisted; do not rename members. */
enum class LocalModelSource {
    /** Picked by the user through the Storage Access Framework. */
    Imported,

    /** Downloaded on the user's explicit request. */
    Downloaded
}

/** Access to the local model registry. */
@Dao
interface LocalModelDao {
    @Query("SELECT * FROM local_models ORDER BY createTime DESC")
    suspend fun getAll(): List<LocalModelEntity>

    @Query("SELECT * FROM local_models WHERE id = :id")
    suspend fun getById(id: String): LocalModelEntity?

    @Insert
    suspend fun insert(model: LocalModelEntity)

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteById(id: String)
}

/**
 * Registry of local models, kept apart from the main database.
 *
 * - Encrypted with SQLCipher like the other databases ([EncryptedDatabase.LocalModels]); Android
 *   backups exclude it like every other file.
 * - Keeping it separate means this track never bumps the main schema version.
 */
@Database(entities = [LocalModelEntity::class], version = 1, exportSchema = true)
abstract class LocalModelDatabase : RoomDatabase() {
    abstract fun localModelDao(): LocalModelDao

    companion object {
        const val FILE_NAME = "local_models.sqlite"
    }
}
