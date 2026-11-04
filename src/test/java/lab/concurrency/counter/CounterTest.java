package lab.concurrency.counter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounterTest {

    static Stream<Counter> allCounters() {
        return Stream.of(new UnsafeCounter(), new VolatileCounter(), new SynchronizedCounter(),
                new AtomicCounter(), new AdderCounter());
    }

    static Stream<Counter> threadSafeCounters() {
        return Stream.of(new SynchronizedCounter(), new AtomicCounter(), new AdderCounter());
    }

    @ParameterizedTest
    @MethodSource("allCounters")
    @DisplayName("в одном потоке все реализации считают точно")
    void countsInSingleThread(Counter counter) {
        assertEquals(0, counter.get());

        for (int i = 0; i < 1000; i++) {
            counter.increment();
        }

        assertEquals(1000, counter.get());
    }

    @ParameterizedTest
    @MethodSource("threadSafeCounters")
    @DisplayName("потокобезопасные реализации не теряют инкременты на стенде")
    void threadSafeCountersLoseNothing(Counter counter) throws InterruptedException {
        CounterBenchmark.RunResult result = CounterBenchmark.run(counter);

        assertEquals(CounterBenchmark.EXPECTED, result.value());
        assertTrue(result.nanos() > 0);
    }

    @Test
    @DisplayName("медиана нечётной выборки: средний элемент независимо от порядка")
    void medianOfOddSample() {
        assertEquals(3, CounterBenchmark.median(new long[]{5, 1, 3, 2, 100}));
    }

    @Test
    @DisplayName("медиана чётной выборки: среднее двух центральных")
    void medianOfEvenSample() {
        assertEquals(15, CounterBenchmark.median(new long[]{20, 10}));
    }
}
