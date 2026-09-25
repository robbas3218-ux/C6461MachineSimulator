package cpu;

/**
 * Raised when the machine detects a fault. The ISA defines four fault ids, each
 * setting a single bit of the 4-bit Machine Fault Register (MFR). When a fault
 * occurs the machine saves the PC at location 4 and transfers control to the
 * handler address stored at location 1.
 */
public class MachineFaultException extends Exception {

    /** id 0, MFR 0001 — illegal memory address to a reserved location. */
    public static final int RESERVED_ADDRESS = 0;
    /** id 1, MFR 0010 — illegal TRAP code (outside 0-15). */
    public static final int ILLEGAL_TRAP_CODE = 1;
    /** id 2, MFR 0100 — illegal operation code. */
    public static final int ILLEGAL_OPCODE = 2;
    /** id 3, MFR 1000 — memory address beyond the installed range. */
    public static final int ADDRESS_NOT_INSTALLED = 3;

    private final int faultId;

    public MachineFaultException(int faultId, String detail) {
        super("machine fault " + faultId + ": " + detail);
        this.faultId = faultId;
    }

    public int getFaultId() {
        return faultId;
    }

    /** The MFR bit pattern for this fault: a single bit at position faultId. */
    public int getMfrValue() {
        return 1 << faultId;
    }
}
