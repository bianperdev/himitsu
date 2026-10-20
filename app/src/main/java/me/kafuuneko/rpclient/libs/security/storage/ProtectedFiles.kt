// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.storage

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import me.kafuuneko.rpclient.libs.room.repository.FILE_REPOSITORY_DIR_NAME
import me.kafuuneko.rpclient.libs.security.database.EncryptedDatabase

/** Name of the wrapped master key file inside `noBackupFilesDir`. */
internal const val MASTER_KEY_FILE_NAME = "himitsu_master.key"

/** Why a set of files was moved aside. */
enum class SetAsideKind(val marker: String) {
    /** "Start over" after the key was lost: encrypted with a key this app no longer has. */
    Unrecoverable("unrecoverable"),

    /** A plain database from an earlier build that could not be encrypted. Not encrypted. */
    Unusable("unusable"),

    /** The data that was in use before a set-aside copy was restored over it. */
    Replaced("replaced");

    companion object {
        fun fromMarker(marker: String): SetAsideKind? = entries.firstOrNull { it.marker == marker }
    }
}

/**
 * Files moved aside together, sharing one [suffix] such as `.unrecoverable-20261020-122641`.
 *
 * @property time when they were moved aside, in milliseconds, or null when unknown
 * @property entries the top-level files and directories, in their storage locations
 * @property hasKeyFile whether the set kept its key file, without which it can never be opened
 */
data class SetAside(
    val suffix: String,
    val kind: SetAsideKind,
    val time: Long?,
    val entries: List<File>,
    val sizeBytes: Long,
    val hasKeyFile: Boolean
)

/**
 * Where Himitsu keeps its protected data, and the sets of it that were moved aside.
 *
 * Nothing here deletes data on its own: [delete] runs only after the user confirmed it.
 */
class ProtectedFiles(private val mContext: Context) {
    private val mDatabaseDir: File get() = mContext.getDatabasePath(EncryptedDatabase.Primary.fileName).parentFile!!
    private val mKeyDir: File get() = mContext.noBackupFilesDir
    private val mPreferencesDir: File get() = File(mContext.applicationInfo.dataDir, "shared_prefs")
    private val mDataDir: File get() = File(mContext.applicationInfo.dataDir)
    private val mRepositoryName: String get() = "app_$FILE_REPOSITORY_DIR_NAME"

    /** The data in use now, as export entries; files moved aside are left out. */
    fun current(): List<ExportEntry> {
        val databases = EncryptedDatabase.entries.map { it.fileName }
        return buildList {
            mDatabaseDir.listFiles().orEmpty()
                .filter { file -> file.isFile && !isSetAside(file.name) && databases.any { file.name.startsWith(it) } }
                .forEach { add(ExportEntry("databases/${it.name}", it)) }
            File(mKeyDir, MASTER_KEY_FILE_NAME).takeIf(File::isFile)?.let { add(ExportEntry("no_backup/${it.name}", it)) }
            mPreferencesDir.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".xml") }
                .forEach { add(ExportEntry("shared_prefs/${it.name}", it)) }
            addTree(File(mDataDir, mRepositoryName), mRepositoryName)
        }
    }

    /** Every set moved aside, newest first. */
    fun setAside(): List<SetAside> {
        val found = linkedMapOf<String, MutableList<File>>()
        listOf(mDatabaseDir, mKeyDir, mPreferencesDir, mDataDir).forEach { directory ->
            directory.listFiles().orEmpty().forEach { file ->
                val suffix = MARKER.find(file.name)?.value ?: return@forEach
                // In the data directory only the stored files directory is ours
                if (directory == mDataDir && !file.name.startsWith(mRepositoryName)) return@forEach
                found.getOrPut(suffix) { mutableListOf() }.add(file)
            }
        }
        return found.mapNotNull { (suffix, entries) ->
            val match = MARKER.matchEntire(suffix) ?: return@mapNotNull null
            val kind = SetAsideKind.fromMarker(match.groupValues[1]) ?: return@mapNotNull null
            SetAside(
                suffix = suffix,
                kind = kind,
                time = runCatching { TIME_FORMAT.parse(match.groupValues[2])?.time }.getOrNull(),
                entries = entries,
                sizeBytes = entries.sumOf { entry -> entry.walk().filter(File::isFile).sumOf(File::length) },
                hasKeyFile = entries.any { it.parentFile == mKeyDir && it.name.startsWith(MASTER_KEY_FILE_NAME) }
            )
        }.sortedByDescending { it.time ?: 0L }
    }

    /** Export entries of [set]. */
    fun entriesOf(set: SetAside): List<ExportEntry> = buildList {
        set.entries.forEach { entry ->
            when (entry.parentFile) {
                mDatabaseDir -> add(ExportEntry("databases/${entry.name}", entry))
                mKeyDir -> add(ExportEntry("no_backup/${entry.name}", entry))
                mPreferencesDir -> add(ExportEntry("shared_prefs/${entry.name}", entry))
                else -> addTree(entry, entry.name)
            }
        }
    }

    /**
     * Deletes every file of [set], after the user confirmed it. This cannot be undone.
     *
     * @return whether nothing of the set is left
     */
    fun delete(set: SetAside): Boolean = set.entries.map { it.deleteRecursively() }.all { it }

    private fun MutableList<ExportEntry>.addTree(root: File, prefix: String) {
        if (!root.isDirectory) return
        root.walk().filter(File::isFile).forEach { file ->
            add(ExportEntry("$prefix/" + file.relativeTo(root).invariantSeparatorsPath, file))
        }
    }

    private fun isSetAside(name: String): Boolean = MARKER.containsMatchIn(name)

    companion object {
        /** Suffix that "start over", "set aside" and "restore" append, with its time. */
        private val MARKER = Regex("""\.(unrecoverable|unusable|replaced)-(\d{8}-\d{6})""")
        private val TIME_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

        /** A new suffix for files moved aside now. */
        fun newSuffix(kind: SetAsideKind, now: Long = System.currentTimeMillis()): String =
            ".${kind.marker}-" + TIME_FORMAT.format(now)
    }
}

/** One file of an export, at [path] inside the zip. */
class ExportEntry(val path: String, val file: File) {
    override fun toString(): String = "ExportEntry([REDACTED])"
}

/**
 * Writes protected files into a zip, byte for byte as they are on disk: encrypted files stay
 * encrypted, and only the recovery code of that data can ever open them again.
 */
object EncryptedExport {
    /**
     * Writes [entries] to [output], which the caller closes. Files that vanish meanwhile are
     * skipped.
     *
     * @throws IOException when a file cannot be read or the output written
     */
    fun write(entries: List<ExportEntry>, output: OutputStream, checkActive: () -> Unit = {}) {
        val zip = ZipOutputStream(BufferedOutputStream(output))
        // Encrypted content does not compress; a fast level keeps large exports quick
        zip.setLevel(Deflater.BEST_SPEED)
        entries.forEach { entry ->
            checkActive()
            if (!entry.file.isFile) return@forEach
            zip.putNextEntry(ZipEntry(entry.path).apply { time = entry.file.lastModified() })
            entry.file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }
}
