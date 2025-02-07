package echelon.core.plugins;

import echelon.desktop.AbstractModule;
import echelon.desktop.core.CentralModuleController;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.metal.MetalIconFactory;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * public class core extends AbstractModule
 *
 * This DownloadManager plugin provides a simple UI:
 *   - A text field for adding download URLs,
 *   - A table that lists downloads (showing URL, file size, progress, and status),
 *   - Control buttons (Pause, Resume, Cancel, Clear).
 *
 * When the user closes the window (clicks the red X), the plugin terminates.
 *
 * This file is intended for internal use and will be uploaded to GitHub.
 */
public class core extends AbstractModule {

    private JFrame frame;
    private JTextField addTextField;
    private DownloadsTableModel tableModel;
    private JTable table;
    private JButton pauseButton, resumeButton, cancelButton, clearButton;
    private Download selectedDownload;
    private boolean clearing;

    @Override
    public void start() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) {
                buildUI();
            }
            frame.setVisible(true);
            frame.toFront();
        });
    }

    private void buildUI() {
        frame = new JFrame("Download Manager (v1.0.0)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);

        // --- Menu ---
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenuItem exitMenuItem = new JMenuItem("Exit", KeyEvent.VK_X);
        exitMenuItem.addActionListener(e -> actionExit());
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // --- Add Panel (URL input) ---
        JPanel addPanel = new JPanel();
        addTextField = new JTextField(30);
        addPanel.add(addTextField);
        JButton addButton = new JButton("Add Download");
        addButton.addActionListener(e -> actionAdd());
        addPanel.add(addButton);

        // --- Downloads Table ---
        tableModel = new DownloadsTableModel();
        table = new JTable(tableModel);
        table.getSelectionModel().addListSelectionListener(e -> tableSelectionChanged());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ProgressRenderer renderer = new ProgressRenderer(0, 100);
        renderer.setStringPainted(true);
        table.setDefaultRenderer(JProgressBar.class, renderer);
        table.setRowHeight((int) renderer.getPreferredSize().getHeight());
        JPanel downloadsPanel = new JPanel(new BorderLayout());
        downloadsPanel.setBorder(BorderFactory.createTitledBorder("Downloads"));
        downloadsPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        // --- Buttons Panel ---
        JPanel buttonsPanel = new JPanel();
        pauseButton = new JButton("Pause");
        pauseButton.addActionListener(e -> actionPause());
        pauseButton.setEnabled(false);
        buttonsPanel.add(pauseButton);
        resumeButton = new JButton("Resume");
        resumeButton.addActionListener(e -> actionResume());
        resumeButton.setEnabled(false);
        buttonsPanel.add(resumeButton);
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> actionCancel());
        cancelButton.setEnabled(false);
        buttonsPanel.add(cancelButton);
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> actionClear());
        clearButton.setEnabled(false);
        buttonsPanel.add(clearButton);

        // --- Main Layout ---
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(addPanel, BorderLayout.NORTH);
        frame.getContentPane().add(downloadsPanel, BorderLayout.CENTER);
        frame.getContentPane().add(buttonsPanel, BorderLayout.SOUTH);

        // When closing the window, exit the plugin.
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                actionExit();
            }
        });
    }

    private void actionExit() {
        System.exit(0);
    }

    private void actionAdd() {
        URL verifiedUrl = verifyUrl(addTextField.getText());
        if (verifiedUrl != null) {
            tableModel.addDownload(new Download(verifiedUrl));
            addTextField.setText("");
        } else {
            JOptionPane.showMessageDialog(frame, "Invalid Download URL", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private URL verifyUrl(String urlStr) {
        if (!urlStr.toLowerCase().startsWith("http://") && !urlStr.toLowerCase().startsWith("https://"))
            return null;
        try {
            URL url = new URL(urlStr);
            if (url.getFile().length() < 2)
                return null;
            return url;
        } catch (Exception e) {
            return null;
        }
    }

    private void tableSelectionChanged() {
        if (selectedDownload != null)
            selectedDownload.deleteObserver((o, arg) -> updateButtons());
        if (!clearing && table.getSelectedRow() > -1) {
            selectedDownload = tableModel.getDownload(table.getSelectedRow());
            selectedDownload.addObserver((o, arg) -> updateButtons());
            updateButtons();
        }
    }

    private void actionPause() {
        selectedDownload.pause();
        updateButtons();
    }

    private void actionResume() {
        selectedDownload.resume();
        updateButtons();
    }

    private void actionCancel() {
        selectedDownload.cancel();
        updateButtons();
    }

    private void actionClear() {
        clearing = true;
        tableModel.clearDownload(table.getSelectedRow());
        clearing = false;
        selectedDownload = null;
        updateButtons();
    }

    private void updateButtons() {
        if (selectedDownload != null) {
            int status = selectedDownload.getStatus();
            switch (status) {
                case Download.DOWNLOADING:
                    pauseButton.setEnabled(true);
                    resumeButton.setEnabled(false);
                    cancelButton.setEnabled(true);
                    clearButton.setEnabled(false);
                    break;
                case Download.PAUSED:
                    pauseButton.setEnabled(false);
                    resumeButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    clearButton.setEnabled(false);
                    break;
                case Download.ERROR:
                    pauseButton.setEnabled(false);
                    resumeButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    clearButton.setEnabled(true);
                    break;
                default: // COMPLETE or CANCELLED
                    pauseButton.setEnabled(false);
                    resumeButton.setEnabled(false);
                    cancelButton.setEnabled(false);
                    clearButton.setEnabled(true);
            }
        } else {
            pauseButton.setEnabled(false);
            resumeButton.setEnabled(false);
            cancelButton.setEnabled(false);
            clearButton.setEnabled(false);
        }
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
        if (frame != null) {
            frame.setVisible(true);
            frame.toFront();
        }
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
    
    // --- Supporting Classes ---
    
    // DownloadsTableModel manages the download table data.
    static class DownloadsTableModel extends AbstractTableModel implements Observer {
        private static final String[] columnNames = {"URL", "Size", "Progress", "Status"};
        private static final Class[] columnClasses = {String.class, String.class, JProgressBar.class, String.class};
        private ArrayList<Download> downloadList = new ArrayList<>();
        
        public void addDownload(Download download) {
            download.addObserver(this);
            downloadList.add(download);
            fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
        }
        
        public Download getDownload(int row) {
            return downloadList.get(row);
        }
        
        public void clearDownload(int row) {
            downloadList.remove(row);
            fireTableRowsDeleted(row, row);
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }
        
        @Override
        public Class getColumnClass(int col) {
            return columnClasses[col];
        }
        
        @Override
        public int getRowCount() {
            return downloadList.size();
        }
        
        @Override
        public Object getValueAt(int row, int col) {
            Download download = downloadList.get(row);
            switch (col) {
                case 0:
                    return download.getUrl();
                case 1:
                    int size = download.getSize();
                    return (size == -1) ? "" : Integer.toString(size);
                case 2:
                    return new Float(download.getProgress());
                case 3:
                    return Download.STATUSES[download.getStatus()];
            }
            return "";
        }
        
        @Override
        public void update(Observable o, Object arg) {
            int index = downloadList.indexOf(o);
            fireTableRowsUpdated(index, index);
        }
    }
    
    // ProgressRenderer renders a progress bar in a table cell.
    static class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressRenderer(int min, int max) {
            super(min, max);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            setValue((int)((Float)value).floatValue());
            return this;
        }
    }
    
    // Download downloads a file from a URL.
    static class Download extends Observable implements Runnable {
        private static final int MAX_BUFFER_SIZE = 1024;
        public static final String STATUSES[] = {"Downloading", "Paused", "Complete", "Cancelled", "Error"};
        public static final int DOWNLOADING = 0;
        public static final int PAUSED = 1;
        public static final int COMPLETE = 2;
        public static final int CANCELLED = 3;
        public static final int ERROR = 4;
        
        private URL url;
        private int size;
        private int downloaded;
        private int status;
        
        public Download(URL url) {
            this.url = url;
            size = -1;
            downloaded = 0;
            status = DOWNLOADING;
            download();
        }
        
        public String getUrl() {
            return url.toString();
        }
        
        public int getSize() {
            return size;
        }
        
        public float getProgress() {
            return ((float) downloaded / size) * 100;
        }
        
        public int getStatus() {
            return status;
        }
        
        public void pause() {
            status = PAUSED;
            stateChanged();
        }
        
        public void resume() {
            status = DOWNLOADING;
            stateChanged();
            download();
        }
        
        public void cancel() {
            status = CANCELLED;
            stateChanged();
        }
        
        private void error() {
            status = ERROR;
            stateChanged();
        }
        
        private void download() {
            Thread thread = new Thread(this);
            thread.start();
        }
        
        private String getFileName(URL url) {
            String fileName = url.getFile();
            return fileName.substring(fileName.lastIndexOf('/') + 1);
        }
        
        public void run() {
            RandomAccessFile file = null;
            InputStream stream = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Range", "bytes=" + downloaded + "-");
                connection.connect();
                if (connection.getResponseCode() / 100 != 2) {
                    error();
                }
                int contentLength = connection.getContentLength();
                if (contentLength < 1) {
                    error();
                }
                if (size == -1) {
                    size = contentLength;
                    stateChanged();
                }
                file = new RandomAccessFile(getFileName(url), "rw");
                file.seek(downloaded);
                stream = connection.getInputStream();
                while (status == DOWNLOADING) {
                    byte[] buffer;
                    if (size - downloaded > MAX_BUFFER_SIZE) {
                        buffer = new byte[MAX_BUFFER_SIZE];
                    } else {
                        buffer = new byte[size - downloaded];
                    }
                    int read = stream.read(buffer);
                    if (read == -1)
                        break;
                    file.write(buffer, 0, read);
                    downloaded += read;
                    stateChanged();
                }
                if (status == DOWNLOADING) {
                    status = COMPLETE;
                    stateChanged();
                }
            } catch (Exception e) {
                error();
            } finally {
                if (file != null) {
                    try { file.close(); } catch (Exception e) { }
                }
                if (stream != null) {
                    try { stream.close(); } catch (Exception e) { }
                }
            }
        }
        
        private void stateChanged() {
            setChanged();
            notifyObservers();
        }
    }
    
    // ComponentTransferHandler stub (not used in this plugin)
    static class ComponentTransferHandler extends TransferHandler {
        public static final DataFlavor SUPPORTED_FLAVOR = DataFlavor.stringFlavor;
        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }
        @Override
        protected Transferable createTransferable(JComponent c) {
            return new StringSelection("");
        }
        @Override
        public boolean canImport(TransferSupport support) {
            return false;
        }
    }
    
    // CommandExecutor stub (not used in this plugin)
    static class CommandExecutor {
        public static void execute(String command, String commandString) {
            System.out.println("Executing command: " + command + ", " + commandString);
        }
    }
    
    // LayoutManager stub (not used in this plugin)
    static class LayoutManager {
        private static final String LAYOUT_FILE = System.getProperty("user.home")
                + File.separator + "Documents" + File.separator + "echelon"
                + File.separator + "desktop" + File.separator + "layout.json";
        static class CommandData {
            String buttonName;
            String command;
            String commandString;
            String icon;
        }
        public static void saveLayout(List<Object> comps) { }
        public static void loadLayout(List<Object> comps) { }
    }
}