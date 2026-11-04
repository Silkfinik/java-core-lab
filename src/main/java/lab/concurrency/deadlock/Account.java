package lab.concurrency.deadlock;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class Account {
    private static final AtomicLong NEXT_ID = new AtomicLong();

    // Уникальность id обязательна для transferOrdered: при равных id двум потокам
    // не из чего вывести общий порядок захвата.
    private final long id = NEXT_ID.incrementAndGet();
    // Монитор счёта и lock не связаны между собой: варианты перевода из Bank нельзя смешивать
    // на одних и тех же счетах, иначе они не исключают друг друга.
    private final ReentrantLock lock = new ReentrantLock();
    // Защищён блокировкой, которую берёт выбранный вариант перевода.
    private long balance;

    public Account(long balance) {
        this.balance = balance;
    }

    public long id() {
        return id;
    }

    // Без синхронизации: читать согласованное значение можно только после завершения переводов.
    public long balance() {
        return balance;
    }

    ReentrantLock lock() {
        return lock;
    }

    void add(long amount) {
        balance += amount;
    }
}
