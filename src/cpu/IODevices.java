package cpu;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The machine's peripherals, as seen by the IN, OUT and CHK instructions.
 *
 * <p>The ISA assigns device 0 to the console keyboard, device 1 to the console
 * printer and device 2 to the card reader. Characters are transferred one at a
 * time in the low eight bits of a general purpose register.
 *
 * <p>Keeping this behind a small class rather than wiring Swing directly into
 * the CPU is what allows the machine to be driven headlessly by the test
 * harness: the tests pre-load the keyboard queue and then read the printer
 * output as a string, while the operator console attaches the same object to
 * its text fields.
 */
public class IODevices {

    public static final int KEYBOARD = 0;
    public static final int PRINTER  = 1;
    public static final int CARD_READER = 2;

    private final Deque<Integer> keyboardQueue = new ArrayDeque<>();
    private final Deque<Integer> cardReaderQueue = new ArrayDeque<>();
    private final StringBuilder printerOutput = new StringBuilder();

    /** Queues a line typed by the operator, terminated by a newline as the ISA expects. */
    public void typeLine(String line) {
        for (char c : line.toCharArray()) {
            keyboardQueue.add((int) c & 0xFF);
        }
        keyboardQueue.add(10); // newline, octal 000012
    }

    public void loadCardReader(String text) {
        for (char c : text.toCharArray()) {
            cardReaderQueue.add((int) c & 0xFF);
        }
    }

    /** IN: removes one character from the device and returns its code. */
    public int in(int deviceId) {
        Deque<Integer> q = queueFor(deviceId);
        if (q == null || q.isEmpty()) {
            return 0;
        }
        return q.poll();
    }

    /** OUT: writes the low eight bits of a register to the device. */
    public void out(int deviceId, int value) {
        if (deviceId == PRINTER) {
            printerOutput.append((char) (value & 0xFF));
        }
    }

    /**
     * CHK: returns device status. The simulator guide defines this as 1 when the
     * device is ready or input is waiting, 0 otherwise, so a program can spin on
     * CHK until a character arrives.
     */
    public int check(int deviceId) {
        Deque<Integer> q = queueFor(deviceId);
        if (q != null) {
            return q.isEmpty() ? 0 : 1;
        }
        return 1; // the printer is always ready
    }

    public String getPrinterOutput() {
        return printerOutput.toString();
    }

    public boolean keyboardHasInput() {
        return !keyboardQueue.isEmpty();
    }

    public void clear() {
        keyboardQueue.clear();
        cardReaderQueue.clear();
        printerOutput.setLength(0);
    }

    private Deque<Integer> queueFor(int deviceId) {
        if (deviceId == KEYBOARD) return keyboardQueue;
        if (deviceId == CARD_READER) return cardReaderQueue;
        return null;
    }
}
