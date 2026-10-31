package r3.io

import java.io.Closeable
import java.io.File
import java.nio.file.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Monitors [dir] for files/directories using both OS [WatchService] events (for immediate reaction)
 * and periodic manual polling every [pollIntervalMs] (for robustness against missed OS events).
 *
 * All entries in [dir] matching [filter] (ignoring hidden/temporary files starting with `.` or `_`
 * or ending with `.tmp`/`.part`) are passed to [handle] once while they remain in [dir].
 * When processed entries are moved or deleted from [dir], their tracking state is automatically cleared.
 */
class DirectoryWatcher(
   val dir: File,
   val pollIntervalMs: Long = 5_000L,
   val isDaemon: Boolean = false,
   val filter: (File) -> Boolean = { true },
   val handle: (File) -> Unit
) : Closeable {
   @Volatile
   var running = true
      private set

   private val seen = HashMap<String, Long>()
   private val comparator = NaturalFileComparator()
   private var watchService: WatchService? = null

   val watchThread: Thread = thread(
      start = true,
      isDaemon = isDaemon,
      name = "DirectoryWatcher-${dir.name}"
   ) {
      if (!dir.exists()) {
         dir.mkdirs()
      }

      val ws = try {
         FileSystems.getDefault().newWatchService().also { service ->
            dir.toPath().register(
               service,
               StandardWatchEventKinds.ENTRY_CREATE,
               StandardWatchEventKinds.ENTRY_MODIFY
            )
         }
      } catch (e: Exception) {
         log("DirectoryWatcher: WatchService unavailable for $dir, falling back to polling: ${e.message}")
         null
      }
      watchService = ws

      try {
         sweep()
         while (running && !Thread.currentThread().isInterrupted) {
            try {
               if (ws != null) {
                  val key: WatchKey? = ws.poll(pollIntervalMs, TimeUnit.MILLISECONDS)
                  if (key != null) {
                     key.pollEvents()
                     key.reset()
                  }
               } else {
                  Thread.sleep(pollIntervalMs)
               }
            } catch (_: InterruptedException) {
               Thread.currentThread().interrupt()
               break
            } catch (_: ClosedWatchServiceException) {
               break
            }
            if (running) {
               sweep()
            }
         }
      } finally {
         runCatching { ws?.close() }
      }
   }

   private fun isIgnoredTempOrHidden(f: File): Boolean {
      val name = f.name
      return f.isHidden ||
         name.startsWith(".") ||
         name.startsWith("_") ||
         name.endsWith(".tmp", ignoreCase = true) ||
         name.endsWith(".part", ignoreCase = true)
   }

   @Synchronized
   fun sweep() {
      val currentFiles = dir.listFiles() ?: return
      val currentPaths = HashSet<String>(currentFiles.size)
      for (f in currentFiles) {
         currentPaths.add(f.absolutePath)
      }
      // Remove tracking for files that have been deleted or moved out of dir
      seen.keys.retainAll(currentPaths)

      val sorted = currentFiles.sortedWith(comparator)
      for (file in sorted) {
         if (!running) break
         if (!file.exists() || isIgnoredTempOrHidden(file)) continue

         val path = file.absolutePath
         val lastMod = file.lastModified()
         if (seen[path] == lastMod) continue

         val accepted = try {
            filter(file)
         } catch (e: Exception) {
            log("DirectoryWatcher filter error for $file: ${e.message}")
            false
         }
         if (!accepted) continue

         seen[path] = lastMod
         try {
            handle(file)
         } catch (e: Exception) {
            log("DirectoryWatcher handle error for $file: ${e.message}")
         }
      }
   }

   override fun close() {
      running = false
      runCatching { watchService?.close() }
      watchThread.interrupt()
   }
}

