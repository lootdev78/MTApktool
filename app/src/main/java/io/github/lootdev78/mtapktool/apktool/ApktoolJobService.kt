package io.github.lootdev78.mtapktool.apktool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.github.lootdev78.mtapktool.MainActivity
import io.github.lootdev78.mtapktool.R
import io.github.apktool.android.runtime.ApktoolCommandRunner
import io.github.apktool.android.runtime.Toolchain
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

class ApktoolJobService : Service() {
    companion object {
        const val ACTION_ENQUEUE = "io.github.lootdev78.mtapktool.apktool.ENQUEUE"
        const val ACTION_CANCEL = "io.github.lootdev78.mtapktool.apktool.CANCEL"
        const val ACTION_CANCEL_ALL = "io.github.lootdev78.mtapktool.apktool.CANCEL_ALL"
        const val ACTION_QUERY = "io.github.lootdev78.mtapktool.apktool.QUERY"
        const val ACTION_SET_WORKERS = "io.github.lootdev78.mtapktool.apktool.SET_WORKERS"
        const val ACTION_STATUS = "io.github.lootdev78.mtapktool.apktool.STATUS"

        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_STATUS = "status"
        const val EXTRA_LINE = "line"
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_CREATED_AT = "created_at"
        const val EXTRA_POST_ALIGN = "post_align"
        const val EXTRA_POST_SIGN = "post_sign"
        const val EXTRA_WORKERS = "workers"

        private const val EXTRA_SIGN_KEYSTORE = "sign_keystore"
        private const val EXTRA_SIGN_PASSWORD = "sign_password"
        private const val EXTRA_SIGN_V1 = "sign_v1"
        private const val EXTRA_SIGN_V2 = "sign_v2"
        private const val EXTRA_SIGN_V3 = "sign_v3"
        private const val EXTRA_SIGN_V4 = "sign_v4"
        private const val EXTRA_CLEAN_PROJECT = "clean_project"
        private const val EXTRA_POST_DECODE_ROOT = "post_decode_root"
        private const val EXTRA_CREATE_NOMEDIA = "create_nomedia"
        private const val EXTRA_REMOVE_SPLIT = "remove_split"
        private const val EXTRA_REMOVE_PROPERTY = "remove_property"

        private const val CHANNEL = "mtapktool_jobs"
        private const val DONE_CHANNEL = "mtapktool_done"
        private const val NOTIFICATION_ID = 2317
        private const val JOB_NOTIFICATION_BASE = 5000
        private const val JOB_PREFS = "mtapktool_job_store"
        private const val KEY_RECORDS = "records_json"
        private const val MAX_STORED_JOBS = 50

        @Volatile
        private var appVisible = false

        fun setAppVisible(visible: Boolean) {
            appVisible = visible
        }

        fun enqueue(
            context: Context,
            title: String,
            command: String,
            postAlign: Boolean = false,
            postSign: Boolean = false,
            signature: ApktoolSignatureDefaults? = null,
            cleanBuildProject: String? = null,
            postDecodeRoot: String? = null,
            createNomedia: Boolean = false,
            removeSplitTraces: Boolean = false,
            removePropertyTags: Boolean = false,
        ): String {
            val id = UUID.randomUUID().toString()
            val sign = signature ?: ApktoolSignatureDefaults(v3 = true)
            val intent = Intent(context, ApktoolJobService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_JOB_ID, id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_COMMAND, command)
                putExtra(EXTRA_POST_ALIGN, postAlign)
                putExtra(EXTRA_POST_SIGN, postSign)
                putExtra(EXTRA_SIGN_KEYSTORE, if (sign.profile == "custom") sign.customKeystorePath else "")
                putExtra(EXTRA_SIGN_PASSWORD, if (sign.profile == "custom") sign.customKeystorePassword else "android")
                putExtra(EXTRA_SIGN_V1, sign.v1)
                putExtra(EXTRA_SIGN_V2, sign.v2)
                putExtra(EXTRA_SIGN_V3, sign.v3)
                putExtra(EXTRA_SIGN_V4, sign.v4)
                putExtra(EXTRA_CLEAN_PROJECT, cleanBuildProject)
                putExtra(EXTRA_POST_DECODE_ROOT, postDecodeRoot)
                putExtra(EXTRA_CREATE_NOMEDIA, createNomedia)
                putExtra(EXTRA_REMOVE_SPLIT, removeSplitTraces)
                putExtra(EXTRA_REMOVE_PROPERTY, removePropertyTags)
            }
            ContextCompat.startForegroundService(context, intent)
            return id
        }

        fun cancel(context: Context, id: String) {
            runCatching {
                context.startService(Intent(context, ApktoolJobService::class.java).apply {
                    action = ACTION_CANCEL
                    putExtra(EXTRA_JOB_ID, id)
                })
            }
        }

        fun cancelAll(context: Context) {
            runCatching { context.startService(Intent(context, ApktoolJobService::class.java).setAction(ACTION_CANCEL_ALL)) }
        }

        fun query(context: Context) {
            runCatching { context.startService(Intent(context, ApktoolJobService::class.java).setAction(ACTION_QUERY)) }
        }

        fun setWorkerLimit(context: Context, workers: Int) {
            runCatching {
                context.startService(Intent(context, ApktoolJobService::class.java).apply {
                    action = ACTION_SET_WORKERS
                    putExtra(EXTRA_WORKERS, workers.coerceIn(1, 4))
                })
            }
        }
    }

    private enum class Status { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    private data class Record(
        val id: String,
        val title: String,
        val command: String,
        val postAlign: Boolean,
        val postSign: Boolean,
        val signKeystore: String,
        val signPassword: String,
        val signV1: Boolean,
        val signV2: Boolean,
        val signV3: Boolean,
        val signV4: Boolean,
        val cleanBuildProject: String?,
        val postDecodeRoot: String?,
        val createNomedia: Boolean,
        val removeSplitTraces: Boolean,
        val removePropertyTags: Boolean,
        val createdAt: Long = System.currentTimeMillis(),
        @Volatile var status: Status = Status.QUEUED,
        @Volatile var line: String = "Queued",
        @Volatile var output: String? = null,
        @Volatile var cancelRequested: Boolean = false,
        @Volatile var future: Future<*>? = null,
        @Volatile var lastNotificationAt: Long = 0L,
    )

    private val records = ConcurrentHashMap<String, Record>()
    private val persistLock = Any()
    @Volatile private var lastPersistAt = 0L
    private lateinit var executor: ThreadPoolExecutor

    override fun onCreate() {
        super.onCreate()
        createChannels()
        restoreRecords()
        val n = ApktoolSettings.maxWorkers(this)
        executor = ThreadPoolExecutor(n, n, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable ->
            Thread(runnable, "mtapktool-worker").apply { priority = Thread.NORM_PRIORITY - 1 }
        }
        executor.allowCoreThreadTimeOut(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENQUEUE -> enqueueInternal(intent, replaceExisting = flags and START_FLAG_REDELIVERY != 0)
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_JOB_ID)?.let(::cancelInternal)
            ACTION_CANCEL_ALL -> {
                records.values.filter { !it.status.isTerminal() }.forEach { cancelInternal(it.id) }
                executor.purge()
            }
            ACTION_QUERY -> records.values.sortedBy { it.createdAt }.forEach(::broadcast)
            ACTION_SET_WORKERS -> resizePool(intent.getIntExtra(EXTRA_WORKERS, ApktoolSettings.maxWorkers(this)).coerceIn(1, 4))
        }
        updateForegroundState()
        return START_REDELIVER_INTENT
    }

    private fun enqueueInternal(intent: Intent, replaceExisting: Boolean = false) {
        val id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString()
        val command = intent.getStringExtra(EXTRA_COMMAND)?.trim().orEmpty()
        if (command.isEmpty()) return
        val previous = records[id]
        if (!replaceExisting && previous?.future?.isDone == false) return
        val record = Record(
            id = id,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Apktool" },
            command = command,
            postAlign = intent.getBooleanExtra(EXTRA_POST_ALIGN, false),
            postSign = intent.getBooleanExtra(EXTRA_POST_SIGN, false),
            signKeystore = intent.getStringExtra(EXTRA_SIGN_KEYSTORE).orEmpty(),
            signPassword = intent.getStringExtra(EXTRA_SIGN_PASSWORD).orEmpty(),
            signV1 = intent.getBooleanExtra(EXTRA_SIGN_V1, true),
            signV2 = intent.getBooleanExtra(EXTRA_SIGN_V2, true),
            signV3 = intent.getBooleanExtra(EXTRA_SIGN_V3, true),
            signV4 = intent.getBooleanExtra(EXTRA_SIGN_V4, false),
            cleanBuildProject = intent.getStringExtra(EXTRA_CLEAN_PROJECT),
            postDecodeRoot = intent.getStringExtra(EXTRA_POST_DECODE_ROOT),
            createNomedia = intent.getBooleanExtra(EXTRA_CREATE_NOMEDIA, false),
            removeSplitTraces = intent.getBooleanExtra(EXTRA_REMOVE_SPLIT, false),
            removePropertyTags = intent.getBooleanExtra(EXTRA_REMOVE_PROPERTY, false),
            createdAt = previous?.createdAt ?: System.currentTimeMillis(),
        )
        records[id] = record
        startForeground(NOTIFICATION_ID, notification("Queued: ${record.title}", true))
        persistRecords(force = true)
        broadcast(record)
        notifyJob(record, force = true)
        record.future = executor.submit { runJob(record) }
    }

    private fun runJob(record: Record) {
        if (record.cancelRequested || Thread.currentThread().isInterrupted) {
            finishCancelled(record)
            return
        }
        record.status = Status.RUNNING
        record.line = "Starting…"
        persistRecords(force = true)
        broadcast(record)
        notifyJob(record, force = true)
        updateForegroundState()

        var log: PrintWriter? = null
        try {
            val toolchain = Toolchain(this)
            toolchain.provision()
            checkCancelled(record)
            val logFile = File(toolchain.logsDir, "job-${stamp()}-${record.id.take(8)}.log")
            val writer = PrintWriter(FileWriter(logFile, true), true)
            log = writer
            writer.println("$ ${record.command}")

            val listener = ApktoolCommandRunner.Listener { line ->
                checkCancelled(record)
                record.line = line.takeLast(600)
                writer.println(line)
                broadcast(record)
                persistRecords()
                notifyJob(record)
            }
            val runner = ApktoolCommandRunner(toolchain, listener)
            var result = if (SplitArchiveSupport.isSplitDecodeCommand(record.command)) {
                SplitArchiveSupport.executeDecode(record.command, toolchain, listener)
            } else {
                runner.execute(record.command)
            }
            checkCancelled(record)

            if (result.isSuccess && !record.postDecodeRoot.isNullOrBlank()) {
                ProjectPostProcessor.process(
                    File(record.postDecodeRoot),
                    ProjectPostProcessor.Options(
                        createNomedia = record.createNomedia,
                        removeSplitTraces = record.removeSplitTraces,
                        removePropertyTags = record.removePropertyTags,
                    ),
                    listener,
                )
            }
            checkCancelled(record)

            if (result.isSuccess && (record.postAlign || record.postSign)) {
                result = runner.postProcessBuild(
                    result,
                    record.postAlign,
                    record.postSign,
                    record.signKeystore.takeIf { it.isNotBlank() },
                    record.signPassword.ifBlank { "android" },
                    record.signV1,
                    record.signV2,
                    record.signV3,
                    record.signV4,
                )
            }
            checkCancelled(record)

            if (result.isSuccess && !record.cleanBuildProject.isNullOrBlank()) {
                val project = File(record.cleanBuildProject)
                val buildDir = File(project, "build")
                if (buildDir.exists()) {
                    listener.onLine("I: Lösche Build-Ordner: ${buildDir.absolutePath}")
                    deleteRecursivelyCancellable(buildDir)
                }
            }

            record.output = result.output?.absolutePath
            record.status = if (result.isSuccess) Status.SUCCEEDED else Status.FAILED
            record.line = result.summary + (record.output?.let { "\n$it" } ?: "") + "\nLog: ${logFile.absolutePath}"
        } catch (cancelled: CancellationException) {
            finishCancelled(record, false)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            finishCancelled(record, false)
        } catch (t: Throwable) {
            if (record.cancelRequested || Thread.currentThread().isInterrupted || causedByInterruption(t)) {
                finishCancelled(record, false)
            } else {
                record.status = Status.FAILED
                record.line = stackMessage(t)
                log?.println(record.line)
            }
        } finally {
            log?.close()
            persistRecords(force = true)
            broadcast(record)
            getSystemService(NotificationManager::class.java).cancel(jobNotificationId(record.id))
            maybeNotifyCompletion(record)
            updateForegroundState()
            Thread.interrupted()
        }
    }

    private fun checkCancelled(record: Record) {
        if (record.cancelRequested || Thread.currentThread().isInterrupted) {
            throw CancellationException("Cancelled")
        }
    }

    private fun finishCancelled(record: Record, send: Boolean = true) {
        record.status = Status.CANCELLED
        record.line = "Cancelled"
        persistRecords(force = true)
        getSystemService(NotificationManager::class.java).cancel(jobNotificationId(record.id))
        if (send) broadcast(record)
    }

    private fun cancelInternal(id: String) {
        val record = records[id] ?: return
        if (record.status.isTerminal()) return
        record.cancelRequested = true
        val wasQueued = record.status == Status.QUEUED
        record.future?.cancel(true)
        executor.purge()
        if (wasQueued) finishCancelled(record)
        else {
            record.line = "Cancelling…"
            persistRecords(force = true)
            broadcast(record)
            notifyJob(record, force = true)
        }
    }

    private fun resizePool(workers: Int) {
        val n = workers.coerceIn(1, 4)
        if (n > executor.maximumPoolSize) {
            executor.maximumPoolSize = n
            executor.corePoolSize = n
        } else {
            executor.corePoolSize = n
            executor.maximumPoolSize = n
        }
        updateForegroundState()
    }

    private fun broadcast(record: Record) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_JOB_ID, record.id)
            putExtra(EXTRA_TITLE, record.title)
            putExtra(EXTRA_COMMAND, record.command)
            putExtra(EXTRA_STATUS, record.status.name)
            putExtra(EXTRA_LINE, record.line)
            putExtra(EXTRA_OUTPUT, record.output)
            putExtra(EXTRA_CREATED_AT, record.createdAt)
        })
    }

    private fun restoreRecords() {
        val raw = getSharedPreferences(JOB_PREFS, Context.MODE_PRIVATE).getString(KEY_RECORDS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val command = obj.optString("command")
                if (id.isBlank() || command.isBlank()) continue
                val stored = runCatching { Status.valueOf(obj.optString("status", Status.FAILED.name)) }
                    .getOrDefault(Status.FAILED)
                val terminal = stored.isTerminal()
                val restoredStatus = if (terminal) stored else Status.FAILED
                val storedLine = obj.optString("line", "")
                val restoredLine = if (terminal) storedLine else {
                    "Android hat den laufenden Prozess beendet. Falls der Foreground-Service redelivert wird, startet der Auftrag automatisch neu."
                }
                val output = if (obj.has("output") && !obj.isNull("output")) obj.optString("output") else null
                records[id] = Record(
                    id = id,
                    title = obj.optString("title", "Apktool"),
                    command = command,
                    postAlign = obj.optBoolean("postAlign", false),
                    postSign = obj.optBoolean("postSign", false),
                    signKeystore = "",
                    signPassword = "",
                    signV1 = obj.optBoolean("signV1", true),
                    signV2 = obj.optBoolean("signV2", true),
                    signV3 = obj.optBoolean("signV3", true),
                    signV4 = obj.optBoolean("signV4", false),
                    cleanBuildProject = obj.optStringOrNull("cleanBuildProject"),
                    postDecodeRoot = obj.optStringOrNull("postDecodeRoot"),
                    createNomedia = obj.optBoolean("createNomedia", false),
                    removeSplitTraces = obj.optBoolean("removeSplitTraces", false),
                    removePropertyTags = obj.optBoolean("removePropertyTags", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    status = restoredStatus,
                    line = restoredLine,
                    output = output,
                )
            }
        }
        // Do not leave stale RUNNING/QUEUED states after process death.
        persistRecords(force = true)
    }

    private fun persistRecords(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 800L) return
        synchronized(persistLock) {
            val checkNow = System.currentTimeMillis()
            if (!force && checkNow - lastPersistAt < 800L) return
            val array = JSONArray()
            records.values
                .sortedBy { it.createdAt }
                .takeLast(MAX_STORED_JOBS)
                .forEach { record ->
                    array.put(JSONObject().apply {
                        put("id", record.id)
                        put("title", record.title)
                        put("command", record.command)
                        put("status", record.status.name)
                        put("line", record.line)
                        record.output?.let { put("output", it) }
                        put("createdAt", record.createdAt)
                        put("postAlign", record.postAlign)
                        put("postSign", record.postSign)
                        put("signV1", record.signV1)
                        put("signV2", record.signV2)
                        put("signV3", record.signV3)
                        put("signV4", record.signV4)
                        record.cleanBuildProject?.let { put("cleanBuildProject", it) }
                        record.postDecodeRoot?.let { put("postDecodeRoot", it) }
                        put("createNomedia", record.createNomedia)
                        put("removeSplitTraces", record.removeSplitTraces)
                        put("removePropertyTags", record.removePropertyTags)
                    })
                }
            getSharedPreferences(JOB_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECORDS, array.toString())
                .apply()
            lastPersistAt = checkNow
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null

    private fun jobNotificationId(id: String): Int = JOB_NOTIFICATION_BASE + (id.hashCode() and 0x3fff)

    private fun notifyJob(record: Record, force: Boolean = false) {
        if (record.status.isTerminal()) {
            getSystemService(NotificationManager::class.java).cancel(jobNotificationId(record.id))
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - record.lastNotificationAt < 500L) return
        record.lastNotificationAt = now

        val cancelIntent = PendingIntent.getService(
            this,
            jobNotificationId(record.id),
            Intent(this, ApktoolJobService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_JOB_ID, record.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val state = when (record.status) {
            Status.QUEUED -> "Wartet"
            Status.RUNNING -> "Läuft"
            else -> record.status.name
        }
        val detail = record.line.lineSequence().lastOrNull { it.isNotBlank() }?.take(180).orEmpty()
        val text = if (detail.isBlank()) state else "$state • $detail"
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle(record.title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_mtapktool)
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(Notification.Action.Builder(R.drawable.ic_stat_mtapktool, "STOPP", cancelIntent).build())
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(jobNotificationId(record.id), notification) }
    }

    private fun updateForegroundState() {
        if (!::executor.isInitialized) return
        val active = records.values.count { !it.status.isTerminal() }
        val running = records.values.count { it.status == Status.RUNNING }
        val queued = records.values.count { it.status == Status.QUEUED }
        if (active == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            val text = "$running running • $queued queued • ${executor.corePoolSize}/4 runner"
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, true))
        }
    }

    private fun maybeNotifyCompletion(record: Record) {
        if (!record.status.isTerminal() || record.status == Status.CANCELLED) return
        val general = ApktoolSettings.generalDefaults(this)
        if (!general.notifyOnCompletion) return
        if (general.suppressCompletionWhileOpen && appVisible) return
        val stateText = when (record.status) {
            Status.SUCCEEDED -> "Abgeschlossen"
            Status.FAILED -> "Fehlgeschlagen"
            else -> return
        }
        val detail = record.output?.let { "$stateText • $it" }
            ?: record.line.lineSequence().lastOrNull { it.isNotBlank() }?.let { "$stateText • ${it.take(180)}" }
            ?: stateText
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                3000 + (record.id.hashCode() and 0x0fff),
                Notification.Builder(this, DONE_CHANNEL)
                    .setContentTitle(record.title)
                    .setContentText(detail)
                    .setStyle(Notification.BigTextStyle().bigText(detail))
                    .setSmallIcon(R.drawable.ic_stat_mtapktool)
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun notification(text: String, ongoing: Boolean): Notification {
        val stopAll = PendingIntent.getService(
            this,
            1,
            Intent(this, ApktoolJobService::class.java).setAction(ACTION_CANCEL_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("MTApktool")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_mtapktool)
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(0, 0, ongoing)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Alle stoppen", stopAll).build())
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "MTApktool Jobs", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Decode, build, zipalign and signing jobs"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "MTApktool Ergebnisse", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Abschlussmeldungen für Apktool-Jobs"
            },
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (::executor.isInitialized) {
            records.values.filter { !it.status.isTerminal() }.forEach { cancelInternal(it.id) }
            executor.purge()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        persistRecords(force = true)
        if (::executor.isInitialized) executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun Status.isTerminal() = this == Status.SUCCEEDED || this == Status.FAILED || this == Status.CANCELLED

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())

    private fun deleteRecursivelyCancellable(file: File) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("Cancelled")
        if (!file.exists()) return
        if (file.isDirectory) file.listFiles()?.forEach(::deleteRecursivelyCancellable)
        if (!file.delete() && file.exists()) error("Kann nicht gelöscht werden: ${file.absolutePath}")
    }

    private fun causedByInterruption(t: Throwable): Boolean {
        var c: Throwable? = t
        repeat(12) {
            if (c is InterruptedException || c is CancellationException) return true
            c = c?.cause
            if (c == null) return false
        }
        return false
    }

    private fun stackMessage(t: Throwable): String {
        val b = StringBuilder(t.toString())
        var c = t.cause
        repeat(6) {
            if (c == null) return@repeat
            b.append("\ncaused by: ").append(c)
            c = c?.cause
        }
        return b.toString()
    }
}
