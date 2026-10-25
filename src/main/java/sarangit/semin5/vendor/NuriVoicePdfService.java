package sarangit.semin5.vendor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import java.awt.Color;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NuriVoicePdfService {
    private static final String TEMPLATE = "pdf/nuri-voice-as-template.pdf";
    private static final String KOREAN_FONT = "pdf/malgun.ttf";
    private static final String MAC_FONT = "pdf/arial.ttf";

    public byte[] create(String deviceName, String mac, String symptoms, String applicantName) {
        return create(deviceName, mac, symptoms, applicantName, LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    byte[] create(String deviceName, String mac, String symptoms, String applicantName, LocalDate applicationDate) {
        String device = required(deviceName, "기기명", 80);
        String macNumber = required(mac, "MAC", 80);
        String description = required(symptoms, "증상", 600);
        String applicant = required(applicantName, "신청자", 40);
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             InputStream koreanFontFile = new ClassPathResource(KOREAN_FONT).getInputStream();
             InputStream macFontFile = new ClassPathResource(MAC_FONT).getInputStream();
             PDDocument document = Loader.loadPDF(template.readAllBytes())) {
            PDType0Font koreanFont = PDType0Font.load(document, koreanFontFile, true);
            PDType0Font macFont = PDType0Font.load(document, macFontFile, true);
            PDPage page = document.getPage(0);
            try (PDPageContentStream content = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                drawSingleLineCentered(content, koreanFont, device,
                        147.8f, 272.4f, 658.2f, 631.2f, 8.88f, 0.757f);
                // Arial matches the original template for ordinary MAC addresses, but cannot
                // encode Korean text entered while drafting a request.
                boolean asciiMac = macNumber.codePoints().allMatch(codePoint -> codePoint >= 32 && codePoint <= 126);
                drawSingleLineCentered(content, asciiMac ? macFont : koreanFont, macNumber,
                        313.7f, 542.4f, 644.6f, 631.2f, 8.88f, asciiMac ? 0.790f : 0.757f);
                drawParagraphCentered(content, koreanFont, description, 149, 542, 631, 530, 8);
                // Replace the template's tiny date blanks and move the signature placeholder
                // far enough right to keep the applicant name between the two labels.
                content.setNonStrokingColor(Color.WHITE);
                content.addRect(214.5f, 506.2f, 79.5f, 9.6f);
                content.addRect(320.5f, 506.2f, 132f, 9.6f);
                content.fill();
                content.setNonStrokingColor(Color.BLACK);
                String date = applicationDate.getYear() + "년 " + applicationDate.getMonthValue()
                        + "월 " + applicationDate.getDayOfMonth() + "일";
                drawSingleLineCentered(content, koreanFont, date, 215f, 293.5f,
                        514.4f, 505.5f, 8.2f, 0.757f);
                drawApplicantAndSignature(content, koreanFont, applicant);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            document.save(result);
            return result.toByteArray();
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF 입력값을 표시할 수 없습니다", exception);
            throw new IllegalStateException("누리보이스 신청서 PDF를 생성하지 못했습니다", exception);
        }
    }

    private String required(String raw, String label, int limit) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty() || text.length() > limit || text.indexOf('\u0000') >= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 입력값을 확인해 주세요");
        return text;
    }

    private void drawApplicantAndSignature(PDPageContentStream content, PDType0Font font,
                                           String applicant) throws IOException {
        float size = 8.2f;
        while (size > 7 && font.getStringWidth(applicant) * size / 1000 > 90) size -= 0.5f;
        float applicantWidth = font.getStringWidth(applicant) * size / 1000;
        if (applicantWidth > 90)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신청자 이름이 PDF 칸보다 깁니다");
        float baseline = (514.4f + 505.5f) / 2 + size * (0.5f - 0.757f);
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(325f, baseline);
        content.showText(applicant);
        content.endText();
        content.beginText();
        content.setFont(font, 8.2f);
        content.newLineAtOffset(325f + applicantWidth + 7f, baseline);
        content.showText("(서명)");
        content.endText();
    }

    private void drawSingleLineCentered(PDPageContentStream content, PDType0Font font, String text,
                                        float left, float right, float top, float bottom,
                                        float preferredSize, float glyphTopFraction) throws IOException {
        float size = preferredSize;
        float width = right - left - 14;
        while (size > 7 && font.getStringWidth(text) * size / 1000 > width) size -= 0.5f;
        float textWidth = font.getStringWidth(text) * size / 1000;
        if (textWidth > width)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입력값이 PDF 칸보다 깁니다");
        float x = (left + right - textWidth) / 2;
        // These font-specific ascender fractions center the rendered glyphs, not only their baseline.
        float y = (top + bottom) / 2 + size * (0.5f - glyphTopFraction);
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private void drawParagraphCentered(PDPageContentStream content, PDType0Font font, String text,
                                       float left, float right, float top, float bottom,
                                       int maxLines) throws IOException {
        float size = 8.88f;
        List<String> lines = wrap(font, text, size, right - left - 14);
        if (lines.size() > maxLines) {
            size = 8;
            lines = wrap(font, text, size, right - left - 14);
        }
        if (lines.size() > maxLines)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "증상이 PDF 입력칸보다 깁니다");
        float lineHeight = 12;
        float blockHeight = size + (lines.size() - 1) * lineHeight;
        // Center the actual glyph area, not just the text baselines, inside the symptom cell.
        float firstBaseline = bottom + (top - bottom + blockHeight) / 2 - size * 0.9f;
        content.setFont(font, size);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            float lineWidth = font.getStringWidth(line) * size / 1000;
            float x = (left + right - lineWidth) / 2;
            content.beginText();
            content.newLineAtOffset(x, firstBaseline - index * lineHeight);
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
