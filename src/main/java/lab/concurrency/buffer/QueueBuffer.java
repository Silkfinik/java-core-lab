package lab.concurrency.buffer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class QueueBuffer<T> implements BoundedBuffer<T> {
    private final BlockingQueue<T> queue = new ArrayBlockingQueue<>(CAPACITY);

    @Override
    public void put(T data) throws InterruptedException {
        queue.put(data);
    }

    @Override
    public T take() throws InterruptedException {
        return queue.take();
    }
}
