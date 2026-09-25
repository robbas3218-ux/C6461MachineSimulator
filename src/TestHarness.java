import cpu.CPU;
import cpu.IODevices;
import loader.LoadFileReader;
import memory.Memory;
import memory.RegisterFile;

/**
 * Headless regression harness.
 *
 * <p>Runs each of the Part 0 test programs and compares the resulting machine
 * state against results that were derived by hand from the ISA specification and
 * then confirmed on the instructor's reference simulator. Because those expected
 * values are independently verified, a passing run here is real evidence that
 * this simulator implements the architecture correctly, not just evidence that
 * it agrees with itself.
 *
 * <p>Run with:  java -cp out TestHarness
 */
public class TestHarness {

    private static int passed = 0;
    private static int failed = 0;

    /** One expected final state. Only the fields that matter to a test are checked. */
    private record Expect(String name, String loadFile, int startPc,
                          int[] gpr, int[] ixr, int pc, int cc, int mfr,
                          int[][] memory, String keyboard, String printer) { }

    public static void main(String[] args) {
        Expect[] cases = {
            new Expect("T1 load/store & addressing modes", "input/T1_load_store.load", 14,
                    new int[]{012, 012, 024, 037}, new int[]{0, 0, 06, 0},
                    030, 00, 00,
                    new int[][]{{012, 012}, {013, 012}, {014, 06}}, null, null),

            new Expect("T2 arithmetic", "input/T2_arithmetic.load", 12,
                    new int[]{04, 017, 0177773, 0100000}, null,
                    026, 0b1000, 00, null, null, null),

            new Expect("T3 branches", "input/T3_branches.load", 12,
                    new int[]{0, 013, 0, 01}, null,
                    037, 0b0001, 00,
                    new int[][]{{011, 01}}, null, null),

            new Expect("T4 subroutine linkage", "input/T4_subroutine.load", 12,
                    new int[]{07, 041, 0, 016}, null,
                    020, 00, 00,
                    new int[][]{{010, 07}}, null, null),

            new Expect("T5 register-to-register", "input/T5_regreg.load", 14,
                    new int[]{06, 03, 0, 0177652}, null,
                    037, 0b0011, 00, null, null, null),

            new Expect("T6 shift & rotate", "input/T6_shift_rotate.load", 10,
                    new int[]{06, 0177774, 02, 0}, null,
                    023, 00, 00, null, null, null),

            new Expect("T7 input/output", "input/T7_io.load", 8,
                    new int[]{01, 012, 0, 0}, null,
                    017, 00, 00, null, "AB", "AB\n"),

            new Expect("T8 run 1 (TRAP)", "input/T8_trap_fault.load", 6,
                    new int[]{0, 0, 0, 07}, null,
                    016, 00, 00,
                    new int[][]{{02, 07}}, null, null),

            new Expect("T8 run 2 (machine fault)", "input/T8_trap_fault.load", 8,
                    new int[]{0, 0, 0, 05}, null,
                    020, 00, 0b0001, null, null, null),
        };

        System.out.println("C6461 simulator regression suite");
        System.out.println("=".repeat(70));
        for (Expect c : cases) {
            runCase(c);
        }
        System.out.println("=".repeat(70));
        System.out.printf("%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void runCase(Expect c) {
        Memory memory = new Memory();
        RegisterFile registers = new RegisterFile();
        IODevices io = new IODevices();
        CPU cpu = new CPU(memory, registers, io);
        cpu.initialise();

        StringBuilder problems = new StringBuilder();
        try {
            new LoadFileReader().load(memory, c.loadFile());
            if (c.keyboard() != null) {
                io.typeLine(c.keyboard());
            }
            registers.setPc(c.startPc());
            cpu.run(10_000);

            if (!cpu.isHalted()) {
                problems.append("    machine never halted\n");
            }
            if (c.gpr() != null) {
                for (int i = 0; i < c.gpr().length; i++) {
                    check(problems, "GPR" + i, c.gpr()[i], registers.getGpr(i));
                }
            }
            if (c.ixr() != null) {
                for (int i = 1; i < c.ixr().length; i++) {
                    check(problems, "IXR" + i, c.ixr()[i], registers.getIxr(i));
                }
            }
            check(problems, "PC", c.pc(), registers.getPc());
            check(problems, "CC", c.cc(), registers.getCc());
            check(problems, "MFR", c.mfr(), registers.getMfr());

            if (c.memory() != null) {
                for (int[] pair : c.memory()) {
                    check(problems, String.format("mem[%06o]", pair[0]),
                            pair[1], memory.read(pair[0]));
                }
            }
            if (c.printer() != null && !c.printer().equals(io.getPrinterOutput())) {
                problems.append(String.format("    printer: expected %s but got %s%n",
                        quote(c.printer()), quote(io.getPrinterOutput())));
            }
        } catch (Exception e) {
            problems.append("    threw ").append(e).append('\n');
        }

        if (problems.isEmpty()) {
            passed++;
            System.out.printf("PASS  %s%n", c.name());
        } else {
            failed++;
            System.out.printf("FAIL  %s%n%s", c.name(), problems);
        }
    }

    private static void check(StringBuilder problems, String label, int expected, int actual) {
        if (expected != actual) {
            problems.append(String.format("    %-12s expected %06o but got %06o%n",
                    label, expected, actual));
        }
    }

    private static String quote(String s) {
        return '"' + s.replace("\n", "\\n") + '"';
    }
}
