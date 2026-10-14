package de.joelneumann.lojinha.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Ensures only a single instance of Lojinha runs at any given time.
 *
 * Uses OS-level file locking (FileChannel.tryLock()) which is enforced by the operating system kernel.
 * If the process crashes or is terminated (e.g. via Task Manager or kill -9), the OS kernel
 * automatically and immediately releases the lock, avoiding stale lock file issues.
 *
 * The lock file itself is never deleted on exit, which prevents Windows sharing-violation issues.
 */
object SingleInstanceLock {
    private const val TAG = "SingleInstanceLock"

    @Volatile
    private var randomAccessFile: RandomAccessFile? = null

    @Volatile
    private var fileChannel: FileChannel? = null

    @Volatile
    private var fileLock: FileLock? = null

    @Volatile
    private var shutdownHookRegistered = false

    /**
     * Attempts to acquire an exclusive OS-level lock on the application's lock file.
     *
     * @param lockFile Target lock file (defaults to `~/.lojinha/.lojinha.lock`).
     * @return `true` if the lock was successfully acquired by this process; `false` if another instance holds it.
     */
    fun acquire(lockFile: File = getDefaultLockFile()): Boolean {
        synchronized(this) {
            if (fileLock?.isValid == true) {
                AppLogger.debug(TAG, "Single-instance lock is already held by this process.")
                return true
            }

            return try {
                val parentDir = lockFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }

                val raf = RandomAccessFile(lockFile, "rw")
                val channel = raf.channel
                val lock = channel.tryLock()

                if (lock != null) {
                    randomAccessFile = raf
                    fileChannel = channel
                    fileLock = lock

                    if (!shutdownHookRegistered) {
                        try {
                            Runtime.getRuntime().addShutdownHook(Thread({
                                release()
                            }, "SingleInstanceLock-ShutdownHook"))
                            shutdownHookRegistered = true
                        } catch (_: Exception) {
                            // Ignored if runtime is already shutting down
                        }
                    }

                    AppLogger.info(TAG, "Single-instance lock acquired successfully on ${lockFile.absolutePath}")
                    true
                } else {
                    AppLogger.warn(TAG, "Another instance of Lojinha is already running (lock held on ${lockFile.absolutePath}).")
                    closeQuietly(lock, channel, raf)
                    false
                }
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Could not acquire single-instance lock (${e.message}). Another instance may be starting up.")
                closeQuietly(null, null, null)
                false
            }
        }
    }

    /**
     * Releases the acquired OS-level lock and closes file handles.
     * We deliberately do NOT delete the lock file, preventing Windows sharing violations.
     */
    fun release() {
        synchronized(this) {
            closeQuietly(fileLock, fileChannel, randomAccessFile)
            fileLock = null
            fileChannel = null
            randomAccessFile = null
            AppLogger.info(TAG, "Single-instance lock released.")
        }
    }

    /**
     * Returns true if the lock is currently held by this process.
     */
    fun isLocked(): Boolean {
        return fileLock?.isValid == true
    }

    /**
     * Default lock file location in user home directory.
     */
    fun getDefaultLockFile(): File {
        return File(System.getProperty("user.home"), ".lojinha/.lojinha.lock")
    }

    private fun closeQuietly(lock: FileLock?, channel: FileChannel?, raf: RandomAccessFile?) {
        try {
            if (lock != null && lock.isValid) {
                lock.release()
            }
        } catch (_: Exception) {}
        try {
            channel?.close()
        } catch (_: Exception) {}
        try {
            raf?.close()
        } catch (_: Exception) {}
    }
}
