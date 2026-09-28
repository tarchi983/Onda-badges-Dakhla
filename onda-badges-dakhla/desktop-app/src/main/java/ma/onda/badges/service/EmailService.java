package ma.onda.badges.service;

import ma.onda.badges.model.Badge;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.activation.DataHandler;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * SMTP email service for sending badge expiration reminder emails.
 * <p>
 * Includes fast network connectivity checking, STARTTLS SMTP sessions,
 * strict UTF-8 MIME formatting, and integrated duplicate prevention via
 * {@link EmailHistoryManager}.
 * </p>
 */
public class EmailService {

    private final String smtpHost;
    private final int smtpPort;
    private final String senderEmail;
    private final String senderPassword;
    private final EmailHistoryManager historyManager;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public EmailService(String smtpHost, int smtpPort, String senderEmail, String senderPassword) {
        this(smtpHost, smtpPort, senderEmail, senderPassword, new EmailHistoryManager());
    }

    public EmailService(String smtpHost, int smtpPort, String senderEmail, String senderPassword,
            EmailHistoryManager historyManager) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.senderEmail = senderEmail;
        this.senderPassword = senderPassword;
        this.historyManager = historyManager != null ? historyManager : new EmailHistoryManager();
    }

    // -------------------------------------------------------------------------
    // Network Availability Check
    // -------------------------------------------------------------------------

    /**
     * Performs a fast network connection check (2-second timeout).
     * <p>
     * Primary check: connects to Google DNS (8.8.8.8:53).
     * Fallback check: connects directly to configured SMTP host & port.
     * </p>
     *
     * @return {@code true} if an internet/network connection is reachable.
     */
    public boolean isNetworkAvailable() {
        // 1. Primary check: Google Public DNS (8.8.8.8:53) with 2-second timeout
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 2000);
            return true;
        } catch (Exception ignored) {
        }

        // 2. Secondary check: target SMTP host & port with 2-second timeout
        if (smtpHost != null && !smtpHost.isBlank()) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(smtpHost, smtpPort), 2000);
                return true;
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Result summary for a bulk send operation.
     */
    public static class EmailSendResult {
        public final int sent;
        public final int failed;
        public final int alreadySent;
        public final int skipped;
        public final List<String> errors;

        public EmailSendResult(int sent, int failed, int alreadySent, int skipped, List<String> errors) {
            this.sent = sent;
            this.failed = failed;
            this.alreadySent = alreadySent;
            this.skipped = skipped;
            this.errors = errors;
        }

        public int getTotalEligible() {
            return sent + failed + alreadySent + skipped;
        }

        /**
         * Formats a clear multi-line summary string suitable for display in a dialog.
         */
        public String getSummaryDialogMessage() {
            StringBuilder sb = new StringBuilder();
            sb.append("Rapport d'Envoi des Remarques de Badge :\n\n");
            sb.append("• Badges éligibles analysés : ").append(getTotalEligible()).append("\n");
            sb.append("• Emails envoyés avec succès : ").append(sent).append("\n");
            sb.append("• Déjà envoyés aujourd'hui (ignorés) : ").append(alreadySent).append("\n");
            sb.append("• Ignorés (sans adresse email) : ").append(skipped).append("\n");
            sb.append("• Échecs d'envoi : ").append(failed).append("\n");

            if (!errors.isEmpty()) {
                sb.append("\nDétails des erreurs rencontrées :\n");
                int maxDisplay = 5;
                for (int i = 0; i < Math.min(errors.size(), maxDisplay); i++) {
                    sb.append(" - ").append(errors.get(i)).append("\n");
                }
                if (errors.size() > maxDisplay) {
                    sb.append(" ... (et ").append(errors.size() - maxDisplay).append(" autre(s) erreur(s))\n");
                }
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(sent).append(" envoyé(s), ")
                    .append(failed).append(" échec(s)");
            if (alreadySent > 0) {
                sb.append(", ").append(alreadySent).append(" déjà envoyé(s) aujourd'hui");
            }
            if (skipped > 0) {
                sb.append(", ").append(skipped).append(" ignoré(s) (sans email)");
            }
            sb.append(".");
            return sb.toString();
        }
    }

    /**
     * Sends reminder emails to all badges in the list that:
     * <ul>
     * <li>Have a non-blank email address.</li>
     * <li>Are expired OR expiring within {@code thresholdDays} days.</li>
     * <li>Have NOT already received an email today (checked via
     * {@link EmailHistoryManager}).</li>
     * </ul>
     *
     * @param badges        Full list of badges to scan.
     * @param thresholdDays Maximum days remaining to qualify for a reminder (e.g.
     *                      7).
     * @return A result summary detailing sent, failed, alreadySent, and skipped
     *         counts.
     */
    public EmailSendResult sendBulkReminders(List<Badge> badges, int thresholdDays) {
        if (badges == null || badges.isEmpty()) {
            return new EmailSendResult(0, 0, 0, 0, List.of());
        }

        Session session = buildSession();

        int sent = 0;
        int failed = 0;
        int alreadySent = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (Badge badge : badges) {
            String recipient = badge.getEmail();
            if (recipient == null || recipient.isBlank()) {
                skipped++;
                continue;
            }

            Long daysRemaining = badge.getDaysRemaining();
            if (daysRemaining == null) {
                skipped++;
                continue;
            }

            // Only email if expired or within the threshold window
            if (daysRemaining > thresholdDays) {
                skipped++;
                continue;
            }

            String expStr = badge.getCalculatedExpiration() != null
                    ? badge.getCalculatedExpiration().toString()
                    : "";

            // Check duplicate prevention
            if (historyManager.isAlreadySentToday(badge.getBadgeNumber(), expStr)) {
                alreadySent++;
                System.out.println("[EmailService] Email déjà envoyé aujourd'hui pour le badge "
                        + badge.getBadgeNumber() + ", ignoré.");
                continue;
            }

            try {
                sendReminderEmail(session, badge, daysRemaining.intValue());
                historyManager.recordSentEmail(badge.getBadgeNumber(), recipient, expStr);
                sent++;
                System.out.println("[EmailService] Email envoyé à " + recipient
                        + " pour le badge " + badge.getBadgeNumber());
            } catch (MessagingException e) {
                failed++;
                String msg = "Échec envoi à " + recipient + " : " + e.getMessage();
                errors.add(msg);
                System.err.println("[EmailService] " + msg);
            }
        }

        return new EmailSendResult(sent, failed, alreadySent, skipped, errors);
    }

    public EmailHistoryManager getHistoryManager() {
        return historyManager;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a single reminder email for one badge using strict UTF-8 MIME
     * formatting
     * and inline CID logo attachment for 100% email client compatibility (Gmail,
     * Outlook, Mobile).
     */
    private void sendReminderEmail(Session session, Badge badge, int daysRemaining)
            throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(senderEmail));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(badge.getEmail()));

        // Subject explicitly in UTF-8
        message.setSubject(buildSubject(badge, daysRemaining), "UTF-8");

        byte[] logoBytes = loadLogoBytes();

        if (logoBytes != null && logoBytes.length > 0) {
            MimeMultipart multipart = new MimeMultipart("related");

            // 1. HTML Body Part
            MimeBodyPart htmlPart = new MimeBodyPart();
            String htmlContent = buildHtmlBody(badge, daysRemaining, true);
            htmlPart.setContent(htmlContent, "text/html; charset=UTF-8");
            multipart.addBodyPart(htmlPart);

            // 2. Inline Image Part (CID)
            MimeBodyPart imagePart = new MimeBodyPart();
            ByteArrayDataSource ds = new ByteArrayDataSource(logoBytes, "image/png");
            imagePart.setDataHandler(new DataHandler(ds));
            imagePart.setContentID("<onda_logo>");
            imagePart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(imagePart);

            message.setContent(multipart);
        } else {
            String htmlContent = buildHtmlBody(badge, daysRemaining, false);
            message.setContent(htmlContent, "text/html; charset=UTF-8");
            message.setHeader("Content-Type", "text/html; charset=UTF-8");
            message.setHeader("Content-Transfer-Encoding", "8bit");
        }

        Transport.send(message);
    }

    private String buildSubject(Badge badge, int daysRemaining) {
        if (daysRemaining < 0) {
            return "[ONDA] Badge expiré — " + badge.getBadgeNumber()
                    + " — " + badge.getFullName();
        } else if (daysRemaining == 0) {
            return "[ONDA] Rappel : votre badge expire AUJOURD'HUI — " + badge.getBadgeNumber();
        } else {
            return "[ONDA] Rappel : votre badge expire dans " + daysRemaining + " jour(s) — "
                    + badge.getBadgeNumber();
        }
    }

    private String buildHtmlBody(Badge badge, int daysRemaining, boolean hasLogo) {
        String expirationStr = badge.getCalculatedExpiration() != null
                ? badge.getCalculatedExpiration().format(DATE_FMT)
                : "Inconnue";
        String todayStr = LocalDate.now().format(DATE_FMT);

        String urgencyColor;
        String urgencyMessage;
        if (daysRemaining < 0) {
            urgencyColor = "#c0392b";
            urgencyMessage = "Votre badge d'accès <strong>a expiré</strong> depuis le "
                    + expirationStr + ". Veuillez procéder à son renouvellement immédiatement.";
        } else if (daysRemaining == 0) {
            urgencyColor = "#e67e22";
            urgencyMessage = "Votre badge d'accès <strong>expire aujourd'hui</strong> ("
                    + expirationStr + "). Veuillez contacter ONDA Dakhla dès que possible.";
        } else {
            urgencyColor = "#2980b9";
            urgencyMessage = "Votre badge d'accès expirera dans <strong>" + daysRemaining
                    + " jour(s)</strong>, le <strong>" + expirationStr
                    + "</strong>. Pensez à effectuer les démarches de renouvellement.";
        }

        // Header logo block — references CID <onda_logo> embedded in the MIME multipart
        String logoBlock = hasLogo
                ? "<div style='background:#ffffff;text-align:center;padding:20px 30px 15px 30px;"
                        + "border-bottom:1px solid #e8edf2;'>"
                        + "<img src='cid:onda_logo' alt='ONDA — Airports of Morocco'"
                        + " style='height:75px;width:auto;max-width:100%;display:inline-block;margin:0 auto;' />"
                        + "</div>"
                : "<div style='background:#004A9F;text-align:center;padding:15px 30px;"
                        + "border-bottom:1px solid #003a80;'>"
                        + "<span style='color:#fff;font-size:22px;font-weight:bold;letter-spacing:2px;'>ONDA</span>"
                        + "</div>";

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body style='"
                + "font-family:Segoe UI,Arial,sans-serif;background:#f4f6f8;margin:0;padding:20px;'>"
                + "<div style='max-width:600px;margin:auto;background:#fff;"
                + "border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1);'>"
                // ONDA logo header (white background, centered)
                + logoBlock
                // Urgency colour banner
                + "<div style='background:" + urgencyColor + ";padding:18px 30px;'>"
                + "<h2 style='color:#fff;margin:0;font-size:18px;'>Notification ONDA Dakhla — Badge d'Accès</h2>"
                + "</div>"
                // Body
                + "<div style='padding:30px;'>"
                + "<p style='font-size:15px;color:#2c3e50;'>Bonjour <strong>"
                + escapeHtml(badge.getFullName()) + "</strong>,</p>"
                + "<p style='font-size:15px;color:#2c3e50;'>" + urgencyMessage + "</p>"
                // Details table
                + "<table style='width:100%;border-collapse:collapse;margin:20px 0;font-size:14px;'>"
                + tableRow("N° Badge", escapeHtml(badge.getBadgeNumber()))
                + tableRow("Nom complet", escapeHtml(badge.getFullName()))
                + tableRow("Organisme", escapeHtml(badge.getOrganisme()))
                + tableRow("Date d'expiration", expirationStr)
                + tableRow("Date du jour", todayStr)
                + "</table>"
                + "<p style='font-size:13px;color:#7f8c8d;'>Merci de contacter le service des badges de "
                + "l'ONDA Dakhla pour toute question relative au renouvellement.</p>"
                + "</div>"
                // Footer
                + "<div style='background:#ecf0f1;padding:15px 30px;font-size:12px;color:#95a5a6;'"
                + " align='center'>"
                + "Ce message est généré automatiquement par l'application ONDA Badges Dakhla. "
                + "Merci de ne pas répondre directement à cet email."
                + "</div>"
                + "</div>"
                + "</body></html>";
    }

    /**
     * Loads onda_logo.png raw bytes from the classpath or filesystem.
     * Tries context ClassLoader, class ClassLoader, class getResourceAsStream, and
     * local filesystem fallbacks.
     * Returns {@code null} if the resource cannot be found or read.
     */
    private byte[] loadLogoBytes() {
        InputStream is = null;
        try {
            // Strategy 1: Thread context ClassLoader
            if (Thread.currentThread().getContextClassLoader() != null) {
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream("onda_logo.png");
            }
            // Strategy 2: ClassLoader from getClass()
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("onda_logo.png");
            }
            // Strategy 3: Class getResourceAsStream with absolute path
            if (is == null) {
                is = getClass().getResourceAsStream("/onda_logo.png");
            }
            // Strategy 4: Class getResourceAsStream with relative path
            if (is == null) {
                is = getClass().getResourceAsStream("onda_logo.png");
            }

            // Strategy 5: Filesystem fallbacks during development or local execution
            if (is == null) {
                String[] fallbackPaths = new String[] {
                        "src/main/resources/onda_logo.png",
                        "target/classes/onda_logo.png",
                        "onda_logo.png",
                        "../onda_images/Screenshot 2026-08-11 121508.png",
                        "C:/Users/user/Videos/projet de stage 2/onda_images/Screenshot 2026-08-11 121508.png"
                };
                for (String path : fallbackPaths) {
                    File file = new File(path);
                    if (file.exists() && file.isFile()) {
                        is = new FileInputStream(file);
                        break;
                    }
                }
            }

            if (is == null) {
                System.err.println("[EmailService] Logo non trouvé dans le classpath ou le système de fichiers.");
                return null;
            }

            try (InputStream stream = is; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int n;
                while ((n = stream.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                return buf.toByteArray();
            }
        } catch (Exception ex) {
            System.err.println("[EmailService] Erreur lors du chargement des octets du logo : " + ex.getMessage());
            return null;
        }
    }

    private String tableRow(String label, String value) {
        return "<tr style='border-bottom:1px solid #ecf0f1;'>"
                + "<td style='padding:10px;background:#f8f9fa;font-weight:bold;width:40%;color:#2c3e50;'>"
                + label + "</td>"
                + "<td style='padding:10px;color:#2c3e50;'>" + (value != null ? value : "—") + "</td>"
                + "</tr>";
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "—";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Builds a javax.mail Session with STARTTLS authentication.
     * Works with common SMTP providers (Gmail, Outlook, etc.).
     */
    private Session buildSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderEmail, senderPassword);
            }
        });
    }
}