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
import io.eksoda.core.database.dao.ReceiptItemRow
import io.eksoda.core.model.ImportSubmission
import io.eksoda.engine.Engine
import io.eksoda.engine.fetch.FetchedDocument
import io.eksoda.engine.fetch.Fetcher
import io.eksoda.engine.providers.REGISTRY
import io.eksoda.engine.providers.entersoft.DOCUMENT_PATH
import io.eksoda.engine.providers.entersoft.DocumentLink
import io.eksoda.engine.providers.entersoft.LegacyLink
import io.eksoda.engine.url.ParsedUrl
import io.eksoda.engine.url.parseSubmittedUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.Executors
import io.eksoda.engine.providers.entersoft.HOST as ENTERSOFT_HOST
import io.eksoda.engine.providers.entersoft.split as entersoftLink

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

    /** The detailed myDATA XML of an Epsilon Digital or Parochos fixture. */
    fun xml(name: String): ByteArray = root.resolve("$name.xml").readBytes()

    /** Whether [name] is read from its XML (Epsilon Digital's platform) or from its page. */
    fun isXml(name: String): Boolean = root.resolve("$name.xml").exists()

    /** Every receipt fixture with an expected output, by "<provider>/<name>". */
    fun withExpected(): List<String> = root.listFiles().orEmpty()
        .flatMap { provider ->
            provider.resolve("expected").listFiles().orEmpty()
                .map { "${provider.name}/${it.nameWithoutExtension}" }
        }
        .sorted()

    /** The parser's expected output for [name]. */
    fun expected(name: String): String {
        val (provider, file) = name.split('/')
        return root.resolve("$provider/expected/$file.json").readText()
    }

    fun canonicalUrl(name: String): String = REGISTRY.resolveUrl(url(name)).second.canonicalUrl
}

/** Serves fixture pages instead of the network; [failure], when set, is thrown instead. */
internal class FixtureFetcher : Fetcher {
    /** Pages by full link. */
    private val pages = mutableMapOf<String, ByteArray>()

    /** Framed documents by host and path, whatever their query (Entersoft's GetInvoice). */
    private val frames = mutableMapOf<String, ByteArray>()

    /** Detailed XML documents by host (Epsilon Digital's platform). */
    private val xmlDocuments = mutableMapOf<String, ByteArray>()
    var failure: Exception? = null

    /** [fixture]'s page at its link, changed by [edit] (a page the parsers have not seen). */
    fun serve(fixture: String, edit: (String) -> String = { it }) {
        val page = edit(Fixtures.page(fixture).toString(Charsets.UTF_8))
        pages[Fixtures.canonicalUrl(fixture)] = page.toByteArray(Charsets.UTF_8)
    }

    /**
     * An Epsilon Digital or Parochos receipt: its detailed XML at the document address of its
     * link's host (`/filedocument/GetInvoiceDocDetailed/<uuid>`).
     */
    fun serveXml(fixture: String) {
        val host = URI(Fixtures.canonicalUrl(fixture)).host
        xmlDocuments[host] = Fixtures.xml(fixture)
    }

    /**
     * An Entersoft receipt: the shell page at its link, framing that link's own document (the
     * sample shells frame one token; the app refuses a frame that names another receipt), and
     * the receipt in the frame.
     */
    fun serveEntersoft(fixture: String) {
        val link = Fixtures.canonicalUrl(fixture)
        val shell = when (val parts = checkNotNull(entersoftLink(parseSubmittedUrl(link)))) {
            is DocumentLink -> Fixtures.page("entersoft/shell").toString(Charsets.UTF_8)
                .replace("hashToken=0xxx0000", "hashToken=${parts.token}")

            is LegacyLink -> Fixtures.page("entersoft/shell_legacy").toString(Charsets.UTF_8)
                .replace("hashToken=0x0xxx00", "hashToken=${parts.token}")
        }
        pages[link] = shell.toByteArray(Charsets.UTF_8)
        frames[ENTERSOFT_HOST + DOCUMENT_PATH] = Fixtures.page(fixture)
    }

    override suspend fun get(
        url: ParsedUrl,
        allowedHosts: Set<String>,
        acceptStatuses: Set<Int>,
        contentTypes: Set<String>,
    ): FetchedDocument {
        failure?.let { throw it }
        check(url.host in allowedHosts) { "a request outside the provider's hosts" }
        // Only what was served for this very host: a setup mistake fails, never another page.
        val page = pages[url.geturl()] ?: frames[url.host + url.path]
            ?: xmlDocuments[url.host]?.takeIf { url.path.startsWith(XML_DOCUMENT_PATH) }
            ?: error("no fixture for ${url.host}${url.path}")
        return FetchedDocument(page)
    }

    private companion object {
        const val XML_DOCUMENT_PATH = "/filedocument/GetInvoiceDocDetailed/"
    }
}

/**
 * The import pipeline as the app wires it (ImportService → WorkManager → ImportWorker →
 * Persister → ReceiptRepositoryImpl) on an in-memory database seeded like the real one, with
 * [fetcher] in place of the network.
 */
internal class ImportHarness(folder: File) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()

    /** Set up first: if it fails, nothing is open yet. [close] releases what follows. */
    val workManager: WorkManager = run {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        WorkManager.getInstance(context)
    }

    /**
     * The dispatcher the repositories compute on (the app injects Default): a real thread, since
     * each test's runTest has its own scheduler.
     */
    val dispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
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
    suspend fun runWorker(jobId: String): ListenableWorker.Result = worker(jobId).doWork()

    /** The import worker for [jobId], as WorkManager would make it. */
    fun worker(jobId: String): ImportWorker {
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
        return TestListenableWorkerBuilder<ImportWorker>(context)
            .setInputData(workDataOf(ImportWorker.KEY_JOB_ID to jobId))
            .setWorkerFactory(factory)
            .build()
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

    /** A stored receipt's lines by line number. */
    suspend fun lines(receiptId: String): Map<Int, ReceiptItemRow> =
        database.receiptDao().observeItems(receiptId).first().associateBy { it.item.lineNumber }

    /** One number from the database (a count, a sum); [args] fill the query's `?`. */
    fun count(sql: String, vararg args: Any): Long =
        database.openHelper.readableDatabase.query(sql, args).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    /** One text value from the database (the first row's first column), or null. */
    fun text(sql: String, vararg args: Any): String? =
        database.openHelper.readableDatabase.query(sql, args).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Every step runs; the first failure is thrown, the others are suppressed into it. */
    override fun close() {
        val failures = listOf<() -> Unit>(
            { scope.cancel() },
            { database.close() },
            { WorkManagerTestInitHelper.closeWorkDatabase() },
            { dispatcher.close() },
        ).mapNotNull { step -> runCatching(step).exceptionOrNull() }
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
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
