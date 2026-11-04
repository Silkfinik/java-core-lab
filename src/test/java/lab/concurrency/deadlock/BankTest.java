package lab.concurrency.deadlock;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Timeout(10)
class BankTest {
    private static final int THREADS = 10;
    private static final int TRANSFERS_PER_THREAD = 10_000;
    private static final int ACCOUNTS = 10;
    private static final long INITIAL_BALANCE = 1_000;
    private static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(5);

    @FunctionalInterface
    private interface Transfer {
        void execute(Account from, Account to, long amount) throws InterruptedException;
    }

    static Stream<Named<Transfer>> transfers() {
        Bank bank = new Bank();
        return Stream.of(
                Named.of("упорядочивание по id", bank::transferOrdered),
                Named.of("tryLock с повтором", bank::transferWithTryLock)
        );
    }

    @ParameterizedTest
    @MethodSource("transfers")
    void randomTransfersFinishInTimeAndKeepTotalBalance(Transfer transfer) throws InterruptedException {
        List<Account> accounts = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            accounts.add(new Account(INITIAL_BALANCE));
        }

        CountDownLatch start = new CountDownLatch(1);
        AtomicLong completed = new AtomicLong();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            // daemon: при взаимной блокировке зависшие потоки не должны мешать завершению JVM.
            threads.add(Thread.ofPlatform().daemon().start(() -> {
                try {
                    start.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int n = 0; n < TRANSFERS_PER_THREAD; n++) {
                        Account from = accounts.get(random.nextInt(ACCOUNTS));
                        Account to = accounts.get(random.nextInt(ACCOUNTS));
                        transfer.execute(from, to, random.nextLong(1, 100));
                        completed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        long deadline = System.nanoTime() + DEADLINE_NANOS;
        start.countDown();
        for (Thread thread : threads) {
            TimeUnit.NANOSECONDS.timedJoin(thread, Math.max(1, deadline - System.nanoTime()));
            assertFalse(thread.isAlive(), "потоки не завершились за 5 секунд");
        }

        // Без этой проверки упавший поток остался бы незамеченным: сумма от этого не меняется.
        assertEquals((long) THREADS * TRANSFERS_PER_THREAD, completed.get());
        long total = accounts.stream().mapToLong(Account::balance).sum();
        assertEquals(ACCOUNTS * INITIAL_BALANCE, total);
    }
}
