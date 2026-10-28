package lab.collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyHashMapTest {

    @Test
    @DisplayName("put и get одного ключа; повторный put возвращает старое значение")
    void putAndGet() {
        MyHashMap<String, Integer> map = new MyHashMap<>();

        assertNull(map.put("a", 1));
        assertEquals(1, map.get("a"));

        assertEquals(1, map.put("a", 2));
        assertEquals(2, map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("remove возвращает значение и уменьшает размер; удаление отсутствующего ключа возвращает null")
    void remove() {
        MyHashMap<String, Integer> map = new MyHashMap<>();
        map.put("a", 1);
        map.put("b", 2);

        assertEquals(1, map.remove("a"));
        assertEquals(1, map.size());
        assertNull(map.get("a"));

        assertNull(map.remove("missing"));
        assertEquals(1, map.size());
    }

    @Test
    @DisplayName("Ключ null работает")
    void nullKey() {
        MyHashMap<String, Integer> map = new MyHashMap<>();

        assertNull(map.put(null, 1));
        assertEquals(1, map.get(null));
        assertEquals(1, map.put(null, 2));
        assertEquals(2, map.get(null));
        assertEquals(1, map.size());

        assertEquals(2, map.remove(null));
        assertNull(map.get(null));
        assertEquals(0, map.size());
    }

    @Test
    @DisplayName("1 000 ключей класса с константным hashCode() все читаются корректно")
    void constantHashCodeKeys() {
        MyHashMap<CollidingKey, Integer> map = new MyHashMap<>();
        int count = 1_000;

        for (int i = 0; i < count; i++) {
            map.put(new CollidingKey(i), i);
        }

        assertEquals(count, map.size());
        for (int i = 0; i < count; i++) {
            assertEquals(i, map.get(new CollidingKey(i)));
        }
    }

    @Test
    @DisplayName("После 13 вставок capacity() равна 32")
    void resizeAfterThreshold() {
        MyHashMap<Integer, Integer> map = new MyHashMap<>();

        for (int i = 0; i < 13; i++) {
            map.put(i, i);
        }

        assertEquals(32, map.capacity());
    }

    @Test
    @DisplayName("100 000 случайных операций (new Random(42)) дают те же результаты, что java.util.HashMap")
    void randomOperationsMatchHashMap() {
        MyHashMap<Integer, Integer> actual = new MyHashMap<>();
        Map<Integer, Integer> expected = new HashMap<>();
        Random random = new Random(42);
        int keyRange = 1_000;

        for (int i = 0; i < 100_000; i++) {
            int key = random.nextInt(keyRange);
            switch (random.nextInt(3)) {
                case 0 -> {
                    int value = random.nextInt();
                    assertEquals(expected.put(key, value), actual.put(key, value), "put at step " + i);
                }
                case 1 -> assertEquals(expected.get(key), actual.get(key), "get at step " + i);
                default -> assertEquals(expected.remove(key), actual.remove(key), "remove at step " + i);
            }
            assertEquals(expected.size(), actual.size(), "size at step " + i);
        }

        for (int key = 0; key < keyRange; key++) {
            assertEquals(expected.get(key), actual.get(key), "final get " + key);
        }
    }

    @Test
    @DisplayName("В Javadoc класса указана сложность операций в среднем и худшем случае")
    void javadocDescribesComplexity() throws IOException {
        String source = Files.readString(Path.of("src/main/java/lab/collections/MyHashMap.java"));
        int classIndex = source.indexOf("public class MyHashMap");
        int javadocStart = source.lastIndexOf("/**", classIndex);
        assertTrue(javadocStart >= 0, "У класса нет Javadoc");

        String javadoc = source.substring(javadocStart, classIndex);
        assertTrue(javadoc.contains("O(1)"), "Не указана сложность в среднем случае");
        assertTrue(javadoc.contains("O(n)") || javadoc.contains("O(log n)"),
                "Не указана сложность в худшем случае");
    }

    private record CollidingKey(int id) {
        @Override
        public int hashCode() {
            return 42;
        }
    }
}
