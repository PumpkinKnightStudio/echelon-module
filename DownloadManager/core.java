import echelon.desktop.AbstractModule;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class core extends AbstractModule {
    private JFrame frame;
    @Override
    public void start() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) {
                frame = new JFrame("Default DownloadManager");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(400, 200);
                frame.setLocationRelativeTo(null);
                frame.getContentPane().add(new JLabel("Default DM: No real function yet.", SwingConstants.CENTER));
            }
            frame.setVisible(true);
            frame.toFront();
        });
    }
    @Override
    public void bringToFront() {
        if (frame != null) {
            frame.setVisible(true);
            frame.toFront();
        }
    }
    @Override
    public void hideModule() {
        if (frame != null) {
            frame.setVisible(false);
        }
    }
    @Override
    public void showModule() {
        bringToFront();
    }
    @Override
    public void close() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }
    @Override
    public boolean isVisible() {
        return frame != null && frame.isVisible();
    }
}
