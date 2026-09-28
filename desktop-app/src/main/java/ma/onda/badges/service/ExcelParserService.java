package ma.onda.badges.service;

import ma.onda.badges.model.Badge;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service responsible for parsing badge records from the ONDA Excel workbook ("Liste Laissez-Passer 2026").
 * Header is at Row Index 3 (Row 4 in Excel 1-based indexing).
 */
public class ExcelParserService {

    // Row 4 in Excel = Index 3 in 0-based POI (fallback default)
    private static final int HEADER_ROW_INDEX = 3; 

    // Default fallback column indices matching standard Row 4 of Excel sheet
    private static final int COL_BADGE_NO = 0;      // N°Badge (A)
    private static final int COL_FULL_NAME = 1;     // Nom et Prenom (B)
    private static final int COL_EMAIL = 2;         // Email (C)
    private static final int COL_ORGANISME = 3;     // Organisme (D)
    private static final int COL_ISSUED_BY = 4;     // Délivré par (E)
    private static final int COL_DELIVERY_DATE = 5; // D-délivrance (F)
    private static final int COL_DURATION = 6;      // Durée prévue (G)

    private static final String KEY_BADGE = "BADGE";
    private static final String KEY_FULL_NAME = "FULL_NAME";
    private static final String KEY_EMAIL = "EMAIL";
    private static final String KEY_ORGANISME = "ORGANISME";
    private static final String KEY_ISSUED_BY = "ISSUED_BY";
    private static final String KEY_DELIVERY_DATE = "DELIVERY_DATE";
    private static final String KEY_DURATION = "DURATION";

    private static final Map<String, String> FRENCH_MONTH_MAP = Map.ofEntries(
            Map.entry("janv", "01"), Map.entry("janvier", "01"),
            Map.entry("févr", "02"), Map.entry("fevr", "02"), Map.entry("février", "02"),
            Map.entry("mars", "03"),
            Map.entry("avr", "04"), Map.entry("avril", "04"),
            Map.entry("mai", "05"),
            Map.entry("juin", "06"),
            Map.entry("juil", "07"), Map.entry("juillet", "07"),
            Map.entry("août", "08"), Map.entry("aout", "08"),
            Map.entry("sept", "09"), Map.entry("septembre", "09"),
            Map.entry("oct", "10"), Map.entry("octobre", "10"),
            Map.entry("nov", "11"), Map.entry("novembre", "11"),
            Map.entry("déc", "12"), Map.entry("dec", "12"), Map.entry("décembre", "12")
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.FRENCH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MMM/yyyy").toFormatter(Locale.FRENCH)
    );

    /**
     * Parses the Excel file from the given file path.
     */
    public List<Badge> parseExcelFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Le fichier Excel spécifié n'existe pas : " + filePath);
        }
        try (InputStream is = new FileInputStream(file)) {
            return parseExcelStream(is);
        }
    }

    /**
     * Parses the Excel workbook stream.
     */
    public List<Badge> parseExcelStream(InputStream inputStream) throws Exception {
        List<Badge> badges = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return badges;
            }

            int headerRowIndex = findHeaderRowIndex(sheet);
            Row headerRow = sheet.getRow(headerRowIndex);
            Map<String, Integer> columnMap = buildColumnMap(headerRow);

            int lastRowNum = sheet.getLastRowNum();

            // Iterate starting from row right after detected header
            for (int rowIndex = headerRowIndex + 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                Badge badge = parseRowToBadge(row, columnMap);
                if (badge != null && badge.getBadgeNumber() != null && !badge.getBadgeNumber().isBlank()) {
                    badges.add(badge);
                }
            }
        }

        return badges;
    }

    private int findHeaderRowIndex(Sheet sheet) {
        int maxRowsToScan = Math.min(sheet.getLastRowNum(), 14);
        for (int r = 0; r <= maxRowsToScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null || isRowEmpty(row)) {
                continue;
            }
            int matches = 0;
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                String text = getCellStringValue(cell).toLowerCase();
                if (text.contains("badge") || text.contains("prenom") || text.contains("nom")
                        || text.contains("email") || text.contains("organisme") || text.contains("délivrance")
                        || text.contains("delivrance") || text.contains("durée") || text.contains("duree")) {
                    matches++;
                }
            }
            if (matches >= 2) {
                return r;
            }
        }
        return HEADER_ROW_INDEX;
    }

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null || headerRow.getFirstCellNum() < 0) {
            return map;
        }

        for (int c = headerRow.getFirstCellNum(); c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell == null) continue;
            String text = getCellStringValue(cell).toLowerCase().trim();
            if (text.isBlank()) continue;

            if (text.contains("badge") && !map.containsKey(KEY_BADGE)) {
                map.put(KEY_BADGE, c);
            } else if ((text.contains("prenom") || text.contains("nom")) && !map.containsKey(KEY_FULL_NAME)) {
                map.put(KEY_FULL_NAME, c);
            } else if (text.contains("email") && !map.containsKey(KEY_EMAIL)) {
                map.put(KEY_EMAIL, c);
            } else if (text.contains("organisme") && !map.containsKey(KEY_ORGANISME)) {
                map.put(KEY_ORGANISME, c);
            } else if ((text.contains("d-délivrance") || text.contains("délivrance") || text.contains("delivrance")) && !map.containsKey(KEY_DELIVERY_DATE)) {
                map.put(KEY_DELIVERY_DATE, c);
            } else if ((text.contains("délivré") || text.contains("delivre")) && !map.containsKey(KEY_ISSUED_BY)) {
                map.put(KEY_ISSUED_BY, c);
            } else if ((text.contains("durée") || text.contains("duree")) && !map.containsKey(KEY_DURATION)) {
                map.put(KEY_DURATION, c);
            }
        }
        return map;
    }

    private Badge parseRowToBadge(Row row, Map<String, Integer> colMap) {
        int badgeNoIdx = colMap.getOrDefault(KEY_BADGE, COL_BADGE_NO);
        int fullNameIdx = colMap.getOrDefault(KEY_FULL_NAME, COL_FULL_NAME);
        int emailIdx = colMap.getOrDefault(KEY_EMAIL, COL_EMAIL);
        int organismeIdx = colMap.getOrDefault(KEY_ORGANISME, COL_ORGANISME);
        int issuedByIdx = colMap.getOrDefault(KEY_ISSUED_BY, COL_ISSUED_BY);
        int deliveryDateIdx = colMap.getOrDefault(KEY_DELIVERY_DATE, COL_DELIVERY_DATE);
        int durationIdx = colMap.getOrDefault(KEY_DURATION, COL_DURATION);

        String badgeNo = getCellStringValue(row.getCell(badgeNoIdx));
        String fullName = getCellStringValue(row.getCell(fullNameIdx));
        String email = getCellStringValue(row.getCell(emailIdx));
        String organisme = getCellStringValue(row.getCell(organismeIdx));
        String issuedBy = getCellStringValue(row.getCell(issuedByIdx));
        LocalDate deliveryDate = getCellDateValue(row.getCell(deliveryDateIdx));
        Cell durationCell = row.getCell(durationIdx);
        String duration = getCellStringValue(durationCell);

        // Skip header repeat or blank rows
        if ((badgeNo == null || badgeNo.isBlank()) && (fullName == null || fullName.isBlank())) {
            return null;
        }
        if ("N°Badge".equalsIgnoreCase(badgeNo) || "Nom et Prenom".equalsIgnoreCase(fullName)) {
            return null;
        }

        // Dynamically calculate expiration date: Expiration Date = D-délivrance + Durée prévue (in days)
        Integer durationDays = parseDurationInDays(duration, durationCell);
        LocalDate expirationDate = null;
        if (deliveryDate != null && durationDays != null) {
            expirationDate = deliveryDate.plusDays(durationDays);
        }

        return new Badge(
                badgeNo != null ? badgeNo.trim() : "",
                fullName != null ? fullName.trim() : "",
                email != null ? email.trim() : "",
                organisme != null ? organisme.trim() : "",
                issuedBy != null ? issuedBy.trim() : "",
                deliveryDate,
                duration != null ? duration.trim() : "",
                expirationDate
        );
    }

    /**
     * Extracts numeric integer days from duration string (e.g., "15 JOURS" -> 15) or numeric cell.
     */
    private Integer parseDurationInDays(String durationStr, Cell cell) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        if (durationStr == null || durationStr.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(durationStr);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = getLocalDateFromCell(cell);
                    return date != null ? date.toString() : "";
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == (long) numValue) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    private LocalDate getCellDateValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return getLocalDateFromCell(cell);
        } else if (cell.getCellType() == CellType.STRING || cell.getCellType() == CellType.FORMULA) {
            String text = getCellStringValue(cell).trim();
            if (text.isBlank()) {
                return null;
            }
            return parseFrenchOrStandardDate(text);
        }
        return null;
    }

    private LocalDate parseFrenchOrStandardDate(String text) {
        // First try standard formatters
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Try parsing French month string e.g. "18-juil-2026", "28-juil-2026", "14-août-2026"
        try {
            String normalized = text.toLowerCase().replace(".", "");
            String[] parts = normalized.split("[-/ ]");
            if (parts.length == 3) {
                String dayStr = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String monthPart = parts[1];
                String yearStr = parts[2];

                String monthNum = FRENCH_MONTH_MAP.get(monthPart);
                if (monthNum != null) {
                    String isoDateStr = yearStr + "-" + monthNum + "-" + dayStr;
                    return LocalDate.parse(isoDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private LocalDate getLocalDateFromCell(Cell cell) {
        try {
            Date date = cell.getDateCellValue();
            if (date != null) {
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception e) {
            // Fallback
        }
        return null;
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellStringValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
