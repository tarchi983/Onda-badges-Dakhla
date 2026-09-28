package ma.onda.badges.service;

import ma.onda.badges.model.Badge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    public void setUp() {
        notificationService = new NotificationService();
        // Clean up any leftover seen_badges.json from previous test runs
        try {
            Files.deleteIfExists(Paths.get("seen_badges.json"));
        } catch (IOException ignored) {
        }
    }

    @Test
    public void testInitializationDoesNotThrowException() {
        assertNotNull(notificationService, "NotificationService instance should not be null");
    }

    @Test
    public void testNotifyExpiredBadgesWithNullOrEmptyList() {
        assertDoesNotThrow(() -> notificationService.notifyExpiredBadges(null));
        assertDoesNotThrow(() -> notificationService.notifyExpiredBadges(new ArrayList<>()));
    }

    @Test
    public void testNotifyExpiredBadgesWithFewExpiredBadges() {
        List<Badge> badges = new ArrayList<>();
        badges.add(new Badge("9103", "Agent 1", "a1@onda.ma", "ONDA", "Agent",
                LocalDate.now().minusYears(1), "12m", LocalDate.now().minusDays(5)));
        badges.add(new Badge("9104", "Agent 2", "a2@onda.ma", "ONDA", "Agent",
                LocalDate.now().minusYears(1), "12m", LocalDate.now().minusDays(2)));

        // Uses SwingUtilities.invokeLater so does not block test
        assertDoesNotThrow(() -> notificationService.notifyExpiredBadges(badges));
    }

    @Test
    public void testNotifyExpiredBadgesWithWarningBadge() {
        List<Badge> badges = new ArrayList<>();
        Badge warningBadge = new Badge(
                "9105", "Marie Martin", "marie.martin@onda.ma", "ONDA", "Agent",
                LocalDate.now().minusMonths(6), "6 mois",
                LocalDate.now().plusDays(1)); // at 1-day threshold
        badges.add(warningBadge);

        assertDoesNotThrow(() -> notificationService.notifyExpiredBadges(badges));
    }

    // ---- seen_badges.json unit tests ----------------------------------------

    @Test
    public void testSeenBadgesLoadEmptyWhenFileAbsent() {
        Set<String> seen = notificationService.loadSeenBadges();
        assertNotNull(seen);
        assertTrue(seen.isEmpty(), "Expected empty set when seen_badges.json does not exist");
    }

    @Test
    public void testSeenBadgesSaveAndReload() {
        Set<String> toSave = new HashSet<>();
        toSave.add("N°9100|2026-07-28");
        toSave.add("N°9101|2026-08-04");

        notificationService.saveSeenBadges(toSave);
        Set<String> loaded = notificationService.loadSeenBadges();

        assertEquals(toSave, loaded, "Reloaded seen badges should match what was saved");

        try {
            Files.deleteIfExists(Paths.get("seen_badges.json"));
        } catch (IOException ignored) {
        }
    }

    @Test
    public void testSeenBadgesSuppressesAlreadyAcknowledgedBadge() {
        LocalDate expDate = LocalDate.now().minusDays(10);
        Badge expiredBadge = new Badge(
                "N°9100", "Test Person", "", "ONDA", "Agent",
                LocalDate.now().minusYears(1), "12m", expDate);

        // Pre-mark this badge as seen
        String badgeId = "N°9100|" + expDate;
        Set<String> preSeenIds = new HashSet<>();
        preSeenIds.add(badgeId);
        notificationService.saveSeenBadges(preSeenIds);

        List<Badge> badges = new ArrayList<>();
        badges.add(expiredBadge);

        // Should run without exception; dialog should NOT be triggered (badge is seen)
        assertDoesNotThrow(() -> notificationService.notifyExpiredBadges(badges));

        try {
            Files.deleteIfExists(Paths.get("seen_badges.json"));
        } catch (IOException ignored) {
        }
    }
}
