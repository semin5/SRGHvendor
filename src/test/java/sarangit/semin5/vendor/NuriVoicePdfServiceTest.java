package sarangit.semin5.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class NuriVoicePdfServiceTest {
    @Test
    void acceptsKoreanCharactersInMacField() throws Exception {
        byte[] pdf = new NuriVoicePdfService().create("ㅁㄴㅇ", "ㅁㄴㅇ", "ㅁㄴㅇ", "홍길동",
                LocalDate.of(2026, 10, 8));
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document))
                    .contains("ㅁㄴㅇ", "2026년 10월 8일", "홍길동");
        }
        String previewPath = System.getProperty("vendor.pdf.unicode-preview");
        if (previewPath != null) Files.write(Path.of(previewPath), pdf);
    }

    @Test
    void fillsTemplateWithoutChangingItsPageCount() throws Exception {
        byte[] pdf = new NuriVoicePdfService().create("테스트 전화기", "AA:BB:CC:DD:EE:FF",
                "통화 중 소리가 끊깁니다.\n전원을 다시 켜도 동일합니다.", "홍길동", LocalDate.of(2026, 10, 7));
        assertThat(pdf).startsWith("%PDF".getBytes());
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document))
                    .contains("테스트 전화기", "AA:BB:CC:DD:EE:FF", "통화 중 소리가 끊깁니다.",
                            "2026년 10월 7일", "홍길동");
        }
        String previewPath = System.getProperty("vendor.pdf.preview");
        if (previewPath != null) Files.write(Path.of(previewPath), pdf);
    }
}
