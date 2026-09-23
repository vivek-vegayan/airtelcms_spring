package com.vegayan.airtelmanagement.common.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

/**
 * Shared look for every generated .xlsx - dark-blue header band, bordered body,
 * auto-filter, frozen header row, fixed column widths.
 *
 * <p>Lifted verbatim out of ImpactBatchFileService (which had the only copy)
 * when the CAB service-impact export needed the same formatting, so the two
 * exports cannot drift apart. Behaviour is unchanged from that original.
 */
public final class ExcelStyleUtils {

    /** Wide enough for the circuit-id / MO-string values these exports carry. */
    public static final int COLUMN_WIDTH = 5000;

    private ExcelStyleUtils() {
    }

    public static CellStyle createHeaderStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();

        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    public static CellStyle createBodyStyle(Workbook workbook) {

        CellStyle style = workbook.createCellStyle();

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * Auto-filter across the used range, freeze the header row, and set every
     * column to {@link #COLUMN_WIDTH} - SXSSF flushes rows to disk, so
     * autoSizeColumn is not available.
     */
    public static void applySheetFeatures(Sheet sheet, int totalRows, int totalColumns) {

        int lastRow = Math.max(totalRows - 1, 0);
        int lastCol = Math.max(totalColumns - 1, 0);

        if (totalRows > 0 && totalColumns > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, lastCol));
        }

        sheet.createFreezePane(0, 1);

        for (int i = 0; i < totalColumns; i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTH);
        }
    }

    /** Strips stray quotes and surrounding whitespace out of a raw CSV field. */
    public static String sanitize(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replace("\"", "").trim();
    }
}
