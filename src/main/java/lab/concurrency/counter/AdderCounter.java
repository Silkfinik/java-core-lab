package lab.concurrency.counter;

import java.util.concurrent.atomic.LongAdder;

public class AdderCounter implements Counter {
    private final LongAdder count = new LongAdder();

    @Override
    public void increment() {
        count.increment();
    }

    @Override
    public long get() {
        return count.sum();
    }
}
