package ma.onda.badges.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Utility to generate a sample Excel workbook ("Badges_2026.xlsx")
 * matching the exact structure from the ONDA screenshot.
 */
public class SampleExcelGenerator {

    public static void generateSampleExcelIfNotExists(String targetFilePath) {
        File file = new File(targetFilePath);
        if (file.exists()) {
            return;
        }

        try {
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Sheet1");

                // Row 1 (Index 0): Empty
                sheet.createRow(0);

                // Row 2 (Index 1): Title "Liste Laissez-Passer 2026"
                Row titleRow = sheet.createRow(1);
                Cell titleCell = titleRow.createCell(2);
                titleCell.setCellValue("Liste Laissez-Passer 2026");

                // Row 3 (Index 2): Empty
                sheet.createRow(2);

                // Header Row at Index 3 (Row 4 in Excel 1-based indexing)
                Row headerRow = sheet.createRow(3);
                String[] headers = {
                        "N°Badge", "Nom et Prenom", "Email", "Organisme",
                        "Délivré par", "D-délivrance", "Durée prévue"
                };

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                for (int c = 0; c < headers.length; c++) {
                    Cell cell = headerRow.createCell(c);
                    cell.setCellValue(headers[c]);
                    cell.setCellStyle(headerStyle);
                }

                // Sample Data Rows starting at Index 4 (Row 5 in Excel)
                Object[][] sampleData = {
                        {"N°9100", "OUSSAMA TARCHI", "oussama.tr05@gmail.com", "MEGA PRO GUARD", "MO,NAJI", "18-juil-2026", "10 JOURS"},
                        {"N°9101", "IDOUCH MARYAM", "tarchipc@gmail.com", "BUDAS CATERING", "T,AYAD", "20-juil-2026", "7 JOURS"},
                        {"N°9102", "HAJAR BADIL", "pbtarchi@gmail.com", "UMU", "M,BENCHAIRA", "15-juil-2026", "30 JOURS"},
                        {"N°9103", "SAADEDDINE BOURHIM", "tarchioussama84@gmail.com", "STAG", "MO,NAJI", "10-juin-2026", "15 JOURS"},
                        {"N°9104", "MOHAMMED EL MHAYOUI", "gdyddvtjf@gmail.com", "MEGA PRO GUARD", "MO,NAJI", "22-juil-2026", "4 JOURS"},
                        {"N°9105", "MAROUAN BENMOUINA", "dweeuvivesrrec@gmail.com", "RAM", "MO,NAJI", "01-juil-2026", "60 JOURS"}
                };

                for (int r = 0; r < sampleData.length; r++) {
                    Row dataRow = sheet.createRow(4 + r);
                    Object[] rowData = sampleData[r];
                    for (int c = 0; c < rowData.length; c++) {
                        Cell cell = dataRow.createCell(c);
                        cell.setCellValue(rowData[c].toString());
                    }
                }

                for (int c = 0; c < headers.length; c++) {
                    sheet.autoSizeColumn(c);
                }

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    workbook.write(fos);
                }
                System.out.println("Sample Excel file created successfully at: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Could not generate sample Excel file: " + e.getMessage());
        }
    }
}
