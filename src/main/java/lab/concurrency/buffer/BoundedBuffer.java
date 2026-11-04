package lab.concurrency.buffer;

public interface BoundedBuffer<T> {
    int CAPACITY = 10;

    void put(T data) throws InterruptedException;

    T take() throws InterruptedException;
}
