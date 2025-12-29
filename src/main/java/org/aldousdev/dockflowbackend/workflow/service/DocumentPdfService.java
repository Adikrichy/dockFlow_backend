package org.aldousdev.dockflowbackend.workflow.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Сервис для обработки PDF документов
 * Добавляет watermarks, подписи, шаблоны
 */
@Service
@Slf4j
public class DocumentPdfService {

    /**
     * Добавляет watermark на все страницы PDF
     */
    public byte[] addWatermark(byte[] pdfBytes, String watermarkText) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputStream), new PdfWriter(outputStream));
            Document document = new Document(pdfDoc);

            PdfFont font = PdfFontFactory.createFont();

            // Добавляем watermark на каждую страницу
            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfCanvas canvas = new PdfCanvas(pdfDoc.getPage(i));

                // Настройки watermark
                canvas.saveState();
                canvas.setFillColor(ColorConstants.LIGHT_GRAY);

                // Поворачиваем текст на 45 градусов
                canvas.concatMatrix(1, 0, 0, 1, 0, 0);

                // Добавляем watermark в центр страницы
                Paragraph watermark = new Paragraph(watermarkText)
                        .setFont(font)
                        .setFontSize(50)
                        .setOpacity(0.3f)
                        .setTextAlignment(TextAlignment.CENTER);

                document.showTextAligned(watermark,
                        pdfDoc.getPage(i).getPageSize().getWidth() / 2,
                        pdfDoc.getPage(i).getPageSize().getHeight() / 2,
                        i, TextAlignment.CENTER, VerticalAlignment.MIDDLE, 0.3f);

                canvas.restoreState();
            }

            document.close();
            pdfDoc.close();

            log.info("Watermark '{}' added to PDF", watermarkText);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error adding watermark to PDF", e);
            throw new IOException("Failed to add watermark: " + e.getMessage(), e);
        }
    }

    /**
     * Добавляет watermark из файла
     */
    public void addWatermark(Path inputPath, Path outputPath, String watermarkText) throws IOException {
        byte[] inputBytes = Files.readAllBytes(inputPath);
        byte[] outputBytes = addWatermark(inputBytes, watermarkText);
        Files.write(outputPath, outputBytes);
    }

    /**
     * Добавляет цифровую подпись (placeholder для будущей реализации)
     * В реальной реализации здесь будет интеграция с электронными подписями
     */
    public byte[] addDigitalSignature(byte[] pdfBytes, String signatureText, String signerName) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PdfDocument pdfDoc = new PdfDocument(new PdfReader(inputStream), new PdfWriter(outputStream));
            Document document = new Document(pdfDoc);

            PdfFont font = PdfFontFactory.createFont();

            // Добавляем подпись на последнюю страницу
            int lastPage = pdfDoc.getNumberOfPages();
            PdfCanvas canvas = new PdfCanvas(pdfDoc.getPage(lastPage));

            // Создаем текст подписи
            String signatureLine = "Digitally signed by: " + signerName + " on " +
                    java.time.LocalDateTime.now().toString();

            Paragraph signature = new Paragraph(signatureLine)
                    .setFont(font)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.LEFT);

            // Добавляем подпись внизу страницы
            document.showTextAligned(signature,
                    50, 50, lastPage, TextAlignment.LEFT, VerticalAlignment.BOTTOM, 1.0f);

            document.close();
            pdfDoc.close();

            log.info("Digital signature added by: {}", signerName);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error adding digital signature to PDF", e);
            throw new IOException("Failed to add signature: " + e.getMessage(), e);
        }
    }

    /**
     * Создает PDF из шаблона с данными (placeholder)
     */
    public byte[] generateFromTemplate(String templateName, java.util.Map<String, Object> data) throws IOException {
        // Placeholder для генерации PDF из шаблонов
        // В реальной реализации здесь будет интеграция с шаблонизаторами
        // типа JasperReports, iText templates, или Apache PDFBox

        log.info("Generating PDF from template: {} with data: {}", templateName, data.keySet());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Добавляем заголовок
            document.add(new Paragraph("Generated Document")
                    .setFontSize(20)
                    .setTextAlignment(TextAlignment.CENTER));

            // Добавляем данные
            for (var entry : data.entrySet()) {
                document.add(new Paragraph(entry.getKey() + ": " + entry.getValue()));
            }

            document.close();
            pdfDoc.close();

            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF from template", e);
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет, является ли файл валидным PDF
     */
    public boolean isValidPdf(byte[] pdfBytes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes)) {
            PdfReader reader = new PdfReader(inputStream);
            PdfDocument pdfDoc = new PdfDocument(reader);
            int pages = pdfDoc.getNumberOfPages();
            pdfDoc.close();

            return pages > 0;

        } catch (Exception e) {
            log.debug("Invalid PDF file: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получает количество страниц в PDF
     */
    public int getPageCount(byte[] pdfBytes) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes)) {
            PdfReader reader = new PdfReader(inputStream);
            PdfDocument pdfDoc = new PdfDocument(reader);
            int pages = pdfDoc.getNumberOfPages();
            pdfDoc.close();

            return pages;

        } catch (Exception e) {
            log.error("Error getting PDF page count", e);
            throw new IOException("Failed to get page count: " + e.getMessage(), e);
        }
    }

    /**
     * Получает количество страниц в PDF из файла
     */
    public int getPageCount(Path pdfPath) throws IOException {
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        return getPageCount(pdfBytes);
    }

    /**
     * Сжимает PDF (placeholder для будущей реализации)
     */
    public byte[] compressPdf(byte[] pdfBytes) throws IOException {
        // Placeholder для сжатия PDF
        // В реальной реализации здесь будет оптимизация PDF
        log.info("PDF compression requested but not implemented yet");
        return pdfBytes; // Возвращаем без изменений
    }
}
