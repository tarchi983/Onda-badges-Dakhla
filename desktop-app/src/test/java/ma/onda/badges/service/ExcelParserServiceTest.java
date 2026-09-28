package ma.onda.badges.service;

import ma.onda.badges.model.Badge;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExcelParserServiceTest {

    private ExcelParserService parserService;

    @BeforeEach
    public void setUp() {
        parserService = new ExcelParserService();
    }

    @Test
    public void testDynamicExpirationCalculation() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Badges");

            // Empty rows
            sheet.createRow(0);
            sheet.createRow(1);
            sheet.createRow(2);

            // Header Row at Index 3 (Row 4 in Excel)
            Row headerRow = sheet.createRow(3);
            String[] headers = {
                    "N°Badge", "Nom et Prenom", "Email", "Organisme",
                    "Délivré par", "D-délivrance", "Durée prévue"
            };
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Data Row 1: 18-juil-2026 + 10 JOURS -> 28-juil-2026
            Row dataRow1 = sheet.createRow(4);
            dataRow1.createCell(0).setCellValue("N°9100");
            dataRow1.createCell(1).setCellValue("OUSSAMA TARCHI");
            dataRow1.createCell(2).setCellValue("oussama@example.com");
            dataRow1.createCell(3).setCellValue("MEGA PRO GUARD");
            dataRow1.createCell(4).setCellValue("MO,NAJI");
            dataRow1.createCell(5).setCellValue("18-juil-2026");
            dataRow1.createCell(6).setCellValue("10 JOURS");

            // Data Row 2: 2026-07-20 (ISO) + 15 JOURS -> 2026-08-04
            Row dataRow2 = sheet.createRow(5);
            dataRow2.createCell(0).setCellValue("N°9101");
            dataRow2.createCell(1).setCellValue("IDOUCH MARYAM");
            dataRow2.createCell(2).setCellValue("maryam@example.com");
            dataRow2.createCell(3).setCellValue("BUDAS CATERING");
            dataRow2.createCell(4).setCellValue("T,AYAD");
            dataRow2.createCell(5).setCellValue("20/07/2026");
            dataRow2.createCell(6).setCellValue("15 JOURS");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);

            try (InputStream is = new ByteArrayInputStream(baos.toByteArray())) {
                List<Badge> badges = parserService.parseExcelStream(is);

                assertEquals(2, badges.size());

                Badge badge1 = badges.get(0);
                assertEquals("N°9100", badge1.getBadgeNumber());
                assertEquals(LocalDate.of(2026, 7, 18), badge1.getDeliveryDate());
                assertEquals("10 JOURS", badge1.getIntendedDuration());
                assertEquals(LocalDate.of(2026, 7, 28), badge1.getCalculatedExpiration());

                Badge badge2 = badges.get(1);
                assertEquals("N°9101", badge2.getBadgeNumber());
                assertEquals(LocalDate.of(2026, 7, 20), badge2.getDeliveryDate());
                assertEquals("15 JOURS", badge2.getIntendedDuration());
                assertEquals(LocalDate.of(2026, 8, 4), badge2.getCalculatedExpiration());
            }
        }
    }
}
