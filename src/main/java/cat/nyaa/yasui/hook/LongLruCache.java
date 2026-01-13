package cat.nyaa.yasui.hook;

import java.util.Arrays;

final class LongLruCache<V> {
    private static final float LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_TABLE_SIZE = 16;
    private static final int DEFAULT_ENTRY_CAPACITY = 16;

    private int limit;

    private long[] tableKeys;
    private int[] tableValues;
    private boolean[] tableUsed;
    private int mask;
    private int maxFill;
    private int size;

    private long[] entryKeys;
    private Object[] entryValues;
    private int[] entryPrev;
    private int[] entryNext;
    private int[] entryFreeNext;
    private int nextEntryIndex;
    private int freeListHead;
    private int head;
    private int tail;

    LongLruCache(int limit) {
        this.limit = Math.max(0, limit);
        reset();
    }

    synchronized void setLimit(int limit) {
        this.limit = Math.max(0, limit);
        if (this.limit == 0) {
            clear();
            return;
        }
        if (size > this.limit) {
            trimToLimit();
        }
    }

    synchronized V get(long key) {
        if (limit <= 0 || size == 0) {
            return null;
        }
        int pos = findSlot(key);
        if (pos < 0) {
            return null;
        }
        int entryIndex = tableValues[pos];
        moveToHead(entryIndex);
        @SuppressWarnings("unchecked")
        V value = (V) entryValues[entryIndex];
        return value;
    }

    synchronized void put(long key, V value) {
        if (limit <= 0) {
            return;
        }
        int pos = findSlot(key);
        if (pos >= 0) {
            int entryIndex = tableValues[pos];
            entryValues[entryIndex] = value;
            moveToHead(entryIndex);
            return;
        }
        int insertPos = -pos - 1;
        int entryIndex = allocateEntry(key, value);
        tableUsed[insertPos] = true;
        tableKeys[insertPos] = key;
        tableValues[insertPos] = entryIndex;
        size++;
        if (size > maxFill) {
            rehash(tableKeys.length * 2);
        }
        if (size > limit) {
            trimToLimit();
        }
    }

    synchronized void remove(long key) {
        if (size == 0) {
            return;
        }
        int pos = findSlot(key);
        if (pos < 0) {
            return;
        }
        removeEntryAt(pos);
    }

    synchronized int size() {
        return size;
    }

    synchronized void clear() {
        reset();
    }

    private void reset() {
        initTable(DEFAULT_TABLE_SIZE);
        initEntries(DEFAULT_ENTRY_CAPACITY);
        size = 0;
        head = -1;
        tail = -1;
        freeListHead = -1;
        nextEntryIndex = 0;
    }

    private void trimToLimit() {
        while (size > limit) {
            evictTail();
        }
    }

    private void evictTail() {
        int index = tail;
        if (index == -1) {
            return;
        }
        long key = entryKeys[index];
        int pos = findSlot(key);
        if (pos >= 0) {
            removeEntryAt(pos);
            return;
        }
        unlink(index);
        freeEntry(index);
        size--;
    }

    private void removeEntryAt(int pos) {
        int entryIndex = tableValues[pos];
        unlink(entryIndex);
        freeEntry(entryIndex);
        size--;
        shiftKeys(pos);
    }

    private void moveToHead(int index) {
        if (head == index) {
            return;
        }
        int prev = entryPrev[index];
        int next = entryNext[index];
        if (prev != -1) {
            entryNext[prev] = next;
        } else if (head == index) {
            head = next;
        }
        if (next != -1) {
            entryPrev[next] = prev;
        } else if (tail == index) {
            tail = prev;
        }
        entryPrev[index] = -1;
        entryNext[index] = head;
        if (head != -1) {
            entryPrev[head] = index;
        }
        head = index;
        if (tail == -1) {
            tail = index;
        }
    }

    private void unlink(int index) {
        int prev = entryPrev[index];
        int next = entryNext[index];
        if (prev != -1) {
            entryNext[prev] = next;
        } else {
            head = next;
        }
        if (next != -1) {
            entryPrev[next] = prev;
        } else {
            tail = prev;
        }
        entryPrev[index] = -1;
        entryNext[index] = -1;
    }

    private int allocateEntry(long key, V value) {
        int index;
        if (freeListHead != -1) {
            index = freeListHead;
            freeListHead = entryFreeNext[index];
        } else {
            index = nextEntryIndex;
            if (index == entryKeys.length) {
                growEntries();
            }
            nextEntryIndex++;
        }
        entryKeys[index] = key;
        entryValues[index] = value;
        entryPrev[index] = -1;
        entryNext[index] = -1;
        entryFreeNext[index] = -1;
        moveToHead(index);
        return index;
    }

    private void freeEntry(int index) {
        entryValues[index] = null;
        entryKeys[index] = 0L;
        entryPrev[index] = -1;
        entryNext[index] = -1;
        entryFreeNext[index] = freeListHead;
        freeListHead = index;
    }

    private int findSlot(long key) {
        int pos = mix(key) & mask;
        while (tableUsed[pos]) {
            if (tableKeys[pos] == key) {
                return pos;
            }
            pos = (pos + 1) & mask;
        }
        return -pos - 1;
    }

    private void shiftKeys(int pos) {
        int last;
        int slot;
        long curr;
        while (true) {
            pos = ((last = pos) + 1) & mask;
            while (tableUsed[pos]) {
                curr = tableKeys[pos];
                slot = mix(curr) & mask;
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) {
                    break;
                }
                pos = (pos + 1) & mask;
            }
            if (!tableUsed[pos]) {
                tableUsed[last] = false;
                tableKeys[last] = 0L;
                tableValues[last] = 0;
                return;
            }
            tableKeys[last] = tableKeys[pos];
            tableValues[last] = tableValues[pos];
            tableUsed[last] = true;
        }
    }

    private void rehash(int newSize) {
        long[] oldKeys = tableKeys;
        int[] oldValues = tableValues;
        boolean[] oldUsed = tableUsed;
        initTable(newSize);
        for (int i = 0; i < oldKeys.length; i++) {
            if (!oldUsed[i]) {
                continue;
            }
            long key = oldKeys[i];
            int pos = mix(key) & mask;
            while (tableUsed[pos]) {
                pos = (pos + 1) & mask;
            }
            tableUsed[pos] = true;
            tableKeys[pos] = key;
            tableValues[pos] = oldValues[i];
        }
    }

    private void initTable(int capacity) {
        int size = 1;
        int target = Math.max(DEFAULT_TABLE_SIZE, capacity);
        while (size < target) {
            size <<= 1;
        }
        tableKeys = new long[size];
        tableValues = new int[size];
        tableUsed = new boolean[size];
        mask = size - 1;
        maxFill = maxFill(size, LOAD_FACTOR);
    }

    private void initEntries(int capacity) {
        entryKeys = new long[capacity];
        entryValues = new Object[capacity];
        entryPrev = new int[capacity];
        entryNext = new int[capacity];
        entryFreeNext = new int[capacity];
        Arrays.fill(entryPrev, -1);
        Arrays.fill(entryNext, -1);
        Arrays.fill(entryFreeNext, -1);
    }

    private void growEntries() {
        int oldCapacity = entryKeys.length;
        int newCapacity = oldCapacity * 2;
        entryKeys = Arrays.copyOf(entryKeys, newCapacity);
        entryValues = Arrays.copyOf(entryValues, newCapacity);
        entryPrev = Arrays.copyOf(entryPrev, newCapacity);
        entryNext = Arrays.copyOf(entryNext, newCapacity);
        entryFreeNext = Arrays.copyOf(entryFreeNext, newCapacity);
        Arrays.fill(entryPrev, oldCapacity, newCapacity, -1);
        Arrays.fill(entryNext, oldCapacity, newCapacity, -1);
        Arrays.fill(entryFreeNext, oldCapacity, newCapacity, -1);
    }

    private static int maxFill(int n, float f) {
        return Math.min(n - 1, (int) Math.ceil(n * f));
    }

    private static int mix(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (int) x;
    }
}
