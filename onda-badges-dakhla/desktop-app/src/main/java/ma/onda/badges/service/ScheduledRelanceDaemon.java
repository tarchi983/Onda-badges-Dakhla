package ma.onda.badges.service;

import ma.onda.badges.model.Badge;

import javax.swing.SwingUtilities;
import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Background daemon service running via ScheduledExecutorService
 * to scan Excel file and dispatch desktop notifications.
 * It polls the file's last modified timestamp every 5 seconds to provide live updates.
 * <p>
 * A second scheduler ({@code emailScheduler}) fires daily at 09:00 AM and sends
 * SMTP reminder emails when {@link #startEmailScheduler} has been called.
 * </p>
 */
public class ScheduledRelanceDaemon {

    private final ExcelParserService excelParserService;
    private final NotificationService notificationService;
    private ScheduledExecutorService scheduler;
    private String excelFilePath;
    private boolean running;

    private long lastModifiedTime = 0;
    private Consumer<List<Badge>> onDataUpdated;

    // --- Email scheduler ---
    private ScheduledExecutorService emailScheduler;
    private boolean emailSchedulerRunning = false;
    /** Daily send time for the automatic email relance (09:00 AM). */
    private static final LocalTime DAILY_EMAIL_TIME = LocalTime.of(9, 0);

    public ScheduledRelanceDaemon(ExcelParserService excelParserService, String excelFilePath) {
        this(excelParserService, excelFilePath, new NotificationService());
    }

    public ScheduledRelanceDaemon(ExcelParserService excelParserService, String excelFilePath, NotificationService notificationService) {
        this.excelParserService = excelParserService;
        this.excelFilePath = excelFilePath;
        this.notificationService = notificationService != null ? notificationService : new NotificationService();
        this.running = false;
        updateLastModifiedTime();
    }

    public void setOnDataUpdated(Consumer<List<Badge>> onDataUpdated) {
        this.onDataUpdated = onDataUpdated;
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ONDA-Relance-Daemon-Thread");
            t.setDaemon(true);
            return t;
        });

        // Poll every 5 seconds for file changes
        scheduler.scheduleAtFixedRate(this::checkFileModified, 0, 5, TimeUnit.SECONDS);
        running = true;
        System.out.println("Balayage automatique (Live-Refresh) démarré.");
    }

    public synchronized void stop() {
        if (!running || scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        running = false;
        System.out.println("Balayage automatique arrêté.");
    }

    public boolean isRunning() {
        return running;
    }

    public void setExcelFilePath(String path) {
        this.excelFilePath = path;
        updateLastModifiedTime();
    }

    private void updateLastModifiedTime() {
        if (excelFilePath != null && !excelFilePath.isBlank()) {
            File f = new File(excelFilePath);
            if (f.exists()) {
                this.lastModifiedTime = f.lastModified();
            }
        }
    }

    public void triggerNow() {
        executeDailyTask();
    }

    // -------------------------------------------------------------------------
    // Email scheduler
    // -------------------------------------------------------------------------

    /**
     * Starts the daily 09:00 AM email scheduler.
     *
     * @param emailService  A fully-configured EmailService (SMTP creds set).
     * @param thresholdDays Badges expiring within this many days will receive an email.
     */
    public synchronized void startEmailScheduler(EmailService emailService, int thresholdDays) {
        if (emailSchedulerRunning) {
            stopEmailScheduler();
        }

        emailScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ONDA-Email-Scheduler-Thread");
            t.setDaemon(true);
            return t;
        });

        long initialDelay = computeInitialDelaySeconds();
        emailScheduler.scheduleAtFixedRate(
                () -> executeEmailTask(emailService, thresholdDays),
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS);

        emailSchedulerRunning = true;
        System.out.println("[EmailScheduler] Relance automatique par email active. "
                + "Prochain envoi dans " + (initialDelay / 3600) + "h "
                + ((initialDelay % 3600) / 60) + "min.");
    }

    /**
     * Stops the daily email scheduler if it is running.
     */
    public synchronized void stopEmailScheduler() {
        if (!emailSchedulerRunning || emailScheduler == null) {
            return;
        }
        emailScheduler.shutdown();
        try {
            if (!emailScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                emailScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            emailScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        emailSchedulerRunning = false;
        System.out.println("[EmailScheduler] Relance automatique par email arretee.");
    }

    /** Returns {@code true} if the daily email scheduler is currently active. */
    public boolean isEmailSchedulerRunning() {
        return emailSchedulerRunning;
    }

    /**
     * Computes the number of seconds until the next 09:00 AM occurrence.
     */
    private long computeInitialDelaySeconds() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.toLocalDate().atTime(DAILY_EMAIL_TIME);
        if (!now.isBefore(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return java.time.Duration.between(now, nextRun).getSeconds();
    }

    /**
     * Loads the badge list from Excel and sends bulk reminder emails.
     */
    private void executeEmailTask(EmailService emailService, int thresholdDays) {
        System.out.println("[" + LocalDateTime.now() + "] [EmailScheduler] Debut de la relance automatique par email...");
        try {
            if (excelFilePath == null || excelFilePath.isBlank()) {
                System.err.println("[EmailScheduler] Fichier Excel non configure, envoi annule.");
                return;
            }
            List<Badge> allBadges = excelParserService.parseExcelFile(excelFilePath);
            EmailService.EmailSendResult result = emailService.sendBulkReminders(allBadges, thresholdDays);
            System.out.println("[EmailScheduler] Resultat : " + result);
        } catch (Exception e) {
            System.err.println("[EmailScheduler] Erreur lors de la relance email : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkFileModified() {
        if (excelFilePath == null || excelFilePath.isBlank()) {
            return;
        }
        File f = new File(excelFilePath);
        if (f.exists() && f.lastModified() > lastModifiedTime) {
            lastModifiedTime = f.lastModified();
            executeDailyTask();
        }
    }

    private void executeDailyTask() {
        System.out.println("[" + LocalDateTime.now() + "] Daemon: Fichier modifié détecté, mise à jour des badges...");
        try {
            if (excelFilePath == null || excelFilePath.isBlank()) {
                System.err.println("Daemon error: Fichier Excel non configuré.");
                return;
            }

            List<Badge> allBadges = excelParserService.parseExcelFile(excelFilePath);
            
            if (onDataUpdated != null) {
                SwingUtilities.invokeLater(() -> onDataUpdated.accept(allBadges));
            }
            
            notificationService.notifyExpiredBadges(allBadges);

        } catch (Exception e) {
            System.err.println("Daemon execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
