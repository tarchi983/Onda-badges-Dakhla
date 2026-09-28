# ONDA Badges Dakhla — Application de Relance Automatique

Refactored lightweight Java Swing Desktop Application for Office National Des Aéroports (ONDA - Dakhla) designed for automated badge expiration tracking, threshold filtering, and email renewal notifications.

---

## 📌 Project Overview & Architectural Shift

The project has transitioned from a heavy database-backed (MySQL/XAMPP) system to a **lightweight, file-based architecture**:
- **Input Data**: Excel workbook parsing using **Apache POI OOXML**.
- **Email History & Deduplication**: Local **JSON logging** (`sent_emails_log.json`) using Jackson with `JavaTimeModule`.
- **UI & Styling**: Modern Java Swing powered by **FlatLaf** Look and Feel.
- **Email Dispatching**: SMTP notification system powered by **Jakarta Mail**.

---

## 🛠️ System Architecture & Layer Breakdown

```
src/main/java/ma/onda/badges/
├── App.java                              # Main entry point (FlatLaf setup & UI launch)
├── config/
│   └── AppConfig.java                    # Configuration manager for application.properties & SMTP settings
├── model/
│   ├── Badge.java                        # Badge record model & status/days-remaining calculations
│   ├── BadgeStatus.java                  # Enum: EXPIRED, WARNING_RELANCE, VALID
│   ├── EmailLog.java                     # Model for JSON logging (sent_emails_log.json)
│   └── Agent.java                        # Authorized agent model for login authentication
├── service/
│   ├── ExcelParserService.java           # POI Excel parser (Header at Row 4 / Index 3, French date parsing)
│   ├── EmailLogService.java              # Jackson JSON persistence & daily duplicate check
│   ├── EmailService.java                 # Jakarta Mail SMTP dispatching & HTML body formatting
│   ├── ScheduledRelanceDaemon.java       # ScheduledExecutorService running daily background task at 09:00 AM
│   └── AuthService.java                  # Agent authentication service
├── controller/
│   ├── BadgeController.java              # Controller for parsing, filtering, emailing, & JSON reporting
│   ├── LoginController.java              # Controller for login workflow and window transition
│   └── SmtpController.java               # Controller for managing SMTP settings dialog
├── ui/
│   ├── LoginFrame.java                   # Swing login window for Agent authentication
│   ├── MainDashboardFrame.java           # Single-window dashboard matching ONDA design specifications
│   └── SmtpConfigDialog.java             # Dialog interface for configuring ONDA sender email & SMTP credentials
└── util/
    └── SampleExcelGenerator.java         # Utility to generate initial sample Excel sheet matching ONDA columns
```

---

## 🎯 Key Specifications & Data Flow

### 1. Excel File Structure (`Liste Laissez-Passer 2026`)
- **Header Row Location**: Row 4 in Excel (Index 3 in 0-based POI indexing).
- **Target Columns**:
  - `N°Badge` (Col 0)
  - `Nom et Prenom` (Col 1)
  - `Email` (Col 2)
  - `Organisme` (Col 3)
  - `Délivré par` (Col 4)
  - `D-délivrance` (Col 5)
  - `Durée prévue` (Col 6)
  - `Expiration Calculée` (Col 7)
- **Date Handling**: Supports numeric cell dates and French date strings (`18-juil-2026`, `28-juil-2026`, `14-août-2026`, `25-juin-2026`, etc.).

### 2. Badge Expiration Thresholds & Status Rules
- **Remaining Days Calculation**: `ChronoUnit.DAYS.between(LocalDate.now(), calculatedExpiration)`
- **Status Categories**:
  - `EXPIRED` (🔴 **Red**): `days < 0` (Text: `"Exp"`)
  - `WARNING_RELANCE` (🟡 **Yellow**): `0 <= days <= threshold` (Default: `<= 7 days`; selectable: 7, 15, 30 days) (Text: `"4j"`, `"2j"`, `"0j"`)
  - `VALID` (🟢 **Green**): `days > threshold` (Text: `"OK"`)

### 3. Execution Modes
- **Manual Mode**: Agent selects file, inspects filtered records, and clicks `LANCER (N)` to dispatch emails.
- **Automatic Daemon Mode**: Background thread (`ScheduledExecutorService`) automatically runs daily at **09:00 AM**, parses the Excel sheet, and sends reminder emails for badges matching `<= 7 days` remaining.

### 4. Deduplication & Logging (`sent_emails_log.json`)
- Before sending an email for a badge, `EmailLogService.hasBeenSentToday(badgeNumber)` is checked.
- If an email was already sent today (`SENT`), it is skipped (`SKIPPED`) to prevent spamming holders.

### 5. SMTP & Source Email Configuration
- Built-in `SmtpConfigDialog` accessed via the **`⚙ Config Email / SMTP`** button.
- Configures:
  - Source Email (`smtp.from`) — e.g. `relance.badges.dakhla@onda.ma` or sender address
  - SMTP Credentials (`smtp.username`, `smtp.password`)
  - Server & Port (`smtp.host`, `smtp.port`)
  - Enable/Disable toggle for real SMTP vs Simulation Mode.

---

## 🔍 Verification Plan

### Automated Build Verification
```bash
mvn clean compile
```

### Execution Command
```bash
mvn exec:java
```

### Manual Verification Checklist
1. **Agent Login**: Log in with default credentials (`agent` / `onda2026`).
2. **Excel Loading**: Select Excel file or use default `C:/ONDA/Badges_2026.xlsx`. Verify rows load cleanly with colored status indicators.
3. **Filter Navigation**: Toggle radio filters (`Tous`, `A relancer (< 7j)`, `Expires`) to verify JTable updates.
4. **SMTP Configuration**: Click `⚙ Config Email / SMTP`, update sender credentials, and test manual email relance (`LANCER`).
5. **JSON Log Inspection**: Inspect `sent_emails_log.json` to verify recorded timestamp and badge log entries.
