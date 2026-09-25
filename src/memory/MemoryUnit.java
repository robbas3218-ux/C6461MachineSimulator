package memory;

import cpu.MachineFaultException;

/**
 * Anything the CPU can read words from and write words to.
 *
 * <p>Both {@link Memory} and {@link Cache} implement this, and a Cache wraps
 * another MemoryUnit. That is what lets the cache be inserted into the data path
 * without the CPU knowing it exists: the CPU holds a MemoryUnit and calls the
 * same three methods either way.
 *
 * <p>It also keeps the door open for Part II, where the caching policy may need
 * to change — a different policy is a different implementation of this interface
 * rather than an edit to the processor.
 */
public interface MemoryUnit {

    /** Reads a word on behalf of a running program. */
    int read(int address) throws MachineFaultException;

    /** Writes a word on behalf of a running program; reserved locations are denied. */
    int write(int address, int value) throws MachineFaultException;

    /**
     * Writes a word on behalf of the machine itself — the ROM loader, the front
     * panel's Store button, and the trap and fault mechanisms. Reserved
     * locations are permitted, because the vectors live there.
     */
    void deposit(int address, int value) throws MachineFaultException;

    /** Number of words installed. */
    int size();

    /** Clears to the power-on state. */
    void clear();
}
