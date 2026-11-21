package io.eksoda.core.data.imports

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.eksoda.core.data.db.WriteCoordinator
import io.eksoda.core.data.repository.HouseholdZone
import io.eksoda.core.data.repository.ReceiptRepositoryImpl
import io.eksoda.core.data.util.NetworkMonitor
import io.eksoda.core.database.CategorySeed
import io.eksoda.core.database.EksodaDatabase
import io.eksoda.core.model.ImportSubmission
import io.eksoda.engine.Engine
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.url.ParsedUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors

/** This repository's receipt fixtures, by "<provider>/<name>". */
internal object Fixtures {
    private val root: File by lazy {
        File(checkNotNull(System.getProperty("eksoda.testdata"))).resolve("fixtures")
    }

    /** The receipt link of a fixture, as a user would scan it. */
    fun url(name: String): String =
        Json.parseToJsonElement(root.resolve("$name.meta.json").readText())
            .jsonObject.getValue("url").jsonPrimitive.content

    fun page(name: String): ByteArray = root.resolve("$name.html").readBytes()

    fun canonicalUrl(name: String): String = REGISTRY.resolveUrl(url(name)).second.canonicalUrl
}

/** Serves fixture pages instead of the network; [failure], when set, is thrown instead. */
internal class FixtureFetcher : Fetcher {
    /** Pages by full link, or by path (Entersoft's document frame). */
    private val pages = mutableMapOf<String, ByteArray>()
    var failure: Exception? = null

    fun serve(fixture: String) {
        pages[Fixtures.canonicalUrl(fixture)] = Fixtures.page(fixture)
    }

    /** An Entersoft receipt: the shell page at its link, the receipt in the frame it points to. */
    fun serveEntersoft(fixture: String) {
        pages[Fixtures.canonicalUrl(fixture)] = Fixtures.page("entersoft/shell")
        pages["/api/GetInvoice"] = Fixtures.page(fixture)
    }

    override suspend fun get(
        url: ParsedUrl,
        allowedHosts: Set<String>,
        acceptStatuses: Set<Int>,
        contentTypes: Set<String>,
    ): FetchedDocument {
        failure?.let { throw it }
        check(url.host in allowedHosts) { "a request outside the provider's hosts" }
        val page = pages[url.geturl()] ?: pages[url.path] ?: error("no fixture for ${url.path}")
        return FetchedDocument(page)
    }
}

/**
 * The import pipeline as the app wires it (ImportService → WorkManager → ImportWorker →
 * Persister → ReceiptRepositoryImpl) on an in-memory database seeded like the real one, with
 * [fetcher] in place of the network.
 */
internal class ImportHarness(folder: File) : AutoCloseable {
    /**
     * The dispatcher the repositories compute on (the app injects Default): a real thread, since
     * each test's runTest has its own scheduler.
     */
    val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val context: Context = ApplicationProvider.getApplicationContext()
    val clock: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    val database: EksodaDatabase =
        Room.inMemoryDatabaseBuilder(context, EksodaDatabase::class.java)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        CategorySeed.insertStatements(NOW).forEach(db::execSQL)
                    }
                },
            )
            .allowMainThreadQueries()
            .build()
    val fetcher = FixtureFetcher()
    val workManager: WorkManager
    val jobs = database.importJobDao()
    val coordinator = WriteCoordinator(database)
    val engine = Engine(fetcher)
    val persister = Persister(
        database.merchantDao(),
        ProductMatcher(database.productDao(), clock),
        database.receiptDao(),
        clock,
    )
    val service: ImportService
    val repository: ReceiptRepositoryImpl
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val scheduler: ImportScheduler
    private val zone = HouseholdZone(database.householdDao())
    private val notifier = ImportNotifier(
        context,
        PreferenceDataStoreFactory.create(scope = scope) {
            folder.resolve("settings.preferences_pb")
        },
    )

    init {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = ImportScheduler(workManager)
        service = ImportService(engine, jobs, coordinator, scheduler, clock)
        repository = ReceiptRepositoryImpl(
            database.receiptDao(),
            jobs,
            database.tombstoneDao(),
            service,
            coordinator,
            zone,
            NetworkMonitor(context),
            clock,
            dispatcher,
        )
    }

    /** One run of the import worker for [jobId], as WorkManager would start it. */
    suspend fun runWorker(jobId: String): ListenableWorker.Result {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ) = ImportWorker(
                appContext,
                workerParameters,
                engine,
                jobs,
                persister,
                coordinator,
                scheduler,
                notifier,
                zone,
                AppVersion(appContext),
                clock,
            )
        }
        val worker = TestListenableWorkerBuilder<ImportWorker>(context)
            .setInputData(workDataOf(ImportWorker.KEY_JOB_ID to jobId))
            .setWorkerFactory(factory)
            .build()
        return worker.doWork()
    }

    /** Scans [fixture]'s link and runs its import: the job id. */
    suspend fun import(fixture: String): String {
        val submission = service.submit(Fixtures.url(fixture))
        val jobId = (submission as ImportSubmission.Queued).jobId
        runWorker(jobId)
        return jobId
    }

    /** The stored receipt of an imported job. */
    suspend fun receiptOf(jobId: String): String = checkNotNull(jobs.get(jobId)?.receiptId)

    /** One number from the database (a count, a sum). */
    fun count(sql: String): Long = database.openHelper.readableDatabase.query(sql).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    override fun close() {
        scope.cancel()
        database.close()
        WorkManagerTestInitHelper.closeWorkDatabase()
        dispatcher.close()
    }

    companion object {
        /** 2026-11-22T10:00Z: the day after the newest fixture (2026-11-21). */
        const val NOW = 1_795_341_600_000L

        /** METRO, 2026-11-21, 21.10. */
        const val MYMARKET = "impact/mymarket_7_lines_discount"

        /** METRO, 2026-11-07, 9.15. */
        const val METRO_SAMPLE = "impact/sample_el"

        /** A grill house, 2026-11-20, 22.90. */
        const val KOGIMOU = "impact/kogimou_3_lines"

        /** LIDL (Entersoft), 2026-07-13, 75.48. */
        const val LIDL = "entersoft/lidl_21_lines"
    }
}
