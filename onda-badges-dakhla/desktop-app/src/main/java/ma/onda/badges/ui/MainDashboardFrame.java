package ma.onda.badges.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import ma.onda.badges.config.AppConfig;
import ma.onda.badges.controller.BadgeController;
import ma.onda.badges.model.Badge;
import ma.onda.badges.service.EmailService;
import ma.onda.badges.service.ScheduledRelanceDaemon;

/**
 * Main Single-Window Dashboard Frame for ONDA Badges Dakhla - Badge Renewal
 * Automation.
 *
 * <p>
 * Layout (top-to-bottom):
 * <ol>
 * <li>Header title bar (gradient, ONDA logo, History button)</li>
 * <li>SMTP Configuration panel</li>
 * <li>Source d'entree panel (Excel file picker)</li>
 * <li>Configuration &amp; Execution panel (threshold, mode, LANCER)</li>
 * <li>Visualisation &amp; Filtres panel (table)</li>
 * <li>Status bar</li>
 * </ol>
 */
public class MainDashboardFrame extends JFrame {

    // ---- Brand colours ----
    private static final Color ONDA_BLUE_DARK = new Color(0x00, 0x4A, 0x9F);
    private static final Color ONDA_BLUE_MID = new Color(0x00, 0x70, 0xCC);
    private static final Color ONDA_BLUE_LIGHT = new Color(0xE3, 0xEE, 0xF9);
    private static final Color BG_MAIN = new Color(0xF5, 0xF7, 0xFA);
    private static final Color BORDER_COLOR = new Color(0xB8, 0xD0, 0xEA);
    private static final Color ROW_ALT = new Color(0xED, 0xF4, 0xFF);

    private final BadgeController badgeController;
    private final ScheduledRelanceDaemon relanceDaemon;

    // --- Excel source ---
    private JTextField excelPathField;
    private JButton browseButton;

    // --- SMTP config panel fields ---
    private JTextField smtpHostField;
    private JTextField smtpPortField;
    private JTextField smtpSenderField;
    private JPasswordField smtpPasswordField;

    // --- Config & Execution panel ---
    private JComboBox<String> delaiComboBox;
    private JRadioButton modeManuelRadio;
    private JRadioButton modeAutoRadio;
    private ButtonGroup modeGroup;
    private JButton lancerButton;
    private JLabel emailStatusLabel;

    // --- Filters panel ---
    private JRadioButton filterAllRadio;
    private JRadioButton filterRelanceRadio;
    private JRadioButton filterExpiredRadio;
    private ButtonGroup filterGroup;

    // --- Table ---
    private JTable badgeTable;
    private DefaultTableModel tableModel;

    // --- Status bar ---
    private JLabel statusLabel;

    // --- Data state ---
    private List<Badge> masterBadgeList = new ArrayList<>();
    private List<Badge> currentFilteredList = new ArrayList<>();

    // --- Config persistence ---
    private static final String CONFIG_PROPERTIES_FILE = "config.properties";
    private static final String PROP_EXCEL_PATH = "excel.path";
    private static final String PROP_SMTP_HOST = "smtp.host";
    private static final String PROP_SMTP_PORT = "smtp.port";
    private static final String PROP_SMTP_SENDER = "smtp.sender";
    private static final String PROP_SMTP_PASSWORD = "smtp.password";
    private static final String FALLBACK_EXCEL_PATH = "C:\\ONDA\\tableau_des_badges(stage).xlsx";

    public MainDashboardFrame() {
        this.badgeController = new BadgeController();

        String resolvedPath = loadExcelPath();

        this.relanceDaemon = new ScheduledRelanceDaemon(
                badgeController.getExcelParserService(),
                resolvedPath);

        this.relanceDaemon.setOnDataUpdated(badges -> {
            this.masterBadgeList = badges;
            updateFilterCounts();
            applyFilter();
            updateStatusWithDaemonInfo();
        });

        initUI();

        // Populate fields after initUI so all components exist
        excelPathField.setText(resolvedPath);
        loadSmtpConfig();
        loadExcelFile(resolvedPath);

        // Start file-watcher background polling by default
        this.relanceDaemon.start();
        updateStatusWithDaemonInfo();
    }

    // =========================================================================
    // UI Initialisation
    // =========================================================================

    private void initUI() {
        setTitle("ONDA Badges Dakhla — Gestion & Relance Automatique");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1060, 820);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 680));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(BG_MAIN);
        mainPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        setContentPane(mainPanel);

        // --- Header ---
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // --- Center: vertically stacked panels ---
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(BG_MAIN);
        centerPanel.setBorder(new EmptyBorder(8, 14, 8, 14));

        centerPanel.add(createSmtpPanel());
        centerPanel.add(Box.createVerticalStrut(7));
        centerPanel.add(createPanel1());
        centerPanel.add(Box.createVerticalStrut(7));
        centerPanel.add(createConfigExecPanel());
        centerPanel.add(Box.createVerticalStrut(7));

        // Wrap panel2 (table) so it fills remaining height
        JPanel tableWrapper = new JPanel(new BorderLayout());
        tableWrapper.setBackground(BG_MAIN);
        tableWrapper.add(createPanel2(), BorderLayout.CENTER);
        centerPanel.add(tableWrapper);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // --- Status bar ---
        statusLabel = new JLabel("Statut : Prêt.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(0x44, 0x55, 0x66));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, ONDA_BLUE_MID),
                new EmptyBorder(7, 10, 7, 10)));

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(new Color(0xEB, 0xF2, 0xFA));
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));
        statusBar.add(statusLabel, BorderLayout.CENTER);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
    }

    // =========================================================================
    // Header Panel (gradient + logo + title + Historique button)
    // =========================================================================

    private JPanel createHeaderPanel() {
        // Gradient painted outer panel
        JPanel header = new JPanel(new BorderLayout(16, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(
                        0, 0, ONDA_BLUE_DARK,
                        getWidth(), 0, ONDA_BLUE_MID);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        // Taller header to accommodate logo + comfortable padding
        header.setBorder(new EmptyBorder(10, 18, 10, 18));
        header.setOpaque(false);

        // --- Logo (LEFT) — white pill background so logo stays legible on dark
        // gradient ---
        JPanel logoPill = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        logoPill.setOpaque(false);
        logoPill.setBorder(new EmptyBorder(5, 8, 5, 8));

        JLabel logoLabel = new JLabel();
        try {
            URL logoUrl = getClass().getClassLoader().getResource("onda_logo.png");
            if (logoUrl != null) {
                ImageIcon rawIcon = new ImageIcon(logoUrl);
                // The real logo is portrait (taller than wide).
                // Target: 56px high so it fits cleanly in the header. Width auto-proportional.
                int targetH = 56;
                int origW = rawIcon.getIconWidth();
                int origH = rawIcon.getIconHeight();
                int targetW = (origH > 0) ? (origW * targetH / origH) : 56;
                Image scaledImg = rawIcon.getImage().getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                logoLabel.setIcon(new ImageIcon(scaledImg));
            } else {
                logoLabel.setText("ONDA");
                logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
                logoLabel.setForeground(ONDA_BLUE_DARK);
            }
        } catch (Exception ex) {
            logoLabel.setText("ONDA");
            logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
            logoLabel.setForeground(ONDA_BLUE_DARK);
        }
        logoPill.add(logoLabel, BorderLayout.CENTER);

        // --- Title block (CENTER) ---
        JLabel titleLabel = new JLabel(
                "ONDA Badges Dakhla \u2014 Gestion & Relance Automatique",
                SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel(
                "Gestion & Automatisation des Badges d'Accès Aéroportuaire",
                SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitleLabel.setForeground(new Color(0xCC, 0xE0, 0xFF));

        JPanel titleBlock = new JPanel(new BorderLayout(0, 3));
        titleBlock.setOpaque(false);
        titleBlock.add(titleLabel, BorderLayout.CENTER);
        titleBlock.add(subtitleLabel, BorderLayout.SOUTH);

        // --- History Button (RIGHT) ---
        JButton histButton = new JButton("\uD83D\uDCCB  Historique");
        histButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        histButton.setForeground(ONDA_BLUE_DARK);
        histButton.setBackground(Color.WHITE);
        histButton.setOpaque(true);
        histButton.setFocusPainted(false);
        histButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        histButton.setBorder(new EmptyBorder(7, 16, 7, 16));
        histButton.putClientProperty("JButton.buttonType", "roundRect");
        histButton.setToolTipText("Voir l'historique des badges vus et des e-mails envoyés");
        histButton.addActionListener(e -> showHistoryDialog());

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(histButton);

        header.add(logoPill, BorderLayout.WEST);
        header.add(titleBlock, BorderLayout.CENTER);
        header.add(rightPanel, BorderLayout.EAST);

        return header;
    }

    // =========================================================================
    // Panel builders
    // =========================================================================

    /** SMTP Configuration panel. */
    private JPanel createSmtpPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(createModernTitledBorder("CONFIGURATION SMTP"));

        GridBagConstraints lc = new GridBagConstraints();
        lc.anchor = GridBagConstraints.WEST;
        lc.insets = new Insets(5, 12, 5, 8);
        lc.gridx = 0;
        lc.gridy = 0;

        GridBagConstraints fc = new GridBagConstraints();
        fc.fill = GridBagConstraints.HORIZONTAL;
        fc.insets = new Insets(5, 0, 5, 18);
        fc.gridx = 1;
        fc.gridy = 0;
        fc.weightx = 1.0;

        Font labelFont = new Font("Segoe UI", Font.PLAIN, 13);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 13);

        // Row 0 — Host & Port on the same row
        smtpHostField = new JTextField(20);
        smtpHostField.setFont(fieldFont);
        smtpHostField.setToolTipText("Ex: smtp.gmail.com  ou  smtp.office365.com");
        styleTextField(smtpHostField);

        smtpPortField = new JTextField("587", 5);
        smtpPortField.setFont(fieldFont);
        smtpPortField.setToolTipText("Port SMTP (généralement 587 pour STARTTLS, 465 pour SSL)");
        styleTextField(smtpPortField);

        JPanel hostPortRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hostPortRow.setOpaque(false);
        JLabel portSep = new JLabel("  Port : ");
        portSep.setFont(labelFont);
        hostPortRow.add(smtpHostField);
        hostPortRow.add(portSep);
        hostPortRow.add(smtpPortField);

        JLabel hostLabel = new JLabel("Serveur SMTP :");
        hostLabel.setFont(labelFont);
        lc.gridy = 0;
        fc.gridy = 0;
        panel.add(hostLabel, lc);
        panel.add(hostPortRow, fc);

        // Row 1 — Sender email
        smtpSenderField = new JTextField(25);
        smtpSenderField.setFont(fieldFont);
        smtpSenderField.setToolTipText("Adresse email de l'expéditeur ONDA");
        styleTextField(smtpSenderField);
        JLabel senderLabel = new JLabel("Email expéditeur :");
        senderLabel.setFont(labelFont);
        lc.gridy = 1;
        fc.gridy = 1;
        panel.add(senderLabel, lc);
        panel.add(smtpSenderField, fc);

        // Row 2 — Password
        smtpPasswordField = new JPasswordField(20);
        smtpPasswordField.setFont(fieldFont);
        smtpPasswordField.setToolTipText("Mot de passe / Mot de passe d'application");
        styleTextField(smtpPasswordField);
        JLabel passLabel = new JLabel("Mot de passe :");
        passLabel.setFont(labelFont);
        lc.gridy = 2;
        fc.gridy = 2;
        panel.add(passLabel, lc);
        panel.add(smtpPasswordField, fc);

        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 20));
        return panel;
    }

    /** PANEL 1 : SOURCE D'ENTREE */
    private JPanel createPanel1() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createModernTitledBorder("SOURCE D'ENTRÉE (Fichier Excel)"));

        JPanel innerPanel = new JPanel(new BorderLayout(10, 5));
        innerPanel.setOpaque(false);
        innerPanel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel pathLabel = new JLabel("Fichier Excel :");
        pathLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        excelPathField = new JTextField("");
        excelPathField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        excelPathField.addActionListener(e -> loadExcelFile(excelPathField.getText()));
        styleTextField(excelPathField);

        browseButton = new JButton("Parcourir…");
        browseButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        browseButton.setForeground(Color.WHITE);
        browseButton.setBackground(ONDA_BLUE_MID);
        browseButton.setOpaque(true);
        browseButton.setFocusPainted(false);
        browseButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        browseButton.putClientProperty("JButton.buttonType", "roundRect");
        browseButton.setBorder(new EmptyBorder(6, 14, 6, 14));
        browseButton.addActionListener(e -> handleBrowseExcel());

        innerPanel.add(pathLabel, BorderLayout.WEST);
        innerPanel.add(excelPathField, BorderLayout.CENTER);
        innerPanel.add(browseButton, BorderLayout.EAST);

        panel.add(innerPanel, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 20));
        return panel;
    }

    /** CONFIGURATION & EXECUTION panel. */
    private JPanel createConfigExecPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createModernTitledBorder("CONFIGURATION & EXÉCUTION"));

        Font labelFont = new Font("Segoe UI", Font.PLAIN, 13);

        // --- Délai de rappel ---
        JLabel delaiLabel = new JLabel("Délai de rappel : ");
        delaiLabel.setFont(labelFont);
        panel.add(delaiLabel);

        String[] delaiOptions = { "1 jour", "3 jours", "7 jours", "14 jours", "30 jours" };
        delaiComboBox = new JComboBox<>(delaiOptions);
        delaiComboBox.setSelectedIndex(2); // default: 7 jours
        delaiComboBox.setFont(labelFont);
        delaiComboBox.setToolTipText("Seuil en jours avant expiration pour déclencher un rappel email");
        delaiComboBox.addActionListener(e -> {
            updateFilterCounts();
            applyFilter();
        });
        panel.add(delaiComboBox);

        panel.add(Box.createHorizontalStrut(20));

        // --- Mode toggle ---
        JLabel modeLabel = new JLabel("Mode :");
        modeLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.add(modeLabel);

        modeManuelRadio = new JRadioButton("Relance Manuelle", true);
        modeManuelRadio.setFont(labelFont);
        modeManuelRadio.setOpaque(false);
        modeManuelRadio.setToolTipText("Cliquez sur LANCER pour envoyer les emails manuellement");

        modeAutoRadio = new JRadioButton("Relance Automatique (09h00)");
        modeAutoRadio.setFont(labelFont);
        modeAutoRadio.setOpaque(false);
        modeAutoRadio.setToolTipText("Les emails seront envoyés automatiquement chaque jour à 09h00");

        modeGroup = new ButtonGroup();
        modeGroup.add(modeManuelRadio);
        modeGroup.add(modeAutoRadio);

        modeManuelRadio.addActionListener(e -> handleModeChange());
        modeAutoRadio.addActionListener(e -> handleModeChange());

        panel.add(modeManuelRadio);
        panel.add(modeAutoRadio);

        panel.add(Box.createHorizontalStrut(20));

        // --- LANCER button ---
        lancerButton = new JButton("  ▶  LANCER");
        lancerButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lancerButton.setForeground(Color.WHITE);
        lancerButton.setBackground(ONDA_BLUE_DARK);
        lancerButton.setOpaque(true);
        lancerButton.setFocusPainted(false);
        lancerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lancerButton.setPreferredSize(new Dimension(130, 36));
        lancerButton.putClientProperty("JButton.buttonType", "roundRect");
        lancerButton.setToolTipText("Envoyer les emails de rappel maintenant (mode manuel)");
        lancerButton.addActionListener(e -> handleLancer());
        panel.add(lancerButton);

        // --- Email status label ---
        emailStatusLabel = new JLabel("");
        emailStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        panel.add(emailStatusLabel);

        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 20));
        return panel;
    }

    /** PANEL 2 : VISUALISATION & FILTRES */
    private JPanel createPanel2() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createModernTitledBorder("VISUALISATION & FILTRES"));

        // Filters bar
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 6));
        filterPanel.setBackground(ONDA_BLUE_LIGHT);
        filterPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JLabel filterTitle = new JLabel("Filtres :");
        filterTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        filterTitle.setForeground(ONDA_BLUE_DARK);
        filterPanel.add(filterTitle);

        filterAllRadio = new JRadioButton("Tous (0)", true);
        filterRelanceRadio = new JRadioButton("À relancer (<= 1 jour) (0)");
        filterExpiredRadio = new JRadioButton("Expirés (0)");

        Font radioFont = new Font("Segoe UI", Font.PLAIN, 13);
        filterAllRadio.setFont(radioFont);
        filterAllRadio.setOpaque(false);
        filterRelanceRadio.setFont(radioFont);
        filterRelanceRadio.setOpaque(false);
        filterExpiredRadio.setFont(radioFont);
        filterExpiredRadio.setOpaque(false);

        filterGroup = new ButtonGroup();
        filterGroup.add(filterAllRadio);
        filterGroup.add(filterRelanceRadio);
        filterGroup.add(filterExpiredRadio);

        filterAllRadio.addActionListener(e -> applyFilter());
        filterRelanceRadio.addActionListener(e -> applyFilter());
        filterExpiredRadio.addActionListener(e -> applyFilter());

        filterPanel.add(filterAllRadio);
        filterPanel.add(filterRelanceRadio);
        filterPanel.add(filterExpiredRadio);

        panel.add(filterPanel, BorderLayout.NORTH);

        // JTable setup
        String[] columnNames = { "N° Badge", "Nom & Prénom", "Entreprise", "Email", "Expiration", "Statut" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        badgeTable = new JTable(tableModel);
        badgeTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        badgeTable.setRowHeight(28);
        badgeTable.setShowGrid(false);
        badgeTable.setIntercellSpacing(new Dimension(0, 0));
        badgeTable.setSelectionBackground(new Color(0xBD, 0xD9, 0xF7));
        badgeTable.setSelectionForeground(ONDA_BLUE_DARK);
        badgeTable.getTableHeader().setReorderingAllowed(false);

        // Styled table header
        JTableHeader tableHeader = badgeTable.getTableHeader();
        tableHeader.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tableHeader.setBackground(ONDA_BLUE_DARK);
        tableHeader.setForeground(Color.WHITE);
        tableHeader.setPreferredSize(new Dimension(tableHeader.getWidth(), 32));
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ONDA_BLUE_MID));

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean isSel, boolean hasFocus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, isSel, hasFocus, row, col);
                lbl.setBackground(ONDA_BLUE_DARK);
                lbl.setForeground(Color.WHITE);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                lbl.setBorder(new EmptyBorder(4, 8, 4, 8));
                lbl.setHorizontalAlignment(SwingConstants.LEFT);
                return lbl;
            }
        };
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            tableHeader.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }

        StatusCellRenderer customRenderer = new StatusCellRenderer();
        for (int i = 0; i < badgeTable.getColumnCount(); i++) {
            badgeTable.getColumnModel().getColumn(i).setCellRenderer(customRenderer);
        }

        TableColumnModel colModel = badgeTable.getColumnModel();
        colModel.getColumn(0).setPreferredWidth(90);
        colModel.getColumn(1).setPreferredWidth(160);
        colModel.getColumn(2).setPreferredWidth(130);
        colModel.getColumn(3).setPreferredWidth(220);
        colModel.getColumn(4).setPreferredWidth(100);
        colModel.getColumn(5).setPreferredWidth(80);

        JScrollPane scrollPane = new JScrollPane(badgeTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        scrollPane.getViewport().setBackground(Color.WHITE);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    // =========================================================================
    // History Dialog (reads seen_badges.json + sent_emails_history.json)
    // =========================================================================

    private void showHistoryDialog() {
        JDialog dialog = new JDialog(this, "📋  Journal & Historique ONDA", true);
        dialog.setSize(820, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        // --- Dialog header ---
        JPanel dlgHeader = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, ONDA_BLUE_DARK, getWidth(), 0, ONDA_BLUE_MID));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        dlgHeader.setBorder(new EmptyBorder(10, 18, 10, 18));
        dlgHeader.setOpaque(false);
        JLabel dlgTitle = new JLabel("Journal des activités — Badges & E-mails");
        dlgTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        dlgTitle.setForeground(Color.WHITE);
        dlgHeader.add(dlgTitle, BorderLayout.CENTER);
        dialog.add(dlgHeader, BorderLayout.NORTH);

        // --- Tabbed pane ---
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.setBorder(new EmptyBorder(6, 6, 6, 6));

        // TAB 1 — Badges Vus
        JPanel seenPanel = buildSeenBadgesPanel();
        tabs.addTab("  🔖  Badges Vus  ", seenPanel);

        // TAB 2 — Emails Envoyés
        JPanel emailPanel = buildSentEmailsPanel();
        tabs.addTab("  📧  Emails Envoyés  ", emailPanel);

        dialog.add(tabs, BorderLayout.CENTER);

        // --- Bottom bar: Rafraîchir + Fermer ---
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        bottomBar.setBackground(BG_MAIN);
        bottomBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR));

        JButton refreshBtn = new JButton("🔄  Rafraîchir");
        refreshBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        refreshBtn.setForeground(ONDA_BLUE_DARK);
        refreshBtn.setFocusPainted(false);
        refreshBtn.putClientProperty("JButton.buttonType", "roundRect");
        refreshBtn.addActionListener(e -> {
            // Rebuild tabs content
            tabs.setComponentAt(0, buildSeenBadgesPanel());
            tabs.setComponentAt(1, buildSentEmailsPanel());
            tabs.repaint();
        });

        JButton closeBtn = new JButton("Fermer");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBackground(ONDA_BLUE_DARK);
        closeBtn.setOpaque(true);
        closeBtn.setFocusPainted(false);
        closeBtn.putClientProperty("JButton.buttonType", "roundRect");
        closeBtn.addActionListener(e -> dialog.dispose());

        bottomBar.add(refreshBtn);
        bottomBar.add(closeBtn);
        dialog.add(bottomBar, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    /** Builds the "Badges Vus" tab panel by reading seen_badges.json. */
    private JPanel buildSeenBadgesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String[] cols = { "#", "N° Badge", "Date Vue" };
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        List<String[]> entries = parseSeenBadgesJson();
        int index = 1;
        for (String[] entry : entries) {
            model.addRow(new Object[] { index++, entry[0], entry[1] });
        }

        JTable table = buildStyledHistoryTable(model);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JLabel info = new JLabel("  " + entries.size() + " badge(s) enregistré(s) dans seen_badges.json");
        info.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        info.setForeground(new Color(0x55, 0x66, 0x77));

        panel.add(info, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Builds the "Emails Envoyés" tab panel by reading sent_emails_history.json.
     */
    private JPanel buildSentEmailsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        String rawJson = readFileAsString("sent_emails_history.json");

        // Try to detect if it is a JSON array of objects or simple strings
        if (rawJson == null || rawJson.isBlank() || rawJson.trim().equals("[]") || rawJson.trim().equals("null")) {
            // Empty file — show informational message
            JLabel empty = new JLabel(
                    "<html><div style='padding:20px; color:#667788; font-size:13px;'>"
                            + "ℹ️  Aucun email enregistré pour l'instant.<br>"
                            + "Les emails envoyés apparaîtront ici après la première relance.</div></html>");
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(empty, BorderLayout.CENTER);
            return panel;
        }

        // Try to parse as array of objects with keys: badge, email, date (adjust as
        // needed)
        List<String[]> entries = parseSentEmailsJson(rawJson);
        if (!entries.isEmpty() && entries.get(0).length >= 3) {
            String[] cols = { "#", "N° Badge", "Email Destinataire", "Date Envoi" };
            DefaultTableModel model = new DefaultTableModel(cols, 0) {
                @Override
                public boolean isCellEditable(int r, int c) {
                    return false;
                }
            };
            int idx = 1;
            for (String[] e : entries) {
                model.addRow(new Object[] { idx++, e[0], e[1], e[2] });
            }
            JTable table = buildStyledHistoryTable(model);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

            JLabel info = new JLabel("  " + entries.size() + " email(s) dans l'historique.");
            info.setFont(new Font("Segoe UI", Font.ITALIC, 12));
            info.setForeground(new Color(0x55, 0x66, 0x77));

            panel.add(info, BorderLayout.NORTH);
            panel.add(scroll, BorderLayout.CENTER);
        } else {
            // Fallback: raw text view
            JTextArea area = new JTextArea(rawJson);
            area.setFont(new Font("Monospaced", Font.PLAIN, 12));
            area.setEditable(false);
            area.setBackground(new Color(0xF8, 0xFA, 0xFD));
            JScrollPane scroll = new JScrollPane(area);
            scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
            panel.add(scroll, BorderLayout.CENTER);
        }

        return panel;
    }

    // =========================================================================
    // JSON Parsers (pure Java — no external library)
    // =========================================================================

    /**
     * Parses seen_badges.json — format: ["N°XXXX|YYYY-MM-DD", ...]
     * Returns a list of {badgeNumber, date} pairs.
     */
    private List<String[]> parseSeenBadgesJson() {
        List<String[]> result = new ArrayList<>();
        String raw = readFileAsString("seen_badges.json");
        if (raw == null || raw.isBlank())
            return result;
        // Strip outer brackets
        raw = raw.trim();
        if (raw.startsWith("["))
            raw = raw.substring(1);
        if (raw.endsWith("]"))
            raw = raw.substring(0, raw.length() - 1);
        // Split on commas not inside quotes (simple: split on "," then strip quotes)
        String[] tokens = raw.split(",");
        for (String token : tokens) {
            token = token.trim().replaceAll("^\"|\"$", "").trim(); // strip surrounding quotes
            if (token.isBlank())
                continue;
            int pipe = token.indexOf('|');
            if (pipe >= 0) {
                String badge = token.substring(0, pipe).trim();
                String date = token.substring(pipe + 1).trim();
                result.add(new String[] { badge, date });
            } else {
                result.add(new String[] { token, "—" });
            }
        }
        return result;
    }

    /**
     * Parses sent_emails_history.json.
     * Supports array-of-objects format: [{"badge":"...", "email":"...",
     * "date":"..."}, ...]
     * Returns list of {badge, email, date}.
     */
    private List<String[]> parseSentEmailsJson(String raw) {
        List<String[]> result = new ArrayList<>();
        if (raw == null || raw.isBlank())
            return result;
        raw = raw.trim();

        if (raw.startsWith("[{")) {
            // Array of JSON objects — extract per object
            String[] objects = raw.split("\\},\\s*\\{");
            for (String obj : objects) {
                obj = obj.replaceAll("[\\[\\]\\{\\}]", "").trim();
                String badge = extractJsonField(obj, "badge");
                String email = extractJsonField(obj, "email");
                String date = extractJsonField(obj, "date");
                if (!badge.isEmpty() || !email.isEmpty()) {
                    result.add(new String[] { badge, email, date });
                }
            }
        } else if (raw.startsWith("[\"")) {
            // Simple string array
            raw = raw.trim();
            if (raw.startsWith("["))
                raw = raw.substring(1);
            if (raw.endsWith("]"))
                raw = raw.substring(0, raw.length() - 1);
            String[] tokens = raw.split(",");
            for (String t : tokens) {
                t = t.trim().replaceAll("^\"|\"$", "");
                if (!t.isBlank())
                    result.add(new String[] { t, "—", "—" });
            }
        }
        return result;
    }

    /** Extracts a string value from a flat JSON object string for a given key. */
    private String extractJsonField(String obj, String key) {
        String search = "\"" + key + "\"";
        int idx = obj.indexOf(search);
        if (idx < 0)
            return "";
        int colon = obj.indexOf(':', idx + search.length());
        if (colon < 0)
            return "";
        int start = obj.indexOf('"', colon + 1);
        int end = start >= 0 ? obj.indexOf('"', start + 1) : -1;
        if (start < 0 || end < 0)
            return "";
        return obj.substring(start + 1, end).trim();
    }

    /** Reads a file relative to the working directory as a String. */
    private String readFileAsString(String filename) {
        File file = new File(filename);
        if (!file.exists())
            return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return sb.toString();
    }

    // =========================================================================
    // History Table Builder
    // =========================================================================

    private JTable buildStyledHistoryTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xBD, 0xD9, 0xF7));
        table.setSelectionForeground(ONDA_BLUE_DARK);

        // Header styling
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(ONDA_BLUE_DARK);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getWidth(), 30));
        header.setReorderingAllowed(false);

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean isSel, boolean hasFocus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(t, val, isSel, hasFocus, row, col);
                lbl.setBackground(ONDA_BLUE_DARK);
                lbl.setForeground(Color.WHITE);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                lbl.setBorder(new EmptyBorder(3, 8, 3, 8));
                return lbl;
            }
        };
        for (int i = 0; i < model.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }

        // Alternating row renderer
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                    boolean isSel, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, val, isSel, hasFocus, row, col);
                if (!isSel) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : ROW_ALT);
                    c.setForeground(new Color(0x22, 0x33, 0x44));
                }
                setBorder(new EmptyBorder(2, 8, 2, 8));
                return c;
            }
        });

        return table;
    }

    // =========================================================================
    // UI Helpers
    // =========================================================================

    /** Creates a modern titled border with ONDA blue accent. */
    private javax.swing.border.Border createModernTitledBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                "  " + title + "  ",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                ONDA_BLUE_DARK);
        return BorderFactory.createCompoundBorder(tb, new EmptyBorder(4, 6, 6, 6));
    }

    /** Applies a consistent style to JTextField / JPasswordField. */
    private void styleTextField(JTextField field) {
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(4, 8, 4, 8)));
        field.setBackground(Color.WHITE);
    }

    // =========================================================================
    // Action Handlers
    // =========================================================================

    private void handleBrowseExcel() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sélectionnez le fichier Excel ONDA");
        fileChooser.setFileFilter(
                new javax.swing.filechooser.FileNameExtensionFilter("Fichiers Excel (*.xlsx)", "xlsx"));

        String currentPath = excelPathField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File curFile = new File(currentPath);
            if (curFile.exists())
                fileChooser.setSelectedFile(curFile);
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
            excelPathField.setText(selectedPath);
            saveExcelPath(selectedPath);
            loadExcelFile(selectedPath);
        }
    }

    /**
     * Manual mode: Reads SMTP credentials from the UI, builds an EmailService,
     * then sends bulk reminder emails on a background thread.
     */
    private void handleLancer() {
        String host = smtpHostField.getText().trim();
        String portStr = smtpPortField.getText().trim();
        String sender = smtpSenderField.getText().trim();
        String password = new String(smtpPasswordField.getPassword());

        if (host.isBlank() || sender.isBlank() || password.isEmpty()) {
            emailStatusLabel.setForeground(new Color(180, 0, 0));
            emailStatusLabel.setText("Erreur : remplissez le serveur SMTP, l'email et le mot de passe.");
            JOptionPane.showMessageDialog(this,
                    "Veuillez remplir tous les champs de configuration SMTP (Serveur, Email et Mot de passe).",
                    "Champs Incomplets", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ex) {
            emailStatusLabel.setForeground(new Color(180, 0, 0));
            emailStatusLabel.setText("Erreur : port SMTP invalide.");
            JOptionPane.showMessageDialog(this,
                    "Le port SMTP doit être un nombre valide (ex: 587).",
                    "Port Invalide", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int threshold = getSelectedThresholdDays();

        // Persist SMTP config (including password)
        saveSmtpConfig(host, port, sender, password);

        EmailService emailService = new EmailService(host, port, sender, password);

        // --- Fast Network Check (2-second timeout) ---
        if (!emailService.isNetworkAvailable()) {
            emailStatusLabel.setForeground(new Color(180, 0, 0));
            emailStatusLabel.setText("Erreur : Pas de connexion Internet.");
            JOptionPane.showMessageDialog(this,
                    "Connexion Internet indisponible.\n\nVeuillez vérifier votre connexion réseau (Wi-Fi ou Câble Ethernet) puis réessayez.",
                    "Erreur Connexion Réseau", JOptionPane.ERROR_MESSAGE);
            return;
        }

        lancerButton.setEnabled(false);
        emailStatusLabel.setForeground(new Color(100, 100, 100));
        emailStatusLabel.setText("Envoi en cours...");

        List<Badge> badgesToSend = new ArrayList<>(masterBadgeList);

        SwingWorker<EmailService.EmailSendResult, Void> worker = new SwingWorker<>() {
            @Override
            protected EmailService.EmailSendResult doInBackground() {
                return emailService.sendBulkReminders(badgesToSend, threshold);
            }

            @Override
            protected void done() {
                lancerButton.setEnabled(true);
                try {
                    EmailService.EmailSendResult result = get();
                    if (result.failed == 0) {
                        emailStatusLabel.setForeground(new Color(0, 120, 0));
                    } else {
                        emailStatusLabel.setForeground(new Color(180, 0, 0));
                    }
                    emailStatusLabel.setText("Résultat : " + result);
                    statusLabel.setText("Statut : Relance manuelle terminée. " + result);

                    // --- Clear Summary Dialog ---
                    int messageType = (result.failed == 0)
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.WARNING_MESSAGE;
                    JOptionPane.showMessageDialog(MainDashboardFrame.this,
                            result.getSummaryDialogMessage(),
                            "Rapport d'Envoi des Remarques", messageType);

                } catch (Exception ex) {
                    emailStatusLabel.setForeground(new Color(180, 0, 0));
                    emailStatusLabel.setText("Erreur SMTP : " + ex.getCause().getMessage());
                    JOptionPane.showMessageDialog(MainDashboardFrame.this,
                            "Erreur lors de l'envoi des emails :\n" + ex.getCause().getMessage(),
                            "Erreur Envoi Email", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Switches between Manual and Automatic email modes.
     * In Automatic mode the LANCER button is disabled and the email scheduler is
     * started.
     */
    private void handleModeChange() {
        if (modeAutoRadio.isSelected()) {
            // Validate credentials before starting scheduler
            String host = smtpHostField.getText().trim();
            String portStr = smtpPortField.getText().trim();
            String sender = smtpSenderField.getText().trim();
            String password = new String(smtpPasswordField.getPassword());

            if (host.isBlank() || sender.isBlank() || password.isEmpty()) {
                emailStatusLabel.setForeground(new Color(180, 0, 0));
                emailStatusLabel.setText("Remplissez les credentials SMTP avant d'activer le mode auto.");
                modeManuelRadio.setSelected(true);
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException ex) {
                emailStatusLabel.setForeground(new Color(180, 0, 0));
                emailStatusLabel.setText("Port SMTP invalide.");
                modeManuelRadio.setSelected(true);
                return;
            }

            saveSmtpConfig(host, port, sender, password);

            EmailService emailService = new EmailService(host, port, sender, password);

            // --- Network check before activating auto scheduler ---
            if (!emailService.isNetworkAvailable()) {
                emailStatusLabel.setForeground(new Color(180, 0, 0));
                emailStatusLabel.setText("Erreur : Pas de connexion Internet.");
                JOptionPane.showMessageDialog(this,
                        "Connexion Internet indisponible.\n\nVeuillez vérifier votre réseau avant d'activer la relance automatique.",
                        "Erreur Connexion Réseau", JOptionPane.ERROR_MESSAGE);
                modeManuelRadio.setSelected(true);
                return;
            }

            relanceDaemon.startEmailScheduler(emailService, getSelectedThresholdDays());

            lancerButton.setEnabled(false);
            emailStatusLabel.setForeground(new Color(0, 120, 0));
            emailStatusLabel.setText("Mode automatique actif. Prochain envoi à 09h00.");
            statusLabel.setText("Statut : Mode Automatique actif (email quotidien à 09:00).");
        } else {
            // Switch back to manual
            relanceDaemon.stopEmailScheduler();
            lancerButton.setEnabled(true);
            emailStatusLabel.setForeground(new Color(100, 100, 100));
            emailStatusLabel.setText("Mode manuel. Cliquez sur LANCER pour envoyer.");
            statusLabel.setText("Statut : Mode Manuel.");
        }
    }

    // =========================================================================
    // Config.properties persistence
    // =========================================================================

    private String loadExcelPath() {
        File localConfig = new File(CONFIG_PROPERTIES_FILE);
        if (localConfig.exists()) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(localConfig)) {
                props.load(in);
                String saved = props.getProperty(PROP_EXCEL_PATH);
                if (saved != null && !saved.isBlank())
                    return saved;
            } catch (IOException e) {
                System.err.println("[Config] Impossible de lire config.properties : " + e.getMessage());
            }
        }
        String appDefault = AppConfig.get("excel.default.path");
        if (appDefault != null && !appDefault.isBlank())
            return appDefault;
        return FALLBACK_EXCEL_PATH;
    }

    private void saveExcelPath(String filePath) {
        if (filePath == null || filePath.isBlank())
            return;
        Properties props = readExistingProps();
        props.setProperty(PROP_EXCEL_PATH, filePath);
        writeProps(props);
    }

    /**
     * Loads SMTP host/port/sender/password from config.properties into the UI
     * fields.
     */
    private void loadSmtpConfig() {
        Properties props = readExistingProps();
        String host = props.getProperty(PROP_SMTP_HOST, "");
        String port = props.getProperty(PROP_SMTP_PORT, "587");
        String sender = props.getProperty(PROP_SMTP_SENDER, "");
        String pass = props.getProperty(PROP_SMTP_PASSWORD, "");
        smtpHostField.setText(host);
        smtpPortField.setText(port);
        smtpSenderField.setText(sender);
        smtpPasswordField.setText(pass);
    }

    /** Persists SMTP host/port/sender/password to config.properties. */
    private void saveSmtpConfig(String host, int port, String sender, String password) {
        Properties props = readExistingProps();
        props.setProperty(PROP_SMTP_HOST, host);
        props.setProperty(PROP_SMTP_PORT, String.valueOf(port));
        props.setProperty(PROP_SMTP_SENDER, sender);
        if (password != null) {
            props.setProperty(PROP_SMTP_PASSWORD, password);
        }
        writeProps(props);
        System.out.println("[Config] Configuration SMTP sauvegardée.");
    }

    private Properties readExistingProps() {
        Properties props = new Properties();
        File localConfig = new File(CONFIG_PROPERTIES_FILE);
        if (localConfig.exists()) {
            try (InputStream in = new FileInputStream(localConfig)) {
                props.load(in);
            } catch (IOException ignored) {
            }
        }
        return props;
    }

    private void writeProps(Properties props) {
        try (FileOutputStream out = new FileOutputStream(new File(CONFIG_PROPERTIES_FILE))) {
            props.store(out, "ONDA Badges Dakhla -- configuration locale");
        } catch (IOException e) {
            System.err.println("[Config] Impossible de sauvegarder config.properties : " + e.getMessage());
        }
    }

    // =========================================================================
    // Data helpers
    // =========================================================================

    private void loadExcelFile(String filePath) {
        if (filePath == null || filePath.isBlank())
            return;
        relanceDaemon.setExcelFilePath(filePath);
        try {
            masterBadgeList = badgeController.loadBadgesFromExcel(filePath);
            updateFilterCounts();
            applyFilter();
            updateStatusWithDaemonInfo();
        } catch (Exception ex) {
            statusLabel.setText("Statut : Erreur de chargement du fichier Excel - " + ex.getMessage());
            masterBadgeList.clear();
            updateFilterCounts();
            applyFilter();
        }
    }

    private void updateStatusWithDaemonInfo() {
        if (statusLabel != null && masterBadgeList != null) {
            String mode = relanceDaemon.isEmailSchedulerRunning()
                    ? "[Mode Email Automatique Actif]"
                    : "[Mode Manuel]";
            statusLabel.setText("Statut : Fichier chargé (" + masterBadgeList.size() + " lignes) | "
                    + countRelanceBadges() + " badges à relancer (<= " + getSelectedThresholdDays() + "j). " + mode);
        }
    }

    private int getSelectedThresholdDays() {
        if (delaiComboBox == null)
            return 7;
        String selected = (String) delaiComboBox.getSelectedItem();
        if (selected == null)
            return 7;
        try {
            return Integer.parseInt(selected.split(" ")[0]);
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    private long countRelanceBadges() {
        int threshold = getSelectedThresholdDays();
        return masterBadgeList.stream()
                .filter(b -> {
                    Long d = b.getDaysRemaining();
                    return d != null && d >= 0 && d <= threshold;
                })
                .count();
    }

    private long countExpiredBadges() {
        return masterBadgeList.stream()
                .filter(b -> {
                    Long d = b.getDaysRemaining();
                    return d != null && d < 0;
                })
                .count();
    }

    private void updateFilterCounts() {
        long total = masterBadgeList.size();
        long relance = countRelanceBadges();
        long expired = countExpiredBadges();
        int threshold = getSelectedThresholdDays();
        if (filterAllRadio != null)
            filterAllRadio.setText("Tous (" + total + ")");
        if (filterRelanceRadio != null)
            filterRelanceRadio.setText("À relancer (<= " + threshold + " jour) (" + relance + ")");
        if (filterExpiredRadio != null)
            filterExpiredRadio.setText("Expirés (" + expired + ")");
    }

    private void applyFilter() {
        updateFilterCounts();
        int threshold = getSelectedThresholdDays();
        String filterType = "ALL";
        if (filterRelanceRadio != null && filterRelanceRadio.isSelected())
            filterType = "RELANCE";
        else if (filterExpiredRadio != null && filterExpiredRadio.isSelected())
            filterType = "EXPIRED";
        currentFilteredList = badgeController.filterBadges(masterBadgeList, filterType, threshold);
        populateTable(currentFilteredList);
    }

    private void populateTable(List<Badge> badges) {
        tableModel.setRowCount(0);
        for (Badge b : badges) {
            Long days = b.getDaysRemaining();
            String statusText;
            if (days == null)
                statusText = "OK";
            else if (days < 0)
                statusText = "Exp";
            else if (days <= getSelectedThresholdDays())
                statusText = days + "j";
            else
                statusText = "OK";

            tableModel.addRow(new Object[] {
                    b.getBadgeNumber(),
                    b.getFullName(),
                    b.getOrganisme(),
                    b.getEmail(),
                    b.getCalculatedExpiration() != null ? b.getCalculatedExpiration().toString() : "-",
                    statusText
            });
        }
    }

    // =========================================================================
    // Custom cell renderer (main badge table)
    // =========================================================================

    private class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String statusVal = (String) table.getValueAt(row, 5);

            if (!isSelected) {
                // Alternating rows
                Color baseRow = (row % 2 == 0) ? Color.WHITE : ROW_ALT;

                if ("Exp".equalsIgnoreCase(statusVal)) {
                    c.setBackground(new Color(0xFF, 0xEB, 0xEB));
                    c.setForeground(new Color(180, 0, 0));
                } else if (statusVal != null && statusVal.endsWith("j")) {
                    c.setBackground(new Color(0xFF, 0xF8, 0xCC));
                    c.setForeground(new Color(150, 110, 0));
                } else {
                    c.setBackground(baseRow);
                    c.setForeground(new Color(0x22, 0x44, 0x22));
                }
            } else {
                c.setBackground(table.getSelectionBackground());
                c.setForeground(table.getSelectionForeground());
            }

            setBorder(new EmptyBorder(2, 8, 2, 8));

            if (column == 5) {
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setHorizontalAlignment(SwingConstants.LEFT);
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            return c;
        }
    }
}
