# C6461 Machine Simulator

A simulator for the C6461, a 16-bit word-addressable computer, built for
CSCI 6461 Computer System Architecture (Section 12, Fall 2026).

The simulator provides a front-panel operator console, a processor implementing
the full C6461 instruction set except the floating-point and vector group, 2048
words of main memory behind a 16-line cache, and console I/O devices.

---

## Contents

- [Requirements](#requirements)
- [Building and running](#building-and-running)
- [Quick start](#quick-start)
- [The operator console](#the-operator-console)
- [Loading and running a program](#loading-and-running-a-program)
- [Assembly source format](#assembly-source-format)
- [Instruction set](#instruction-set)
- [Addressing modes](#addressing-modes)
- [Machine faults](#machine-faults)
- [The cache](#the-cache)
- [Test suite](#test-suite)
- [Repository layout](#repository-layout)
- [Known limitations](#known-limitations)

---

## Requirements

JDK 17 or newer. Built and tested against OpenJDK 21. No external libraries;
the interface uses Swing, which ships with the JDK.

Verify with:

```
java -version
javac -version
```

---

## Building and running

The build script compiles, runs the regression suite, and optionally packages a
runnable JAR:

```
./build.sh          compile and run all regression tests
./build.sh jar      also package dist/C6461MachineSimulator.jar
```

Then launch the simulator:

```
java -jar dist/C6461MachineSimulator.jar
```

To build and run without the script:

```
javac -d out $(find src -name '*.java')
java -cp out Main                 launch the operator console
java -cp out TestHarness          run the regression suite only
```

In an IDE, the project is a plain source tree with `src` as the source root and
no build tool. `.vscode/settings.json` configures this for VS Code; IntelliJ
detects it on import.

---

## Quick start

1. `./build.sh jar` then `java -jar dist/C6461MachineSimulator.jar`
2. **File -> Load Program…**, choose `input/T1_load_store.load`
3. Type `16` into the **PC** field (values on the panel are octal; this is
   decimal 14)
4. Press **Run**

The machine halts with GPR0 and GPR1 both showing `000012`, GPR2 `000024`,
GPR3 `000037` and IXR2 `000006`. That program exercises all four addressing
modes, so a correct result there means the effective-address logic is sound.

---

## The operator console

Everything on the panel is displayed in octal, except MFR and CC which are shown
as four binary digits so that individual flags can be read directly.

### Registers

| Display | Width | Meaning |
|---|---|---|
| GPR 0–3 | 16 bits | General purpose registers, addressable by programs |
| IXR 1–3 | 16 bits | Index registers. There is no IXR 0; index field 0 means "no indexing" |
| PC | 12 bits | Program counter |
| MAR | 12 bits | Memory address register — the address of the most recent access |
| MBR | 16 bits | Memory buffer register — the word read or written by that access |
| IR | 16 bits | Instruction register — the instruction currently executing |
| MFR | 4 bits | Machine fault register, one bit per fault id |
| CC | 4 bits | Condition code: OVERFLOW, UNDERFLOW, DIVZERO, EQUALORNOT, left to right |

Any register with an editable field can be set two ways: type an octal value
into the field and press Enter, or set the switch register and press the **LD**
button beside it. Both work. (The reference simulator supports only the second,
and silently discards a typed value when LD is pressed — a defect recorded
during Part 0 testing and deliberately not reproduced here.)

### Controls

| Button | Action |
|---|---|
| **Store** | Write MBR into the address held in MAR |
| **Load** | Read the address held in MAR into MBR |
| **St+** / **Ld+** | As Store / Load, then advance MAR by one |
| **Init** | Clear memory, all registers, the cache and the device queues |
| **Run** | Execute until the machine halts |
| **SS** | Execute exactly one instruction |
| **HLT** | Stop the machine |

### Switch register

Sixteen switches along the bottom, numbered 0 to 15 left to right, with switch 0
the most significant bit. The labels beneath them — Operation, GPR, IXR, I,
Address — mark the fields of the standard instruction format, so a complete
instruction can be keyed in by hand and deposited into memory with Store. The
readout shows the current value in binary and octal.

### Side panels

**Console** — printer output from device 1, and a Line Input box for keyboard
input on device 0. Type a line and press Enter or **Send Line** to queue it;
characters are delivered one at a time to `IN`, followed by a newline.

**Cache** — the current contents of all sixteen lines, oldest first, with hit
and miss counts and the hit rate. **Invalidate cache** empties it without
disturbing main memory.

**Trace** — one line per instruction executed, showing the address, the machine
word and the decoded mnemonic.

---

## Loading and running a program

1. **Init** first. This clears memory, so anything left from a previous program
   cannot affect the next one.
2. **File -> Load Program…** and choose a `.load` file. The chooser accepts
   `.load` and `.txt` and leaves the all-files option enabled, so no file is
   ever unselectable.
3. Enter the program's start address in the **PC** field, in octal.
4. **Run** for continuous execution, or **SS** to step one instruction at a
   time and watch the registers change.

Run executes a burst of instructions per timer tick rather than looping to
completion, so the interface stays responsive and **HLT** can be pressed part
way through a long program.

To inspect memory: type an address into **MAR**, press **Load**, and read the
word from **MBR**. **Ld+** repeats this while advancing through consecutive
addresses.

---

## Assembly source format

The simulator reads load files produced by the course web assembler. Its source
format is:

```
[Label:]  Operation  Operand[,Operand...]   ; comment
```

Labels begin with a letter or underscore and end with a colon. Everything after
the first semicolon is ignored. Case is not significant in mnemonics. Operands
are written in decimal and separated by commas; the assembler emits octal.

Two directives:

| Directive | Meaning |
|---|---|
| `LOC n` | Set the location counter to decimal `n` |
| `Data n` or `Data label` | Place one word holding a decimal value or a label's address |

A load file is two octal columns per line, an address and the word to place
there. Blank lines and semicolon comments are ignored.

---

## Instruction set

Opcodes are octal, as in the ISA specification.

### Load and store

| Opcode | Mnemonic | Operation |
|---|---|---|
| 01 | `LDR r,x,addr[,I]` | `r <- c(EA)` |
| 02 | `STR r,x,addr[,I]` | `c(EA) <- c(r)` |
| 03 | `LDA r,x,addr[,I]` | `r <- EA` (the address itself, not its contents) |
| 41 | `LDX x,addr[,I]` | `c(Xx) <- c(EA)` |
| 42 | `STX x,addr[,I]` | `c(EA) <- c(Xx)` |

### Arithmetic

| Opcode | Mnemonic | Operation |
|---|---|---|
| 04 | `AMR r,x,addr[,I]` | `r <- c(r) + c(EA)`, sets OVERFLOW / UNDERFLOW |
| 05 | `SMR r,x,addr[,I]` | `r <- c(r) − c(EA)`, sets OVERFLOW / UNDERFLOW |
| 06 | `AIR r,immed` | `r <- c(r) + immed` |
| 07 | `SIR r,immed` | `r <- c(r) − immed` |

### Transfer of control

| Opcode | Mnemonic | Operation |
|---|---|---|
| 10 | `JZ r,x,addr[,I]` | Branch if `c(r) = 0` |
| 11 | `JNE r,x,addr[,I]` | Branch if `c(r) != 0` |
| 12 | `JCC cc,x,addr[,I]` | Branch if condition code `cc` is set |
| 13 | `JMA x,addr[,I]` | Unconditional branch |
| 14 | `JSR x,addr[,I]` | `R3 <- PC+1`, then branch |
| 15 | `RFS [immed]` | `R0 <- immed`, `PC <- c(R3)` |
| 16 | `SOB r,x,addr[,I]` | Decrement `r`, branch if still positive |
| 17 | `JGE r,x,addr[,I]` | Branch if `c(r) >= 0` |

`JCC` takes the condition code *number*, which the ISA numbers 0 to 3 in the
order OVERFLOW, UNDERFLOW, DIVZERO, EQUALORNOT. So `JCC 3` branches on an equal
comparison set by `TRR`.

### Register to register

| Opcode | Mnemonic | Operation |
|---|---|---|
| 70 | `MLT rx,ry` | `rx,rx+1 <- c(rx) × c(ry)` as a 32-bit pair |
| 71 | `DVD rx,ry` | `rx <- quotient`, `rx+1 <- remainder`; sets DIVZERO if `ry = 0` |
| 72 | `TRR rx,ry` | Set EQUALORNOT if `c(rx) = c(ry)`, clear it otherwise |
| 73 | `AND rx,ry` | `rx <- c(rx) AND c(ry)` |
| 74 | `ORR rx,ry` | `rx <- c(rx) OR c(ry)` |
| 75 | `NOT rx` | `rx <- NOT c(rx)` |

### Shift, rotate and I/O

| Opcode | Mnemonic | Operation |
|---|---|---|
| 31 | `SRC r,count,L/R,A/L` | Shift; an arithmetic right shift preserves the sign bit |
| 32 | `RRC r,count,L/R,A/L` | Rotate; bits leaving one end re-enter at the other |
| 61 | `IN r,devid` | Read one character from a device |
| 62 | `OUT r,devid` | Write the low eight bits of `r` to a device |
| 63 | `CHK r,devid` | `r <- 1` if the device is ready or input is waiting, else 0 |

Devices: 0 keyboard, 1 printer, 2 card reader.

### Miscellaneous

| Opcode | Mnemonic | Operation |
|---|---|---|
| 00 | `HLT` | Stop the machine |
| 30 | `TRAP code` | Save `PC+1` at location 2, vector through the table at `c(0)` |

---

## Addressing modes

The ISA states the effective address in two rules:

```
If IX = 00  then EA = Address
Else             EA = c(IX) + Address
If I = 1    then EA = c(EA)
```

The four named modes are the combinations of those two rules — direct, indexed,
indirect, and indexed-indirect — so they share a single code path rather than
four. `LDX`, `STX`, `JMA` and `JSR` suppress the indexing step, because their IX
field names the register being operated on rather than an index to add;
indirection still applies to them.

---

## Machine faults

A fault sets one bit of MFR, saves the interrupted PC at location 4, and
transfers control to the handler address held at location 1.

| Id | MFR | Condition |
|---|---|---|
| 0 | 0001 | A running program wrote to a reserved location (0–5) |
| 1 | 0010 | Illegal TRAP code |
| 2 | 0100 | Illegal operation code |
| 3 | 1000 | Address beyond installed memory |

Locations 0 to 5 are reserved: 0 holds the trap table address, 1 the fault
handler address, 2 the PC saved on a trap, and 4 the PC saved on a fault. A
running program may not write there, but the loader and the console's Store
button may, because a program needs some way to install its own vectors.

Fault id 1 is unreachable in practice. The TRAP code field is four bits wide and
so cannot express a value outside 0 to 15, which is the condition the ISA
defines for this fault. This was reported as a specification inconsistency in
Part 0.

---

## The cache

Sixteen lines, one word per line, fully associative, FIFO replacement,
write-through with no write-allocate.

Fully associative because with only sixteen lines a direct-mapped or
set-associative organisation would suffer conflict misses on programs whose code
and data sit a few words apart, which describes every test program here. FIFO
because it needs one queue and no per-access bookkeeping, and at this size the
difference from LRU is slight. Write-through because the front panel reads
memory directly, so a dirty line that had not yet reached memory would make the
console display lie.

The cache sits behind a `MemoryUnit` interface, so the processor is unaware of
it. The regression suite runs with the cache installed and produces identical
results, which is how its transparency is demonstrated rather than assumed.

---

## Test suite

`java -cp out TestHarness` runs ten scenarios drawn from the Part 0 test
programs in `input/`. Each expected result was derived by hand from the ISA
specification and then confirmed against the instructor's reference simulator,
so a passing run is evidence that this simulator implements the architecture
correctly rather than evidence that it agrees with itself.

Coverage: all four addressing modes; arithmetic including overflow and
two's-complement wrap; every branch instruction; subroutine linkage; the
register pair results of multiply and divide; divide by zero; shifts and
rotates; console I/O; TRAP; and three of the four machine faults.

---

## Repository layout

```
src/
  Main.java                    launches the operator console
  TestHarness.java             headless regression suite
  ScreenshotHarness.java       renders the console off-screen to a PNG
  cpu/
    CPU.java                   fetch, decode, execute; fault vectoring
    IODevices.java             keyboard, printer, card reader
    MachineFaultException.java
  memory/
    MemoryUnit.java            the interface the CPU sees
    Memory.java                2048 words, reserved-region enforcement
    Cache.java                 16-line fully associative FIFO cache
    RegisterFile.java          all registers, width masking, condition codes
  instruction/
    Opcode.java                the instruction set and its formats
    Instruction.java           decodes one word into named fields
  loader/
    LoadFileReader.java        reads the assembler's octal load files
  ui/
    OperatorConsole.java       the front panel
input/                         test programs: .asm sources and .load files
docs/                          console screenshots
```

---

## Known limitations

The floating-point and vector instructions — `FADD`, `FSUB`, `LDFR`, `STFR`,
`VADD`, `VSUB`, `CNVRT` — are decoded but not executed; attempting one raises an
illegal-operation fault. The ISA defers them to a later part of the project.

Memory is fixed at 2048 words. The ISA permits expansion to 4096, and the size
is a constructor parameter, but the console does not expose a way to change it.

The cache holds one word per line. A multi-word line would show spatial locality
effects more clearly and is a candidate for the next part of the project.
