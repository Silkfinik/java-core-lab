package lab.concurrency.counter;

public class UnsafeCounter implements Counter {
    private long count;

    @Override
    public void increment() {
        count++;
    }

    @Override
    public long get() {
        return count;
    }
}
