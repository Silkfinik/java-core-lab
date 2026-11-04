package lab.concurrency.deadlock;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Bank {
    private static final long TRY_LOCK_TIMEOUT_MILLIS = 50;
    private static final long MAX_BACKOFF_MILLIS = 10;

    /**
     * Намеренно небезопасный вариант: порядок захвата задаётся аргументами, поэтому встречные
     * переводы a -> b и b -> a могут взять по одному монитору и вечно ждать друг друга.
     */
    public void transfer(Account from, Account to, long amount) {
        synchronized (from) {
            synchronized (to) {
                move(from, to, amount);
            }
        }
    }

    /**
     * Все потоки берут мониторы в порядке возрастания id, поэтому цикл ожидания невозможен.
     */
    public void transferOrdered(Account from, Account to, long amount) {
        Account first = from.id() < to.id() ? from : to;
        Account second = first == from ? to : from;

        synchronized (first) {
            synchronized (second) {
                move(from, to, amount);
            }
        }
    }

    /**
     * Порядок захвата здесь не важен: не получив вторую блокировку, поток отпускает первую
     * и повторяет попытку, поэтому никто не ждёт, удерживая счёт, и цикл ожидания невозможен.
     * <p>
     * Таймаут только на первой блокировке, вторая пробуется без ожидания. С таймаутом на обеих
     * встречные потоки держат счета по 50 мс, остальные встают за ними в очередь, и 100 000
     * переводов занимают 5-40 с вместо долей секунды.
     */
    public void transferWithTryLock(Account from, Account to, long amount) throws InterruptedException {
        while (true) {
            if (from.lock().tryLock(TRY_LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock().tryLock()) {
                        try {
                            move(from, to, amount);
                            return;
                        } finally {
                            to.lock().unlock();
                        }
                    }
                } finally {
                    from.lock().unlock();
                }
            }
            // Случайная пауза: при одинаковой задержке встречные потоки могут снова и снова
            // сталкиваться в одном и том же порядке (livelock).
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, MAX_BACKOFF_MILLIS + 1));
        }
    }

    private static void move(Account from, Account to, long amount) {
        from.add(-amount);
        to.add(amount);
    }
}
