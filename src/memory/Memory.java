package memory;

import cpu.MachineFaultException;

/**
 * Main memory for the C6461 machine.
 *
 * <p>The machine is word-addressable with 16-bit words. Part I installs 2048
 * words; the ISA allows expansion to 4096, so the installed size is a field
 * rather than a hard-coded constant.
 *
 * <p>This class is deliberately "dumb": it stores words and enforces the two
 * address-related machine faults. It does not touch MAR or MBR. Those registers
 * are driven by {@link cpu.CPU}, which mirrors how the real data path works —
 * the CPU places an address in MAR, asserts a read or write, and the word
 * appears in MBR. Keeping that logic in the CPU is what lets the operator
 * console display MAR and MBR correctly during single-step execution.
 */
public class Memory {

    /** Number of words installed. The ISA permits 2048 or 4096. */
    public static final int DEFAULT_SIZE = 2048;

    /**
     * Locations 0-5 are reserved by the ISA:
     * 0 = trap vector table address, 1 = machine fault handler address,
     * 2 = PC saved on trap, 4 = PC saved on machine fault.
     * Writing here from a user program raises fault id 0.
     */
    public static final int RESERVED_LIMIT = 5;

    private final int[] words;

    public Memory() {
        this(DEFAULT_SIZE);
    }

    public Memory(int size) {
        this.words = new int[size];
    }

    public int size() {
        return words.length;
    }

    /**
     * Reads a word. Raises fault id 3 if the address is outside installed memory.
     * Reads from reserved locations are legal — the machine itself must read the
     * trap and fault vectors stored there.
     */
    public int read(int address) throws MachineFaultException {
        checkInstalled(address);
        return words[address];
    }

    /**
     * Writes a word from a running program. Raises fault id 3 for an address
     * beyond installed memory, and fault id 0 for an attempt to write into the
     * reserved region.
     */
    public int write(int address, int value) throws MachineFaultException {
        checkInstalled(address);
        if (address <= RESERVED_LIMIT) {
            throw new MachineFaultException(MachineFaultException.RESERVED_ADDRESS,
                    "write to reserved location " + address);
        }
        return words[address] = value & 0xFFFF;
    }

    /**
     * Writes without the reserved-location check. Used by the program loader and
     * by the operator console's Store button, both of which act as the machine's
     * own front panel rather than as a running program, and must be able to set
     * up the trap and fault vectors.
     */
    public void deposit(int address, int value) throws MachineFaultException {
        checkInstalled(address);
        words[address] = value & 0xFFFF;
    }

    /** Clears all of memory to zero, as the Init control on the console requires. */
    public void clear() {
        java.util.Arrays.fill(words, 0);
    }

    private void checkInstalled(int address) throws MachineFaultException {
        if (address < 0 || address >= words.length) {
            throw new MachineFaultException(MachineFaultException.ADDRESS_NOT_INSTALLED,
                    "address " + address + " is outside installed memory of " + words.length);
        }
    }
}
