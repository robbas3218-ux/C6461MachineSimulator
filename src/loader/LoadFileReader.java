package loader;

import cpu.MachineFaultException;
import memory.MemoryUnit;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Reads the assembler's load file into memory.
 *
 * <p>The format is two octal columns per line, an address followed by the word
 * to place there. Blank lines and anything after a semicolon are ignored, which
 * matches what the course assembler emits and what the reference simulator
 * accepts.
 *
 * <p>Words are placed with {@link MemoryUnit#deposit} rather than
 * {@link MemoryUnit#write} because a program is entitled to set up the trap vector
 * and fault handler pointers in the reserved region — the loader is acting as
 * the machine's ROM loader, not as a running program.
 */
public class LoadFileReader {

    /** Loads from a file on disk and returns the number of words placed. */
    public int load(MemoryUnit memory, String path) throws IOException, MachineFaultException {
        try (Reader reader = new FileReader(path)) {
            return load(memory, reader);
        }
    }

    /** Loads from load-file text, for the console's paste box and for tests. */
    public int loadFromText(MemoryUnit memory, String text)
            throws IOException, MachineFaultException {
        return load(memory, new StringReader(text));
    }

    private int load(MemoryUnit memory, Reader source) throws IOException, MachineFaultException {
        int count = 0;
        try (BufferedReader in = new BufferedReader(source)) {
            String line;
            int lineNumber = 0;
            while ((line = in.readLine()) != null) {
                lineNumber++;
                int comment = line.indexOf(';');
                if (comment >= 0) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 2) {
                    throw new IOException("load file line " + lineNumber
                            + " does not have an address and a word: " + line);
                }
                try {
                    int address = Integer.parseInt(parts[0], 8);
                    int word = Integer.parseInt(parts[1], 8);
                    memory.deposit(address, word);
                    count++;
                } catch (NumberFormatException e) {
                    throw new IOException("load file line " + lineNumber
                            + " is not a pair of octal numbers: " + line);
                }
            }
        }
        return count;
    }
}
