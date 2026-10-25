package sarangit.semin5.vendor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinetechPdfService {
    private static final String TEMPLATE = "pdf/finetech-form-template.pdf";
    private static final String FONT = "pdf/arial.ttf";

    public byte[] create(String quantityOne, String quantityTwo) {
        String one = quantity(quantityOne);
        String two = quantity(quantityTwo);
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             InputStream fontFile = new ClassPathResource(FONT).getInputStream();
             PDDocument document = Loader.loadPDF(template.readAllBytes())) {
            PDType0Font font = PDType0Font.load(document, fontFile, true);
            try (PDPageContentStream content = new PDPageContentStream(document, document.getPage(0),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                // The original form has four spaces between ':' and 'ea' in each quantity row.
                drawQuantity(content, font, one, 619.1f);
                drawQuantity(content, font, two, 595.5f);
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            document.save(result);
            return result.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("파인텍 양식 PDF를 생성하지 못했습니다", exception);
        }
    }

    private String quantity(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "00";
        if (!value.matches("[0-9]{1,4}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수량은 1~4자리 숫자로 입력해 주세요");
        return value.length() == 1 ? "0" + value : value;
    }

    private void drawQuantity(PDPageContentStream content, PDType0Font font,
                              String value, float baseline) throws IOException {
        float size = 9.9f;
        float availableWidth = 17f;
        while (font.getStringWidth(value) * size / 1000 > availableWidth && size > 7.5f)
            size -= 0.5f;
        float textWidth = font.getStringWidth(value) * size / 1000;
        if (textWidth > availableWidth)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수량이 PDF 입력칸보다 깁니다");
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset((371.5f + 388.5f - textWidth) / 2, baseline);
        content.showText(value);
        content.endText();
    }
}
