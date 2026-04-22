// PdfReportGenerator.java
package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfReportGenerator {
    private static final String TAG = "PdfReportGenerator";
    private static final String PDF_DIR = "WorkoutReports";

    // 中文字体
    private static PdfFont chineseFont;
    private static PdfFont normalFont;

    /**
     * 生成PDF报告
     * @param context 上下文
     * @param record 运动记录
     * @param chartBitmap 图表截图
     * @return PDF文件路径，失败返回null
     */
    public static String generateReport(Context context, WorkoutRecord record, Bitmap chartBitmap) {
        File pdfFile = null;
        PdfDocument pdfDoc = null;
        Document document = null;

        try {
            // 初始化字体
            initFonts();

            // 创建PDF目录
            File pdfDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), PDF_DIR);
            if (!pdfDir.exists()) {
                pdfDir.mkdirs();
            }

            // 生成文件名
            String fileName = "report_" + record.getActionName() + "_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date(record.getStartTime())) + ".pdf";
            pdfFile = new File(pdfDir, fileName);

            // 初始化PDF
            PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFile));
            pdfDoc = new PdfDocument(writer);
            document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(50, 50, 50, 50);

            // 添加标题
            addTitle(document, record.getActionName());

            // 添加基本信息表格
            addBasicInfoTable(document, record);

            // 添加相似度统计
            addSimilarityStats(document, record);

            // 添加相似度变化图表
            if (chartBitmap != null && !chartBitmap.isRecycled()) {
                addChartImage(document, chartBitmap);
            }

            // 添加时序数据表格
            addTimeSeriesTable(document, record);

            // 添加页脚
            addFooter(document);

            document.close();
            pdfDoc.close();

            Log.d(TAG, "PDF报告已生成: " + pdfFile.getAbsolutePath());
            return pdfFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "生成PDF失败: " + e.getMessage(), e);
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
            if (pdfDoc != null) {
                try { pdfDoc.close(); } catch (Exception ignored) {}
            }
            return null;
        }
    }

    private static void initFonts() {
        try {
            // 使用标准字体（支持英文和数字）
            normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // 尝试加载中文字体
            try {
                // iText7 的中文字体加载方式
                chineseFont = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H",
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            } catch (Exception e) {
                try {
                    // 备选方案：使用系统字体
                    chineseFont = PdfFontFactory.createFont("/system/fonts/NotoSansCJK-Regular.ttc",
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                } catch (Exception ex) {
                    // 最终备选：使用标准字体（中文可能显示为方框）
                    chineseFont = normalFont;
                    Log.w(TAG, "中文字体加载失败，使用默认字体");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "字体初始化失败: " + e.getMessage());
        }
    }

    private static void addTitle(Document document, String actionName) {
        Paragraph title = new Paragraph("运动报告 - " + actionName)
                .setFontSize(24)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        if (chineseFont != null) {
            title.setFont(chineseFont);
        }
        document.add(title);

        Paragraph dateLine = new Paragraph("生成时间: " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(new DeviceRgb(128, 128, 128))
                .setMarginBottom(30);
        if (chineseFont != null) {
            dateLine.setFont(chineseFont);
        }
        document.add(dateLine);
    }

    private static void addBasicInfoTable(Document document, WorkoutRecord record) {
        Paragraph sectionTitle = new Paragraph("一、基本信息")
                .setFontSize(16)
                .setBold()
                .setMarginBottom(10);
        if (chineseFont != null) {
            sectionTitle.setFont(chineseFont);
        }
        document.add(sectionTitle);

        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(20);

        // 运动类型
        addTableRow(table, "运动类型：", record.getActionName());
        // 开始时间
        addTableRow(table, "开始时间：", record.getFormattedDateTime());
        // 运动时长
        addTableRow(table, "运动时长：", record.getFormattedDuration());
        // 运动次数
        if (record.getCount() > 0) {
            addTableRow(table, "运动次数：", record.getCount() + "次");
        } else {
            addTableRow(table, "运动次数：", "无计数");
        }
        // 平均相似度
        addTableRow(table, "平均相似度：", String.format("%.1f%%", record.getAvgSimilarity() * 100));

        document.add(table);
    }

    private static void addSimilarityStats(Document document, WorkoutRecord record) {
        Paragraph sectionTitle = new Paragraph("二、相似度统计")
                .setFontSize(16)
                .setBold()
                .setMarginBottom(10);
        if (chineseFont != null) {
            sectionTitle.setFont(chineseFont);
        }
        document.add(sectionTitle);

        Table table = new Table(UnitValue.createPercentArray(new float[]{33, 33, 34}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(20);

        // 表头 - 使用 DeviceRgb 代替 Color.WHITE
        DeviceRgb purpleColor = new DeviceRgb(153, 51, 255);
        DeviceRgb whiteColor = new DeviceRgb(255, 255, 255);

        Cell headerCell1 = new Cell()
                .setBackgroundColor(purpleColor)
                .setFontColor(whiteColor)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        if (chineseFont != null) {
            headerCell1.setFont(chineseFont);
        }
        headerCell1.add(new Paragraph("项目"));
        table.addCell(headerCell1);

        Cell headerCell2 = new Cell()
                .setBackgroundColor(purpleColor)
                .setFontColor(whiteColor)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        if (chineseFont != null) {
            headerCell2.setFont(chineseFont);
        }
        headerCell2.add(new Paragraph("数值"));
        table.addCell(headerCell2);

        Cell headerCell3 = new Cell()
                .setBackgroundColor(purpleColor)
                .setFontColor(whiteColor)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER);
        if (chineseFont != null) {
            headerCell3.setFont(chineseFont);
        }
        headerCell3.add(new Paragraph("评级"));
        table.addCell(headerCell3);

        // 平均相似度
        float avgPercent = record.getAvgSimilarity() * 100;
        addStatRow(table, "平均相似度", String.format("%.1f%%", avgPercent), getRating(avgPercent));

        // 最高相似度
        float maxPercent = record.getMaxSimilarity() * 100;
        addStatRow(table, "最高相似度", String.format("%.1f%%", maxPercent), getRating(maxPercent));

        // 最低相似度
        float minPercent = record.getMinSimilarity() * 100;
        addStatRow(table, "最低相似度", String.format("%.1f%%", minPercent), getRating(minPercent));

        document.add(table);
    }

    private static void addStatRow(Table table, String label, String value, String rating) {
        Cell labelCell = new Cell().add(new Paragraph(label));
        Cell valueCell = new Cell().add(new Paragraph(value));
        Cell ratingCell = new Cell().add(new Paragraph(rating));

        if (chineseFont != null) {
            labelCell.setFont(chineseFont);
            valueCell.setFont(chineseFont);
            ratingCell.setFont(chineseFont);
        }

        table.addCell(labelCell);
        table.addCell(valueCell);
        table.addCell(ratingCell);
    }

    private static String getRating(float percent) {
        if (percent >= 85) return "优秀";
        if (percent >= 70) return "良好";
        if (percent >= 60) return "及格";
        return "需改进";
    }

    private static void addChartImage(Document document, Bitmap chartBitmap) throws Exception {
        Paragraph sectionTitle = new Paragraph("三、相似度变化曲线")
                .setFontSize(16)
                .setBold()
                .setMarginBottom(10);
        if (chineseFont != null) {
            sectionTitle.setFont(chineseFont);
        }
        document.add(sectionTitle);

        Paragraph note = new Paragraph("横轴：时间（秒） | 纵轴：相似度（%）")
                .setFontSize(10)
                .setFontColor(new DeviceRgb(128, 128, 128))
                .setMarginBottom(8);
        if (chineseFont != null) {
            note.setFont(chineseFont);
        }
        document.add(note);

        // 将Bitmap转换为PDF Image
        File tempFile = File.createTempFile("chart", ".png");
        FileOutputStream fos = new FileOutputStream(tempFile);
        chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();

        Image image = new Image(com.itextpdf.io.image.ImageDataFactory.create(tempFile.getAbsolutePath()));
        image.setAutoScale(true);
        image.setMarginBottom(20);
        document.add(image);

        tempFile.delete();
    }

    private static void addTimeSeriesTable(Document document, WorkoutRecord record) {
        List<Float> similarities = record.getSimilarities();
        if (similarities == null || similarities.isEmpty()) {
            return;
        }

        Paragraph sectionTitle = new Paragraph("四、时序数据详情")
                .setFontSize(16)
                .setBold()
                .setMarginBottom(10);
        if (chineseFont != null) {
            sectionTitle.setFont(chineseFont);
        }
        document.add(sectionTitle);

        Paragraph note = new Paragraph("以下为运动过程中每帧的相似度变化数据")
                .setFontSize(10)
                .setFontColor(new DeviceRgb(128, 128, 128))
                .setMarginBottom(8);
        if (chineseFont != null) {
            note.setFont(chineseFont);
        }
        document.add(note);

        // 创建表格，每行显示5个数据点
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 20, 20, 20, 20}));
        table.setWidth(UnitValue.createPercentValue(100));

        // 表头颜色
        DeviceRgb purpleColor = new DeviceRgb(153, 51, 255);
        DeviceRgb whiteColor = new DeviceRgb(255, 255, 255);

//        // 表头
//        for (int i = 1; i <= 5; i++) {
//            Cell headerCell = new Cell()
//                    .setBackgroundColor(purpleColor)
//                    .setFontColor(whiteColor)
//                    .setBold()
//                    .setTextAlignment(TextAlignment.CENTER);
//            if (chineseFont != null) {
//                headerCell.setFont(chineseFont);
//            }
//            headerCell.add(new Paragraph("第" + i + "秒"));
//            table.addCell(headerCell);
//        }

        // 填充数据（每5秒一行）
        int rowCount = (similarities.size() + 4) / 5;
        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col < 5; col++) {
                int index = row * 5 + col;
                Cell dataCell = new Cell().setTextAlignment(TextAlignment.CENTER);
                if (chineseFont != null) {
                    dataCell.setFont(chineseFont);
                }
                if (index < similarities.size()) {
                    float percent = similarities.get(index) * 100;
                    String value = String.format("%.1f%%", percent);
                    dataCell.add(new Paragraph(value));
                } else {
                    dataCell.add(new Paragraph("--"));
                }
                table.addCell(dataCell);
            }
        }

        document.add(table);

        // 添加统计摘要
        Paragraph summary = new Paragraph("数据统计：共 " + similarities.size() + " 个数据点" )
                .setFontSize(10)
                .setFontColor(new DeviceRgb(128, 128, 128));
        if (chineseFont != null) {
            summary.setFont(chineseFont);
        }
        document.add(summary);
    }

    private static void addFooter(Document document) {
        Paragraph footer = new Paragraph("本报告由智能运动姿态分析系统生成\n如有疑问请联系客服")
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(new DeviceRgb(128, 128, 128))
                .setMarginTop(30);
        if (chineseFont != null) {
            footer.setFont(chineseFont);
        }
        document.add(footer);
    }

    private static void addTableRow(Table table, String label, String value) {
        DeviceRgb lightGray = new DeviceRgb(245, 245, 245);

        Cell labelCell = new Cell().add(new Paragraph(label))
                .setBackgroundColor(lightGray)
                .setBold();
        Cell valueCell = new Cell().add(new Paragraph(value));

        if (chineseFont != null) {
            labelCell.setFont(chineseFont);
            valueCell.setFont(chineseFont);
        }

        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}