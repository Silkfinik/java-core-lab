package lab.concurrency.counter;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class CounterBenchmark {
    static final int THREADS = 8;
    static final int INCREMENTS_PER_THREAD = 1_000_000;
    static final long EXPECTED = (long) THREADS * INCREMENTS_PER_THREAD;

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 5;
    private static final int VERIFICATION_ROUNDS = 10;
    private static final long TIMEOUT_MINUTES = 5;

    private static final List<Implementation> IMPLEMENTATIONS = List.of(
            new Implementation("UnsafeCounter", UnsafeCounter::new, false),
            new Implementation("VolatileCounter", VolatileCounter::new, false),
            new Implementation("SynchronizedCounter", SynchronizedCounter::new, true),
            new Implementation("AtomicCounter", AtomicCounter::new, true),
            new Implementation("AdderCounter", AdderCounter::new, true)
    );

    private CounterBenchmark() {
    }

    record Implementation(String name, Supplier<Counter> factory, boolean threadSafe) {
    }

    record RunResult(long nanos, long value) {
    }

    public static void main(String[] args) throws InterruptedException {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (Implementation implementation : IMPLEMENTATIONS) {
                run(implementation.factory().get());
            }
        }

        long[][] nanos = new long[IMPLEMENTATIONS.size()][MEASURED_ROUNDS];
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            for (int i = 0; i < IMPLEMENTATIONS.size(); i++) {
                nanos[i][round] = run(IMPLEMENTATIONS.get(i).factory().get()).nanos();
            }
        }

        long[][] values = new long[IMPLEMENTATIONS.size()][VERIFICATION_ROUNDS];
        for (int round = 0; round < VERIFICATION_ROUNDS; round++) {
            for (int i = 0; i < IMPLEMENTATIONS.size(); i++) {
                values[i][round] = run(IMPLEMENTATIONS.get(i).factory().get()).value();
            }
        }

        System.out.printf("%d потоков x %d инкрементов, ожидается %d; разогрев: %d%n%n",
                THREADS, INCREMENTS_PER_THREAD, EXPECTED, WARMUP_ROUNDS);

        System.out.printf("Время, прогонов: %d%n", MEASURED_ROUNDS);
        System.out.printf("%-20s %12s  %s%n", "Реализация", "Медиана, мс", "Прогоны, мс");
        for (int i = 0; i < IMPLEMENTATIONS.size(); i++) {
            System.out.printf("%-20s %12.2f  %s%n",
                    IMPLEMENTATIONS.get(i).name(), toMillis(median(nanos[i])), formatRuns(nanos[i]));
        }

        System.out.printf("%nЗначения, прогонов: %d%n", VERIFICATION_ROUNDS);
        System.out.printf("%-20s %6s  %s%n", "Реализация", "Точно", "Значения");
        boolean threadSafeCountersCorrect = true;
        for (int i = 0; i < IMPLEMENTATIONS.size(); i++) {
            Implementation implementation = IMPLEMENTATIONS.get(i);
            long exactRuns = Arrays.stream(values[i]).filter(value -> value == EXPECTED).count();
            threadSafeCountersCorrect &= !implementation.threadSafe() || exactRuns == VERIFICATION_ROUNDS;
            System.out.printf("%-20s %3d/%-2d  %s%n",
                    implementation.name(), exactRuns, VERIFICATION_ROUNDS, Arrays.toString(values[i]));
        }

        if (!threadSafeCountersCorrect) {
            throw new IllegalStateException("Потокобезопасная реализация потеряла инкременты");
        }
    }

    static RunResult run(Counter counter) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                        counter.increment();
                    }
                });
            }
            ready.await();

            long startedAt = System.nanoTime();
            start.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Прогон не завершился за " + TIMEOUT_MINUTES + " мин");
            }
            long elapsed = System.nanoTime() - startedAt;

            return new RunResult(elapsed, counter.get());
        } finally {
            executor.shutdownNow();
        }
    }

    static long median(long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[middle]
                : sorted[middle - 1] + (sorted[middle] - sorted[middle - 1]) / 2;
    }

    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String formatRuns(long[] nanos) {
        StringBuilder runs = new StringBuilder();
        for (long sample : nanos) {
            runs.append(String.format("%8.2f", toMillis(sample)));
        }
        return runs.toString();
    }
}
