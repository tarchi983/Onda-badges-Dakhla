package ma.onda.badges.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.List;

/**
 * A persistent, non-modal notification dialog that displays expired/warning badges.
 * Supports:
 *   - Acknowledgment callback on "Fermer" to persist seen badge IDs.
 *   - Notification beep that plays exactly once when the dialog is first displayed.
 */
public class CustomNotificationDialog extends JDialog {

    // ONDA brand colours consistent with MainDashboardFrame
    private static final Color ONDA_BLUE_DARK  = new Color(0x00, 0x4A, 0x9F);
    private static final Color ALERT_RED        = new Color(0xC0, 0x20, 0x20);
    private static final Color ALERT_RED_LIGHT  = new Color(0xFF, 0xF0, 0xF0);
    private static final Color ALERT_RED_BORDER = new Color(0xE0, 0x40, 0x40);

    /**
     * @param expiredBadges  Formatted strings of expired badges to display.
     * @param warningBadges  Formatted strings of warning/relance badges to display.
     * @param onCloseAction  Runnable executed BEFORE dispose() when the user clicks "Fermer".
     *                       Used to save acknowledged badge IDs to seen_badges.json.
     */
    public CustomNotificationDialog(List<String> expiredBadges, List<String> warningBadges, Runnable onCloseAction) {
        super((Frame) null, "Notification Badges ONDA", false); // non-modal
        setUndecorated(true);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initUI(expiredBadges, warningBadges, onCloseAction);
        attachFocusReBeep();
        pack();
        positionBottomRight();
    }

    private void initUI(List<String> expiredBadges, List<String> warningBadges, Runnable onCloseAction) {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ALERT_RED_BORDER, 2),
                new EmptyBorder(0, 0, 8, 0)));
        mainPanel.setBackground(ALERT_RED_LIGHT);

        // ---- Header: logo pill + title ----
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Top section: dark red gradient
                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(0xB0, 0x10, 0x10),
                        getWidth(), 0, ALERT_RED);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        headerPanel.setBorder(new EmptyBorder(10, 14, 10, 14));
        headerPanel.setOpaque(false);

        // -- Logo on the left inside a white rounded pill --
        JPanel logoPill = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        logoPill.setOpaque(false);
        logoPill.setBorder(new EmptyBorder(4, 6, 4, 6));

        JLabel logoLabel = new JLabel();
        try {
            URL logoUrl = getClass().getClassLoader().getResource("onda_logo.png");
            if (logoUrl != null) {
                ImageIcon raw = new ImageIcon(logoUrl);
                int targetH = 44;
                int origW = raw.getIconWidth();
                int origH = raw.getIconHeight();
                int targetW = (origH > 0) ? (origW * targetH / origH) : 44;
                Image scaled = raw.getImage().getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                logoLabel.setIcon(new ImageIcon(scaled));
            } else {
                logoLabel.setText("ONDA");
                logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
                logoLabel.setForeground(ONDA_BLUE_DARK);
            }
        } catch (Exception ex) {
            logoLabel.setText("ONDA");
            logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            logoLabel.setForeground(ONDA_BLUE_DARK);
        }
        logoPill.add(logoLabel, BorderLayout.CENTER);

        // -- Title block on the right --
        JPanel titleBlock = new JPanel(new BorderLayout(0, 2));
        titleBlock.setOpaque(false);

        JLabel titleLabel = new JLabel("⚠️  ALERTE BADGES");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel("Des badges nécessitent votre attention");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitleLabel.setForeground(new Color(0xFF, 0xCC, 0xCC));

        titleBlock.add(titleLabel,    BorderLayout.CENTER);
        titleBlock.add(subtitleLabel, BorderLayout.SOUTH);

        headerPanel.add(logoPill,   BorderLayout.WEST);
        headerPanel.add(titleBlock, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // ---- Content area ----
        JTextArea contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        contentArea.setBackground(ALERT_RED_LIGHT);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setBorder(new EmptyBorder(4, 14, 4, 14));

        StringBuilder sb = new StringBuilder();

        if (expiredBadges != null && !expiredBadges.isEmpty()) {
            sb.append("🔴  BADGES EXPIRÉS (").append(expiredBadges.size()).append(") :\n");
            sb.append(String.join("\n", expiredBadges)).append("\n\n");
        }

        if (warningBadges != null && !warningBadges.isEmpty()) {
            sb.append("🟠  RELANCE NÉCESSAIRE (<= 1j) (").append(warningBadges.size()).append(") :\n");
            sb.append(String.join("\n", warningBadges)).append("\n");
        }

        contentArea.setText(sb.toString());
        contentArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setPreferredSize(new Dimension(500, 220));
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(8, 10, 4, 10),
                BorderFactory.createLineBorder(new Color(0xE0, 0xB0, 0xB0), 1)));
        scrollPane.getViewport().setBackground(ALERT_RED_LIGHT);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // ---- Footer / Button ----
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 6));
        footerPanel.setBackground(ALERT_RED_LIGHT);

        JButton closeButton = new JButton("  ✓  Fermer");
        closeButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeButton.setBackground(ALERT_RED);
        closeButton.setForeground(Color.WHITE);
        closeButton.setOpaque(true);
        closeButton.setFocusPainted(false);
        closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeButton.putClientProperty("JButton.buttonType", "roundRect");
        closeButton.setBorder(new EmptyBorder(7, 18, 7, 18));
        closeButton.addActionListener(e -> {
            // Save acknowledged badges BEFORE closing
            if (onCloseAction != null) {
                onCloseAction.run();
            }
            dispose();
        });
        footerPanel.add(closeButton);

        mainPanel.add(footerPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);
    }

    /**
     * Plays the system beep only once when the dialog window first gains focus.
     */
    private void attachFocusReBeep() {
        addWindowFocusListener(new WindowAdapter() {
            private boolean hasPlayedSound = false;

            @Override
            public void windowGainedFocus(WindowEvent e) {
                if (!hasPlayedSound) {
                    Toolkit.getDefaultToolkit().beep();
                    hasPlayedSound = true;
                }
            }
        });
    }

    private void positionBottomRight() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Insets scnMax = Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        int taskBarSize = scnMax.bottom;
        int x = screenSize.width - getWidth() - 20;
        int y = screenSize.height - taskBarSize - getHeight() - 20;
        setLocation(x, y);
    }
}
