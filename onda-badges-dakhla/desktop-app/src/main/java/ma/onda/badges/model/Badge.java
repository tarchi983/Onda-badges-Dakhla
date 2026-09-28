package ma.onda.badges.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Model representing a badge record extracted from the ONDA Excel sheet.
 */
public class Badge {

    private String badgeNumber;
    private String fullName;
    private String email;
    private String organisme;
    private String issuedBy;
    private LocalDate deliveryDate;
    private String intendedDuration;
    private LocalDate calculatedExpiration;

    public Badge() {
    }

    public Badge(String badgeNumber, String fullName, String email, String organisme,
                 String issuedBy, LocalDate deliveryDate, String intendedDuration,
                 LocalDate calculatedExpiration) {
        this.badgeNumber = badgeNumber;
        this.fullName = fullName;
        this.email = email;
        this.organisme = organisme;
        this.issuedBy = issuedBy;
        this.deliveryDate = deliveryDate;
        this.intendedDuration = intendedDuration;
        this.calculatedExpiration = calculatedExpiration;
    }

    // Getters and Setters

    public String getBadgeNumber() {
        return badgeNumber;
    }

    public void setBadgeNumber(String badgeNumber) {
        this.badgeNumber = badgeNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrganisme() {
        return organisme;
    }

    public void setOrganisme(String organisme) {
        this.organisme = organisme;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getIntendedDuration() {
        return intendedDuration;
    }

    public void setIntendedDuration(String intendedDuration) {
        this.intendedDuration = intendedDuration;
    }

    public LocalDate getCalculatedExpiration() {
        return calculatedExpiration;
    }

    public void setCalculatedExpiration(LocalDate calculatedExpiration) {
        this.calculatedExpiration = calculatedExpiration;
    }

    // Business Logic / Helper Methods

    /**
     * Calculates days remaining until expiration relative to today.
     * 
     * @return Number of days remaining (negative if expired, null if expiration date is absent).
     */
    public Long getDaysRemaining() {
        return getDaysRemaining(LocalDate.now());
    }

    /**
     * Calculates days remaining until expiration relative to a reference date.
     * 
     * @param referenceDate Date to compare against.
     * @return Number of days remaining.
     */
    public Long getDaysRemaining(LocalDate referenceDate) {
        if (calculatedExpiration == null || referenceDate == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(referenceDate, calculatedExpiration);
    }

    /**
     * Computes the BadgeStatus based on the current date.
     * 
     * - EXPIRED: days remaining < 0
     * - WARNING_RELANCE: 0 <= days remaining <= 1
     * - VALID: days remaining > 1
     */
    public BadgeStatus getStatus() {
        return getStatus(LocalDate.now());
    }

    /**
     * Computes the BadgeStatus based on a given reference date.
     */
    public BadgeStatus getStatus(LocalDate referenceDate) {
        Long days = getDaysRemaining(referenceDate);
        if (days == null) {
            return BadgeStatus.VALID;
        }
        if (days < 0) {
            return BadgeStatus.EXPIRED;
        } else if (days <= 1) {
            return BadgeStatus.WARNING_RELANCE;
        } else {
            return BadgeStatus.VALID;
        }
    }

    /**
     * Utility check to determine if relance is required (<= 1 day remaining and not expired).
     */
    public boolean requiresRelance() {
        return getStatus() == BadgeStatus.WARNING_RELANCE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Badge badge = (Badge) o;
        return Objects.equals(badgeNumber, badge.badgeNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(badgeNumber);
    }

    @Override
    public String toString() {
        return "Badge{" +
                "badgeNumber='" + badgeNumber + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", organisme='" + organisme + '\'' +
                ", issuedBy='" + issuedBy + '\'' +
                ", deliveryDate=" + deliveryDate +
                ", intendedDuration='" + intendedDuration + '\'' +
                ", calculatedExpiration=" + calculatedExpiration +
                ", status=" + getStatus() +
                '}';
    }
}
