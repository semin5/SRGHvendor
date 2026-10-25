package sarangit.semin5.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FinetechPdfServiceTest {
    @Test
    void fillsTheTwoQuantityBlanks() throws Exception {
        byte[] pdf = new FinetechPdfService().create("2", "13");
        assertThat(pdf).startsWith("%PDF".getBytes());
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains("02", "13");
        }
        String previewPath = System.getProperty("vendor.finetech.pdf.preview");
        if (previewPath != null) Files.write(Path.of(previewPath), pdf);
    }

    @Test
    void emptyQuantitiesDefaultToDoubleZero() throws Exception {
        byte[] pdf = new FinetechPdfService().create("", null);
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document)).contains("00");
        }
        String previewPath = System.getProperty("vendor.finetech.empty-preview");
        if (previewPath != null) Files.write(Path.of(previewPath), pdf);
    }

    @Test
    void rejectsNonNumericQuantities() {
        assertThatThrownBy(() -> new FinetechPdfService().create("1.5", "2"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> new FinetechPdfService().create("2", "ABC"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
