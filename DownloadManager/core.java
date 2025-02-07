package echelon.desktop.plugins.downloadmanager;

import echelon.desktop.AbstractModule;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A minimal DownloadManager plugin (default version).
 * 
 * Usage:
 *   core dm = new core();
 *   dm.doDownload("https://github.com/PumpkinKnightStudio/echelon-module/raw/main/DownloadManager/somefile.zip",
 *                 "/path/to/dest/somefile.zip");
 */
public class core extends AbstractModule {

    private JFrame frame;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private volatile boolean downloading = false;

    public core() {
        // Optionally store version info or do other init
    }

    /**
     * Start the plugin (called by the system). Just builds a hidden UI.
     * We won't show it until doDownload(...) is called.
     */
    @Override
    public void start() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) {
                buildUI();
            }
        });
    }

    /**
     * doDownload(link, destination):
     *  - Shows the UI with a progress bar,
     *  - Downloads the file in a background thread,
     *  - Disposes the UI once done or if the window is closed.
     */
    public void doDownload(String link, String destination) {
        if (frame == null) {
            buildUI();
        }
        frame.setTitle("DownloadManager - Downloading...");
        statusLabel.setText("Downloading from: " + link);
        progressBar.setValue(0);
        frame.setVisible(true);
        frame.toFront();

        downloading = true;

        Thread downloadThread = new Thread(() -> {
            try {
                downloadFile(link, destination);
                SwingUtilities.invokeLater(() -> {
                    // If still open, show success
                    statusLabel.setText("Download complete: " + destination);
                    // Dispose automatically after a short delay
                    Timer t = new Timer(1500, (e) -> close());
                    t.setRepeats(false);
                    t.start();
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Download failed: " + ex.getMessage());
                    // Dispose automatically after a short delay
                    Timer t = new Timer(3000, (e) -> close());
                    t.setRepeats(false);
                    t.start();
                });
            }
        });
        downloadThread.start();
    }

    /**
     * Actually download the file, updating progressBar if content-length known.
     */
    private void downloadFile(String link, String destPath) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(link).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        int response = conn.getResponseCode();
        if (response != 200) {
            throw new Exception("HTTP error " + response);
        }

        int contentLength = conn.getContentLength();
        boolean hasLength = (contentLength > 0);

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destPath)) {

            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int read;
            while ((read = in.read(buffer)) != -1 && downloading) {
                out.write(buffer, 0, read);
                totalRead += read;

                if (hasLength) {
                    int pct = (int)((totalRead * 100) / contentLength);
                    SwingUtilities.invokeLater(() -> progressBar.setValue(pct));
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Build a simple UI with a progress bar and status label.
     * The window automatically disposes when done or if user closes it.
     */
    private void buildUI() {
        frame = new JFrame("DownloadManager (Default)");
        frame.setSize(400, 150);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        statusLabel = new JLabel("Ready to download", SwingConstants.CENTER);
        panel.add(statusLabel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        panel.add(progressBar, BorderLayout.CENTER);

        // If user closes the window, cancel download
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                downloading = false;
                close();
            }
        });

        frame.getContentPane().add(panel);
    }

    // ----------------- AbstractModule methods ------------------

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
        if (frame != null) {
            frame.setVisible(true);
            frame.toFront();
        }
    }

    @Override
    public void close() {
        downloading = false;
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
