package cpu;

import instruction.Instruction;
import instruction.Opcode;
import memory.Memory;
import memory.RegisterFile;

/**
 * The C6461 processor: fetch, decode, execute.
 *
 * <p>The public entry point is {@link #singleStep()}, which executes exactly one
 * instruction and returns. {@link #run(int)} simply calls it in a loop. Building
 * it this way round — rather than writing a run loop and bolting stepping on
 * afterwards — is what makes the operator console's Single Step control work,
 * and Part I specifically requires that a program be executable one instruction
 * at a time.
 *
 * <p>Memory is never touched directly. Every access goes through
 * {@link #readMemory(int)} or {@link #writeMemory(int, int)}, which set MAR and
 * MBR on the way past. The console therefore always displays the address and
 * word from the most recent access without needing any extra bookkeeping.
 */
public class CPU {

    private final Memory memory;
    private final RegisterFile registers;
    private final IODevices io;

    private boolean halted;

    /** Set when a fault has been vectored, so the console can show what happened. */
    private String lastFaultMessage;

    public CPU(Memory memory, RegisterFile registers, IODevices io) {
        this.memory = memory;
        this.registers = registers;
        this.io = io;
    }

    public Memory getMemory()          { return memory; }
    public RegisterFile getRegisters() { return registers; }
    public IODevices getIo()           { return io; }
    public boolean isHalted()          { return halted; }
    public void setHalted(boolean h)   { halted = h; }
    public String getLastFaultMessage(){ return lastFaultMessage; }

    /** The console's Init control: clears memory, registers, devices and halt state. */
    public void initialise() {
        memory.clear();
        registers.clear();
        io.clear();
        halted = false;
        lastFaultMessage = null;
    }

    // ------------------------------------------------------------------
    // Memory access, with MAR and MBR maintained for the console display
    // ------------------------------------------------------------------

    private int readMemory(int address) throws MachineFaultException {
        registers.setMar(address);
        int value = memory.read(address);
        registers.setMbr(value);
        return value;
    }

    private void writeMemory(int address, int value) throws MachineFaultException {
        registers.setMar(address);
        registers.setMbr(value);
        memory.write(address, value);
    }

    // ------------------------------------------------------------------
    // Effective address
    // ------------------------------------------------------------------

    /**
     * Computes the effective address exactly as the ISA states it:
     *
     * <pre>
     *   If IX = 00           then EA = Address
     *   Else                      EA = c(IX) + Address
     *   If I = 1             then EA = c(EA)
     * </pre>
     *
     * <p>The four addressing modes fall out of those two rules combined: direct,
     * indexed, indirect and indexed-indirect. There is no separate code path for
     * each mode, which is why test T1 passing proves all four at once.
     *
     * @param allowIndexing false for LDX, STX, JMA and JSR, where the IX field
     *                      names the register being operated on rather than an
     *                      index to add. Indirection still applies to those.
     */
    private int effectiveAddress(Instruction inst, boolean allowIndexing)
            throws MachineFaultException {
        int ea = inst.getAddress();
        if (allowIndexing && inst.getIndex() != 0) {
            ea = (registers.getIxr(inst.getIndex()) + ea) & 0xFFFF;
        }
        if (inst.isIndirect()) {
            ea = readMemory(ea);
        }
        return ea;
    }

    // ------------------------------------------------------------------
    // The instruction cycle
    // ------------------------------------------------------------------

    /**
     * Executes one instruction. A machine fault raised anywhere inside is caught
     * here, recorded in MFR, and vectored through the handler address held at
     * memory location 1, with the interrupted PC saved at location 4.
     *
     * @return false if the machine is halted and nothing was executed.
     */
    public boolean singleStep() {
        if (halted) {
            return false;
        }
        int pcBefore = registers.getPc();
        try {
            int word = readMemory(registers.getPc());
            registers.setIr(word);
            registers.incrementPc();

            Instruction inst = Instruction.decode(word);
            if (inst.getOpcode() == null) {
                throw new MachineFaultException(MachineFaultException.ILLEGAL_OPCODE,
                        String.format("opcode %02o is not defined", word >> 10));
            }
            if (inst.getOpcode().isFloatingOrVector()) {
                // The ISA defers these to Part IV. Treating them as an illegal
                // operation matches the reference simulator's behaviour rather
                // than failing silently.
                throw new MachineFaultException(MachineFaultException.ILLEGAL_OPCODE,
                        inst.getOpcode().getMnemonic() + " is not implemented until Part IV");
            }
            execute(inst);
        } catch (MachineFaultException fault) {
            handleFault(fault, pcBefore);
        }
        return true;
    }

    /** Runs until the machine halts or the step limit is reached. */
    public int run(int maxSteps) {
        int steps = 0;
        while (!halted && steps < maxSteps) {
            singleStep();
            steps++;
        }
        return steps;
    }

    private void handleFault(MachineFaultException fault, int pcBefore) {
        lastFaultMessage = fault.getMessage();
        registers.setMfr(fault.getMfrValue());
        try {
            // Location 4 holds the PC at the time of the fault; location 1 holds
            // the handler's address. Written with deposit() because the machine
            // itself is allowed to use the reserved region a program may not.
            memory.deposit(4, pcBefore + 1);
            registers.setPc(memory.read(1));
        } catch (MachineFaultException nested) {
            // A fault while vectoring a fault leaves nowhere to go; stop.
            halted = true;
        }
    }

    // ------------------------------------------------------------------
    // Execution, grouped by instruction family
    // ------------------------------------------------------------------

    private void execute(Instruction inst) throws MachineFaultException {
        switch (inst.getOpcode()) {
            case HLT -> halted = true;

            case LDR, STR, LDA, LDX, STX -> executeLoadStore(inst);

            case AMR, SMR, AIR, SIR -> executeArithmetic(inst);

            case JZ, JNE, JCC, JMA, JSR, RFS, SOB, JGE -> executeTransfer(inst);

            case MLT, DVD, TRR, AND, ORR, NOT -> executeRegisterToRegister(inst);

            case SRC, RRC -> executeShiftRotate(inst);

            case IN, OUT, CHK -> executeIo(inst);

            case TRAP -> executeTrap(inst);

            default -> throw new MachineFaultException(
                    MachineFaultException.ILLEGAL_OPCODE,
                    inst.getOpcode().getMnemonic() + " has no execute path");
        }
    }

    /** LDR, STR, LDA, LDX, STX — the Part I instruction set. */
    private void executeLoadStore(Instruction inst) throws MachineFaultException {
        int r = inst.getRegister();
        switch (inst.getOpcode()) {
            case LDR -> registers.setGpr(r, readMemory(effectiveAddress(inst, true)));
            case STR -> writeMemory(effectiveAddress(inst, true), registers.getGpr(r));

            // LDA loads the address itself, not the word stored there. It is the
            // machine's only way to get an arbitrary computed address into a
            // register, and is how a program builds a pointer.
            case LDA -> registers.setGpr(r, effectiveAddress(inst, true));

            // For LDX and STX the IX field selects the index register being
            // loaded or stored, so it must not also be added into the address.
            case LDX -> registers.setIxr(inst.getIndex(),
                    readMemory(effectiveAddress(inst, false)));
            case STX -> writeMemory(effectiveAddress(inst, false),
                    registers.getIxr(inst.getIndex()));
            default -> throw new IllegalStateException();
        }
    }

    /** AMR, SMR, AIR, SIR. */
    private void executeArithmetic(Instruction inst) throws MachineFaultException {
        int r = inst.getRegister();
        int result;
        switch (inst.getOpcode()) {
            case AMR -> result = registers.getGprSigned(r)
                    + RegisterFile.toSigned(readMemory(effectiveAddress(inst, true)));
            case SMR -> result = registers.getGprSigned(r)
                    - RegisterFile.toSigned(readMemory(effectiveAddress(inst, true)));
            case AIR -> result = registers.getGprSigned(r) + inst.getImmediate();
            case SIR -> result = registers.getGprSigned(r) - inst.getImmediate();
            default -> throw new IllegalStateException();
        }
        // The condition code records that the true result did not fit in 16 bits.
        // The register still receives the wrapped value, which is what makes
        // adding 1 to +32767 produce -32768.
        registers.setCcBit(RegisterFile.CC_OVERFLOW, result > 32767);
        registers.setCcBit(RegisterFile.CC_UNDERFLOW, result < -32768);
        registers.setGpr(r, result);
    }

    /** JZ, JNE, JCC, JMA, JSR, RFS, SOB, JGE. */
    private void executeTransfer(Instruction inst) throws MachineFaultException {
        int r = inst.getRegister();
        switch (inst.getOpcode()) {
            case JZ -> {
                if (registers.getGprSigned(r) == 0) registers.setPc(effectiveAddress(inst, true));
            }
            case JNE -> {
                if (registers.getGprSigned(r) != 0) registers.setPc(effectiveAddress(inst, true));
            }
            // JCC tests one bit of the condition code. The bit number travels in
            // the R field, which is why the assembler writes it as JCC 3,...
            case JCC -> {
                if (registers.getCcBit(r)) registers.setPc(effectiveAddress(inst, true));
            }
            case JMA -> registers.setPc(effectiveAddress(inst, false));
            case JSR -> {
                int target = effectiveAddress(inst, false);
                // PC has already been incremented past the JSR, so it is exactly
                // the return address the subroutine needs.
                registers.setGpr(3, registers.getPc());
                registers.setPc(target);
            }
            case RFS -> {
                registers.setGpr(0, inst.getImmediate());
                registers.setPc(registers.getGpr(3));
            }
            case SOB -> {
                registers.setGpr(r, registers.getGpr(r) - 1);
                if (registers.getGprSigned(r) > 0) registers.setPc(effectiveAddress(inst, true));
            }
            case JGE -> {
                if (registers.getGprSigned(r) >= 0) registers.setPc(effectiveAddress(inst, true));
            }
            default -> throw new IllegalStateException();
        }
    }

    /** MLT, DVD, TRR, AND, ORR, NOT. */
    private void executeRegisterToRegister(Instruction inst) {
        int rx = inst.getRegister();
        int ry = inst.getIndex();
        switch (inst.getOpcode()) {
            case MLT -> {
                // The product of two 16-bit values needs 32 bits, so it is
                // returned as a register pair: high word in rx, low word in rx+1.
                int product = registers.getGprSigned(rx) * registers.getGprSigned(ry);
                registers.setCcBit(RegisterFile.CC_OVERFLOW,
                        product > Integer.MAX_VALUE || product < Integer.MIN_VALUE);
                registers.setGpr(rx, (product >> 16) & 0xFFFF);
                registers.setGpr(rx + 1, product & 0xFFFF);
            }
            case DVD -> {
                int divisor = registers.getGprSigned(ry);
                if (divisor == 0) {
                    // DIVZERO is raised and the operands are left untouched, so
                    // a program can detect the condition and recover.
                    registers.setCcBit(RegisterFile.CC_DIVZERO, true);
                } else {
                    int dividend = registers.getGprSigned(rx);
                    registers.setGpr(rx, dividend / divisor);
                    registers.setGpr(rx + 1, dividend % divisor);
                }
            }
            case TRR -> registers.setCcBit(RegisterFile.CC_EQUALORNOT,
                    registers.getGpr(rx) == registers.getGpr(ry));
            case AND -> registers.setGpr(rx, registers.getGpr(rx) & registers.getGpr(ry));
            case ORR -> registers.setGpr(rx, registers.getGpr(rx) | registers.getGpr(ry));
            case NOT -> registers.setGpr(rx, ~registers.getGpr(rx));
            default -> throw new IllegalStateException();
        }
    }

    /**
     * SRC and RRC.
     *
     * <p>The difference between the two is what happens to bits leaving the word:
     * a shift discards them, a rotate brings them round to the other end. The
     * arithmetic form of a right shift replicates the sign bit so that shifting
     * a negative number right still divides it by two.
     */
    private void executeShiftRotate(Instruction inst) {
        int r = inst.getRegister();
        int count = inst.getShiftCount();
        if (count == 0) {
            return; // the ISA defines a count of zero as no operation
        }
        int value = registers.getGpr(r);
        boolean left = inst.isShiftLeft();
        boolean logical = inst.isShiftLogical();

        if (inst.getOpcode() == Opcode.SRC) {
            if (left) {
                value = (value << count) & 0xFFFF;
            } else if (logical) {
                value = (value & 0xFFFF) >>> count;
            } else {
                value = (RegisterFile.toSigned(value) >> count) & 0xFFFF;
            }
        } else { // RRC
            int n = count % 16;
            if (n != 0) {
                value = left
                        ? ((value << n) | (value >>> (16 - n))) & 0xFFFF
                        : ((value >>> n) | (value << (16 - n))) & 0xFFFF;
            }
        }
        registers.setGpr(r, value);
    }

    /** IN, OUT, CHK. */
    private void executeIo(Instruction inst) {
        int r = inst.getRegister();
        int device = inst.getDeviceId();
        switch (inst.getOpcode()) {
            case IN  -> registers.setGpr(r, io.in(device));
            case OUT -> io.out(device, registers.getGpr(r));
            case CHK -> registers.setGpr(r, io.check(device));
            default -> throw new IllegalStateException();
        }
    }

    /**
     * TRAP. The code indexes a table of sixteen handler addresses whose base is
     * stored at location 0. The return address is saved at location 2.
     */
    private void executeTrap(Instruction inst) throws MachineFaultException {
        int code = inst.getTrapCode();
        if (code > 15) {
            throw new MachineFaultException(MachineFaultException.ILLEGAL_TRAP_CODE,
                    "trap code " + code);
        }
        memory.deposit(2, registers.getPc());
        int tableBase = readMemory(0);
        registers.setPc(readMemory(tableBase + code));
    }
}
