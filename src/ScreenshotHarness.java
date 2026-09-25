import ui.OperatorConsole;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the operator console off-screen and writes a PNG.
 *
 * <p>Used to check the panel's layout without a human at a display, and to
 * produce the screenshots the project documentation needs. Not part of the
 * simulator itself.
 *
 * <p>Run with:  java -cp out ScreenshotHarness <output.png> [load-file] [startPc] [steps] [tab]
 */
public class ScreenshotHarness {

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "console.png";

        final OperatorConsole[] holder = new OperatorConsole[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new OperatorConsole();
            holder[0].setVisible(true);
        });
        OperatorConsole console = holder[0];

        if (args.length > 1) {
            String loadFile = args[1];
            int startPc = args.length > 2 ? Integer.parseInt(args[2], 8) : 0;
            int steps = args.length > 3 ? Integer.parseInt(args[3]) : 0;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    new loader.LoadFileReader().load(console.getCache(), loadFile);
                    console.getCpu().getRegisters().setPc(startPc);
                    if (steps > 0) {
                        console.getCpu().run(steps);
                    }
                    console.refreshAll();
                    if (args.length > 4) {
                        console.showTab(args[4]);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Thread.sleep(400); // let layout and painting settle
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(
                    console.getWidth(), console.getHeight(), BufferedImage.TYPE_INT_RGB);
            console.paint(image.getGraphics());
            try {
                ImageIO.write(image, "png", new File(out));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("wrote " + out);
        System.exit(0);
    }
}
