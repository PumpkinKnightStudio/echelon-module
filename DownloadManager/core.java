cpackage echelon.desktop.plugins.downloadmanager;

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
import java.awt.datatransfer.*;
import java.awt.dnd.DnDConstants;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * The public class core is our DownloadManager plugin.
 * It extends AbstractModule so that the desktop can start, hide, bring-to-front, or close it.
 *
 * When running, it shows a UI with:
 * - A text field and an "Add Download" button,
 * - A table listing downloads with a progress bar,
 * - Control buttons: Pause, Resume, Cancel, Clear.
 *
 * When the user clicks the window's X (close button), the plugin terminates.
 *
 * This file (core.java) is intended to be uploaded to GitHub as the DownloadManager plugin.
 */
public class core extends AbstractModule {

    private JFrame frame;
    private JTextField addTextField;
    private DownloadsTableModel tableModel;
    private JTable table;
    private JButton pauseButton, resumeButton, cancelButton, clearButton;
    private Download selectedDownload;
    private boolean clearing;
    
    // Constructor (empty; UI is built in start())
    public core() {
    }
    
    @Override
    public void start() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (frame == null) {
                    buildUI();
                }
                frame.setVisible(true);
                frame.toFront();
            }
        });
    }
    
    private void buildUI() {
        frame = new JFrame("Download Manager (v1.0.0)");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(640, 480);
        frame.setLocationRelativeTo(null);
        
        // --- Setup Menu ---
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenuItem fileExitMenuItem = new JMenuItem("Exit", KeyEvent.VK_X);
        fileExitMenuItem.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                actionExit();
            }
        });
        fileMenu.add(fileExitMenuItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);
        
        // --- Add Panel (for URL input) ---
        JPanel addPanel = new JPanel();
        addTextField = new JTextField(30);
        addPanel.add(addTextField);
        JButton addButton = new JButton("Add Download");
        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                actionAdd();
            }
        });
        addPanel.add(addButton);
        
        // --- Downloads Table ---
        tableModel = new DownloadsTableModel();
        table = new JTable(tableModel);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                tableSelectionChanged();
            }
        });
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
        pauseButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                actionPause();
            }
        });
        pauseButton.setEnabled(false);
        buttonsPanel.add(pauseButton);
        resumeButton = new JButton("Resume");
        resumeButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                actionResume();
            }
        });
        resumeButton.setEnabled(false);
        buttonsPanel.add(resumeButton);
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                actionCancel();
            }
        });
        cancelButton.setEnabled(false);
        buttonsPanel.add(cancelButton);
        clearButton = new JButton("Clear");
        clearButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                actionClear();
            }
        });
        clearButton.setEnabled(false);
        buttonsPanel.add(clearButton);
        
        // --- Main Layout ---
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(addPanel, BorderLayout.NORTH);
        frame.getContentPane().add(downloadsPanel, BorderLayout.CENTER);
        frame.getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
        
        // --- Window Listener to terminate the plugin on close ---
        frame.addWindowListener(new WindowAdapter(){
            @Override
            public void windowClosing(WindowEvent e) {
                actionExit();
            }
        });
    }
    
    // --- Action Methods ---
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
    
    private URL verifyUrl(String url) {
        if (!url.toLowerCase().startsWith("http://") && !url.toLowerCase().startsWith("https://"))
            return null;
        try {
            URL verifiedUrl = new URL(url);
            if (verifiedUrl.getFile().length() < 2)
                return null;
            return verifiedUrl;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void tableSelectionChanged() {
        if (selectedDownload != null)
            selectedDownload.deleteObserver(o -> updateButtons());
        if (!clearing && table.getSelectedRow() > -1) {
            selectedDownload = tableModel.getDownload(table.getSelectedRow());
            selectedDownload.addObserver(o -> updateButtons());
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
    
    // --- AbstractModule Methods ---
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
    
    // (Optional) You can add an onCloseListener method here if your AbstractModule supports it.
}

//
// ------------------------- Supporting Classes -------------------------
//

// DownloadsTableModel: manages the downloads table's data.
class DownloadsTableModel extends AbstractTableModel implements Observer {
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
            case 0: return download.getUrl();
            case 1: int size = download.getSize(); return (size == -1) ? "" : Integer.toString(size);
            case 2: return new Float(download.getProgress());
            case 3: return Download.STATUSES[download.getStatus()];
        }
        return "";
    }
    
    @Override
    public void update(Observable o, Object arg) {
        int index = downloadList.indexOf(o);
        fireTableRowsUpdated(index, index);
    }
}

// ProgressRenderer: renders a JProgressBar in a table cell.
class ProgressRenderer extends JProgressBar implements TableCellRenderer {
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

// Download: downloads a file from a URL.
class Download extends Observable implements Runnable {
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

// ComponentTransferHandler: supports drag-and-drop swapping.
class ComponentTransferHandler extends TransferHandler {
    public static final DataFlavor SUPPORTED_FLAVOR = DataFlavor.stringFlavor;
    private static CommandComponent dragSource;
    
    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }
    
    @Override
    protected Transferable createTransferable(JComponent c) {
        if (c instanceof CommandComponent) {
            dragSource = (CommandComponent)c;
            return new StringSelection(dragSource.getButtonName());
        }
        return null;
    }
    
    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(SUPPORTED_FLAVOR);
    }
    
    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support))
            return false;
        try {
            String data = (String) support.getTransferable().getTransferData(SUPPORTED_FLAVOR);
            Component comp = support.getComponent();
            if (!(comp instanceof CommandComponent))
                return false;
            CommandComponent target = (CommandComponent) comp;
            if (dragSource != null && dragSource != target) {
                String srcName = dragSource.getButtonName();
                String srcCmd = dragSource.getCommand();
                String srcCmdStr = dragSource.getCommandString();
                String srcIcon = dragSource.getIconLocation();
                String tgtName = target.getButtonName();
                String tgtCmd = target.getCommand();
                String tgtCmdStr = target.getCommandString();
                String tgtIcon = target.getIconLocation();
                dragSource.setData(tgtName, tgtCmd, tgtCmdStr, tgtIcon);
                target.setData(srcName, srcCmd, srcCmdStr, srcIcon);
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }
}

// CommandExecutor: fallback for SystemRun commands.
class CommandExecutor {
    public static void execute(String command, String commandString) {
        if("SystemRun".equals(command)) {
            System.out.println("Hello World");
        } else {
            System.out.println("Unknown command: " + command);
        }
    }
}

// LayoutManager: saves/loads layout to/from a JSON file.
class LayoutManager {
    private static final String LAYOUT_FILE = System.getProperty("user.home")
            + File.separator + "Documents" + File.separator + "echelon"
            + File.separator + "desktop" + File.separator + "layout.json";
    
    static class CommandData {
        String buttonName;
        String command;
        String commandString;
        String icon;
    }
    
    public static void saveLayout(List<CommandComponent> comps) {
        try {
            List<CommandData> dataList = new ArrayList<>();
            for (CommandComponent comp : comps) {
                CommandData data = new CommandData();
                data.buttonName = comp.getButtonName();
                data.command = comp.getCommand();
                data.commandString = comp.getCommandString();
                data.icon = comp.getIconLocation();
                dataList.add(data);
            }
            Gson gson = new Gson();
            String json = gson.toJson(dataList);
            File file = new File(LAYOUT_FILE);
            File parent = file.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write(json);
            }
            System.out.println("Layout saved to " + file.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public static void loadLayout(List<CommandComponent> comps) {
        File file = new File(LAYOUT_FILE);
        if (!file.exists()) {
            System.out.println("Layout file not found; using default layout.");
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<CommandData>>() {}.getType();
            List<CommandData> dataList = gson.fromJson(reader, listType);
            int count = Math.min(comps.size(), dataList.size());
            for (int i = 0; i < count; i++) {
                CommandData data = dataList.get(i);
                comps.get(i).setData(data.buttonName, data.command, data.commandString, data.icon);
            }
            System.out.println("Layout loaded from " + file.getAbsolutePath());
            saveLayout(comps); // Optionally re-save the reconciled layout.
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
