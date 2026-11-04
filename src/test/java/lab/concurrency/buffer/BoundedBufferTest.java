package lab.concurrency.buffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class BoundedBufferTest {
    private static final long JOIN_MILLIS = 5_000;

    static Stream<BoundedBuffer<Integer>> buffers() {
        return Stream.of(new QueueBuffer<>(), new LockBuffer<>());
    }

    @ParameterizedTest
    @MethodSource("buffers")
    @DisplayName("элементы выходят в порядке поступления")
    void keepsFifoOrder(BoundedBuffer<Integer> buffer) throws InterruptedException {
        for (int i = 0; i < BoundedBuffer.CAPACITY; i++) {
            buffer.put(i);
        }

        for (int i = 0; i < BoundedBuffer.CAPACITY; i++) {
            assertEquals(i, buffer.take());
        }
    }

    @ParameterizedTest
    @MethodSource("buffers")
    @DisplayName("put блокируется на 11-м элементе, пока take не освободит место")
    void putBlocksWhenFull(BoundedBuffer<Integer> buffer) throws InterruptedException {
        for (int i = 0; i < BoundedBuffer.CAPACITY; i++) {
            buffer.put(i);
        }

        Thread putter = Thread.ofPlatform().start(() -> assertDoesNotThrow(() -> buffer.put(BoundedBuffer.CAPACITY)));
        awaitBlocked(putter);

        assertEquals(0, buffer.take());
        putter.join(JOIN_MILLIS);
        assertFalse(putter.isAlive());

        for (int i = 1; i <= BoundedBuffer.CAPACITY; i++) {
            assertEquals(i, buffer.take());
        }
    }

    @ParameterizedTest
    @MethodSource("buffers")
    @DisplayName("take блокируется на пустом буфере, пока put не добавит элемент")
    void takeBlocksWhenEmpty(BoundedBuffer<Integer> buffer) throws InterruptedException {
        List<Integer> taken = new ArrayList<>();
        Thread taker = Thread.ofPlatform().start(() -> assertDoesNotThrow(() -> taken.add(buffer.take())));
        awaitBlocked(taker);

        buffer.put(42);
        taker.join(JOIN_MILLIS);

        assertFalse(taker.isAlive());
        assertEquals(List.of(42), taken);
    }

    @ParameterizedTest
    @MethodSource("buffers")
    @DisplayName("прерывание снимает блокировку, и буфер остаётся рабочим")
    void blockedTakeIsInterruptible(BoundedBuffer<Integer> buffer) throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread taker = Thread.ofPlatform().start(() -> {
            try {
                buffer.take();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        awaitBlocked(taker);

        taker.interrupt();
        taker.join(JOIN_MILLIS);

        assertTrue(interrupted.get());
        buffer.put(1);
        assertEquals(1, buffer.take());
    }

    @ParameterizedTest
    @MethodSource("buffers")
    @DisplayName("null отклоняется")
    void rejectsNull(BoundedBuffer<Integer> buffer) {
        assertThrows(NullPointerException.class, () -> buffer.put(null));
    }

    @RepeatedTest(10)
    @DisplayName("сценарий на QueueBuffer: суммы равны, дубликатов нет")
    void scenarioOnQueueBuffer() throws InterruptedException, ExecutionException {
        assertScenarioCorrect(new QueueBuffer<>());
    }

    @RepeatedTest(10)
    @DisplayName("сценарий на LockBuffer: суммы равны, дубликатов нет")
    void scenarioOnLockBuffer() throws InterruptedException, ExecutionException {
        assertScenarioCorrect(new LockBuffer<>());
    }

    @Test
    @DisplayName("проверка сценария ловит потерю числа")
    void verifyDetectsLostItem() {
        List<Integer> items = new ArrayList<>(BufferScenario.expectedItems());
        items.removeLast();

        assertVerifyFails(items);
    }

    @Test
    @DisplayName("проверка сценария ловит дубликаты, даже если сумма совпала")
    void verifyDetectsDuplicateWithSameSum() {
        List<Integer> items = new ArrayList<>(BufferScenario.expectedItems());
        items.set(0, 1);
        items.set(2, 1);

        assertVerifyFails(items);
    }

    @Test
    @DisplayName("проверка сценария ловит нарушение порядка внутри производителя")
    void verifyDetectsReorder() {
        List<Integer> items = new ArrayList<>(BufferScenario.expectedItems());
        items.set(0, items.get(1));
        items.set(1, BufferScenario.expectedItems().get(0));

        assertVerifyFails(items);
    }

    private static void assertScenarioCorrect(BoundedBuffer<Integer> buffer)
            throws InterruptedException, ExecutionException {
        BufferScenario.Result result = BufferScenario.run(buffer);

        assertEquals(result.producedSum(), result.consumedSum());
        List<Integer> all = result.received().stream().flatMap(List::stream).toList();
        assertEquals(BufferScenario.PRODUCERS * BufferScenario.ITEMS_PER_PRODUCER, all.size());
        assertEquals(all.size(), all.stream().distinct().count());
        assertEquals(BufferScenario.CONSUMERS, result.received().size());
        BufferScenario.verify(result);
    }

    private static void assertVerifyFails(List<Integer> received) {
        long producedSum = BufferScenario.expectedItems().stream().mapToLong(Integer::longValue).sum();
        BufferScenario.Result result = new BufferScenario.Result(producedSum, List.of(received));

        assertThrows(IllegalStateException.class, () -> BufferScenario.verify(result));
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        while (thread.getState() != Thread.State.WAITING) {
            assertTrue(thread.isAlive(), "поток завершился, не заблокировавшись");
            Thread.sleep(1);
        }
    }
}
