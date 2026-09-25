package memory;

/**
 * Every register the machine has, in one place.
 *
 * <p>Values are held as plain ints but are masked to the register's real width
 * on every write, so nothing outside this class has to remember that the PC is
 * 12 bits while a GPR is 16. That masking is what makes two's-complement
 * wrap-around happen naturally: adding 1 to 077777 (+32767) yields 100000,
 * which is -32768, exactly as the hardware would.
 *
 * <p>Index registers are stored in a 4-element array with slot 0 unused. The ISA
 * numbers them X1-X3 and uses IX=0 in an instruction to mean "no indexing", so
 * keeping the array 1-based avoids a subtraction on every access and removes a
 * whole class of off-by-one bugs.
 */
public class RegisterFile {

    public static final int GPR_COUNT = 4;   // R0 - R3
    public static final int IXR_COUNT = 4;   // slot 0 unused; X1 - X3 live in 1..3

    private final int[] gpr = new int[GPR_COUNT];
    private final int[] ixr = new int[IXR_COUNT];

    private int pc;    // 12 bits
    private int mar;   // 12 bits
    private int mbr;   // 16 bits
    private int ir;    // 16 bits
    private int mfr;   // 4 bits
    private int cc;    // 4 bits

    // ----- general purpose registers -----

    public int getGpr(int n) {
        return gpr[n];
    }

    public void setGpr(int n, int value) {
        gpr[n] = value & 0xFFFF;
    }

    /** The GPR's contents read as a signed 16-bit quantity. */
    public int getGprSigned(int n) {
        return toSigned(gpr[n]);
    }

    // ----- index registers -----

    public int getIxr(int n) {
        return ixr[n];
    }

    public void setIxr(int n, int value) {
        ixr[n] = value & 0xFFFF;
    }

    // ----- internal registers -----

    public int getPc()            { return pc; }
    public void setPc(int v)      { pc = v & 0x0FFF; }
    public void incrementPc()     { setPc(pc + 1); }

    public int getMar()           { return mar; }
    public void setMar(int v)     { mar = v & 0x0FFF; }

    public int getMbr()           { return mbr; }
    public void setMbr(int v)     { mbr = v & 0xFFFF; }

    public int getIr()            { return ir; }
    public void setIr(int v)      { ir = v & 0xFFFF; }

    public int getMfr()           { return mfr; }
    public void setMfr(int v)     { mfr = v & 0x0F; }

    // ----- condition code -----
    //
    // The CC holds four independent flags. The ISA numbers them 0 to 3 in the
    // order OVERFLOW, UNDERFLOW, DIVZERO, EQUALORNOT, and JCC takes that number
    // directly as its operand — which is why the test programs write JCC 3 to
    // branch on the result of a TRR comparison.
    //
    // Those numbers are NOT bit positions. The register is displayed as four
    // binary digits with OVERFLOW leftmost, so flag 0 has to live in the most
    // significant bit and flag 3 in the least. Everything outside this class
    // works in flag numbers; the conversion happens in one place, below.
    //
    // Getting this backwards is silent: arithmetic still sets the right flag and
    // the console still displays the right digits, but every JCC tests the wrong
    // condition. It was caught only because test T3 branches on a condition it
    // has independently arranged to be true.

    public static final int CC_OVERFLOW    = 0;
    public static final int CC_UNDERFLOW   = 1;
    public static final int CC_DIVZERO     = 2;
    public static final int CC_EQUALORNOT  = 3;

    private static final int CC_FLAG_COUNT = 4;

    public int getCc() {
        return cc;
    }

    public void setCc(int v) {
        cc = v & 0x0F;
    }

    /** @param flag one of the CC_* constants, numbered as the ISA numbers them. */
    public boolean getCcBit(int flag) {
        return ((cc >> bitPositionOf(flag)) & 1) == 1;
    }

    /** @param flag one of the CC_* constants, numbered as the ISA numbers them. */
    public void setCcBit(int flag, boolean on) {
        int position = bitPositionOf(flag);
        cc = on ? (cc | (1 << position)) : (cc & ~(1 << position));
        cc &= 0x0F;
    }

    /** Flag 0 is the most significant of the four bits, flag 3 the least. */
    private static int bitPositionOf(int flag) {
        return (CC_FLAG_COUNT - 1) - flag;
    }

    /** Resets every register to zero, as the console's Init control requires. */
    public void clear() {
        java.util.Arrays.fill(gpr, 0);
        java.util.Arrays.fill(ixr, 0);
        pc = mar = mbr = ir = mfr = cc = 0;
    }

    /** Interprets a 16-bit pattern as a signed value. */
    public static int toSigned(int word) {
        word &= 0xFFFF;
        return (word & 0x8000) != 0 ? word - 0x10000 : word;
    }
}
