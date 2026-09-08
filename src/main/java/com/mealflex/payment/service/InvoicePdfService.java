package com.mealflex.payment.service;

import com.mealflex.customer.entity.CustomerProfile;
import com.mealflex.customer.repository.CustomerProfileRepository;
import com.mealflex.payment.entity.Invoice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Bağımlılıksız, Türkçe karakter destekli tek sayfalık kurumsal fatura üreticisi. */
@Service
@RequiredArgsConstructor
public class InvoicePdfService {
    private static final int WIDTH = 1240;
    private static final int HEIGHT = 1754;
    private static final Color NAVY = new Color(15, 29, 55);
    private static final Color RED = new Color(232, 50, 37);
    private static final Color MUTED = new Color(86, 104, 130);
    private final CustomerProfileRepository customerProfiles;

    public byte[] create(Invoice invoice) {
        CustomerProfile customer = customerProfiles.findByUserId(invoice.getPayment().getCustomer().getId()).orElse(null);
        BufferedImage page = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = page.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, WIDTH, HEIGHT);
            graphics.setColor(RED); graphics.fillRoundRect(75, 70, 95, 95, 24, 24);
            graphics.setColor(Color.WHITE); graphics.setFont(new Font("SansSerif", Font.BOLD, 42));
            graphics.drawString("M", 104, 135);
            graphics.setColor(NAVY); graphics.setFont(new Font("SansSerif", Font.BOLD, 42));
            graphics.drawString("Meal", 195, 123); graphics.setColor(RED); graphics.drawString("Flex", 300, 123);
            graphics.setColor(MUTED); graphics.setFont(new Font("SansSerif", Font.PLAIN, 20));
            graphics.drawString("B2B yemek aboneliği ve catering pazaryeri", 197, 153);

            graphics.setColor(NAVY); graphics.setFont(new Font("SansSerif", Font.BOLD, 48));
            graphics.drawString("KURUMSAL FATURA", 75, 270);
            graphics.setColor(MUTED); graphics.setFont(new Font("SansSerif", Font.PLAIN, 23));
            graphics.drawString("Belge No", 75, 325); graphics.drawString("Düzenleme Tarihi", 720, 325);
            graphics.setColor(NAVY); graphics.setFont(new Font("SansSerif", Font.BOLD, 25));
            graphics.drawString(invoice.getInvoiceNumber(), 75, 360);
            graphics.drawString(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Istanbul"))
                    .format(invoice.getIssuedAt()), 720, 360);
            graphics.setColor(new Color(222, 228, 237)); graphics.fillRect(75, 400, 1090, 2);

            int y = 465;
            y = party(graphics, "ALICI", value(customer == null ? null : customer.getCompanyName(),
                    invoice.getPayment().getCustomer().getFirstName() + " " + invoice.getPayment().getCustomer().getLastName()),
                    "Vergi No: " + value(customer == null ? null : customer.getTaxNumber(), "Belirtilmedi"),
                    "Vergi Dairesi: " + value(customer == null ? null : customer.getTaxOffice(), "Belirtilmedi"),
                    value(customer == null ? null : customer.getInvoiceAddress(), "Fatura adresi belirtilmedi"), 75, y);
            var seller = invoice.getSubscription().getStore().getSeller();
            party(graphics, "HİZMET SAĞLAYAN", value(seller.getCompanyTitle(), invoice.getSubscription().getStore().getName()),
                    "Vergi No: " + value(seller.getTaxNumber(), "Belirtilmedi"),
                    "Vergi Dairesi: " + value(seller.getTaxOffice(), "Belirtilmedi"),
                    invoice.getSubscription().getStore().getName(), 650, 465);

            y = Math.max(y, 690);
            graphics.setColor(new Color(247, 249, 252)); graphics.fillRoundRect(75, y, 1090, 90, 18, 18);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 21)); graphics.setColor(MUTED);
            graphics.drawString("AÇIKLAMA", 100, y + 36); graphics.drawString("TUTAR", 945, y + 36);
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 23)); graphics.setColor(NAVY);
            graphics.drawString(invoice.getSubscription().getMenu().getName() + " - Abonelik #"
                    + invoice.getSubscription().getId(), 100, y + 70);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 25));
            graphics.drawString(amount(invoice.getGrossAmount(), invoice.getCurrency()), 945, y + 70);

            int totalY = y + 160;
            graphics.setColor(MUTED); graphics.setFont(new Font("SansSerif", Font.PLAIN, 23));
            graphics.drawString("Genel toplam", 800, totalY);
            graphics.setColor(RED); graphics.setFont(new Font("SansSerif", Font.BOLD, 36));
            graphics.drawString(amount(invoice.getGrossAmount(), invoice.getCurrency()), 800, totalY + 55);
            graphics.setColor(new Color(222, 228, 237)); graphics.fillRect(75, 1510, 1090, 2);
            graphics.setColor(MUTED); graphics.setFont(new Font("SansSerif", Font.PLAIN, 18));
            graphics.drawString("Bu belge MealFlex üzerindeki ödeme kaydından elektronik olarak üretilmiştir.", 75, 1560);
            graphics.drawString("e-Arşiv/e-Fatura sağlayıcı numarası, sağlayıcı entegrasyonu etkinleştirildiğinde ayrıca gösterilir.", 75, 1592);
            return wrapJpegAsPdf(page);
        } finally {
            graphics.dispose();
        }
    }

    private int party(Graphics2D g, String heading, String name, String tax, String office, String address, int x, int y) {
        g.setColor(RED); g.setFont(new Font("SansSerif", Font.BOLD, 19)); g.drawString(heading, x, y);
        g.setColor(NAVY); g.setFont(new Font("SansSerif", Font.BOLD, 28)); g.drawString(name, x, y + 43);
        g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g.drawString(tax, x, y + 80); g.drawString(office, x, y + 112);
        int lineY = y + 144;
        for (String line : wrap(address, 42)) { g.drawString(line, x, lineY); lineY += 28; }
        return lineY;
    }

    private List<String> wrap(String value, int max) {
        List<String> lines = new ArrayList<>(); StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > max) {
                lines.add(line.toString()); line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' '); line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private byte[] wrapJpegAsPdf(BufferedImage image) {
        try {
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream(); ImageIO.write(image, "jpg", jpeg);
            ByteArrayOutputStream pdf = new ByteArrayOutputStream(); List<Integer> offsets = new ArrayList<>();
            ascii(pdf, "%PDF-1.4\n");
            object(pdf, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
            object(pdf, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
            object(pdf, offsets, 3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im1 5 0 R >> >> /Contents 4 0 R >>");
            byte[] commands = "q 595 0 0 842 0 0 cm /Im1 Do Q".getBytes(StandardCharsets.US_ASCII);
            streamObject(pdf, offsets, 4, "<< /Length " + commands.length + " >>", commands);
            streamObject(pdf, offsets, 5, "<< /Type /XObject /Subtype /Image /Width " + WIDTH + " /Height " + HEIGHT
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.size() + " >>", jpeg.toByteArray());
            int xref = pdf.size(); ascii(pdf, "xref\n0 6\n0000000000 65535 f \n");
            for (int offset : offsets) ascii(pdf, String.format("%010d 00000 n \n", offset));
            ascii(pdf, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
            return pdf.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("Fatura PDF'i üretilemedi.", exception); }
    }

    private void object(ByteArrayOutputStream out, List<Integer> offsets, int id, String body) throws IOException {
        offsets.add(out.size()); ascii(out, id + " 0 obj\n" + body + "\nendobj\n");
    }
    private void streamObject(ByteArrayOutputStream out, List<Integer> offsets, int id, String dictionary, byte[] data) throws IOException {
        offsets.add(out.size()); ascii(out, id + " 0 obj\n" + dictionary + "\nstream\n"); out.write(data); ascii(out, "\nendstream\nendobj\n");
    }
    private void ascii(ByteArrayOutputStream out, String value) throws IOException { out.write(value.getBytes(StandardCharsets.US_ASCII)); }
    private String value(String candidate, String fallback) { return candidate == null || candidate.isBlank() ? fallback : candidate; }
    private String amount(BigDecimal value, String currency) { return value.setScale(2) + " " + currency; }
}
