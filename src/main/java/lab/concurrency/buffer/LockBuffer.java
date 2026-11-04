package lab.concurrency.buffer;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LockBuffer<T> implements BoundedBuffer<T> {
    private final Queue<T> items = new ArrayDeque<>(CAPACITY);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    @Override
    public void put(T data) throws InterruptedException {
        Objects.requireNonNull(data);
        lock.lockInterruptibly();
        try {
            // while, а не if: см. комментарий в take(). С if производитель после пробуждения
            // добавит элемент в уже заполненный буфер, и ArrayDeque молча превысит CAPACITY.
            while (items.size() == CAPACITY) {
                notFull.await();
            }
            items.add(data);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            // Почему while, а не if: возврат из await() не гарантирует, что условие всё ещё истинно.
            // signal() только переводит поток в очередь на захват lock, и пока он её ждёт, другой
            // потребитель может захватить lock первым и забрать единственный элемент. Кроме того,
            // await() может завершиться и без signal() (spurious wakeup). С if условие повторно
            // не проверяется, и items.remove() на пустой очереди бросит NoSuchElementException.
            while (items.isEmpty()) {
                notEmpty.await();
            }
            T data = items.remove();
            notFull.signal();
            return data;
        } finally {
            lock.unlock();
        }
    }
}
