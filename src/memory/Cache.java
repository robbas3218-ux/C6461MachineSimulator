package memory;

import cpu.MachineFaultException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fully associative cache sitting between the CPU and main memory.
 *
 * <p>Sixteen lines, one word per line, FIFO replacement, write-through with no
 * write-allocate. Those choices are deliberate for this machine rather than
 * copied from a textbook:
 *
 * <ul>
 *   <li><b>Fully associative</b> because with only sixteen lines, a set-associative
 *       or direct-mapped organisation would suffer conflict misses on programs
 *       whose data and instructions sit a few words apart — which describes
 *       every test program here, since they load at location 6 and run from 14.</li>
 *   <li><b>FIFO replacement</b> because it needs one queue and no per-access
 *       bookkeeping, and with a cache this small the difference from LRU is
 *       slight. It is also trivially explainable on the operator console, which
 *       matters when the contents are on display.</li>
 *   <li><b>Write-through</b> because the front panel can read memory directly,
 *       so a dirty cache line that had not yet reached memory would make the
 *       console display lie. Correctness of what the operator sees outweighs
 *       the write traffic saved by a write-back policy.</li>
 * </ul>
 *
 * <p>Because it is write-through and every miss is serviced from memory, the
 * cache is transparent: the machine computes exactly the same results with it
 * in place, which the regression suite confirms.
 */
public class Cache implements MemoryUnit {

    public static final int DEFAULT_LINES = 16;

    /** One cache line: the address it holds and the word cached from it. */
    public record Line(int address, int value) { }

    private final MemoryUnit backing;
    private final int capacity;

    /** Address to cached word. LinkedHashMap keeps insertion order for display. */
    private final Map<Integer, Integer> lines = new LinkedHashMap<>();

    /** Insertion order, used to choose the FIFO victim. */
    private final Deque<Integer> fifo = new ArrayDeque<>();

    private int hits;
    private int misses;

    public Cache(MemoryUnit backing) {
        this(backing, DEFAULT_LINES);
    }

    public Cache(MemoryUnit backing, int capacity) {
        this.backing = backing;
        this.capacity = capacity;
    }

    @Override
    public int read(int address) throws MachineFaultException {
        Integer cached = lines.get(address);
        if (cached != null) {
            hits++;
            return cached;
        }
        misses++;
        int value = backing.read(address);
        insert(address, value);
        return value;
    }

    @Override
    public int write(int address, int value) throws MachineFaultException {
        int written = backing.write(address, value);
        // Write-through, no write-allocate: an address already cached is kept
        // consistent, but writing does not pull a new line in.
        if (lines.containsKey(address)) {
            lines.put(address, written);
        }
        return written;
    }

    @Override
    public void deposit(int address, int value) throws MachineFaultException {
        backing.deposit(address, value);
        if (lines.containsKey(address)) {
            lines.put(address, value & 0xFFFF);
        }
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void clear() {
        backing.clear();
        invalidate();
    }

    /** Empties the cache without disturbing main memory. */
    public void invalidate() {
        lines.clear();
        fifo.clear();
        hits = 0;
        misses = 0;
    }

    private void insert(int address, int value) {
        if (lines.size() >= capacity) {
            Integer victim = fifo.poll();
            if (victim != null) {
                lines.remove(victim);
            }
        }
        lines.put(address, value);
        fifo.add(address);
    }

    // ----- for the operator console's cache display -----

    /** The current contents, oldest line first, for display. */
    public java.util.List<Line> snapshot() {
        java.util.List<Line> out = new java.util.ArrayList<>(lines.size());
        for (Map.Entry<Integer, Integer> e : lines.entrySet()) {
            out.add(new Line(e.getKey(), e.getValue()));
        }
        return out;
    }

    public int getHits()     { return hits; }
    public int getMisses()   { return misses; }
    public int getCapacity() { return capacity; }

    public double getHitRate() {
        int total = hits + misses;
        return total == 0 ? 0.0 : (double) hits / total;
    }
}
