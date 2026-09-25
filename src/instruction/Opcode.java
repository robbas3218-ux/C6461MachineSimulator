package instruction;

import java.util.HashMap;
import java.util.Map;

/**
 * The C6461 instruction set, keyed by its 6-bit opcode.
 *
 * <p>Opcodes are written here in Java octal notation (a leading zero) because
 * every C6461 document — the ISA specification, the assembler listing and the
 * front panel — expresses them in octal. Writing 041 rather than 33 means the
 * source can be checked against the specification by eye.
 *
 * <p>{@link Format} records how the remaining ten bits are laid out, which is
 * what the decoder needs in order to pull the right fields out of a word.
 */
public enum Opcode {

    HLT (000, Format.HALT,       "HLT"),

    // Load / store
    LDR (001, Format.STANDARD,   "LDR"),
    STR (002, Format.STANDARD,   "STR"),
    LDA (003, Format.STANDARD,   "LDA"),
    LDX (041, Format.INDEX,      "LDX"),
    STX (042, Format.INDEX,      "STX"),

    // Arithmetic
    AMR (004, Format.STANDARD,   "AMR"),
    SMR (005, Format.STANDARD,   "SMR"),
    AIR (006, Format.IMMEDIATE,  "AIR"),
    SIR (007, Format.IMMEDIATE,  "SIR"),

    // Transfer
    JZ  (010, Format.STANDARD,   "JZ"),
    JNE (011, Format.STANDARD,   "JNE"),
    JCC (012, Format.STANDARD,   "JCC"),
    JMA (013, Format.INDEX,      "JMA"),
    JSR (014, Format.INDEX,      "JSR"),
    RFS (015, Format.IMMEDIATE,  "RFS"),
    SOB (016, Format.STANDARD,   "SOB"),
    JGE (017, Format.STANDARD,   "JGE"),

    // Register to register
    MLT (070, Format.REG_REG,    "MLT"),
    DVD (071, Format.REG_REG,    "DVD"),
    TRR (072, Format.REG_REG,    "TRR"),
    AND (073, Format.REG_REG,    "AND"),
    ORR (074, Format.REG_REG,    "ORR"),
    NOT (075, Format.REG_REG,    "NOT"),

    // Shift and rotate
    SRC (031, Format.SHIFT,      "SRC"),
    RRC (032, Format.SHIFT,      "RRC"),

    // Input / output
    IN  (061, Format.IO,         "IN"),
    OUT (062, Format.IO,         "OUT"),
    CHK (063, Format.IO,         "CHK"),

    // Miscellaneous
    TRAP(030, Format.TRAP,       "TRAP"),

    // Floating point and vector — recognised by the decoder so that an illegal
    // opcode fault can be told apart from a valid but not-yet-implemented one.
    // Not required until Part IV.
    FADD (033, Format.STANDARD,  "FADD"),
    FSUB (034, Format.STANDARD,  "FSUB"),
    VADD (035, Format.STANDARD,  "VADD"),
    VSUB (036, Format.STANDARD,  "VSUB"),
    CNVRT(037, Format.STANDARD,  "CNVRT"),
    LDFR (050, Format.STANDARD,  "LDFR"),
    STFR (051, Format.STANDARD,  "STFR");

    /** How the low ten bits of the instruction word are interpreted. */
    public enum Format {
        /** opcode(6) R(2) IX(2) I(1) Address(5) */
        STANDARD,
        /** As STANDARD, but the R field is unused and IX selects the index register. */
        INDEX,
        /** opcode(6) R(2) unused(3) Immed(5) */
        IMMEDIATE,
        /** opcode(6) Rx(2) Ry(2) unused(6) */
        REG_REG,
        /** opcode(6) R(2) A/L(1) L/R(1) unused(2) Count(4) */
        SHIFT,
        /** opcode(6) R(2) unused(3) DevId(5) */
        IO,
        /** opcode(6) unused(6) TrapCode(4) */
        TRAP,
        /** No operands. */
        HALT
    }

    private static final Map<Integer, Opcode> BY_VALUE = new HashMap<>();

    static {
        for (Opcode op : values()) {
            BY_VALUE.put(op.value, op);
        }
    }

    private final int value;
    private final Format format;
    private final String mnemonic;

    Opcode(int value, Format format, String mnemonic) {
        this.value = value;
        this.format = format;
        this.mnemonic = mnemonic;
    }

    public int getValue()       { return value; }
    public Format getFormat()   { return format; }
    public String getMnemonic() { return mnemonic; }

    /** Returns the opcode with this 6-bit value, or null if none is defined. */
    public static Opcode fromValue(int value) {
        return BY_VALUE.get(value & 0x3F);
    }

    /** True for the floating-point and vector group, which Part IV adds. */
    public boolean isFloatingOrVector() {
        return this == FADD || this == FSUB || this == VADD || this == VSUB
                || this == CNVRT || this == LDFR || this == STFR;
    }
}
