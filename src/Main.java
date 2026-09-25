import ui.OperatorConsole;

import javax.swing.SwingUtilities;

/**
 * Entry point for the C6461 machine simulator.
 *
 * <p>Launches the operator console on the Swing event dispatch thread, which is
 * the only thread allowed to touch Swing components.
 *
 * <p>Run from the command line with:  java -jar C6461MachineSimulator.jar
 * or during development with:         java -cp out Main
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OperatorConsole().setVisible(true));
    }
}
