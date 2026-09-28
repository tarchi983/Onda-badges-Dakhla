package ma.onda.badges.model;

/**
 * Enum representing the status of an ONDA badge based on expiration date.
 * 
 * - EXPIRED: Expiration date has passed (< 0 days remaining).
 * - WARNING_RELANCE: Expiration is within 1 day (0 to 1 days remaining).
 * - VALID: Badge is valid for more than 1 day (> 1 days remaining).
 */
public enum BadgeStatus {
    EXPIRED("Expiré"),
    WARNING_RELANCE("Relance Nécessaire (<= 1j)"),
    VALID("Valide");

    private final String label;

    BadgeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
