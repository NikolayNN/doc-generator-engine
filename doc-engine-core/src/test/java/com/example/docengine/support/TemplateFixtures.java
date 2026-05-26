package com.example.docengine.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class TemplateFixtures {

    private TemplateFixtures() {}

    public static byte[] simpleFields() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("data");
            Row row = sh.createRow(0);
            row.createCell(0).setCellValue("${greeting}");
            row.createCell(1).setCellValue("${name}");
            addAreaComment(wb, sh, row.getCell(0), 0, 0, 1, 1, "jx:area(lastCell=\"B1\")");
            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] tableEach() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("data");

            Row header = sh.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Qty");
            header.createCell(2).setCellValue("Total");

            Row data = sh.createRow(1);
            data.createCell(0).setCellValue("${item.name}");
            data.createCell(1).setCellValue("${item.qty}");
            Cell totalCell = data.createCell(2);
            totalCell.setCellValue("${item.qty * item.price}");

            Row totals = sh.createRow(2);
            totals.createCell(0).setCellValue("Total");
            totals.createCell(2).setCellFormula("SUM(C2:C2)");

            // Outer area on A1
            addAreaComment(wb, sh, header.getCell(0), 0, 0, 2, 3, "jx:area(lastCell=\"C3\")");
            // jx:each on A2
            addAreaComment(wb, sh, data.getCell(0), 0, 1, 2, 3,
                "jx:each(items=\"items\", var=\"item\", lastCell=\"C2\")");

            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] formulas() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("data");
            Row r0 = sh.createRow(0); r0.createCell(0).setCellValue("${a}");
            Row r1 = sh.createRow(1); r1.createCell(0).setCellValue("${b}");
            Row r2 = sh.createRow(2); r2.createCell(0).setCellFormula("A1+A2");
            addAreaComment(wb, sh, r0.getCell(0), 0, 0, 0, 2, "jx:area(lastCell=\"A3\")");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void addAreaComment(Workbook wb, Sheet sh, Cell cell,
                                       int col1, int row1, int col2, int row2,
                                       String text) {
        CreationHelper helper = wb.getCreationHelper();
        Drawing<?> drawing = sh.createDrawingPatriarch();
        XSSFClientAnchor anchor = (XSSFClientAnchor) helper.createClientAnchor();
        anchor.setCol1(col1); anchor.setCol2(col2);
        anchor.setRow1(row1); anchor.setRow2(row2);
        Comment c = drawing.createCellComment(anchor);
        c.setString(helper.createRichTextString(text));
        cell.setCellComment(c);
    }
}
