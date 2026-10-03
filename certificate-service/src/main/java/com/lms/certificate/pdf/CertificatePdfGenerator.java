package com.lms.certificate.pdf;

import com.lms.certificate.entity.Certificate;
import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a certificate as a landscape A4 PDF (OpenPDF) with a decorative border and a QR code that points to
 * the public verification page. The PDF is produced in memory on every download; nothing is stored on disk.
 */
@Component
public class CertificatePdfGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final Color NAVY = new Color(25, 42, 86);
    private static final Color GOLD = new Color(191, 144, 0);
    private static final Color GREY = new Color(90, 90, 90);
    private static final int QR_SIZE = 110;

    private final QrCodeGenerator qrCodeGenerator;
    private final String verifyBaseUrl;

    public CertificatePdfGenerator(QrCodeGenerator qrCodeGenerator,
                                   @Value("${certificate.verify-base-url:http://localhost:5173/verify}") String verifyBaseUrl) {
        this.qrCodeGenerator = qrCodeGenerator;
        this.verifyBaseUrl = verifyBaseUrl.endsWith("/") ? verifyBaseUrl.substring(0, verifyBaseUrl.length() - 1)
                : verifyBaseUrl;
    }

    /** URL encoded in the QR code. */
    public String verificationUrl(Certificate certificate) {
        return verifyBaseUrl + "/" + certificate.getVerificationCode();
    }

    public byte[] generate(Certificate certificate) {
        Rectangle pageSize = PageSize.A4.rotate();
        Document document = new Document(pageSize, 60, 60, 70, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle("Certificate of Completion - " + nullSafe(certificate.getCertificateNumber()));
            document.addAuthor("LMS");
            document.open();

            drawBorder(writer.getDirectContentUnder(), pageSize);
            writeBody(document, certificate);
            writeFooter(writer.getDirectContent(), pageSize, certificate);

            document.close();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Could not generate certificate PDF", ex);
        }
        return out.toByteArray();
    }

    private void drawBorder(PdfContentByte canvas, Rectangle page) {
        canvas.saveState();
        // outer thick navy frame
        canvas.setColorStroke(NAVY);
        canvas.setLineWidth(6f);
        canvas.rectangle(20, 20, page.getWidth() - 40, page.getHeight() - 40);
        canvas.stroke();
        // inner thin gold frame
        canvas.setColorStroke(GOLD);
        canvas.setLineWidth(1.5f);
        canvas.rectangle(32, 32, page.getWidth() - 64, page.getHeight() - 64);
        canvas.stroke();
        // corner ornaments
        canvas.setColorFill(GOLD);
        float s = 10f;
        canvas.rectangle(27, 27, s, s);
        canvas.rectangle(page.getWidth() - 27 - s, 27, s, s);
        canvas.rectangle(27, page.getHeight() - 27 - s, s, s);
        canvas.rectangle(page.getWidth() - 27 - s, page.getHeight() - 27 - s, s, s);
        canvas.fill();
        canvas.restoreState();
    }

    private void writeBody(Document document, Certificate c) throws DocumentException {
        document.add(centered("LEARNING MANAGEMENT SYSTEM", font(FontFactory.HELVETICA_BOLD, 12, GOLD), 0, 6));
        document.add(centered("Certificate of Completion", font(FontFactory.TIMES_BOLD, 40, NAVY), 0, 22));
        document.add(centered("This is to certify that", font(FontFactory.TIMES_ITALIC, 16, GREY), 0, 10));
        document.add(centered(nullSafe(c.getStudentName()), font(FontFactory.TIMES_BOLDITALIC, 32, NAVY), 0, 10));
        document.add(centered("has successfully completed the course", font(FontFactory.TIMES_ITALIC, 16, GREY), 0, 10));
        document.add(centered(nullSafe(c.getCourseName()), font(FontFactory.HELVETICA_BOLD, 22, NAVY), 0, 18));

        StringBuilder details = new StringBuilder("Completed on ").append(formatDate(c.getCompletionDate()));
        if (c.getFinalScore() != null) {
            details.append("  |  Final score: ")
                    .append(c.getFinalScore().setScale(2, RoundingMode.HALF_UP).toPlainString()).append('%');
        }
        document.add(centered(details.toString(), font(FontFactory.HELVETICA, 13, GREY), 0, 0));
    }

    private void writeFooter(PdfContentByte canvas, Rectangle page, Certificate c) throws IOException, DocumentException {
        Font label = font(FontFactory.HELVETICA, 10, GREY);
        Font value = font(FontFactory.HELVETICA_BOLD, 11, NAVY);
        float left = 70;
        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                new Phrase("Certificate No.", label), left, 112, 0);
        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                new Phrase(nullSafe(c.getCertificateNumber()), value), left, 97, 0);
        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                new Phrase("Issued on", label), left, 78, 0);
        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                new Phrase(formatDate(c.getIssueDate()), value), left, 63, 0);

        // signature line in the centre
        float centre = page.getWidth() / 2;
        canvas.saveState();
        canvas.setColorStroke(NAVY);
        canvas.setLineWidth(0.8f);
        canvas.moveTo(centre - 90, 85);
        canvas.lineTo(centre + 90, 85);
        canvas.stroke();
        canvas.restoreState();
        ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                new Phrase("LMS Academic Office", label), centre, 70, 0);

        if (c.getVerificationCode() != null) {
            Image qr = Image.getInstance(qrCodeGenerator.generatePng(verificationUrl(c), 300));
            qr.scaleAbsolute(QR_SIZE, QR_SIZE);
            float x = page.getWidth() - 70 - QR_SIZE;
            qr.setAbsolutePosition(x, 62);
            canvas.addImage(qr);
            ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                    new Phrase("Scan to verify", font(FontFactory.HELVETICA, 8, GREY)), x + QR_SIZE / 2, 52, 0);
        }
    }

    private static Paragraph centered(String text, Font font, float before, float after) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
        return p;
    }

    private static Font font(String name, float size, Color color) {
        return FontFactory.getFont(name, size, color);
    }

    private static String formatDate(LocalDateTime date) {
        return date == null ? "-" : DATE.format(date);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
