package io.github.ghals5737.loadmin.core.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.ghals5737.loadmin.core.engine.LoadTestRun;
import io.github.ghals5737.loadmin.core.engine.LoadTestSpec;

import org.junit.jupiter.api.Test;

class SlowQueryAggregationTest {

    private static LoadTestRun newRun() {
        return new LoadTestRun("test",
                new LoadTestSpec("GET", "/api/x", "/api/x", null, 10, 15));
    }

    @Test
    void repeatsOfAStatementAreAggregated() {
        LoadTestRun run = newRun();

        run.recordSlowQuery("select 1", 100);
        run.recordSlowQuery("select 1", 300);
        run.recordSlowQuery("select 2", 150);

        List<SlowQuery> queries = run.view().slowQueries();
        assertEquals(2, queries.size());
        SlowQuery first = queries.get(0);
        assertEquals("select 1", first.sql());
        assertEquals(2, first.count());
        assertEquals(300, first.maxMillis());
        assertEquals(400, first.totalMillis());
        assertEquals(200, first.averageMillis());
    }

    @Test
    void theWorstTotalComesFirst() {
        LoadTestRun run = newRun();

        run.recordSlowQuery("rare outlier", 900);
        for (int i = 0; i < 50; i++) {
            run.recordSlowQuery("slightly slow, very often", 120);
        }

        List<SlowQuery> queries = run.view().slowQueries();
        // 50 x 120ms hurts more than one 900ms call, so it ranks first.
        assertEquals("slightly slow, very often", queries.get(0).sql());
        assertEquals("rare outlier", queries.get(1).sql());
    }

    @Test
    void theNumberOfDistinctStatementsIsBounded() {
        LoadTestRun run = newRun();

        for (int i = 0; i < 5000; i++) {
            run.recordSlowQuery("select " + i, 100);
        }

        // Reported list is capped, and the run does not keep 5000 of them.
        assertTrue(run.view().slowQueries().size() <= 20, "reported list is not capped");
    }

    @Test
    void recordingIsSafeFromTheApplicationsRequestThreads() throws Exception {
        LoadTestRun run = newRun();
        int threads = 8;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        run.recordSlowQuery("select 1", 10);
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        SlowQuery query = run.view().slowQueries().get(0);
        assertEquals((long) threads * perThread, query.count());
        assertEquals((long) threads * perThread * 10, query.totalMillis());
    }
}
