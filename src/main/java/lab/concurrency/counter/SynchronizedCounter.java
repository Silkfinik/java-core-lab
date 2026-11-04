package lab.concurrency.counter;

public class SynchronizedCounter implements Counter {
    private long count;

    @Override
    public synchronized void increment() {
        count++;
    }

    @Override
    public synchronized long get() {
        return count;
    }
}
