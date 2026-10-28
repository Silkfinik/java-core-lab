package lab.collections;

import java.util.Objects;

/**
 * Хеш-таблица с разрешением коллизий цепочками.
 *
 * <p>Ёмкость всегда степень двойки (начальная 16), load factor 0.75. Когда
 * {@code size > capacity * 0.75}, ёмкость удваивается и все узлы перераспределяются.
 * Поддерживаются ключ {@code null} (всегда в бакете 0) и значения {@code null}.
 *
 * <p>Сложность {@code put}, {@code get}, {@code remove}, {@code containsKey}:
 * <ul>
 *     <li>в среднем — O(1) при равномерном распределении хешей;</li>
 *     <li>в худшем — O(n), когда все ключи попадают в один бакет
 *     (деревьев в бакетах нет, цепочка просматривается линейно).</li>
 * </ul>
 * {@code put} с расширением таблицы стоит O(n), но амортизированно остаётся O(1).
 * {@code size} и {@code isEmpty} — O(1).
 */
public class MyHashMap<K, V> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private Node<K, V>[] table;
    private int size;

    public MyHashMap() {
        table = newTable(DEFAULT_CAPACITY);
    }

    /**
     * Связывает значение с ключом. Если ключ уже был, заменяет значение.
     *
     * @return предыдущее значение для ключа или {@code null}, если его не было
     */
    public V put(K key, V value) {
        int hash = hash(key);
        int index = indexFor(hash, table.length);

        for (Node<K, V> node = table[index]; node != null; node = node.next) {
            if (node.matches(hash, key)) {
                V oldValue = node.value;
                node.value = value;
                return oldValue;
            }
        }

        table[index] = new Node<>(hash, key, value, table[index]);
        size++;
        if (size > table.length * LOAD_FACTOR) {
            resize();
        }
        return null;
    }

    /**
     * @return значение для ключа или {@code null}, если ключа нет
     */
    public V get(Object key) {
        Node<K, V> node = findNode(key);
        return node == null ? null : node.value;
    }

    /**
     * Удаляет ключ вместе со значением.
     *
     * @return удалённое значение или {@code null}, если ключа не было
     */
    public V remove(Object key) {
        int hash = hash(key);
        int index = indexFor(hash, table.length);

        Node<K, V> prev = null;
        for (Node<K, V> node = table[index]; node != null; prev = node, node = node.next) {
            if (node.matches(hash, key)) {
                if (prev == null) {
                    table[index] = node.next;
                } else {
                    prev.next = node.next;
                }
                size--;
                return node.value;
            }
        }
        return null;
    }

    /**
     * @return {@code true}, если ключ есть в мапе
     */
    public boolean containsKey(Object key) {
        return findNode(key) != null;
    }

    /**
     * @return количество пар ключ-значение
     */
    public int size() {
        return size;
    }

    /**
     * @return {@code true}, если мапа пуста
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Только для тестов.
     *
     * @return текущий размер массива бакетов
     */
    int capacity() {
        return table.length;
    }

    private Node<K, V> findNode(Object key) {
        int hash = hash(key);
        for (Node<K, V> node = table[indexFor(hash, table.length)]; node != null; node = node.next) {
            if (node.matches(hash, key)) {
                return node;
            }
        }
        return null;
    }

    private void resize() {
        Node<K, V>[] oldTable = table;
        Node<K, V>[] newTable = newTable(oldTable.length * 2);

        for (Node<K, V> head : oldTable) {
            Node<K, V> node = head;
            while (node != null) {
                Node<K, V> next = node.next;
                int index = indexFor(node.hash, newTable.length);
                node.next = newTable[index];
                newTable[index] = node;
                node = next;
            }
        }
        table = newTable;
    }

    private static int hash(Object key) {
        if (key == null) {
            return 0;
        }
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private static int indexFor(int hash, int capacity) {
        return hash & (capacity - 1);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Node<K, V>[] newTable(int capacity) {
        return (Node<K, V>[]) new Node[capacity];
    }

    private static final class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        boolean matches(int hash, Object key) {
            return this.hash == hash && Objects.equals(this.key, key);
        }
    }
}
