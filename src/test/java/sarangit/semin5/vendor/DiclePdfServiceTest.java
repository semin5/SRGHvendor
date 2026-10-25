package sarangit.semin5.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class DiclePdfServiceTest {
    @Test
    void fillsDicleReceiptWithoutChangingTemplatePageCount() throws Exception {
        byte[] pdf = new DiclePdfService().create("SN-12345",
                "전원이 켜지지 않습니다.\n점검 부탁드립니다.", "홍길동");
        assertThat(pdf).startsWith("%PDF".getBytes());
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("SN-12345", "전원이 켜지지 않습니다.", "홍길동");
        }
        String previewPath = System.getProperty("vendor.dicle.pdf.preview");
        if (previewPath != null) Files.write(Path.of(previewPath), pdf);
    }
}
