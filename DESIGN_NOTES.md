# Design Notes — C6461 Machine Simulator

CSCI 6461 Section 12, Fall 2026 — Team 1
Part I: basic machine, operator console, and cache display

These notes record the decisions taken in building the simulator and the
reasoning behind them, including the alternatives rejected. They are written to
be read alongside the source, not as a substitute for it.

---

## 1. What Part I had to achieve

The project description sets out the functional target: a processor with the
programmer-visible registers, 2048 words of memory reached through MAR and MBR,
a ROM loader, and an operator console from which a person can enter data into
R0–R3, enter data into memory via switches, enter load and store instructions,
set the PC, single-step, and see the contents of memory at the address in MAR.

Two constraints shaped the structure more than any feature did.

**Single-stepping is a requirement, not a debugging aid.** The console must be
able to advance the machine one instruction at a time. That means the
instruction cycle cannot be written as a loop with an exit condition; it has to
be a function that performs exactly one cycle and returns.

**The console displays MAR and MBR.** Those registers are meant to show the
address and word of the most recent memory access. If memory access is scattered
through the execute logic, keeping them current becomes a matter of remembering
to update them in a dozen places, and any one omission produces a display that
is subtly wrong in a way no test would catch.

Both are addressed in section 3.

---

## 2. Overall structure

```
          OperatorConsole  (ui)
                 |
                CPU  (cpu)  ----  RegisterFile  (memory)
                 |
            MemoryUnit  (interface)
              /        \
         Cache  ---->  Memory
```

Packages divide by role rather than by convenience: `cpu` for the processor and
its peripherals, `memory` for storage and registers, `instruction` for the
instruction set and its decoding, `loader` for reading load files, `ui` for the
console. Nothing in `cpu` or `memory` imports anything from `ui`, which is what
allows the whole machine to be driven headlessly by the test harness.

---

## 3. Key decisions

### 3.1 `singleStep()` is the primitive; `run()` is built from it

`CPU.singleStep()` performs one fetch-decode-execute cycle and returns.
`CPU.run(maxSteps)` calls it in a loop.

The obvious alternative — write the run loop first and add stepping later — was
rejected because it tends to produce two code paths that drift apart, and
because stepping is a stated requirement rather than an afterthought. Building
the single cycle first means the console's SS button and its Run button execute
identical code, so a program cannot behave differently depending on how it was
started.

The console's Run does not call `run()` to completion. It drives a Swing timer
that executes a burst of instructions per tick. Executing to completion on the
event dispatch thread would freeze the interface, making HLT unpressable and a
runaway program unstoppable without killing the process.

### 3.2 All memory access passes through two helpers

`CPU.readMemory()` and `CPU.writeMemory()` set MAR, perform the access, and set
MBR. No other code in the CPU touches memory directly.

This is what makes the console's MAR and MBR displays correct for free: they are
maintained at the single point where memory is actually reached, so there is no
opportunity to forget. It also mirrors the real data path, where the processor
places an address in MAR, asserts a read or write, and the word appears in MBR.

### 3.3 Effective address is computed once, from the ISA's own two rules

The specification defines the effective address as two rules applied in
sequence: add the index register when the IX field is non-zero, then dereference
once if the indirect bit is set. The four named addressing modes — direct,
indexed, indirect, indexed-indirect — are simply the four combinations.

`effectiveAddress()` implements the two rules rather than the four modes. Beyond
being shorter, this has a testing consequence: a single program that exercises
the combinations exercises the rules, so test T1 passing establishes all four
modes at once rather than four separately.

`LDX`, `STX`, `JMA` and `JSR` pass `allowIndexing = false`, because for those
instructions the IX field names the register being operated on rather than an
index to add. Indirection still applies. Conflating these two uses of the same
field is an easy error and would corrupt every indexed load of an index
register.

### 3.4 `write()` and `deposit()` are separate operations

Memory exposes two write paths. `write()` refuses the reserved region 0–5 and
raises fault id 0; `deposit()` permits it.

The reserved locations hold the trap vector table address, the machine fault
handler address, and the saved PCs. A running program must not write there — the
ISA makes that a machine fault. But the loader must, because a program needs to
install its own handler addresses, and the console's Store button must, because
the operator is acting as the machine rather than as a program. A single write
method with a flag would work but puts the decision at each call site; two
methods put it in the type, and the name at the call site documents which role
the caller is playing.

### 3.5 Registers mask to their real width on write

`RegisterFile` masks every value to the register's width as it is stored: 16
bits for a GPR, 12 for the PC and MAR, 4 for MFR and CC.

Nothing outside the class then has to remember how wide anything is, and
two's-complement wrap-around falls out of the masking rather than needing
special handling. Adding 1 to `077777` (+32767) produces `100000`, which read as
signed is −32768, exactly as the hardware would.

### 3.6 Index registers use a 1-based array with slot 0 unused

The ISA numbers the index registers X1 to X3 and uses IX = 0 in an instruction
to mean "no indexing". Storing them in a four-element array with index 0 unused
means the instruction's IX field is the array index directly, with no
subtraction anywhere. One wasted slot removes an entire class of off-by-one
error.

### 3.7 Condition codes are numbered as the ISA numbers them, not by bit position

This decision is called out because getting it wrong produced the only real bug
found during development, and the bug was silent.

The ISA numbers the four condition codes 0 to 3 in the order OVERFLOW,
UNDERFLOW, DIVZERO, EQUALORNOT, and `JCC` takes that number directly as its
operand — which is why the test programs write `JCC 3` to branch on a comparison
set by `TRR`. But the register is *displayed* as four binary digits with
OVERFLOW leftmost, so flag 0 must occupy the most significant bit and flag 3 the
least. The flag number is therefore the reverse of the bit position.

The first implementation used the bit position as the flag number. Arithmetic
still set the correct flag. The console still displayed the correct digits.
Every `JCC` tested the wrong condition. Nothing about the failure was visible
except that one branch did not branch.

`RegisterFile` now works exclusively in ISA flag numbers, and converts to a bit
position in one private method. See section 5 for how the bug was caught.

### 3.8 The cache sits behind an interface, so the CPU cannot see it

`MemoryUnit` declares read, write, deposit, size and clear. `Memory` implements
it; `Cache` implements it and wraps another `MemoryUnit`. The CPU holds a
`MemoryUnit` and is constructed with whichever it is given.

This means the cache can be inserted or removed without touching the processor,
and Part II can change the caching policy by writing another implementation
rather than editing the CPU. It also makes transparency testable: the regression
suite runs with the cache installed and produces byte-identical results, which
demonstrates rather than asserts that the cache does not alter behaviour.

### 3.9 Cache policy: sixteen lines, fully associative, FIFO, write-through

Each choice was made for this machine specifically.

*Fully associative.* With only sixteen lines, a direct-mapped or set-associative
organisation would suffer conflict misses on programs whose instructions and
data sit a few words apart — which describes every test program in `input/`,
since they load data at location 6 and execute from location 14. Associativity
is cheap at this size and avoids a pathology that would otherwise dominate the
measurements.

*FIFO replacement.* One queue, no per-access bookkeeping. At sixteen lines the
difference from LRU is slight, and FIFO is trivially explainable to someone
looking at the cache display, which matters when the contents are on screen.

*Write-through, no write-allocate.* This is the decision most specific to this
machine. The front panel can read memory directly through MAR and Load. Under a
write-back policy a dirty line that had not yet reached memory would make that
display show a stale value — the console would lie about the machine's own
state. Correctness of what the operator sees outweighs the write traffic a
write-back policy would save.

*One word per line.* Simple, and adequate for Part I's display requirement. A
multi-word line would show spatial locality far better and is the obvious
improvement for Part II.

### 3.10 The console mirrors the reference simulator, with two deliberate departures

The panel follows the layout of the course reference simulator — registers on
the left, internal registers and controls in the middle, the switch register
along the bottom, console terminal to the right — so that anyone who has used
that machine can operate this one without instruction.

Two behaviours are deliberately different, both fixing defects found while
testing the reference tools in Part 0:

*Registers accept typed values.* On the reference machine, typing an address
into the PC field and pressing the adjacent LD button silently loads zero from
the switch register instead, and the program then runs from location 0 for no
visible reason. Here both paths work: type and press Enter, or set the switches
and press LD.

*The program chooser does not filter out `.load` files.* The reference
simulator's file dialog excludes the very extension its companion assembler
produces, making the documented workflow impossible without a workaround. Ours
accepts `.load` and `.txt` and leaves the all-files option enabled.

---

## 4. Instruction decoding

`Instruction.decode()` splits a 16-bit word once into named fields, and the
execute logic reads those fields rather than repeating shift-and-mask
expressions. The bit layout is therefore described in exactly one place.

Bit numbering follows the ISA document, where bit 0 is the most significant bit
of the word. The standard format is opcode in bits 0–5, R in 6–7, IX in 8–9, the
indirect bit at 10, address in 11–15.

`Opcode` is an enum carrying each mnemonic's octal value and its format. Opcodes
are written in Java octal notation — `041` rather than `33` — so that the source
can be checked against the specification by eye. The enum includes the
floating-point and vector instructions even though they are not implemented, so
that encountering one can be reported as "not implemented until Part IV" rather
than being indistinguishable from an undefined opcode.

---

## 5. Verification

Correctness here does not rest on the code looking right.

The ten programs in `input/` were written for Part 0, where the task was to test
the instructor's assembler and simulator. For each, the expected assembler
output and the expected final machine state were derived by hand from the ISA
specification, and then confirmed by running them on the reference simulator.
Those expected values are therefore independently established.

`TestHarness` runs the same programs against this simulator and compares the
resulting registers, condition codes, machine fault register, memory contents
and printer output against those values. A passing run is evidence about the
architecture, not merely evidence that the implementation is self-consistent —
which is the distinction that makes the suite worth having.

Coverage: all four addressing modes; arithmetic with overflow and
two's-complement wrap; every branch instruction; subroutine linkage; multiply
and divide register pairs; divide by zero; shifts and rotates including sign
preservation; console I/O; TRAP; and three of the four machine faults. The
fourth, id 1, cannot be raised by any program — the TRAP code field is four bits
wide and so cannot express a value outside the legal range that the ISA defines
as the fault condition. This was reported as a specification inconsistency in
Part 0.

**The condition code bug.** Of nine scenarios on the first run, eight passed.
T3 halted at octal 0034 — the address of a HLT placed immediately after a `JCC`
precisely so that reaching it would prove the branch had not been taken. That
single address identified the failing instruction without any further debugging,
and the cause was the flag-numbering error described in 3.7. The bug would not
have been caught by inspection, by any test that merely checked arithmetic
results, or by looking at the console: it is only visible to a test that
arranges a condition to be true and then branches on it. This is the strongest
argument for the diagnostic-halt technique used throughout the test programs.

---

## 6. What Part II will need

The cache is the obvious extension: a multi-word line, and a policy comparison
using the hit-rate counters the display already maintains. `MemoryUnit` is the
seam for that work, so the processor should need no changes.

The remaining instruction families are already implemented and tested, so Part
II's "all instructions working" requirement is largely met. The floating-point
and vector group is deferred to Part IV per the ISA.

The two programs Parts II and III require — reading and sorting numbers, and
searching text loaded through the card reader — will need the card reader device
fleshed out beyond the queue `IODevices` currently provides, and a means of
loading a text file into it from the console.
