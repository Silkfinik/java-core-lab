package lab.concurrency.buffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public final class BufferScenario {
    static final int PRODUCERS = 3;
    static final int CONSUMERS = 2;
    static final int ITEMS_PER_PRODUCER = 10_000;

    private static final int RANGE_STEP = 1_000_000;
    private static final int POISON_PILL = -1;
    private static final long TIMEOUT_MINUTES = 1;

    private BufferScenario() {
    }

    record Result(long producedSum, List<List<Integer>> received) {
        long consumedSum() {
            return received.stream().flatMap(List::stream).mapToLong(Integer::longValue).sum();
        }
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        report("QueueBuffer", new QueueBuffer<>());
        report("LockBuffer", new LockBuffer<>());
    }

    private static void report(String name, BoundedBuffer<Integer> buffer)
            throws InterruptedException, ExecutionException {
        Result result = run(buffer);
        verify(result);
        List<Integer> counts = result.received().stream().map(List::size).toList();
        System.out.printf("%-12s получено по потребителям: %s, сумма произведённых %d, сумма потреблённых %d, "
                + "дубликатов нет%n", name, counts, result.producedSum(), result.consumedSum());
    }

    static Result run(BoundedBuffer<Integer> buffer) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(PRODUCERS + CONSUMERS + 1);
        try {
            List<Future<List<Integer>>> consumers = new ArrayList<>();
            for (int i = 0; i < CONSUMERS; i++) {
                consumers.add(executor.submit(consumer(buffer)));
            }
            List<Future<Long>> producers = new ArrayList<>();
            for (int i = 0; i < PRODUCERS; i++) {
                producers.add(executor.submit(producer(buffer, i)));
            }
            Future<Void> pills = executor.submit(pillSender(buffer, producers));

            executor.shutdown();
            if (!executor.awaitTermination(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new IllegalStateException("Сценарий не завершился за " + TIMEOUT_MINUTES + " мин");
            }

            pills.get();
            long producedSum = 0;
            for (Future<Long> producer : producers) {
                producedSum += producer.get();
            }
            List<List<Integer>> received = new ArrayList<>();
            for (Future<List<Integer>> consumer : consumers) {
                received.add(consumer.get());
            }
            return new Result(producedSum, received);
        } finally {
            executor.shutdownNow();
        }
    }

    static void verify(Result result) {
        if (result.producedSum() != result.consumedSum()) {
            throw new IllegalStateException("Сумма потреблённых " + result.consumedSum()
                    + " не равна сумме произведённых " + result.producedSum());
        }
        List<List<Integer>> received = result.received();
        List<Integer> all = received.stream().flatMap(List::stream).sorted().toList();
        if (!all.equals(expectedItems())) {
            throw new IllegalStateException("Числа потеряны, продублированы или подменены");
        }
        for (List<Integer> items : received) {
            int[] last = new int[PRODUCERS];
            Arrays.fill(last, -1);
            for (int item : items) {
                int producer = item / RANGE_STEP;
                if (item <= last[producer]) {
                    throw new IllegalStateException("Нарушен порядок чисел производителя " + producer);
                }
                last[producer] = item;
            }
        }
    }

    static List<Integer> expectedItems() {
        return IntStream.range(0, PRODUCERS)
                .flatMap(producer -> IntStream.range(0, ITEMS_PER_PRODUCER).map(j -> firstItem(producer) + j))
                .boxed()
                .toList();
    }

    private static int firstItem(int producer) {
        return producer * RANGE_STEP;
    }

    private static Callable<Long> producer(BoundedBuffer<Integer> buffer, int producer) {
        return () -> {
            long sum = 0;
            int first = firstItem(producer);
            for (int item = first; item < first + ITEMS_PER_PRODUCER; item++) {
                buffer.put(item);
                sum += item;
            }
            return sum;
        };
    }

    private static Callable<List<Integer>> consumer(BoundedBuffer<Integer> buffer) {
        return () -> {
            List<Integer> received = new ArrayList<>();
            for (int item = buffer.take(); item != POISON_PILL; item = buffer.take()) {
                received.add(item);
            }
            return received;
        };
    }

    private static Callable<Void> pillSender(BoundedBuffer<Integer> buffer, List<Future<Long>> producers) {
        return () -> {
            for (Future<Long> producer : producers) {
                producer.get();
            }
            for (int i = 0; i < CONSUMERS; i++) {
                buffer.put(POISON_PILL);
            }
            return null;
        };
    }
}
