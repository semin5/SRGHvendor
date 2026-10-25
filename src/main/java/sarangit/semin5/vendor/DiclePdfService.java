package sarangit.semin5.vendor;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DiclePdfService {
    private static final String TEMPLATE = "pdf/dicle-receipt-template.pdf";
    private static final String FONT = "pdf/malgunbd.ttf";

    public byte[] create(String serialNumber, String symptoms, String applicantName) {
        String serial = required(serialNumber, "시리얼번호", 100);
        String description = required(symptoms, "증상 및 점검요청", 800);
        String applicant = required(applicantName, "이름", 40);
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             InputStream fontFile = new ClassPathResource(FONT).getInputStream();
             PDDocument document = Loader.loadPDF(template.readAllBytes())) {
            PDType0Font font = PDType0Font.load(document, fontFile, true);
            try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                // This supplied PDF is rasterized. Keep the preprinted product name intact;
                // replace only the name while preserving the borders and contact details.
                content.setNonStrokingColor(Color.WHITE);
                content.addRect(216.5f, 698f, 110f, 40f); // Preprinted name
                content.fill();
                content.setNonStrokingColor(Color.BLACK);
                drawLine(content, font, applicant, 220f, 715f, 103f, 11.5f);
                drawLineCentered(content, font, serial, 430f, 580f, 607f, 11.5f);
                drawParagraphCentered(content, font, description, 222f, 577f, 586f, 506f, 4);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            document.save(result);
            return result.toByteArray();
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 입력값을 표시할 수 없습니다", exception);
            throw new IllegalStateException("디클 접수증 PDF를 생성하지 못했습니다", exception);
        }
    }

    private String required(String raw, String label, int limit) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || text.length() > limit || text.indexOf('\u0000') >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 입력값을 확인해 주세요");
        return text;
    }

    private void drawLine(PDPageContentStream content, PDType0Font font, String text,
                          float x, float y, float maxWidth, float preferredSize) throws IOException {
        float size = preferredSize;
        while (size > 8 && font.getStringWidth(text) * size / 1000 > maxWidth) size -= 0.5f;
        if (font.getStringWidth(text) * size / 1000 > maxWidth)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입력값이 PDF 칸보다 깁니다");
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private void drawLineCentered(PDPageContentStream content, PDType0Font font, String text,
                                  float left, float right, float y, float preferredSize) throws IOException {
        float size = preferredSize;
        float width = right - left;
        while (size > 8 && font.getStringWidth(text) * size / 1000 > width) size -= 0.5f;
        float textWidth = font.getStringWidth(text) * size / 1000;
        if (textWidth > width)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "시리얼번호가 PDF 칸보다 깁니다");
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset((left + right - textWidth) / 2, y);
        content.showText(text);
        content.endText();
    }

    private void drawParagraphCentered(PDPageContentStream content, PDType0Font font, String text,
                                       float left, float right, float top, float bottom,
                                       int maxLines) throws IOException {
        float size = 11.5f;
        float width = right - left - 12f;
        List<String> lines = wrap(font, text, size, width);
        if (lines.size() > maxLines) {
            size = 10f;
            lines = wrap(font, text, size, width);
        }
        if (lines.size() > maxLines)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "증상이 PDF 칸보다 깁니다");
        float lineHeight = 14f;
        float blockHeight = size + (lines.size() - 1) * lineHeight;
        float firstBaseline = bottom + (top - bottom + blockHeight) / 2 - size * 0.9f;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            float lineWidth = font.getStringWidth(line) * size / 1000;
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset((left + right - lineWidth) / 2, firstBaseline - index * lineHeight);
            content.showText(line);
            content.endText();
        }
    }

    private List<String> wrap(PDType0Font font, String text, float size, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < paragraph.length();) {
                int codePoint = paragraph.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                if (!line.isEmpty() && font.getStringWidth(line + character) * size / 1000 > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(character);
                offset += Character.charCount(codePoint);
            }
            lines.add(line.toString());
        }
        return lines;
    }
}
