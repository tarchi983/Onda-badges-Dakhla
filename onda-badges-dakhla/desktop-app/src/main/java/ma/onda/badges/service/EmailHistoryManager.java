package ma.onda.badges.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages email history persistence to prevent sending duplicate reminder emails.
 * Sent email records are saved to and loaded from `sent_emails_history.json`.
 */
public class EmailHistoryManager {

    private static final String HISTORY_FILE = "sent_emails_history.json";
    private final List<EmailHistoryRecord> historyRecords = new ArrayList<>();

    public EmailHistoryManager() {
        loadHistory();
    }

    /**
     * Structure representing a single sent email record.
     */
    public static class EmailHistoryRecord {
        private final String badgeNumber;
        private final String recipientEmail;
        private final String sentDate; // Format: YYYY-MM-DD
        private final String sentTimestamp; // Format: ISO-8601 LocalDateTime
        private final String badgeExpirationDate;

        public EmailHistoryRecord(String badgeNumber, String recipientEmail, String sentDate,
                                  String sentTimestamp, String badgeExpirationDate) {
            this.badgeNumber = badgeNumber;
            this.recipientEmail = recipientEmail;
            this.sentDate = sentDate;
            this.sentTimestamp = sentTimestamp;
            this.badgeExpirationDate = badgeExpirationDate;
        }

        public String getBadgeNumber() { return badgeNumber; }
        public String getRecipientEmail() { return recipientEmail; }
        public String getSentDate() { return sentDate; }
        public String getSentTimestamp() { return sentTimestamp; }
        public String getBadgeExpirationDate() { return badgeExpirationDate; }
    }

    /**
     * Checks if a reminder email has already been sent for the given badge today
     * or for the same expiration date.
     *
     * @param badgeNumber         Badge number to check.
     * @param badgeExpirationDate Expiration date string.
     * @return {@code true} if an email was already sent today for this badge.
     */
    public synchronized boolean isAlreadySentToday(String badgeNumber, String badgeExpirationDate) {
        if (badgeNumber == null || badgeNumber.isBlank()) {
            return false;
        }
        String today = LocalDate.now().toString();
        String normBadge = normalizeBadgeNum(badgeNumber);
        String normExp = badgeExpirationDate != null ? badgeExpirationDate.trim() : "";

        for (EmailHistoryRecord r : historyRecords) {
            String rNormBadge = normalizeBadgeNum(r.getBadgeNumber());
            String rNormExp = r.getBadgeExpirationDate() != null ? r.getBadgeExpirationDate().trim() : "";

            if (normBadge.equalsIgnoreCase(rNormBadge)
                    && today.equals(r.getSentDate())
                    && normExp.equalsIgnoreCase(rNormExp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends a new sent email record and persists it to `sent_emails_history.json`.
     */
    public synchronized void recordSentEmail(String badgeNumber, String recipientEmail, String badgeExpirationDate) {
        String today = LocalDate.now().toString();
        String timestamp = LocalDateTime.now().toString();
        EmailHistoryRecord record = new EmailHistoryRecord(
                badgeNumber != null ? badgeNumber.trim() : "",
                recipientEmail != null ? recipientEmail.trim() : "",
                today,
                timestamp,
                badgeExpirationDate != null ? badgeExpirationDate.trim() : ""
        );
        historyRecords.add(record);
        saveHistory();
    }

    /**
     * Loads existing records from `sent_emails_history.json`.
     */
    public synchronized void loadHistory() {
        historyRecords.clear();
        Path path = Paths.get(HISTORY_FILE);
        if (!Files.exists(path)) {
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Pattern objectPattern = Pattern.compile("\\{[^{}]*\\}");
            Matcher matcher = objectPattern.matcher(json);
            while (matcher.find()) {
                String obj = matcher.group();
                String badgeNumber = extractJsonField(obj, "badgeNumber");
                String recipientEmail = extractJsonField(obj, "recipientEmail");
                String sentDate = extractJsonField(obj, "sentDate");
                String sentTimestamp = extractJsonField(obj, "sentTimestamp");
                String badgeExpirationDate = extractJsonField(obj, "badgeExpirationDate");

                if (badgeNumber != null && !badgeNumber.isBlank()) {
                    historyRecords.add(new EmailHistoryRecord(badgeNumber, recipientEmail, sentDate,
                            sentTimestamp, badgeExpirationDate));
                }
            }
        } catch (Exception e) {
            System.err.println("[EmailHistoryManager] Erreur lors de la lecture de " + HISTORY_FILE + " : " + e.getMessage());
        }
    }

    /**
     * Saves all history records to `sent_emails_history.json`.
     */
    public synchronized void saveHistory() {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < historyRecords.size(); i++) {
                EmailHistoryRecord r = historyRecords.get(i);
                sb.append("  {\n");
                sb.append("    \"badgeNumber\": \"").append(escapeJson(r.getBadgeNumber())).append("\",\n");
                sb.append("    \"recipientEmail\": \"").append(escapeJson(r.getRecipientEmail())).append("\",\n");
                sb.append("    \"sentDate\": \"").append(escapeJson(r.getSentDate())).append("\",\n");
                sb.append("    \"sentTimestamp\": \"").append(escapeJson(r.getSentTimestamp())).append("\",\n");
                sb.append("    \"badgeExpirationDate\": \"").append(escapeJson(r.getBadgeExpirationDate())).append("\"\n");
                sb.append("  }");
                if (i < historyRecords.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("]");
            Files.writeString(Paths.get(HISTORY_FILE), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[EmailHistoryManager] Erreur lors de la sauvegarde de " + HISTORY_FILE + " : " + e.getMessage());
        }
    }

    public List<EmailHistoryRecord> getHistoryRecords() {
        return new ArrayList<>(historyRecords);
    }

    private String normalizeBadgeNum(String num) {
        if (num == null) return "";
        String s = num.trim();
        if (s.startsWith("N°") || s.startsWith("N° ")) {
            s = s.substring(2).trim();
        } else if (s.startsWith("N ")) {
            s = s.substring(2).trim();
        }
        return s;
    }

    private String extractJsonField(String jsonObject, String fieldName) {
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(jsonObject);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}