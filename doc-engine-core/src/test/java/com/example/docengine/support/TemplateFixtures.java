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

            CreationHelper helper = wb.getCreationHelper();
            Drawing<?> drawing = sh.createDrawingPatriarch();
            XSSFClientAnchor anchor = (XSSFClientAnchor) helper.createClientAnchor();
            anchor.setCol1(0); anchor.setCol2(2);
            anchor.setRow1(1); anchor.setRow2(3);
            Comment c = drawing.createCellComment(anchor);
            c.setString(helper.createRichTextString(
                "jx:each(items=\"items\", var=\"item\", lastCell=\"C2\")"));
            data.getCell(0).setCellComment(c);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] formulas() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("data");
            sh.createRow(0).createCell(0).setCellValue("${a}");
            sh.createRow(1).createCell(0).setCellValue("${b}");
            sh.createRow(2).createCell(0).setCellFormula("A1+A2");
            wb.write(out);
            return out.toByteArray();
        }
    }
}
