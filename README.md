# ONDA Badges Dakhla

ONDA Badges Dakhla is a Java desktop application for tracking badge expiration dates from an Excel workbook. It displays badge status in a Swing dashboard and supports desktop alerts and email reminders.

## Features

- Reads badge records from the first worksheet of an `.xlsx` workbook.
- Calculates expiration dates from delivery dates and planned durations.
- Displays badges as valid, approaching expiration, or expired, with dashboard filters.
- Watches the selected workbook for changes and can display local alerts.
- Sends reminder emails through SMTP, manually or on a daily schedule.
- Stores local alert acknowledgements and email history to reduce repeated notifications.

## Requirements

- Java 17 or later
- Maven 3.8 or later
- Windows is required for the included `run.bat` launcher; the application itself uses Java Swing.

## Build, Test, and Run

Run Maven commands from the `desktop-app` directory:

```shell
cd desktop-app
mvn test
mvn clean package
```

To start the application from source:

```shell
mvn compile exec:java
```

The packaged application JAR is created at:

```text
desktop-app/target/onda-badges-desktop-1.0.0-jar-with-dependencies.jar
```

On Windows, `desktop-app/run.bat` starts that JAR using a Java 17 runtime placed beside the launcher in `desktop-app/jdk-17.0.18.8-hotspot/`.

## Excel Workbook

Choose the workbook in the dashboard. The parser reads the first worksheet and detects the header row among the first 15 rows. It recognizes badge number, holder name, email, organization, issuer, delivery date, and planned duration columns. If headers cannot be matched, it uses the default columns A through G.

Expiration is calculated as delivery date plus the number of days parsed from the planned duration. Supported date values include Excel date cells and common numeric or French date formats; durations may be numeric or text such as `10 JOURS`.

## Configuration and Local Data

The application can use `desktop-app/config.properties` for the selected Excel path and SMTP settings. Typical keys are:

```properties
excel.path=/path/to/badges.xlsx
smtp.host=smtp.example.com
smtp.port=587
smtp.sender=sender@example.com
smtp.password=your-app-password
```

Keep this file private. It contains credentials and is intentionally excluded from version control. Configure SMTP from the application's settings and do not publish passwords, app passwords, recipient data, or private workbooks in the repository. If real credentials have ever been committed or shared, revoke and replace them.

The app also creates local state files for acknowledged desktop alerts (`seen_badges.json`) and sent email history (`sent_emails_history.json`). These are runtime data, not project source.

## Project Structure

```text
desktop-app/
  pom.xml
  run.bat
  src/
    main/java/ma/onda/badges/   Application source
    main/resources/             Default application properties
    test/java/                  Unit tests
```

The project uses Java 17, Maven, Swing, FlatLaf, Apache POI, JavaMail, and JUnit 5. Maven build output is generated under `desktop-app/target/`.
