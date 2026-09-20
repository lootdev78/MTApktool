package io.github.lootdev78.mtapktool.apktool

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.android.apksig.ApkVerifier
import androidx.core.content.ContextCompat
import io.github.lootdev78.mtapktool.MainActivity
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

/**
 * Runs Apktool outside the UI process. Heavy resource/dex decoding therefore
 * cannot block Compose or cause UI GC pauses. Status is streamed back through
 * package-scoped broadcasts and intentionally throttled.
 */
class ApktoolJobService : Service() {
    companion object {
        const val ACTION_ENQUEUE = "io.github.lootdev78.mtapktool.apktool.ENQUEUE"
        const val ACTION_CANCEL = "io.github.lootdev78.mtapktool.apktool.CANCEL"
        const val ACTION_CANCEL_ALL = "io.github.lootdev78.mtapktool.apktool.CANCEL_ALL"
        const val ACTION_DISMISS = "io.github.lootdev78.mtapktool.apktool.DISMISS"
        const val ACTION_CLEAR_FINISHED = "io.github.lootdev78.mtapktool.apktool.CLEAR_FINISHED"
        const val ACTION_QUERY = "io.github.lootdev78.mtapktool.apktool.QUERY"
        const val ACTION_SET_WORKERS = "io.github.lootdev78.mtapktool.apktool.SET_WORKERS"
        const val ACTION_STATUS = "io.github.lootdev78.mtapktool.apktool.STATUS"

        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_STATUS = "status"
        const val EXTRA_LINE = "line"
        const val EXTRA_LOG = "log"
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_CREATED_AT = "created_at"
        const val EXTRA_STAGE = "stage"
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
        private const val EXTRA_PROJECT_ROOT = "project_root"
        private const val EXTRA_POST_DECODE_ROOT = "post_decode_root"
        private const val EXTRA_SOURCE_APK = "source_apk"
        private const val EXTRA_CREATE_NOMEDIA = "create_nomedia"
        private const val EXTRA_REMOVE_SPLIT = "remove_split"
        private const val EXTRA_REMOVE_PROPERTY = "remove_property"
        private const val EXTRA_NOTIFY_DONE = "notify_done"
        private const val EXTRA_SUPPRESS_NOTIFY_WHILE_OPEN = "suppress_notify_while_open"

        private const val CHANNEL = "mtapktool_jobs"
        private const val DONE_CHANNEL = "mtapktool_done"
        private const val NOTIFICATION_ID = 2317
        private const val MAX_UI_LOG_CHARS = 64 * 1024
        private const val UI_UPDATE_INTERVAL_MS = 180L

        fun enqueue(
            context: Context,
            title: String,
            command: String,
            postAlign: Boolean = false,
            postSign: Boolean = false,
            signature: ApktoolSignatureDefaults? = null,
            cleanBuildProject: String? = null,
            projectRoot: String? = null,
            postDecodeRoot: String? = null,
            sourceApk: String? = null,
            createNomedia: Boolean = false,
            removeSplitTraces: Boolean = false,
            removePropertyTags: Boolean = false,
        ): String {
            val id = UUID.randomUUID().toString()
            val sign = signature ?: ApktoolSignatureDefaults(v3 = true)
            val general = ApktoolSettings.generalDefaults(context)
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
                putExtra(EXTRA_PROJECT_ROOT, projectRoot)
                putExtra(EXTRA_POST_DECODE_ROOT, postDecodeRoot)
                putExtra(EXTRA_SOURCE_APK, sourceApk)
                putExtra(EXTRA_CREATE_NOMEDIA, createNomedia)
                putExtra(EXTRA_REMOVE_SPLIT, removeSplitTraces)
                putExtra(EXTRA_REMOVE_PROPERTY, removePropertyTags)
                // Settings are snapshotted in the UI process. This keeps the
                // dedicated :apktool process independent from multi-process prefs.
                putExtra(EXTRA_WORKERS, ApktoolSettings.maxWorkers(context))
                putExtra(EXTRA_NOTIFY_DONE, general.notifyOnCompletion)
                putExtra(EXTRA_SUPPRESS_NOTIFY_WHILE_OPEN, general.suppressCompletionWhileOpen)
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

        fun dismiss(context: Context, id: String) {
            runCatching {
                context.startService(Intent(context, ApktoolJobService::class.java).apply {
                    action = ACTION_DISMISS
                    putExtra(EXTRA_JOB_ID, id)
                })
            }
        }

        fun clearFinished(context: Context) {
            runCatching { context.startService(Intent(context, ApktoolJobService::class.java).setAction(ACTION_CLEAR_FINISHED)) }
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
        val projectRoot: String?,
        val postDecodeRoot: String?,
        val sourceApk: String?,
        val createNomedia: Boolean,
        val removeSplitTraces: Boolean,
        val removePropertyTags: Boolean,
        val notifyOnCompletion: Boolean,
        val suppressCompletionWhileOpen: Boolean,
        val createdAt: Long = System.currentTimeMillis(),
        @Volatile var status: Status = Status.QUEUED,
        @Volatile var stage: ApktoolWorkflowStage = ApktoolWorkflowStage.QUEUED,
        @Volatile var line: String = "Queued",
        @Volatile var output: String? = null,
        @Volatile var cancelRequested: Boolean = false,
        @Volatile var future: Future<*>? = null,
        @Volatile var lastBroadcastAt: Long = 0L,
        val logTail: StringBuilder = StringBuilder(),
    )

    private val records = ConcurrentHashMap<String, Record>()
    private lateinit var executor: ThreadPoolExecutor

    override fun onCreate() {
        super.onCreate()
        ApktoolProjectSessionManager.configure(filesDir)
        createChannels()
        // Do not read SharedPreferences in this secondary process. A job carries
        // the current worker setting and resizes the pool before it is submitted.
        executor = ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { runnable ->
            Thread(runnable, "mtapktool-worker").apply { priority = Thread.MIN_PRIORITY }
        }
        executor.allowCoreThreadTimeOut(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENQUEUE -> enqueueInternal(intent)
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_JOB_ID)?.let(::cancelInternal)
            ACTION_CANCEL_ALL -> {
                records.values.filter { !it.status.isTerminal() }.forEach { cancelInternal(it.id) }
                executor.purge()
            }
            ACTION_DISMISS -> intent.getStringExtra(EXTRA_JOB_ID)?.let { id ->
                records[id]?.takeIf { it.status.isTerminal() }?.let { records.remove(id) }
            }
            ACTION_CLEAR_FINISHED -> records.entries
                .filter { it.value.status.isTerminal() }
                .map { it.key }
                .forEach { records.remove(it) }
            ACTION_QUERY -> records.values.sortedBy { it.createdAt }.forEach { broadcast(it, force = true) }
            ACTION_SET_WORKERS -> resizePool(intent.getIntExtra(EXTRA_WORKERS, 1).coerceIn(1, 4))
        }
        updateForegroundState()
        return START_NOT_STICKY
    }

    private fun enqueueInternal(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_JOB_ID) ?: UUID.randomUUID().toString()
        val command = intent.getStringExtra(EXTRA_COMMAND)?.trim().orEmpty()
        if (command.isEmpty()) return
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
            projectRoot = intent.getStringExtra(EXTRA_PROJECT_ROOT),
            postDecodeRoot = intent.getStringExtra(EXTRA_POST_DECODE_ROOT),
            sourceApk = intent.getStringExtra(EXTRA_SOURCE_APK),
            createNomedia = intent.getBooleanExtra(EXTRA_CREATE_NOMEDIA, false),
            removeSplitTraces = intent.getBooleanExtra(EXTRA_REMOVE_SPLIT, false),
            removePropertyTags = intent.getBooleanExtra(EXTRA_REMOVE_PROPERTY, false),
            notifyOnCompletion = intent.getBooleanExtra(EXTRA_NOTIFY_DONE, true),
            suppressCompletionWhileOpen = intent.getBooleanExtra(EXTRA_SUPPRESS_NOTIFY_WHILE_OPEN, true),
        )
        appendLog(record, "$ ${record.command}")
        appendLog(record, "Queued")
        records[id] = record
        startForeground(NOTIFICATION_ID, notification("Queued: ${record.title}", true))
        resizePool(intent.getIntExtra(EXTRA_WORKERS, 1).coerceIn(1, 4))
        broadcast(record, force = true)
        record.future = executor.submit { runJob(record) }
    }

    private fun runJob(record: Record) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        if (record.cancelRequested || Thread.currentThread().isInterrupted) {
            finishCancelled(record)
            return
        }
        record.status = Status.RUNNING
        record.stage = ApktoolWorkflowStage.PROVISIONING
        record.line = "Starting…"
        appendLog(record, record.line)
        broadcast(record, force = true)
        updateForegroundState()
        var log: PrintWriter? = null
        try {
            val toolchain = Toolchain(this)
            record.stage = ApktoolWorkflowStage.PROVISIONING
            broadcast(record, force = true)
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
                appendLog(record, line)
                broadcast(record)
            }
            val runner = ApktoolCommandRunner(toolchain, listener)
            val commandHead = record.command.trim().substringBefore(' ').lowercase()
            record.stage = when (commandHead) {
                "d", "decode" -> ApktoolWorkflowStage.DECODING
                "b", "build" -> ApktoolWorkflowStage.BUILDING
                else -> ApktoolWorkflowStage.PROVISIONING
            }
            record.projectRoot?.let { project -> if (record.stage == ApktoolWorkflowStage.BUILDING) ApktoolProjectSessionManager.begin(File(project), ApktoolWorkflowStage.BUILDING) }
            broadcast(record, force = true)
            var result = if (SplitArchiveSupport.isSplitDecodeCommand(record.command)) {
                SplitArchiveSupport.executeDecode(record.command, toolchain, listener)
            } else {
                runner.execute(record.command)
            }
            checkCancelled(record)

            if (result.isSuccess && !record.postDecodeRoot.isNullOrBlank()) {
                record.stage = ApktoolWorkflowStage.POST_DECODE
                broadcast(record, force = true)
                ProjectPostProcessor.process(
                    File(record.postDecodeRoot),
                    ProjectPostProcessor.Options(record.createNomedia, record.removeSplitTraces, record.removePropertyTags),
                    listener,
                )
                val decodedRoot = File(record.postDecodeRoot)
                val sourceApk = record.sourceApk?.takeIf { it.isNotBlank() }?.let(::File)
                val projects = buildList {
                    if (File(decodedRoot, "apktool.yml").isFile) add(decodedRoot)
                    decodedRoot.walkTopDown().maxDepth(3)
                        .filter { it.isDirectory && it != decodedRoot && File(it, "apktool.yml").isFile }
                        .forEach { add(it) }
                }.distinctBy { it.canonicalPath }
                if (projects.isEmpty()) {
                    ApktoolProjectSessionManager.registerDecodedProject(decodedRoot, sourceApk)
                } else {
                    projects.forEach { ApktoolProjectSessionManager.registerDecodedProject(it, sourceApk) }
                }
            }
            checkCancelled(record)

            if (result.isSuccess && (record.postAlign || record.postSign)) {
                record.stage = ApktoolWorkflowStage.POST_PROCESSING
                record.line = when {
                    record.postAlign && record.postSign -> "Aligning and signing…"
                    record.postAlign -> "Aligning…"
                    else -> "Signing…"
                }
                broadcast(record, force = true)
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

            val verifiedOutput = result.output
            if (result.isSuccess && verifiedOutput?.isFile == true && verifiedOutput.extension.equals("apk", true)) {
                record.stage = ApktoolWorkflowStage.VERIFYING
                record.line = "Verifying APK…"
                broadcast(record, force = true)
                val verify = ApkVerifier.Builder(verifiedOutput).build().verify()
                if (!verify.isVerified) error("APK verification failed")
            }
            if (result.isSuccess && !record.cleanBuildProject.isNullOrBlank()) {
                val buildDir = File(record.cleanBuildProject, "build")
                if (buildDir.exists()) deleteRecursivelyCancellable(buildDir)
            }

            record.output = result.output?.absolutePath
            record.status = if (result.isSuccess) Status.SUCCEEDED else Status.FAILED
            record.stage = if (result.isSuccess) ApktoolWorkflowStage.SUCCEEDED else ApktoolWorkflowStage.FAILED
            record.projectRoot?.let { projectPath ->
                val project = File(projectPath)
                if (result.isSuccess) {
                    ApktoolProjectSessionManager.completeBuild(project, result.output)
                } else {
                    ApktoolProjectSessionManager.fail(project, IllegalStateException(result.summary.ifBlank { "Apktool build failed" }))
                }
            }
            record.line = result.summary + (record.output?.let { "\n$it" } ?: "") + "\nLog: ${logFile.absolutePath}"
            appendLog(record, result.summary)
            record.output?.let { appendLog(record, "Output: $it") }
            appendLog(record, "Log: ${logFile.absolutePath}")
        } catch (_: CancellationException) {
            finishCancelled(record, send = false)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            finishCancelled(record, send = false)
        } catch (t: Throwable) {
            if (record.cancelRequested || Thread.currentThread().isInterrupted || causedByInterruption(t)) {
                finishCancelled(record, send = false)
            } else {
                record.status = Status.FAILED
                record.stage = ApktoolWorkflowStage.FAILED
                record.projectRoot?.let { ApktoolProjectSessionManager.fail(File(it), t) }
                record.line = stackMessage(t)
                appendLog(record, record.line)
                log?.println(record.line)
            }
        } finally {
            log?.close()
            broadcast(record, force = true)
            maybeNotifyCompletion(record)
            updateForegroundState()
            Thread.interrupted()
        }
    }

    private fun appendLog(record: Record, text: String) {
        if (text.isBlank()) return
        synchronized(record.logTail) {
            record.logTail.append(text).append('\n')
            val extra = record.logTail.length - MAX_UI_LOG_CHARS
            if (extra > 0) record.logTail.delete(0, extra)
        }
    }

    private fun checkCancelled(record: Record) {
        if (record.cancelRequested || Thread.currentThread().isInterrupted) throw CancellationException("Cancelled")
    }

    private fun finishCancelled(record: Record, send: Boolean = true) {
        record.status = Status.CANCELLED
        record.stage = ApktoolWorkflowStage.CANCELLED
        record.projectRoot?.let { runCatching { ApktoolProjectSessionManager.cancel(File(it)) } }
        record.line = "Cancelled"
        appendLog(record, record.line)
        if (send) broadcast(record, force = true)
    }

    private fun cancelInternal(id: String) {
        val record = records[id] ?: return
        if (record.status.isTerminal()) return
        record.cancelRequested = true
        val queued = record.status == Status.QUEUED
        record.future?.cancel(true)
        executor.purge()
        if (queued) {
            finishCancelled(record)
        } else {
            record.line = "Cancelling…"
            appendLog(record, record.line)
            broadcast(record, force = true)
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
    }

    private fun broadcast(record: Record, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - record.lastBroadcastAt < UI_UPDATE_INTERVAL_MS) return
        record.lastBroadcastAt = now
        val logTail = synchronized(record.logTail) { record.logTail.toString() }
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_JOB_ID, record.id)
            putExtra(EXTRA_TITLE, record.title)
            putExtra(EXTRA_COMMAND, record.command)
            putExtra(EXTRA_STATUS, record.status.name)
            putExtra(EXTRA_STAGE, record.stage.name)
            putExtra(EXTRA_LINE, record.line)
            putExtra(EXTRA_LOG, logTail)
            putExtra(EXTRA_OUTPUT, record.output)
            putExtra(EXTRA_CREATED_AT, record.createdAt)
        })
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
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                notification("$running running • $queued queued • ${executor.corePoolSize}/4 runner", true),
            )
        }
    }

    private fun maybeNotifyCompletion(record: Record) {
        if (!record.status.isTerminal() || record.status == Status.CANCELLED) return
        if (!record.notifyOnCompletion || (record.suppressCompletionWhileOpen && isUiProcessVisible())) return
        val text = if (record.status == Status.SUCCEEDED) "Abgeschlossen" else "Fehlgeschlagen"
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                3000 + (record.id.hashCode() and 0x0fff),
                Notification.Builder(this, DONE_CHANNEL)
                    .setContentTitle(record.title)
                    .setContentText(text)
                    .setSmallIcon(if (record.status == Status.SUCCEEDED) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                    .setContentIntent(openAppIntent())
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun isUiProcessVisible(): Boolean {
        val manager = getSystemService(ActivityManager::class.java) ?: return false
        return manager.runningAppProcesses.orEmpty().any { process ->
            process.processName == packageName &&
                process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
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
            .setSmallIcon(android.R.drawable.stat_sys_download)
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
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "MTApktool Jobs", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(DONE_CHANNEL, "MTApktool Ergebnisse", NotificationManager.IMPORTANCE_DEFAULT))
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
