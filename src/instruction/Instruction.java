package instruction;

/**
 * One decoded instruction word.
 *
 * <p>Decoding happens once, in {@link #decode(int)}, and the result is an
 * immutable record of every field. The execute step then reads named fields
 * instead of repeating shift-and-mask expressions, which keeps the bit layout
 * described in exactly one place.
 *
 * <p>Bit numbering follows the ISA document: bit 0 is the most significant bit
 * of the 16-bit word, bit 15 the least. The standard layout is therefore
 * opcode in bits 0-5, R in 6-7, IX in 8-9, I in bit 10, address in bits 11-15.
 * In Java those become right-shifts of 10, 8, 6, 5 and a mask of 0x1F.
 */
public final class Instruction {

    private final int word;
    private final Opcode opcode;
    private final int register;     // R or Rx, bits 6-7
    private final int index;        // IX or Ry, bits 8-9
    private final int indirect;     // I, bit 10
    private final int address;      // bits 11-15, also used as immediate / devid / trap code

    private Instruction(int word, Opcode opcode, int register, int index,
                        int indirect, int address) {
        this.word = word;
        this.opcode = opcode;
        this.register = register;
        this.index = index;
        this.indirect = indirect;
        this.address = address;
    }

    /**
     * Splits a 16-bit word into its fields. Returns an instruction whose opcode
     * is null when the 6-bit opcode is not defined by the ISA; the CPU turns
     * that into an illegal-opcode machine fault.
     */
    public static Instruction decode(int word) {
        word &= 0xFFFF;
        return new Instruction(
                word,
                Opcode.fromValue(word >> 10),
                (word >> 8) & 0x03,
                (word >> 6) & 0x03,
                (word >> 5) & 0x01,
                word & 0x1F);
    }

    public int getWord()      { return word; }
    public Opcode getOpcode() { return opcode; }
    public int getRegister()  { return register; }
    public int getIndex()     { return index; }
    public int getIndirect()  { return indirect; }
    public int getAddress()   { return address; }

    public boolean isIndirect() { return indirect == 1; }

    /** The immediate operand of AIR, SIR and RFS: the low five bits. */
    public int getImmediate() { return address; }

    /** The device id of IN, OUT and CHK: the low five bits. */
    public int getDeviceId() { return address; }

    /** The trap code of TRAP: the low four bits. */
    public int getTrapCode() { return word & 0x0F; }

    /** Shift and rotate count: bits 12-15. */
    public int getShiftCount() { return word & 0x0F; }

    /** Shift and rotate direction: 1 = left, 0 = right (bit 9). */
    public boolean isShiftLeft() { return ((word >> 6) & 1) == 1; }

    /** Shift and rotate type: 1 = logical, 0 = arithmetic (bit 8). */
    public boolean isShiftLogical() { return ((word >> 7) & 1) == 1; }

    @Override
    public String toString() {
        String name = opcode == null ? "???" : opcode.getMnemonic();
        return String.format("%s (word %06o)", name, word);
    }
}
