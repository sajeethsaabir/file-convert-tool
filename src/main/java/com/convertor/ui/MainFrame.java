package com.convertor.ui;

import com.convertor.converter.*;
import com.convertor.util.SecurityValidator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

public class MainFrame extends JFrame {

    private final JTextArea logArea;
    private final JTabbedPane tabs;
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
    private boolean darkMode = false;

    public MainFrame() {
        setTitle("Document Convertor Tool");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        JMenuBar menuBar = new JMenuBar();
        JMenu viewMenu = new JMenu("View");
        boolean savedDark = prefs.getBoolean("darkMode", false);
        JCheckBoxMenuItem themeItem = new JCheckBoxMenuItem("Dark Mode", savedDark);
        themeItem.addActionListener(e -> {
            prefs.putBoolean("darkMode", themeItem.isSelected());
            applyTheme(themeItem.isSelected());
        });
        viewMenu.add(themeItem);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);

        if (savedDark) {
            SwingUtilities.invokeLater(() -> applyTheme(true));
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        tabs = new JTabbedPane();
        tabs.addTab("DOCX → MD", createConverterPanel(
                SecurityValidator.extDocx(), SecurityValidator.extMd(),
                (in, out, progress) -> {
                    progress.accept(10);
                    try (var validated = SecurityValidator.validateDocxMagicBytes(in)) {
                        progress.accept(30);
                        String result = new DocxToMarkdown().convert(validated);
                        progress.accept(60);
                        Files.writeString(out, result);
                        progress.accept(100);
                    }
                }));
        tabs.addTab("PDF → MD", createConverterPanel(
                SecurityValidator.extPdf(), SecurityValidator.extMd(),
                (in, out, progress) -> {
                    progress.accept(10);
                    try (var validated = SecurityValidator.validatePdfMagicBytes(in)) {
                        progress.accept(30);
                        String result = new PdfToMarkdown().convert(validated);
                        progress.accept(60);
                        Files.writeString(out, result);
                        progress.accept(100);
                    }
                }));
        tabs.addTab("MD → DOCX", createConverterPanel(
                SecurityValidator.extMd(), SecurityValidator.extDocx(),
                (in, out, progress) -> {
                    progress.accept(10);
                    String md = new String(in.readAllBytes());
                    progress.accept(30);
                    try (OutputStream os = Files.newOutputStream(out)) {
                        new MarkdownToDocx().convert(md, os);
                    }
                    progress.accept(100);
                }));
        tabs.addTab("MD → PDF", createConverterPanel(
                SecurityValidator.extMd(), SecurityValidator.extPdf(),
                (in, out, progress) -> {
                    progress.accept(10);
                    String md = new String(in.readAllBytes());
                    progress.accept(30);
                    try (OutputStream os = Files.newOutputStream(out)) {
                        new MarkdownToPdf().convert(md, os);
                    }
                    progress.accept(100);
                }));

        mainPanel.add(tabs, BorderLayout.CENTER);

        logArea = new JTextArea(5, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(240, 240, 240));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        mainPanel.add(logScroll, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JPanel createConverterPanel(java.util.Set<String> inExts,
                                         java.util.Set<String> outExts,
                                         ConvertTask task) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        String inLabel = inExts.iterator().next().toUpperCase().replace(".", "");
        String outLabel = outExts.iterator().next().toUpperCase().replace(".", "");

        JTextField inputField = new JTextField(40);
        inputField.setEditable(false);
        JButton inputBtn = new JButton("Browse...");
        JTextField outputField = new JTextField(40);
        outputField.setEditable(false);
        JButton outputBtn = new JButton("Browse...");
        JButton convertBtn = new JButton("Convert");
        JButton clearBtn = new JButton("Clear");
        JButton openBtn = new JButton("Open File");
        openBtn.setVisible(false);
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(0);
        progress.setStringPainted(true);
        progress.setVisible(false);

        inputBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(lastDir());
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    inLabel + " files (*." + inLabel.toLowerCase() + ")",
                    inLabel.toLowerCase()));
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getAbsolutePath();
                inputField.setText(path);
                saveLastDir(path);
                String base = autoOutputBase(path);
                outputField.setText(base + "." + outLabel.toLowerCase());
            }
        });

        outputBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(lastDir());
            String current = outputField.getText();
            if (!current.isEmpty()) {
                fc.setSelectedFile(new java.io.File(current));
            }
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    outLabel + " files (*." + outLabel.toLowerCase() + ")",
                    outLabel.toLowerCase()));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                String path = fc.getSelectedFile().getAbsolutePath();
                String ext = "." + outLabel.toLowerCase();
                if (!path.toLowerCase().endsWith(ext)) path += ext;
                outputField.setText(path);
            }
        });

        clearBtn.addActionListener(e -> {
            inputField.setText("");
            outputField.setText("");
            openBtn.setVisible(false);
            progress.setVisible(false);
            convertBtn.setEnabled(true);
            log("Cleared fields");
        });

        convertBtn.addActionListener(e -> {
            String inputPath = inputField.getText();
            String outputPath = outputField.getText();
            if (inputPath.isEmpty() || outputPath.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select input and output files.");
                return;
            }

            try {
                SecurityValidator.validateInputFile(inputPath, inExts);
                SecurityValidator.validateOutputFile(outputPath, outExts);
            } catch (SecurityException ex) {
                JOptionPane.showMessageDialog(this, "Validation error:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            convertBtn.setEnabled(false);
            progress.setValue(0);
            progress.setVisible(true);
            openBtn.setVisible(false);
            log("Starting: " + inputPath + " → " + outputPath);

            Path outP = Path.of(outputPath);
            new Thread(() -> {
                try (InputStream in = Files.newInputStream(Path.of(inputPath))) {
                    task.convert(in, outP, pct ->
                            SwingUtilities.invokeLater(() -> progress.setValue(pct)));
                    SwingUtilities.invokeLater(() -> {
                        log("✓ Complete: " + outP);
                        convertBtn.setEnabled(true);
                        openBtn.setVisible(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        log("✗ Error: " + ex.getMessage());
                        convertBtn.setEnabled(true);
                        progress.setVisible(false);
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Conversion failed:\n" + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        });

        openBtn.addActionListener(e -> {
            String path = outputField.getText();
            if (path.isEmpty()) return;
            try {
                Path validated = SecurityValidator.validatePath(path, SecurityValidator.extAll());
                if (Files.exists(validated)) {
                    Desktop.getDesktop().open(validated.toFile());
                }
            } catch (Exception ex) {
                log("✗ Cannot open file: " + ex.getMessage());
            }
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnRow.add(convertBtn);
        btnRow.add(clearBtn);
        btnRow.add(openBtn);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Input " + inLabel + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(inputField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(inputBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("Output " + outLabel + ":"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        panel.add(outputField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        panel.add(outputBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        gbc.insets = new Insets(15, 5, 5, 5);
        panel.add(btnRow, gbc);

        gbc.gridy = 3; gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(progress, gbc);

        return panel;
    }

    private void applyTheme(boolean dark) {
        darkMode = dark;
        try {
            if (dark) {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                UIManager.put("Panel.background", new Color(43, 43, 43));
                UIManager.put("Panel.foreground", new Color(220, 220, 220));
                UIManager.put("OptionPane.background", new Color(43, 43, 43));
                UIManager.put("OptionPane.messageForeground", new Color(220, 220, 220));
                UIManager.put("TextArea.background", new Color(55, 55, 55));
                UIManager.put("TextArea.foreground", new Color(220, 220, 220));
                UIManager.put("TextArea.caretForeground", Color.WHITE);
                UIManager.put("TextField.background", new Color(55, 55, 55));
                UIManager.put("TextField.foreground", new Color(220, 220, 220));
                UIManager.put("TextField.caretForeground", Color.WHITE);
                UIManager.put("Label.foreground", new Color(220, 220, 220));
                UIManager.put("Button.background", new Color(70, 70, 70));
                UIManager.put("Button.foreground", new Color(220, 220, 220));
                UIManager.put("ToggleButton.background", new Color(70, 70, 70));
                UIManager.put("ToggleButton.foreground", new Color(220, 220, 220));
                UIManager.put("TabbedPane.background", new Color(43, 43, 43));
                UIManager.put("TabbedPane.foreground", new Color(220, 220, 220));
                UIManager.put("TabbedPane.unselectedBackground", new Color(55, 55, 55));
                UIManager.put("TabbedPane.selected", new Color(60, 60, 60));
                UIManager.put("ProgressBar.background", new Color(70, 70, 70));
                UIManager.put("ProgressBar.foreground", new Color(80, 140, 200));
                UIManager.put("ProgressBar.selectionForeground", Color.WHITE);
                UIManager.put("ProgressBar.selectionBackground", Color.WHITE);
                UIManager.put("ScrollPane.background", new Color(43, 43, 43));
                UIManager.put("Viewport.background", new Color(43, 43, 43));
                UIManager.put("List.background", new Color(55, 55, 55));
                UIManager.put("List.foreground", new Color(220, 220, 220));
                UIManager.put("FileChooser.background", new Color(43, 43, 43));
                UIManager.put("FileChooser.foreground", new Color(220, 220, 220));
                UIManager.put("TitledBorder.titleColor", new Color(180, 180, 180));
                UIManager.put("MenuBar.background", new Color(43, 43, 43));
                UIManager.put("MenuBar.foreground", new Color(220, 220, 220));
                UIManager.put("Menu.background", new Color(43, 43, 43));
                UIManager.put("Menu.foreground", new Color(220, 220, 220));
                UIManager.put("MenuItem.background", new Color(43, 43, 43));
                UIManager.put("MenuItem.foreground", new Color(220, 220, 220));
                UIManager.put("CheckBoxMenuItem.background", new Color(43, 43, 43));
                UIManager.put("CheckBoxMenuItem.foreground", new Color(220, 220, 220));
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                UIManager.put("TextArea.background", new Color(240, 240, 240));
                UIManager.put("TextArea.foreground", Color.BLACK);
            }
            for (Window w : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(w);
            }
        } catch (Exception ex) {
            log("✗ Theme error: " + ex.getMessage());
        }
    }

    private String lastDir() {
        String dir = prefs.get("lastDir", null);
        return dir != null ? dir : System.getProperty("user.home");
    }

    private void saveLastDir(String path) {
        Path p = Path.of(path).getParent();
        if (p != null) prefs.put("lastDir", p.toString());
    }

    private String autoOutputBase(String inputPath) {
        int dot = inputPath.lastIndexOf('.');
        if (dot > 0 && inputPath.indexOf('.', dot + 1) == -1) {
            return inputPath.substring(0, dot);
        }
        return inputPath + "_converted";
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @FunctionalInterface
    interface ConvertTask {
        void convert(InputStream in, Path outputPath, Consumer<Integer> progress) throws Exception;
    }
}
