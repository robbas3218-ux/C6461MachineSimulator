package ui;

import cpu.CPU;
import cpu.IODevices;
import cpu.MachineFaultException;
import loader.LoadFileReader;
import memory.Cache;
import memory.Memory;
import memory.RegisterFile;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * The operator console: the machine's front panel.
 *
 * <p>The layout deliberately follows the reference simulator used in the course
 * — registers down the left, internal registers and controls in the middle, a
 * sixteen-position switch register along the bottom, and the console terminal to
 * the right — so that anyone familiar with that machine can operate this one
 * without instruction.
 *
 * <p>Two behaviours differ from that reference, both on purpose. Registers can
 * be set either from the switch register with the LD button or by typing an
 * octal value directly into the field; the reference silently discards a typed
 * value when LD is pressed, which is easy to mistake for a broken machine. And
 * the program loader's file chooser does not filter out the assembler's own
 * <code>.load</code> extension. Both of these were recorded as defects while
 * testing the reference tools in Part 0.
 */
public class OperatorConsole extends JFrame {

    private static final Font PANEL_FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);
    private static final Font SMALL_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 11);
    private static final Color PANEL_BG = new Color(0x2E6D8E);
    private static final Color LABEL_FG = Color.WHITE;

    // ----- the machine -----
    private final Memory memory = new Memory();
    private final Cache cache = new Cache(memory);
    private final RegisterFile registers = new RegisterFile();
    private final IODevices io = new IODevices();
    private final CPU cpu = new CPU(cache, registers, io);

    // ----- register displays -----
    private final JTextField[] gprFields = new JTextField[RegisterFile.GPR_COUNT];
    private final JTextField[] ixrFields = new JTextField[RegisterFile.IXR_COUNT];
    private JTextField pcField, marField, mbrField, irField, mfrField, ccField;

    // ----- switch register -----
    private final JToggleButton[] switches = new JToggleButton[16];
    private JLabel switchReadout;

    // ----- side panels -----
    private JTextArea printerArea;
    private JTextField lineInput;
    private JTextArea cacheArea;
    private JLabel cacheStats;
    private JTextArea traceArea;
    private JLabel statusLabel;
    private JTabbedPane tabs;

    /**
     * Drives continuous execution without freezing the interface. Not final,
     * because its own action listener refers back to it in order to stop itself
     * when the machine halts.
     */
    private Timer runTimer;

    public OperatorConsole() {
        super("C6461 Machine Simulator — Operator Console");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Run executes a burst of instructions per tick rather than looping to
        // completion, so the event thread stays free and HLT can still be pressed.
        runTimer = new Timer(10, e -> {
            for (int i = 0; i < 200 && !cpu.isHalted(); i++) {
                traceCurrentInstruction();
                cpu.singleStep();
            }
            if (cpu.isHalted()) {
                runTimer.stop();
            }
            refresh();
        });

        setJMenuBar(buildMenuBar());
        setLayout(new BorderLayout(8, 8));
        add(buildPanel(), BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        add(buildSouth(), BorderLayout.SOUTH);

        cpu.initialise();
        refresh();
        pack();
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");

        JMenuItem loadProgram = new JMenuItem("Load Program…");
        loadProgram.addActionListener(e -> chooseAndLoadProgram());
        file.add(loadProgram);

        file.addSeparator();
        JMenuItem quit = new JMenuItem("Quit");
        quit.addActionListener(e -> System.exit(0));
        file.add(quit);

        bar.add(file);
        return bar;
    }

    private void chooseAndLoadProgram() {
        JFileChooser chooser = new JFileChooser(new File("input"));
        chooser.setDialogTitle("Load program");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Assembler load files (*.load, *.txt)", "load", "txt"));
        // Leaving the all-files option enabled means no file is ever unselectable.
        // The reference simulator filters out .load — the very extension its own
        // assembler produces — which makes the documented workflow impossible.
        chooser.setAcceptAllFileFilterUsed(true);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            int words = new LoadFileReader().load(cache, chooser.getSelectedFile().getPath());
            appendTrace("loaded " + words + " words from "
                    + chooser.getSelectedFile().getName());
            status("Memory load complete — " + words + " words");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Could not load program", JOptionPane.ERROR_MESSAGE);
        }
        refresh();
    }

    // ------------------------------------------------------------------
    // The panel proper
    // ------------------------------------------------------------------

    private JComponent buildPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        // Left column: the programmer-visible registers. Index registers follow
        // the general purpose ones after a one-row gap, matching the reference
        // panel's grouping.
        int leftRow = 0;
        for (int i = 0; i < RegisterFile.GPR_COUNT; i++) {
            final int n = i;
            gprFields[i] = registerRow(panel, c, leftRow++, "GPR " + i,
                    v -> registers.setGpr(n, v));
        }
        leftRow++;
        for (int i = 1; i < RegisterFile.IXR_COUNT; i++) {
            final int n = i;
            ixrFields[i] = registerRow(panel, c, leftRow++, "IXR " + i,
                    v -> registers.setIxr(n, v));
        }

        // Right column: the internal registers, which a program cannot name.
        final int col = 3;
        int rightRow = 0;
        pcField  = internalRow(panel, c, col, rightRow++, "PC",  v -> registers.setPc(v),  true);
        marField = internalRow(panel, c, col, rightRow++, "MAR", v -> registers.setMar(v), true);
        mbrField = internalRow(panel, c, col, rightRow++, "MBR", v -> registers.setMbr(v), false);
        irField  = internalRow(panel, c, col, rightRow++, "IR",  null, false);
        mfrField = internalRow(panel, c, col, rightRow++, "MFR", null, false);
        ccField  = internalRow(panel, c, col, rightRow++, "CC",  null, false);

        // Controls sit below both columns so neither can be pushed out of line
        // by the other growing.
        c.gridx = col;
        c.gridy = Math.max(leftRow, rightRow) + 1;
        c.gridwidth = 3;
        c.anchor = GridBagConstraints.NORTHWEST;
        panel.add(buildControls(), c);

        return panel;
    }

    /** One general purpose or index register: label, octal field, LD button. */
    private JTextField registerRow(JPanel panel, GridBagConstraints c, int row,
                                   String label, java.util.function.IntConsumer setter) {
        return addRow(panel, c, 0, row, label, setter, 6, true);
    }

    private JTextField internalRow(JPanel panel, GridBagConstraints c, int col, int row,
                                   String label, java.util.function.IntConsumer setter,
                                   boolean loadable) {
        return addRow(panel, c, col, row, label, setter, label.equals("PC")
                || label.equals("MAR") ? 4 : 6, loadable);
    }

    private JTextField addRow(JPanel panel, GridBagConstraints c, int col, int row,
                              String label, java.util.function.IntConsumer setter,
                              int width, boolean loadable) {
        c.gridwidth = 1;

        JLabel name = new JLabel(label);
        name.setFont(PANEL_FONT);
        name.setForeground(LABEL_FG);
        c.gridx = col;
        c.gridy = row;
        panel.add(name, c);

        JTextField field = new JTextField(width);
        field.setFont(PANEL_FONT);
        field.setHorizontalAlignment(JTextField.CENTER);
        if (setter != null) {
            // Typing an octal value and pressing Enter sets the register. The
            // reference simulator ignores typed values when LD is pressed, which
            // was recorded as a usability defect in Part 0.
            field.addActionListener(e -> {
                try {
                    setter.accept(Integer.parseInt(field.getText().trim(), 8));
                    status(label + " set from keyboard");
                } catch (NumberFormatException ex) {
                    status(label + ": not an octal number");
                }
                refresh();
            });
        } else {
            field.setEditable(false);
        }
        c.gridx = col + 1;
        panel.add(field, c);

        if (setter != null && loadable) {
            JButton load = new JButton("LD");
            load.setMargin(new Insets(1, 4, 1, 4));
            load.setToolTipText("Load " + label + " from the switch register");
            load.addActionListener(e -> {
                setter.accept(switchRegisterValue());
                status(label + " loaded from switches");
                refresh();
            });
            c.gridx = col + 2;
            panel.add(load, c);
        }
        return field;
    }

    private JComponent buildControls() {
        JPanel controls = new JPanel(new GridLayout(4, 3, 6, 6));
        controls.setOpaque(false);

        controls.add(button("Store", "Write MBR into the address held in MAR", e -> doStore(false)));
        controls.add(button("Load",  "Read the address held in MAR into MBR",  e -> doLoad(false)));
        controls.add(redButton("Init", "Clear memory, registers and devices",   e -> doInit()));

        controls.add(button("St+", "Store, then advance MAR", e -> doStore(true)));
        controls.add(button("Ld+", "Load, then advance MAR",  e -> doLoad(true)));
        controls.add(new JLabel());

        controls.add(button("Run", "Execute until the machine halts", e -> doRun()));
        controls.add(button("SS",  "Execute a single instruction",    e -> doSingleStep()));
        controls.add(new JLabel());

        controls.add(redButton("HLT", "Stop the machine", e -> doHalt()));
        return controls;
    }

    private JButton button(String text, String tip, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.addActionListener(action);
        return b;
    }

    private JButton redButton(String text, String tip, java.awt.event.ActionListener action) {
        JButton b = button(text, tip, action);
        b.setForeground(new Color(0xB00000));
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        return b;
    }

    // ------------------------------------------------------------------
    // Switch register and status bar
    // ------------------------------------------------------------------

    private JComponent buildSouth() {
        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.setBackground(PANEL_BG);

        JPanel switchPanel = new JPanel(new GridBagLayout());
        switchPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1, 1, 1, 1);

        for (int i = 0; i < 16; i++) {
            switches[i] = new JToggleButton(String.valueOf(i));
            switches[i].setFont(SMALL_FONT);
            switches[i].setMargin(new Insets(2, 4, 2, 4));
            switches[i].addActionListener(e -> refreshSwitchReadout());
            c.gridx = i;
            c.gridy = 0;
            switchPanel.add(switches[i], c);
        }

        // Field labels matching the standard instruction format, so the switch
        // register can be used to key an instruction in by hand.
        addFieldLabel(switchPanel, c, "Operation", 0, 6);
        addFieldLabel(switchPanel, c, "GPR", 6, 2);
        addFieldLabel(switchPanel, c, "IXR", 8, 2);
        addFieldLabel(switchPanel, c, "I", 10, 1);
        addFieldLabel(switchPanel, c, "Address", 11, 5);

        switchReadout = new JLabel();
        switchReadout.setFont(PANEL_FONT);
        switchReadout.setForeground(LABEL_FG);
        switchReadout.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));

        JButton clearSwitches = new JButton("Clear switches");
        clearSwitches.addActionListener(e -> {
            for (JToggleButton s : switches) s.setSelected(false);
            refreshSwitchReadout();
        });

        JPanel readoutRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        readoutRow.setOpaque(false);
        readoutRow.add(switchReadout);
        readoutRow.add(clearSwitches);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(SMALL_FONT);
        statusLabel.setForeground(LABEL_FG);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));

        south.add(switchPanel, BorderLayout.NORTH);
        south.add(readoutRow, BorderLayout.CENTER);
        south.add(statusLabel, BorderLayout.SOUTH);
        return south;
    }

    private void addFieldLabel(JPanel panel, GridBagConstraints c,
                               String text, int startBit, int width) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(SMALL_FONT);
        label.setForeground(LABEL_FG);
        c.gridx = startBit;
        c.gridy = 1;
        c.gridwidth = width;
        panel.add(label, c);
        c.gridwidth = 1;
    }

    /** Switch 0 is the most significant bit, switch 15 the least. */
    private int switchRegisterValue() {
        int value = 0;
        for (int i = 0; i < 16; i++) {
            if (switches[i].isSelected()) {
                value |= 1 << (15 - i);
            }
        }
        return value;
    }

    private void refreshSwitchReadout() {
        int v = switchRegisterValue();
        // The binary field is zero-padded on its own; padding the whole string
        // would turn the separating spaces into zeroes too.
        String binary = String.format("%16s", Integer.toBinaryString(v)).replace(' ', '0');
        switchReadout.setText(String.format("%s  /  %06o octal", binary, v));
    }

    // ------------------------------------------------------------------
    // Console terminal, cache display and trace
    // ------------------------------------------------------------------

    private JComponent buildSidePanel() {
        tabs = new JTabbedPane();
        tabs.setPreferredSize(new Dimension(360, 480));
        tabs.addTab("Console", buildConsoleTab());
        tabs.addTab("Cache", buildCacheTab());
        tabs.addTab("Trace", buildTraceTab());
        return tabs;
    }

    private JComponent buildConsoleTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));

        printerArea = new JTextArea(16, 28);
        printerArea.setEditable(false);
        printerArea.setFont(SMALL_FONT);
        printerArea.setBackground(Color.BLACK);
        printerArea.setForeground(new Color(0x33FF66));
        JScrollPane scroll = new JScrollPane(printerArea);
        scroll.setBorder(new TitledBorder("Printer output (device 1)"));

        lineInput = new JTextField();
        lineInput.setFont(SMALL_FONT);
        JButton send = new JButton("Send Line");
        send.addActionListener(e -> sendLine());
        lineInput.addActionListener(e -> sendLine());

        JPanel inputRow = new JPanel(new BorderLayout(4, 4));
        inputRow.setBorder(new TitledBorder("Keyboard input (device 0)"));
        inputRow.add(lineInput, BorderLayout.CENTER);
        inputRow.add(send, BorderLayout.EAST);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(inputRow, BorderLayout.SOUTH);
        return panel;
    }

    private void sendLine() {
        io.typeLine(lineInput.getText());
        status("queued " + (lineInput.getText().length() + 1) + " characters for device 0");
        lineInput.setText("");
        refresh();
    }

    private JComponent buildCacheTab() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        cacheArea = new JTextArea(16, 28);
        cacheArea.setEditable(false);
        cacheArea.setFont(SMALL_FONT);
        JScrollPane scroll = new JScrollPane(cacheArea);
        scroll.setBorder(new TitledBorder("Cache contents (oldest line first)"));

        cacheStats = new JLabel(" ");
        cacheStats.setFont(SMALL_FONT);
        cacheStats.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JButton invalidate = new JButton("Invalidate cache");
        invalidate.addActionListener(e -> {
            cache.invalidate();
            status("cache invalidated");
            refresh();
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(cacheStats, BorderLayout.CENTER);
        bottom.add(invalidate, BorderLayout.EAST);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildTraceTab() {
        traceArea = new JTextArea(16, 28);
        traceArea.setEditable(false);
        traceArea.setFont(SMALL_FONT);
        JScrollPane scroll = new JScrollPane(traceArea);
        scroll.setBorder(new TitledBorder("Instruction trace"));
        return scroll;
    }

    // ------------------------------------------------------------------
    // Front panel operations
    // ------------------------------------------------------------------

    private void doInit() {
        runTimer.stop();
        cpu.initialise();
        cache.invalidate();
        printerArea.setText("");
        traceArea.setText("");
        for (JToggleButton s : switches) s.setSelected(false);
        status("machine initialised");
        refresh();
    }

    private void doStore(boolean advance) {
        try {
            // Store uses deposit: the front panel is the machine itself, so it is
            // allowed to set up the reserved vectors a running program may not.
            cache.deposit(registers.getMar(), registers.getMbr());
            status(String.format("stored %06o at %04o", registers.getMbr(), registers.getMar()));
            if (advance) registers.setMar(registers.getMar() + 1);
        } catch (MachineFaultException ex) {
            status(ex.getMessage());
        }
        refresh();
    }

    private void doLoad(boolean advance) {
        try {
            registers.setMbr(cache.read(registers.getMar()));
            status(String.format("read %06o from %04o", registers.getMbr(), registers.getMar()));
            if (advance) registers.setMar(registers.getMar() + 1);
        } catch (MachineFaultException ex) {
            status(ex.getMessage());
        }
        refresh();
    }

    private void doSingleStep() {
        if (cpu.isHalted()) {
            status("machine is halted — press Init or set the PC to continue");
            return;
        }
        traceCurrentInstruction();
        cpu.singleStep();
        status(cpu.isHalted() ? "halted" : "stepped");
        refresh();
    }

    private void doRun() {
        if (cpu.isHalted()) {
            status("machine is halted — press Init or set the PC to continue");
            return;
        }
        status("running");
        runTimer.start();
    }

    private void doHalt() {
        runTimer.stop();
        cpu.setHalted(true);
        status("halted from the front panel");
        refresh();
    }

    private void traceCurrentInstruction() {
        try {
            int pc = registers.getPc();
            int word = memory.read(pc);
            appendTrace(String.format("%04o: %06o  %s", pc, word,
                    instruction.Instruction.decode(word)));
        } catch (MachineFaultException ignored) {
            // Tracing must never be what breaks a run.
        }
    }

    private void appendTrace(String line) {
        if (traceArea.getLineCount() > 2000) {
            traceArea.setText("");   // keep the trace from growing without bound
        }
        traceArea.append(line + "\n");
        traceArea.setCaretPosition(traceArea.getDocument().getLength());
    }

    private void status(String message) {
        statusLabel.setText(message);
    }

    // ------------------------------------------------------------------
    // Display refresh
    // ------------------------------------------------------------------

    /** Repaints every display from the machine's current state. */
    private void refresh() {
        for (int i = 0; i < RegisterFile.GPR_COUNT; i++) {
            gprFields[i].setText(String.format("%06o", registers.getGpr(i)));
        }
        for (int i = 1; i < RegisterFile.IXR_COUNT; i++) {
            ixrFields[i].setText(String.format("%06o", registers.getIxr(i)));
        }
        pcField.setText(String.format("%04o", registers.getPc()));
        marField.setText(String.format("%04o", registers.getMar()));
        mbrField.setText(String.format("%06o", registers.getMbr()));
        irField.setText(String.format("%06o", registers.getIr()));
        mfrField.setText(binary4(registers.getMfr()));
        ccField.setText(binary4(registers.getCc()));

        printerArea.setText(io.getPrinterOutput());
        refreshSwitchReadout();
        refreshCacheDisplay();

        setTitle("C6461 Machine Simulator — Operator Console"
                + (cpu.isHalted() ? "  [HALTED]" : "  [running]"));
    }

    private void refreshCacheDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("line  address   word\n");
        sb.append("--------------------\n");
        int n = 0;
        for (Cache.Line line : cache.snapshot()) {
            sb.append(String.format("%3d   %06o   %06o%n", n++, line.address(), line.value()));
        }
        for (; n < cache.getCapacity(); n++) {
            sb.append(String.format("%3d   ------   ------%n", n));
        }
        cacheArea.setText(sb.toString());
        cacheStats.setText(String.format("%d hits / %d misses (%.0f%%)",
                cache.getHits(), cache.getMisses(), cache.getHitRate() * 100));
    }

    private static String binary4(int value) {
        return String.format("%4s", Integer.toBinaryString(value & 0x0F)).replace(' ', '0');
    }

    // ------------------------------------------------------------------

    /** Exposed so the screenshot harness can drive a console without a user. */
    public CPU getCpu()      { return cpu; }
    public Cache getCache()  { return cache; }
    public void refreshAll() { refresh(); }

    /** Brings a named tab to the front; used by the screenshot harness. */
    public void showTab(String title) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTitleAt(i).equalsIgnoreCase(title)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }
}
