package ma.onda.badges.service;

import ma.onda.badges.model.Badge;
import ma.onda.badges.model.BadgeStatus;
import ma.onda.badges.ui.CustomNotificationDialog;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles application notifications by displaying a custom persistent Swing dialog.
 * <p>
 * Uses {@code seen_badges.json} as a local persistence store to track which
 * expired/warning badges have been acknowledged by the user. Only badges NOT
 * present in this file will trigger a popup and beep alert.
 * </p>
 */
public class NotificationService {

    private static final String SEEN_BADGES_FILE = "seen_badges.json";

    public NotificationService() {
    }

    public boolean isSupported() {
        return true;
    }

    /**
     * Checks the badge list against seen_badges.json.
     * Only triggers a popup + beep if at least one badge has NOT been acknowledged yet.
     * On "Fermer" click, all newly displayed badge IDs are written to seen_badges.json.
     */
    public void notifyExpiredBadges(List<Badge> badges) {
        if (badges == null || badges.isEmpty()) {
            return;
        }

        // Load previously acknowledged badge IDs
        Set<String> seenIds = loadSeenBadges();

        // Filter to ONLY unseen badges (those not in seen_badges.json)
        List<Badge> expiredBadges = badges.stream()
                .filter(b -> b != null && b.getStatus() == BadgeStatus.EXPIRED)
                .filter(b -> !seenIds.contains(getBadgeId(b)))
                .collect(Collectors.toList());

        List<Badge> warningBadges = badges.stream()
                .filter(b -> b != null && b.getStatus() == BadgeStatus.WARNING_RELANCE)
                .filter(b -> !seenIds.contains(getBadgeId(b)))
                .collect(Collectors.toList());

        // If all current alert badges are already acknowledged -> stay completely silent
        if (expiredBadges.isEmpty() && warningBadges.isEmpty()) {
            return;
        }

        List<String> expiredDetails = expiredBadges.stream()
                .map(this::formatBadgeDetails)
                .collect(Collectors.toList());

        List<String> warningDetails = warningBadges.stream()
                .map(this::formatBadgeDetails)
                .collect(Collectors.toList());

        // Collect IDs of all newly displayed badges for acknowledgment on close
        Set<String> newlyShownIds = new HashSet<>();
        expiredBadges.forEach(b -> newlyShownIds.add(getBadgeId(b)));
        warningBadges.forEach(b -> newlyShownIds.add(getBadgeId(b)));

        SwingUtilities.invokeLater(() -> {
            // Initial beep when dialog appears
            java.awt.Toolkit.getDefaultToolkit().beep();

            Runnable onClose = () -> {
                // Merge newly acknowledged IDs into the persisted set
                Set<String> updated = loadSeenBadges();
                updated.addAll(newlyShownIds);
                saveSeenBadges(updated);
            };

            CustomNotificationDialog dialog = new CustomNotificationDialog(expiredDetails, warningDetails, onClose);
            dialog.setVisible(true);
        });
    }

    /**
     * Builds a stable composite key for a badge used to track it in seen_badges.json.
     * Format: {@code <badgeNumber>|<expirationDate>}
     */
    private String getBadgeId(Badge b) {
        String num = b.getBadgeNumber() != null ? b.getBadgeNumber().trim() : "unknown";
        String exp = b.getCalculatedExpiration() != null ? b.getCalculatedExpiration().toString() : "noexp";
        return num + "|" + exp;
    }

    private String formatBadgeDetails(Badge b) {
        if (b == null) {
            return "N°Inconnu";
        }
        String num = b.getBadgeNumber() != null && !b.getBadgeNumber().isBlank() ? b.getBadgeNumber().trim() : "Inconnu";
        String formattedNum = num.startsWith("N°") ? num : "N°" + num;
        String name = b.getFullName() != null && !b.getFullName().isBlank() ? b.getFullName().trim() : "Nom Inconnu";
        String exp = b.getCalculatedExpiration() != null ? b.getCalculatedExpiration().toString() : "Inconnue";
        return formattedNum + " - " + name + " (Exp: " + exp + ")";
    }

    // -------------------------------------------------------------------------
    // seen_badges.json I/O (manual JSON, no external dependencies)
    // -------------------------------------------------------------------------

    /**
     * Reads {@code seen_badges.json} and returns the set of acknowledged badge IDs.
     * Returns an empty set if the file does not exist or cannot be parsed.
     */
    Set<String> loadSeenBadges() {
        Set<String> ids = new HashSet<>();
        Path path = Paths.get(SEEN_BADGES_FILE);
        if (!Files.exists(path)) {
            return ids;
        }
        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            if (content.isEmpty() || content.equals("[]")) {
                return ids;
            }
            // Manual parse: strip [ ] brackets, split by comma, trim surrounding quotes
            content = content.replaceAll("^\\[", "").replaceAll("\\]$", "");
            for (String token : content.split(",")) {
                String id = token.trim().replaceAll("^\"|\"$", "");
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        } catch (IOException e) {
            System.err.println("[NotificationService] Could not read " + SEEN_BADGES_FILE + ": " + e.getMessage());
        }
        return ids;
    }

    /**
     * Writes the set of acknowledged badge IDs back to {@code seen_badges.json}.
     */
    void saveSeenBadges(Set<String> ids) {
        try {
            StringBuilder sb = new StringBuilder("[\n");
            String[] arr = ids.toArray(new String[0]);
            for (int i = 0; i < arr.length; i++) {
                sb.append("  \"").append(arr[i].replace("\"", "\\\"")).append("\"");
                if (i < arr.length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("]");
            Files.write(Paths.get(SEEN_BADGES_FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[NotificationService] Could not write " + SEEN_BADGES_FILE + ": " + e.getMessage());
        }
    }
}
