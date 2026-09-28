package ma.onda.badges;

import com.formdev.flatlaf.FlatLightLaf;
import ma.onda.badges.util.SampleExcelGenerator;
import ma.onda.badges.ui.MainDashboardFrame;

import javax.swing.SwingUtilities;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.PopupMenu;
import java.awt.MenuItem;
import java.awt.AWTException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class App {

    private static FileChannel channel;
    private static FileLock lock;

    public static void main(String[] args) {
        // Enforce single instance execution
        if (!lockInstance()) {
            System.out.println("L'application est déjà en cours d'exécution. Fermeture de cette instance.");
            System.exit(0);
        }

        SwingUtilities.invokeLater(() -> {
            try {
                FlatLightLaf.setup();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Ensure sample Excel file is available for demo/testing
            SampleExcelGenerator.generateSampleExcelIfNotExists("C:/ONDA/Badges_2026.xlsx");

            MainDashboardFrame mainFrame = new MainDashboardFrame();
            
            // Create System Tray icon
            setupSystemTray(mainFrame);
            
            // Do NOT display the main dashboard window automatically
            // mainFrame.setVisible(true);
        });
    }

    private static boolean lockInstance() {
        try {
            File file = new File(System.getProperty("user.home"), ".onda_badges.lock");
            @SuppressWarnings("resource")
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            channel = randomAccessFile.getChannel();
            lock = channel.tryLock();
            return lock != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void setupSystemTray(MainDashboardFrame mainFrame) {
        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray is not supported.");
            return;
        }

        PopupMenu popup = new PopupMenu();
        
        MenuItem openItem = new MenuItem("Ouvrir le Tableau de Bord");
        openItem.addActionListener(e -> {
            mainFrame.setVisible(true);
            mainFrame.toFront();
        });

        MenuItem exitItem = new MenuItem("Quitter");
        exitItem.addActionListener(e -> {
            System.exit(0);
        });

        popup.add(openItem);
        popup.addSeparator();
        popup.add(exitItem);

        // Create a simple red circle as the tray icon
        BufferedImage trayIconImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = trayIconImage.createGraphics();
        g.setColor(new Color(200, 50, 50));
        g.fillOval(2, 2, 12, 12);
        g.dispose();

        TrayIcon trayIcon = new TrayIcon(trayIconImage, "ONDA Badges Dakhla", popup);
        trayIcon.setImageAutoSize(true);
        
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    mainFrame.setVisible(true);
                    mainFrame.toFront();
                }
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }
    }
}
