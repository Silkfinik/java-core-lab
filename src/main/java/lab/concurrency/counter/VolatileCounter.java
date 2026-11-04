package lab.concurrency.counter;

public class VolatileCounter implements Counter {
    private volatile long count;

    @Override
    public void increment() {
        count++;
    }

    @Override
    public long get() {
        return count;
    }
}
