package net.momirealms.craftengine.core.util;

import ca.spottedleaf.common.util.HashUtil;
import ca.spottedleaf.common.util.IntegerUtil;
import ca.spottedleaf.common.util.ThrowUtil;
import ca.spottedleaf.concurrentutil.collection.iterator.BaseObjectIterator;
import ca.spottedleaf.concurrentutil.map.BaseMapCollection;
import ca.spottedleaf.concurrentutil.map.BaseMapSet;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;

import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class ConcurrentChainedUUID2ReferenceHashTable<V> implements Iterable<ConcurrentChainedUUID2ReferenceHashTable.TableEntry<V>> {
    protected static final int DEFAULT_CAPACITY = 16;
    protected static final float DEFAULT_LOAD_FACTOR = 0.75f;
    protected static final int MAXIMUM_CAPACITY = Integer.MIN_VALUE >>> 1;
    protected static final int THRESHOLD_NO_RESIZE = -1;
    protected static final int THRESHOLD_RESIZING = -2;
    protected static final VarHandle THRESHOLD_HANDLE = ConcurrentUtil.getVarHandle(ConcurrentChainedUUID2ReferenceHashTable.class, "threshold", int.class);
    private static final TableEntry<?> RESIZE_NODE = new TableEntry<>(new UUID(0L, 0L), null);
    protected final AtomicLong size = new AtomicLong();
    protected final float loadFactor;
    protected volatile TableEntry<V>[] table;
    protected volatile TableEntry<V>[] nextTable;
    protected volatile int threshold;
    protected Values<V> values;
    protected EntrySet<V> entrySet;

    public ConcurrentChainedUUID2ReferenceHashTable() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    protected ConcurrentChainedUUID2ReferenceHashTable(final int capacity, final float loadFactor) {
        final int tableSize = getCapacityFor(capacity);

        if (loadFactor <= 0.0 || !Float.isFinite(loadFactor)) {
            throw new IllegalArgumentException("Invalid load factor: " + loadFactor);
        }

        if (tableSize == MAXIMUM_CAPACITY) {
            this.setThresholdPlain(THRESHOLD_NO_RESIZE);
        } else {
            this.setThresholdPlain(getTargetThreshold(tableSize, loadFactor));
        }

        this.loadFactor = loadFactor;
        // noinspection unchecked
        this.table = (TableEntry<V>[]) new TableEntry[tableSize];
        this.nextTable = this.table;
    }

    public static int defaultCapacity() {
        return DEFAULT_CAPACITY;
    }

    public static float defaultLoadFactor() {
        return DEFAULT_LOAD_FACTOR;
    }

    protected static int getTargetThreshold(final int capacity, final float loadFactor) {
        final double ret = (double) capacity * (double) loadFactor;
        if (Double.isInfinite(ret) || ret >= ((double) Integer.MAX_VALUE - 1)) {
            return THRESHOLD_NO_RESIZE;
        }

        return (int) Math.ceil(ret);
    }

    protected static int getCapacityFor(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Invalid capacity: " + capacity);
        }
        if (capacity >= MAXIMUM_CAPACITY) {
            return MAXIMUM_CAPACITY;
        }
        return IntegerUtil.roundCeilLog2(capacity);
    }

    public static <V> ConcurrentChainedUUID2ReferenceHashTable<V> createWithCapacity(final int capacity) {
        return createWithCapacity(capacity, DEFAULT_LOAD_FACTOR);
    }

    public static <V> ConcurrentChainedUUID2ReferenceHashTable<V> createWithCapacity(final int capacity, final float loadFactor) {
        return new ConcurrentChainedUUID2ReferenceHashTable<>(capacity, loadFactor);
    }

    public static <V> ConcurrentChainedUUID2ReferenceHashTable<V> createWithExpected(final int expected) {
        return createWithExpected(expected, DEFAULT_LOAD_FACTOR);
    }

    public static <V> ConcurrentChainedUUID2ReferenceHashTable<V> createWithExpected(final int expected, final float loadFactor) {
        double capacity = Math.ceil((double) expected / (double) loadFactor);
        if (!Double.isFinite(capacity)) {
            throw new IllegalArgumentException("Invalid load factor");
        }
        if (capacity > (double) Integer.MAX_VALUE) {
            capacity = (double) Integer.MAX_VALUE;
        }

        return createWithCapacity((int) capacity, loadFactor);
    }

    /**
     * must be deterministic given a key
     */
    // define for subclasses to use
    protected static int getHash(final UUID key) {
        // UUID.hashCode() folds both 64-bit halves, but we mix the 128-bit value directly to preserve distribution.
        final long hash = key.getMostSignificantBits() ^ Long.rotateLeft(key.getLeastSignificantBits(), 32);
        return (int) HashUtil.mix(hash);
    }

    protected static <V> TableEntry<V> getAtIndexAcquire(final TableEntry<V>[] table, final int index) {
        //noinspection unchecked
        return (TableEntry<V>) TableEntry.TABLE_ENTRY_ARRAY_HANDLE.getAcquire(table, index);
    }

    protected static <V> void setAtIndexRelease(final TableEntry<V>[] table, final int index, final TableEntry<V> value) {
        TableEntry.TABLE_ENTRY_ARRAY_HANDLE.setRelease(table, index, value);
    }

    protected static <V> void setAtIndexVolatile(final TableEntry<V>[] table, final int index, final TableEntry<V> value) {
        TableEntry.TABLE_ENTRY_ARRAY_HANDLE.setVolatile(table, index, value);
    }

    protected static <V> TableEntry<V> compareAndExchangeAtIndexVolatile(final TableEntry<V>[] table, final int index,
                                                                         final TableEntry<V> expect, final TableEntry<V> update) {
        //noinspection unchecked
        return (TableEntry<V>) TableEntry.TABLE_ENTRY_ARRAY_HANDLE.compareAndExchange(table, index, expect, update);
    }

    protected final int getThresholdAcquire() {
        return (int) THRESHOLD_HANDLE.getAcquire(this);
    }

    protected final int getThresholdVolatile() {
        return (int) THRESHOLD_HANDLE.getVolatile(this);
    }

    protected final void setThresholdVolatile(final int threshold) {
        THRESHOLD_HANDLE.setVolatile(this, threshold);
    }

    protected final void setThresholdPlain(final int threshold) {
        THRESHOLD_HANDLE.set(this, threshold);
    }

    protected final void setThresholdRelease(final int threshold) {
        THRESHOLD_HANDLE.setRelease(this, threshold);
    }

    protected final int compareExchangeThresholdVolatile(final int expect, final int update) {
        return (int) THRESHOLD_HANDLE.compareAndExchange(this, expect, update);
    }

    /**
     * Returns the load factor associated with this map.
     */
    public final float getLoadFactor() {
        return this.loadFactor;
    }

    protected TableEntry<V>[] fetchNewTable(final TableEntry<V>[] expectedCurr) {
        final TableEntry<V>[] candidate = this.nextTable;
        final TableEntry<V>[] current = this.table;
        // Note: We fetch a new table once RESIZE_NODE is encountered in the expectedCurr table.
        //       The resize logic guarantees that the RESIZE_NODE is only written to a bin once
        //       the chain is fully moved to the next table. Provided that we actually fetch the next table,
        //       we can guarantee that we will see the chain in the next table. However, we may not end up fetching
        //       the next table, but rather the resize after that - which will not guarantee that we see the correct
        //       chain. We catch this race condition by checking if the current table is the same as the expected table.
        //       If the current table is not the expected table, then we just use that - as it is guaranteed to either
        //       contain the full chain or be referenced to the next table.
        // Note: the read order of next and current table is important, do not re-order.
        return expectedCurr == current ? candidate : current;
    }

    /**
     * Returns the possible node associated with the key, or {@code null} if there is no such node.
     * The node returned may have a {@code null} {@link TableEntry#value}, in which case the node is a placeholder for
     * a compute/computeIfAbsent call. The placeholder node should not be considered mapped in order to preserve
     * happens-before relationships between writes and reads in the map.
     */
    protected final TableEntry<V> getNode(final UUID key) {
        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        for (; ; ) {
            TableEntry<V> node = getAtIndexAcquire(table, hash & (table.length - 1));

            if (node == null) {
                // node == null
                return node;
            }

            if (node == RESIZE_NODE) {
                table = this.fetchNewTable(table);
                continue;
            }

            do {
                if (key.equals(node.key)) {
                    return node;
                }

                node = node.getNextAcquire();
            } while (node != null);

            // node == null
            return node;
        }
    }

    /**
     * Returns the currently mapped value associated with the specified key, or {@code null} if there is none.
     *
     * @param key Specified key
     * @throws NullPointerException If key is null
     */
    public V get(final UUID key) {


        final TableEntry<V> node = this.getNode(key);
        // note: getValueVolatile == null if a placement node, so we don't need to check it
        return node == null ? null : node.getValueVolatile();
    }

    /**
     * Returns the currently mapped value associated with the specified key, or the specified default value if there is none.
     *
     * @param key          Specified key
     * @param defaultValue Specified default value
     * @throws NullPointerException If key is null
     */
    public V getOrDefault(final UUID key, final V defaultValue) {


        final TableEntry<V> node = this.getNode(key);
        if (node == null) {
            return defaultValue;
        }

        final V ret = node.getValueVolatile();
        if (ret == null) {
            // ret == null for nodes pre-allocated to compute() and friends
            return defaultValue;
        }

        return ret;
    }

    /**
     * Returns whether the specified key is mapped to some value.
     *
     * @param key Specified key
     * @throws NullPointerException If key is null
     */
    public boolean containsKey(final UUID key) {


        // must also check for null, for nodes pre-allocated to compute() and friends
        final TableEntry<V> node = this.getNode(key);
        return node != null && node.getValueVolatile() != null;
    }

    /**
     * Returns whether the specified value has a key mapped to it.
     *
     * @param value Specified value
     * @throws NullPointerException If value is null
     */
    public boolean containsValue(final V value) {
        Objects.requireNonNull(value, "Value may not be null");

        final NodeIterator<V> iterator = new NodeIterator<>(this);

        TableEntry<V> node;
        while ((node = iterator.findNext()) != null) {
            // need to use acquire here to ensure the happens-before relationship
            final V nodeValue = node.getValueAcquire();
            if ((value == nodeValue)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the specified key is mapped and is mapped to the specified value.
     *
     * @param key   Specified key
     * @param value Specified value
     * @throws NullPointerException If key is null
     * @throws NullPointerException If value is null
     */
    public boolean contains(final UUID key, final V value) {

        Objects.requireNonNull(value, "Value may not be null");

        final TableEntry<V> node = this.getNode(key);

        if (node == null) {
            return false;
        }

        final V nodeValue = node.getValueAcquire();

        // value != null due to check, so we check placeholder node implicitly
        return (value == nodeValue);
    }

    /**
     * Returns the number of mappings in this map.
     */
    public int size() {
        return (int) Math.clamp(this.size.get(), 0L, (long) Integer.MAX_VALUE);
    }

    /**
     * Returns whether this map has no mappings.
     */
    public boolean isEmpty() {
        return this.size.get() <= 0L;
    }

    /**
     * Adds count to size and checks threshold for resizing
     */
    protected final void addSize(final long count) {
        final long sum = this.size.addAndGet(count);

        final int threshold = this.getThresholdAcquire();

        if (threshold < 0L) {
            // resizing or no resizing allowed, in either cases we do not need to do anything
            return;
        }

        if (sum < (long) threshold) {
            return;
        }

        if (threshold != this.compareExchangeThresholdVolatile(threshold, THRESHOLD_RESIZING)) {
            // some other thread resized
            return;
        }

        // create new table
        this.resize(sum);
    }

    /**
     * Resizes table, only invoke for the thread which has successfully updated threshold to {@link #THRESHOLD_RESIZING}
     *
     * @param sum Estimate of current mapping count, must be >= old threshold
     */
    private void resize(final long sum) {
        int capacity;

        // add 1.0, as sum may equal threshold (in which case, sum / loadFactor = current capacity)
        // adding 1.0 should at least raise the size by a factor of two due to usage of roundCeilLog2
        final double targetD = ((double) sum / (double) this.loadFactor) + 1.0;
        if (targetD >= (double) MAXIMUM_CAPACITY) {
            capacity = MAXIMUM_CAPACITY;
        } else {
            capacity = (int) Math.ceil(targetD);
            capacity = IntegerUtil.roundCeilLog2(capacity);
            capacity = Math.min(capacity, MAXIMUM_CAPACITY);
        }

        // create new table data

        // noinspection unchecked
        final TableEntry<V>[] newTable = new TableEntry[capacity];

        // transfer nodes from old table

        // does not need to be volatile read, just plain
        final TableEntry<V>[] oldTable = this.table;
        this.nextTable = newTable;

        // when resizing, the old entries at bin i (where i = hash % oldTable.length) are assigned to
        // bin k in the new table (where k = hash % newTable.length)
        // since both table lengths are powers of two (specifically, newTable is a multiple of oldTable),
        // the possible number of locations in the new table to assign any given i is newTable.length/oldTable.length

        // we can build the new linked nodes for the new table by using a work array sized to newTable.length/oldTable.length
        // which holds the _last_ entry in the chain per bin

        final int capOldShift = IntegerUtil.floorLog2(oldTable.length);
        final int capDiffShift = IntegerUtil.floorLog2(capacity) - capOldShift;

        if (capDiffShift == 0) {
            throw new IllegalStateException("Resizing to same size");
        }

        // noinspection unchecked
        final TableEntry<V>[] work = new TableEntry[1 << capDiffShift]; // typically, capDiffShift = 1

        for (int i = 0, len = oldTable.length; i < len; ++i) {
            TableEntry<V> binNode = getAtIndexAcquire(oldTable, i);

            for (; ; ) {
                if (binNode == null) {
                    // just need to replace the bin node, do not need to move anything
                    if (null == (binNode = compareAndExchangeAtIndexVolatile(oldTable, i, null, (TableEntry<V>) RESIZE_NODE))) {
                        break;
                    } // else: binNode != null, fall through
                }

                // need write lock to block other writers
                synchronized (binNode) {
                    if (binNode != (binNode = getAtIndexAcquire(oldTable, i))) {
                        continue;
                    }

                    // an important detail of resizing is that we do not need to be concerned with synchronisation on
                    // writes to the new table, as no access to any nodes on bin i on oldTable will occur until a thread
                    // sees the resizeNode
                    // specifically, as long as the resizeNode is release written there are no cases where another thread
                    // will see our writes to the new table

                    TableEntry<V> next = binNode.getNextPlain();

                    if (next == null) {
                        // simple case: do not use work array

                        // do not need to create new node, readers only need to see the state of the map at the
                        // beginning of a call, so any additions onto _next_ don't really matter
                        // additionally, the old node is replaced so that writers automatically forward to the new table,
                        // which resolves any issues
                        newTable[getHash(binNode.key) & (capacity - 1)] = binNode;
                    } else {
                        // reset for next usage
                        Arrays.fill(work, null);

                        for (TableEntry<V> curr = binNode; curr != null; curr = curr.getNextPlain()) {
                            final int newTableIdx = getHash(curr.key) & (capacity - 1);
                            final int workIdx = newTableIdx >>> capOldShift;

                            final TableEntry<V> replace = new TableEntry<>(curr.key, curr.getValuePlain());

                            final TableEntry<V> workNode = work[workIdx];
                            work[workIdx] = replace;

                            if (workNode == null) {
                                newTable[newTableIdx] = replace;
                                continue;
                            } else {
                                workNode.setNextPlain(replace);
                                continue;
                            }
                        }
                    }

                    setAtIndexRelease(oldTable, i, (TableEntry<V>) RESIZE_NODE);
                    break;
                }
            }
        }

        // calculate new threshold
        final int newThreshold;
        if (capacity == MAXIMUM_CAPACITY) {
            newThreshold = THRESHOLD_NO_RESIZE;
        } else {
            newThreshold = getTargetThreshold(capacity, loadFactor);
        }

        this.table = newTable;
        // finish resize operation by releasing hold on threshold
        this.setThresholdVolatile(newThreshold);
    }

    /**
     * Subtracts count from size
     */
    protected final void subSize(final long count) {
        this.size.getAndAdd(-count);
    }

    /**
     * Atomically updates the value associated with {@code key} to {@code value}, or inserts a new mapping with {@code key}
     * mapped to {@code value}.
     *
     * @param key   Specified key
     * @param value Specified value
     * @return Old value previously associated with key, or {@code null} if none.
     * @throws NullPointerException If key is null
     * @throws NullPointerException If value is null
     */
    public V put(final UUID key, final V value) {

        Objects.requireNonNull(value, "Value may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    if (null == (node = compareAndExchangeAtIndexVolatile(table, index, null, new TableEntry<>(key, value)))) {
                        // successfully inserted
                        this.addSize(1L);
                        return null;
                    } // else: node != null, fall through
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }
                    // plain reads are fine during synchronised access, as we are the only writer
                    TableEntry<V> prev = null;
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            final V ret = node.getValuePlain();
                            node.setValueVolatile(value);
                            return ret;
                        }
                    }

                    // volatile ordering ensured by addSize(), but we need release here
                    // to ensure proper ordering with reads and other writes
                    prev.setNextRelease(new TableEntry<>(key, value));
                }

                this.addSize(1L);
                return null;
            }
        }
    }

    /**
     * Atomically inserts a new mapping with {@code key} mapped to {@code value} if and only if {@code key} is not
     * currently mapped to some value.
     *
     * @param key   Specified key
     * @param value Specified value
     * @return Value currently associated with key, or {@code null} if none and {@code value} was associated.
     * @throws NullPointerException If key is null
     * @throws NullPointerException If value is null
     */
    public V putIfAbsent(final UUID key, final V value) {

        Objects.requireNonNull(value, "Value may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    if (null == (node = compareAndExchangeAtIndexVolatile(table, index, null, new TableEntry<>(key, value)))) {
                        // successfully inserted
                        this.addSize(1L);
                        return null;
                    } // else: node != null, fall through
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                // optimise ifAbsent calls: check if first node is key before attempting lock acquire
                if (key.equals(node.key)) {
                    final V ret = node.getValueVolatile();
                    if (ret != null) {
                        return ret;
                    } // else: fall back to lock to read the node
                }

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }
                    // plain reads are fine during synchronised access, as we are the only writer
                    TableEntry<V> prev = null;
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            return node.getValuePlain();
                        }
                    }

                    // volatile ordering ensured by addSize(), but we need release here
                    // to ensure proper ordering with reads and other writes
                    prev.setNextRelease(new TableEntry<>(key, value));
                }

                this.addSize(1L);
                return null;
            }
        }
    }

    /**
     * Atomically updates the value associated with {@code key} to {@code value}, or does nothing if {@code key} is not
     * associated with a value.
     *
     * @param key   Specified key
     * @param value Specified value
     * @return Old value previously associated with key, or {@code null} if none.
     * @throws NullPointerException If key is null
     * @throws NullPointerException If value is null
     */
    public V replace(final UUID key, final V value) {

        Objects.requireNonNull(value, "Value may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    return null;
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }

                    // plain reads are fine during synchronised access, as we are the only writer
                    for (; node != null; node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            final V ret = node.getValuePlain();
                            node.setValueVolatile(value);
                            return ret;
                        }
                    }
                }

                return null;
            }
        }
    }

    /**
     * Atomically updates the value associated with {@code key} to {@code update} if the currently associated
     * value is equal to {@code expect}, otherwise does nothing.
     *
     * @param key    Specified key
     * @param expect Expected value to check current mapped value with
     * @param update Update value to replace mapped value with
     * @return If the currently mapped value is not equal to {@code expect}, then returns the currently mapped
     * value. If the key is not mapped to any value, then returns {@code null}. If neither of the two cases are
     * true, then returns {@code expect}.
     * @throws NullPointerException If key is null
     * @throws NullPointerException If expect is null
     * @throws NullPointerException If update is null
     */
    public V replace(final UUID key, final V expect, final V update) {
        Objects.requireNonNull(expect, "Expect may not be null");
        Objects.requireNonNull(update, "Update may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    return null;
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }

                    // plain reads are fine during synchronised access, as we are the only writer
                    for (; node != null; node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            final V ret = node.getValuePlain();

                            if (!(expect == ret)) {
                                return ret;
                            }

                            node.setValueVolatile(update);
                            return ret;
                        }
                    }
                }

                return null;
            }
        }
    }

    /**
     * Atomically removes the mapping for the specified key and returns the value it was associated with. If the key
     * is not mapped to a value, then does nothing and returns {@code null}.
     *
     * @param key Specified key
     * @return Old value previously associated with key, or {@code null} if none.
     * @throws NullPointerException If key is null
     */
    public V remove(final UUID key) {


        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    return null;
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                boolean removed = false;
                V ret = null;

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }

                    TableEntry<V> prev = null;

                    // plain reads are fine during synchronised access, as we are the only writer
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            ret = node.getValuePlain();
                            removed = true;

                            // volatile ordering ensured by addSize(), but we need release here
                            // to ensure proper ordering with reads and other writes
                            if (prev == null) {
                                setAtIndexRelease(table, index, node.getNextPlain());
                            } else {
                                prev.setNextRelease(node.getNextPlain());
                            }

                            break;
                        }
                    }
                }

                if (removed) {
                    this.subSize(1L);
                }

                return ret;
            }
        }
    }

    /**
     * Atomically removes the mapping for the specified key if it is mapped to {@code expect} and returns {@code expect}. If the key
     * is not mapped to a value, then does nothing and returns {@code null}. If the key is mapped to a value that is not
     * equal to {@code expect}, then returns that value.
     *
     * @param key    Specified key
     * @param expect Specified expected value
     * @return The specified expected value if the key was mapped to {@code expect}. If
     * the key is not mapped to any value, then returns {@code null}. If neither of those cases are true,
     * then returns the current mapped value for key.
     * @throws NullPointerException If key is null
     */
    public V remove(final UUID key, final V expect) {


        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    return null;
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                boolean removed = false;
                V ret = null;

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }

                    TableEntry<V> prev = null;

                    // plain reads are fine during synchronised access, as we are the only writer
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            ret = node.getValuePlain();
                            if ((expect == ret)) {
                                removed = true;

                                // volatile ordering ensured by addSize(), but we need release here
                                // to ensure proper ordering with reads and other writes
                                if (prev == null) {
                                    setAtIndexRelease(table, index, node.getNextPlain());
                                } else {
                                    prev.setNextRelease(node.getNextPlain());
                                }
                            }
                            break;
                        }
                    }
                }

                if (removed) {
                    this.subSize(1L);
                }

                return ret;
            }
        }
    }

    /**
     * Atomically removes the mapping for the specified key the predicate returns true for its currently mapped value. If the key
     * is not mapped to a value, then does nothing and returns {@code null}.
     *
     * <p>
     * This function is a "functional method" as defined by {@link ConcurrentChainedUUID2ReferenceHashTable}.
     * </p>
     *
     * @param key       Specified key
     * @param predicate Specified predicate
     * @return The specified expected value if the key was mapped to {@code expect}. If
     * the key is not mapped to any value, then returns {@code null}. If neither of those cases are true,
     * then returns the current (non-null) mapped value for key.
     * @throws NullPointerException If key is null
     * @throws NullPointerException If predicate is null
     */
    public V removeIf(final UUID key, final RemoveIfPredicate<? super V> predicate) {

        Objects.requireNonNull(predicate, "Predicate may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    return null;
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                boolean removed = false;
                V ret = null;

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }

                    TableEntry<V> prev = null;

                    // plain reads are fine during synchronised access, as we are the only writer
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            ret = node.getValuePlain();
                            if (predicate.test(ret)) {
                                removed = true;

                                // volatile ordering ensured by addSize(), but we need release here
                                // to ensure proper ordering with reads and other writes
                                if (prev == null) {
                                    setAtIndexRelease(table, index, node.getNextPlain());
                                } else {
                                    prev.setNextRelease(node.getNextPlain());
                                }
                            }
                            break;
                        }
                    }
                }

                if (removed) {
                    this.subSize(1L);
                }

                return ret;
            }
        }
    }

    /**
     * See {@link java.util.concurrent.ConcurrentMap#compute(Object, BiFunction)}
     * <p>
     * This function is a "functional method" as defined by {@link ConcurrentChainedUUID2ReferenceHashTable}.
     * </p>
     */
    public V compute(final UUID key, final BiUUIDObjectObjectFunction<? super V, ? extends V> function) {

        Objects.requireNonNull(function, "Function may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                V ret = null;
                if (node == null) {
                    final TableEntry<V> insert = new TableEntry<>(key, null);

                    boolean added = false;

                    synchronized (insert) {
                        if (null == (node = compareAndExchangeAtIndexVolatile(table, index, null, insert))) {
                            try {
                                ret = function.apply(key, null);
                            } catch (final Throwable throwable) {
                                setAtIndexVolatile(table, index, null);
                                ThrowUtil.throwUnchecked(throwable);
                                // unreachable
                                return null;
                            }

                            if (ret == null) {
                                setAtIndexVolatile(table, index, null);
                                return ret;
                            } else {
                                // volatile ordering ensured by addSize(), but we need release here
                                // to ensure proper ordering with reads and other writes
                                insert.setValueRelease(ret);
                                added = true;
                            }
                        } // else: node != null, fall through
                    }

                    if (added) {
                        this.addSize(1L);
                        return ret;
                    }
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                boolean removed = false;
                boolean added = false;

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }
                    // plain reads are fine during synchronised access, as we are the only writer
                    TableEntry<V> prev = null;
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            final V old = node.getValuePlain();

                            final V computed = function.apply(key, old);

                            if (computed != null) {
                                node.setValueVolatile(computed);
                                return computed;
                            }

                            // volatile ordering ensured by addSize(), but we need release here
                            // to ensure proper ordering with reads and other writes
                            if (prev == null) {
                                setAtIndexRelease(table, index, node.getNextPlain());
                            } else {
                                prev.setNextRelease(node.getNextPlain());
                            }

                            removed = true;
                            break;
                        }
                    }

                    if (!removed) {
                        final V computed = function.apply(key, null);
                        if (computed != null) {
                            // volatile ordering ensured by addSize(), but we need release here
                            // to ensure proper ordering with reads and other writes
                            prev.setNextRelease(new TableEntry<>(key, computed));
                            ret = computed;
                            added = true;
                        }
                    }
                }

                if (removed) {
                    this.subSize(1L);
                }
                if (added) {
                    this.addSize(1L);
                }

                return ret;
            }
        }
    }

    /**
     * See {@link java.util.concurrent.ConcurrentMap#computeIfAbsent(Object, Function)}
     * <p>
     * This function is a "functional method" as defined by {@link ConcurrentChainedUUID2ReferenceHashTable}.
     * </p>
     */
    public V computeIfAbsent(final UUID key, final BiUUIDObjectFunction<? extends V> function) {

        Objects.requireNonNull(function, "Function may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                V ret = null;
                if (node == null) {
                    final TableEntry<V> insert = new TableEntry<>(key, null);

                    boolean added = false;

                    synchronized (insert) {
                        if (null == (node = compareAndExchangeAtIndexVolatile(table, index, null, insert))) {
                            try {
                                ret = function.apply(key);
                            } catch (final Throwable throwable) {
                                setAtIndexVolatile(table, index, null);
                                ThrowUtil.throwUnchecked(throwable);
                                // unreachable
                                return null;
                            }

                            if (ret == null) {
                                setAtIndexVolatile(table, index, null);
                                return null;
                            } else {
                                // volatile ordering ensured by addSize(), but we need release here
                                // to ensure proper ordering with reads and other writes
                                insert.setValueRelease(ret);
                                added = true;
                            }
                        } // else: node != null, fall through
                    }

                    if (added) {
                        this.addSize(1L);
                        return ret;
                    }
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                // optimise ifAbsent calls: check if first node is key before attempting lock acquire
                if (key.equals(node.key)) {
                    ret = node.getValueVolatile();
                    if (ret != null) {
                        return ret;
                    } // else: fall back to lock to read the node
                }

                boolean added = false;

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }
                    // plain reads are fine during synchronised access, as we are the only writer
                    TableEntry<V> prev = null;
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            ret = node.getValuePlain();
                            return ret;
                        }
                    }

                    final V computed = function.apply(key);
                    if (computed != null) {
                        // volatile ordering ensured by addSize(), but we need release here
                        // to ensure proper ordering with reads and other writes
                        prev.setNextRelease(new TableEntry<>(key, computed));
                        ret = computed;
                        added = true;
                    }
                }

                if (added) {
                    this.addSize(1L);
                }

                return ret;
            }
        }
    }

    /**
     * See {@link java.util.concurrent.ConcurrentMap#computeIfPresent(Object, BiFunction)}
     * <p>
     * This function is a "functional method" as defined by {@link ConcurrentChainedUUID2ReferenceHashTable}.
     * </p>
     */
    public V computeIfPresent(final UUID key, final BiUUIDObjectObjectFunction<? super V, ? extends V> function) {

        Objects.requireNonNull(function, "Function may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    return null;
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                boolean removed = false;

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }
                    // plain reads are fine during synchronised access, as we are the only writer
                    TableEntry<V> prev = null;
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            final V old = node.getValuePlain();

                            final V computed = function.apply(key, old);

                            if (computed != null) {
                                node.setValueVolatile(computed);
                                return computed;
                            }

                            // volatile ordering ensured by addSize(), but we need release here
                            // to ensure proper ordering with reads and other writes
                            if (prev == null) {
                                setAtIndexRelease(table, index, node.getNextPlain());
                            } else {
                                prev.setNextRelease(node.getNextPlain());
                            }

                            removed = true;
                            break;
                        }
                    }
                }

                if (removed) {
                    this.subSize(1L);
                }

                return null;
            }
        }
    }

    /**
     * See {@link java.util.concurrent.ConcurrentMap#merge(Object, Object, BiFunction)}
     * <p>
     * This function is a "functional method" as defined by {@link ConcurrentChainedUUID2ReferenceHashTable}.
     * </p>
     */
    public V merge(final UUID key, final V def, final BiFunction<? super V, ? super V, ? extends V> function) {

        Objects.requireNonNull(def, "Default value may not be null");
        Objects.requireNonNull(function, "Function may not be null");

        Objects.requireNonNull(key, "Key may not be null");

        final int hash = getHash(key);

        TableEntry<V>[] table = this.table;
        table_loop:
        for (; ; ) {
            final int index = hash & (table.length - 1);

            TableEntry<V> node = getAtIndexAcquire(table, index);
            node_loop:
            for (; ; ) {
                if (node == null) {
                    if (null == (node = compareAndExchangeAtIndexVolatile(table, index, null, new TableEntry<>(key, def)))) {
                        // successfully inserted
                        this.addSize(1L);
                        return def;
                    } // else: node != null, fall through
                }

                if (node == RESIZE_NODE) {
                    table = this.fetchNewTable(table);
                    continue table_loop;
                }

                boolean removed = false;
                boolean added = false;
                V ret = null;

                synchronized (node) {
                    if (node != (node = getAtIndexAcquire(table, index))) {
                        continue node_loop;
                    }
                    // plain reads are fine during synchronised access, as we are the only writer
                    TableEntry<V> prev = null;
                    for (; node != null; prev = node, node = node.getNextPlain()) {
                        if (key.equals(node.key)) {
                            final V old = node.getValuePlain();

                            final V computed = function.apply(old, def);

                            if (computed != null) {
                                node.setValueVolatile(computed);
                                return computed;
                            }

                            // volatile ordering ensured by addSize(), but we need release here
                            // to ensure proper ordering with reads and other writes
                            if (prev == null) {
                                setAtIndexRelease(table, index, node.getNextPlain());
                            } else {
                                prev.setNextRelease(node.getNextPlain());
                            }

                            removed = true;
                            break;
                        }
                    }

                    if (!removed) {
                        // volatile ordering ensured by addSize(), but we need release here
                        // to ensure proper ordering with reads and other writes
                        prev.setNextRelease(new TableEntry<>(key, def));
                        ret = def;
                        added = true;
                    }
                }

                if (removed) {
                    this.subSize(1L);
                }
                if (added) {
                    this.addSize(1L);
                }

                return ret;
            }
        }
    }

    /**
     * Removes at least all entries currently mapped at the beginning of this call. May not remove entries added during
     * this call. As a result, only if this map is not modified during the call, that all entries will be removed by
     * the end of the call.
     *
     * <p>
     * This function is not atomic.
     * </p>
     */
    public void clear() {
        // it is possible to optimise this to directly interact with the table,
        // but we do need to be careful when interacting with resized tables,
        // and the NodeIterator already does this logic
        final NodeIterator<V> nodeIterator = new NodeIterator<>(this);

        TableEntry<V> node;
        while ((node = nodeIterator.findNext()) != null) {
            this.remove(node.key);
        }
    }

    /**
     * Returns an iterator over the entries in this map. The iterator is only guaranteed to see entries that were
     * added before the beginning of this call, but it may see entries added during.
     */
    public BaseObjectIterator<TableEntry<V>> entryIterator() {
        return new EntryNodeIterator<>(this);
    }

    @Override
    public final BaseObjectIterator<TableEntry<V>> iterator() {
        return this.entryIterator();
    }

    /**
     * Returns an iterator over the keys in this map. The iterator is only guaranteed to see keys that were
     * added before the beginning of this call, but it may see keys added during.
     */
    public BaseObjectIterator<UUID> keyIterator() {
        return new KeyIterator<>(this);
    }

    /**
     * Returns an iterator over the values in this map. The iterator is only guaranteed to see values that were
     * added before the beginning of this call, but it may see values added during.
     */
    public BaseObjectIterator<V> valueIterator() {
        return new ValueIterator<>(this);
    }

    public Collection<V> values() {
        final Values<V> values = this.values;
        if (values != null) {
            return values;
        }
        return this.values = new Values<>(this);
    }

    public Set<TableEntry<V>> entrySet() {
        final EntrySet<V> entrySet = this.entrySet;
        if (entrySet != null) {
            return entrySet;
        }
        return this.entrySet = new EntrySet<>(this);
    }

    @FunctionalInterface
    public static interface RemoveIfPredicate<V> {
        public boolean test(final V value);
    }

    @FunctionalInterface
    public static interface BiUUIDObjectObjectFunction<P2, R> {
        public R apply(final UUID key, final P2 value);
    }

    @FunctionalInterface
    public static interface BiUUIDObjectFunction<V> {
        public V apply(final UUID key);
    }

    protected static class Values<V> extends BaseMapCollection<V> {
        protected final ConcurrentChainedUUID2ReferenceHashTable<V> map;

        protected Values(final ConcurrentChainedUUID2ReferenceHashTable<V> map) {
            this.map = map;
        }

        @Override
        public int size() {
            return this.map.size();
        }

        @Override
        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public boolean contains(final Object value) {
            return this.map.containsValue((V) value);
        }

        @Override
        protected List<V> asList() {
            final List<V> ret = new ArrayList<>(this.map.size());

            for (final V element : this) {
                ret.add(element);
            }

            return ret;
        }

        @Override
        public BaseObjectIterator<V> iterator() {
            return this.map.valueIterator();
        }
    }

    protected static class EntrySet<V> extends BaseMapSet<TableEntry<V>> {
        protected final ConcurrentChainedUUID2ReferenceHashTable<V> map;

        protected EntrySet(final ConcurrentChainedUUID2ReferenceHashTable<V> map) {
            this.map = map;
        }

        @Override
        public int size() {
            return this.map.size();
        }

        @Override
        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public boolean contains(final Object value) {
            if (!(value instanceof ConcurrentChainedUUID2ReferenceHashTable.TableEntry<?> entry)) {
                return false;
            }

            final UUID entryKey = entry.getKey();
            final Object entryValue = entry.getValue();

            if (entryValue == null) {
                // placeholder entry: not mapped
                return false;
            }
            return this.map.contains(entryKey, (V) entryValue);
        }

        @Override
        protected List<TableEntry<V>> asList() {
            final List<TableEntry<V>> ret = new ArrayList<>(this.map.size());

            for (final TableEntry<V> element : this) {
                ret.add(element);
            }

            return ret;
        }

        @Override
        public BaseObjectIterator<TableEntry<V>> iterator() {
            return this.map.entryIterator();
        }
    }

    protected static final class EntryNodeIterator<V> extends BaseObjectIterator<TableEntry<V>> {
        protected final NodeIterator<V> nodeIterator;
        protected TableEntry<V> lastReturned;
        protected TableEntry<V> nextToReturn;

        protected EntryNodeIterator(final ConcurrentChainedUUID2ReferenceHashTable<V> map) {
            this.nodeIterator = new NodeIterator<>(map);
        }

        @Override
        public boolean hasNext() {
            if (this.nextToReturn != null) {
                return true;
            }

            return (this.nextToReturn = this.nodeIterator.findNext()) != null;
        }

        @Override
        public TableEntry<V> next() throws NoSuchElementException {
            TableEntry<V> ret = this.nextToReturn;
            if (ret != null) {
                this.lastReturned = ret;
                this.nextToReturn = null;
                return ret;
            }
            ret = this.nodeIterator.findNext();
            if (ret != null) {
                this.lastReturned = ret;
                return ret;
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            final TableEntry<V> lastReturned = this.lastReturned;
            if (lastReturned == null) {
                throw new NoSuchElementException();
            }
            this.lastReturned = null;
            this.nodeIterator.map.remove(lastReturned.key);
        }
    }

    protected static final class KeyIterator<V> extends BaseObjectIterator<UUID> {

        private final EntryNodeIterator<V> iterator;

        public KeyIterator(final ConcurrentChainedUUID2ReferenceHashTable<V> map) {
            this.iterator = new EntryNodeIterator<>(map);
        }

        @Override
        public boolean hasNext() {
            return this.iterator.hasNext();
        }

        @Override
        public void remove() {
            this.iterator.remove();
        }

        @Override
        public UUID next() {
            return this.iterator.next().key;
        }
    }

    protected static final class ValueIterator<V> extends BaseObjectIterator<V> {

        private final EntryNodeIterator<V> iterator;

        public ValueIterator(final ConcurrentChainedUUID2ReferenceHashTable<V> map) {
            this.iterator = new EntryNodeIterator<>(map);
        }

        @Override
        public boolean hasNext() {
            return this.iterator.hasNext();
        }

        @Override
        public void remove() {
            this.iterator.remove();
        }

        @Override
        public V next() {
            return this.iterator.next().getValueVolatile();
        }
    }

    protected static class NodeIterator<V> {

        protected final ConcurrentChainedUUID2ReferenceHashTable<V> map;
        private TableEntry<V>[] currentTable;
        private ResizeChain<V> resizeChain;
        private TableEntry<V> last;
        private int nextBin;
        private int increment;

        protected NodeIterator(final ConcurrentChainedUUID2ReferenceHashTable<V> map) {
            this.map = map;
            this.currentTable = map.table;
            this.increment = 1;
        }

        private TableEntry<V>[] pullResizeChain(final int index) {
            final ResizeChain<V> resizeChain = this.resizeChain;
            if (resizeChain == null) {
                this.currentTable = null;
                return null;
            }

            final ResizeChain<V> prevChain = resizeChain.prev;
            this.resizeChain = prevChain;
            if (prevChain == null) {
                this.currentTable = null;
                return null;
            }

            final TableEntry<V>[] newTable = prevChain.table;

            // we recover the original index by modding by the new table length, as the increments applied to the index
            // are a multiple of the new table's length
            int newIdx = index & (newTable.length - 1);

            // the increment is always the previous table's length
            final ResizeChain<V> nextPrevChain = prevChain.prev;
            final int increment;
            if (nextPrevChain == null) {
                increment = 1;
            } else {
                increment = nextPrevChain.table.length;
            }

            // done with the upper table, so we can skip the resize node
            newIdx += increment;

            this.increment = increment;
            this.nextBin = newIdx;
            this.currentTable = newTable;

            return newTable;
        }

        private TableEntry<V>[] pushResizeChain(final TableEntry<V>[] table) {
            final ResizeChain<V> chain = this.resizeChain;

            if (chain == null) {
                final TableEntry<V>[] nextTable = this.map.fetchNewTable(table);

                final ResizeChain<V> oldChain = new ResizeChain<>(table, null, null);
                final ResizeChain<V> currChain = new ResizeChain<>(nextTable, oldChain, null);
                oldChain.next = currChain;

                this.increment = table.length;
                this.resizeChain = currChain;
                this.currentTable = nextTable;

                return nextTable;
            } else {
                ResizeChain<V> currChain = chain.next;
                if (currChain == null) {
                    final TableEntry<V>[] ret = this.map.fetchNewTable(table);
                    currChain = new ResizeChain<>(ret, chain, null);
                    chain.next = currChain;

                    this.increment = table.length;
                    this.resizeChain = currChain;
                    this.currentTable = ret;

                    return ret;
                } else {
                    this.increment = table.length;
                    this.resizeChain = currChain;
                    return this.currentTable = currChain.table;
                }
            }
        }

        protected final TableEntry<V> findNext() {
            for (; ; ) {
                final TableEntry<V> last = this.last;
                if (last != null) {
                    final TableEntry<V> next = last.getNextVolatile();
                    if (next != null) {
                        this.last = next;
                        if (next.getValuePlain() == null) {
                            // compute() node not yet available
                            continue;
                        }
                        return next;
                    }
                }

                TableEntry<V>[] table = this.currentTable;

                if (table == null) {
                    return null;
                }

                int idx = this.nextBin;
                int increment = this.increment;
                for (; ; ) {
                    if (idx >= table.length) {
                        table = this.pullResizeChain(idx);
                        idx = this.nextBin;
                        increment = this.increment;
                        if (table != null) {
                            continue;
                        } else {
                            this.last = null;
                            return null;
                        }
                    }

                    final TableEntry<V> entry = getAtIndexAcquire(table, idx);
                    if (entry == null) {
                        idx += increment;
                        continue;
                    }

                    if (entry == RESIZE_NODE) {
                        // push onto resize chain
                        table = this.pushResizeChain(table);
                        increment = this.increment;
                        continue;
                    }

                    this.last = entry;
                    this.nextBin = idx + increment;
                    if (entry.getValuePlain() == null) {
                        // compute() node not yet available
                        break;
                    }
                    return entry;
                }
            }
        }

        private static final class ResizeChain<V> {

            public final TableEntry<V>[] table;
            public final ResizeChain<V> prev;
            public ResizeChain<V> next;

            public ResizeChain(final TableEntry<V>[] table, final ResizeChain<V> prev, final ResizeChain<V> next) {
                this.table = table;
                this.prev = prev;
                this.next = next;
            }
        }
    }

    public static final class TableEntry<V> {

        private static final VarHandle TABLE_ENTRY_ARRAY_HANDLE = ConcurrentUtil.getArrayHandle(TableEntry[].class);
        private static final VarHandle VALUE_HANDLE = ConcurrentUtil.getVarHandle(TableEntry.class, "value", Object.class);
        private static final VarHandle NEXT_HANDLE = ConcurrentUtil.getVarHandle(TableEntry.class, "next", TableEntry.class);
        private final UUID key;
        private volatile V value;
        private volatile TableEntry<V> next;

        protected TableEntry(final UUID key, final V value) {
            this.key = key;
            this.setValuePlain(value);
        }

        private V getValuePlain() {
            //noinspection unchecked
            return (V) VALUE_HANDLE.get(this);
        }

        private void setValuePlain(final V value) {
            VALUE_HANDLE.set(this, (Object) value);
        }

        private V getValueAcquire() {
            //noinspection unchecked
            return (V) VALUE_HANDLE.getAcquire(this);
        }

        private V getValueVolatile() {
            //noinspection unchecked
            return (V) VALUE_HANDLE.getVolatile(this);
        }

        private void setValueVolatile(final V value) {
            VALUE_HANDLE.setVolatile(this, (Object) value);
        }

        private void setValueRelease(final V value) {
            VALUE_HANDLE.setRelease(this, (Object) value);
        }

        private TableEntry<V> getNextPlain() {
            //noinspection unchecked
            return (TableEntry<V>) NEXT_HANDLE.get(this);
        }

        private void setNextPlain(final TableEntry<V> next) {
            NEXT_HANDLE.set(this, next);
        }

        private TableEntry<V> getNextAcquire() {
            //noinspection unchecked
            return (TableEntry<V>) NEXT_HANDLE.getAcquire(this);
        }

        private TableEntry<V> getNextVolatile() {
            //noinspection unchecked
            return (TableEntry<V>) NEXT_HANDLE.getVolatile(this);
        }

        private void setNextVolatile(final TableEntry<V> next) {
            NEXT_HANDLE.setVolatile(this, next);
        }

        private void setNextRelease(final TableEntry<V> next) {
            NEXT_HANDLE.setRelease(this, next);
        }

        public UUID getKey() {
            return this.key;
        }

        public V getValue() {
            return this.getValueVolatile();
        }
    }
}
