package sarangit.semin5.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class VendorControllerTest {
    @Test
    void listDoesNotSelectPasswords() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("source_row", 2, "work_name", "example")));
        VendorController controller = new VendorController(jdbc, mock(NuriVoicePdfService.class), mock(DiclePdfService.class), mock(FinetechPdfService.class), mock(ItApplicantService.class));

        assertThat(controller.list("sites").getBody()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("FROM work_sites", "remarks")
                .doesNotContain("password", "contact_person");
    }

    @Test
    void detailReadsPasswordOnlyWhenRequested() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(2))).thenReturn(List.of(Map.of("source_row", 2, "password", "example")));
        VendorController controller = new VendorController(jdbc, mock(NuriVoicePdfService.class), mock(DiclePdfService.class), mock(FinetechPdfService.class), mock(ItApplicantService.class));

        assertThat(controller.detail("sites", 2).getBody()).containsEntry("password", "example");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(2));
        assertThat(sql.getValue()).contains("password");
    }

    @Test
    void videoListUsesEnglishTableAndColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of(
                "source_row", 2, "inspection_year", 2026, "inspection_month", 1)));
        VendorController controller = new VendorController(jdbc, mock(NuriVoicePdfService.class), mock(DiclePdfService.class), mock(FinetechPdfService.class), mock(ItApplicantService.class));

        assertThat(controller.list("video").getBody()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("FROM video_monthly_inspections")
                .contains("inspection_year", "inspection_month", "change_details");
    }

    @Test
    void vendorListOmitsHiddenColumnsButKeepsAddressAndCourierService() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("source_row", 1, "address", "example")));
        VendorController controller = new VendorController(jdbc, mock(NuriVoicePdfService.class), mock(DiclePdfService.class), mock(FinetechPdfService.class), mock(ItApplicantService.class));

        assertThat(controller.list("vendors").getBody()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("FROM vendor_info", "address", "courier_service")
                .doesNotContain("contact_person", "office_phone");
    }

    @Test
    void asRequestRejectsUnsupportedVendor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NuriVoicePdfService pdf = mock(NuriVoicePdfService.class);
        when(jdbc.queryForList(anyString(), eq(2)))
                .thenReturn(List.of(Map.of("source_row", 2, "vendor_name", "다른 업체")));
        DiclePdfService dicle = mock(DiclePdfService.class);
        VendorController controller = new VendorController(jdbc, pdf, dicle, mock(FinetechPdfService.class), mock(ItApplicantService.class));

        assertThatThrownBy(() -> controller.asRequest(2,
                new VendorController.AsRequest("기기", "00:00:00:00:00:00", "증상"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
        verifyNoInteractions(pdf);
        verifyNoInteractions(dicle);
    }

    @Test
    void asRequestAllowsNuriVoiceVendor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NuriVoicePdfService pdf = mock(NuriVoicePdfService.class);
        when(jdbc.queryForList(anyString(), eq(2)))
                .thenReturn(List.of(Map.of("source_row", 2, "vendor_name", "누리보이스(전화기)")));
        when(pdf.create("전화기", "AA:BB:CC:DD:EE:FF", "통화 불량", "홍길동"))
                .thenReturn("%PDF".getBytes());
        ItApplicantService applicant = mock(ItApplicantService.class);
        when(applicant.applicantName("token")).thenReturn("홍길동");
        VendorController controller = new VendorController(jdbc, pdf, mock(DiclePdfService.class), mock(FinetechPdfService.class), applicant);

        assertThat(controller.asRequest(2, new VendorController.AsRequest(
                "전화기", "AA:BB:CC:DD:EE:FF", "통화 불량"), "token").getBody())
                .isEqualTo("%PDF".getBytes());
    }

    @Test
    void asRequestUsesDicleTemplateForDicleVendor() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        NuriVoicePdfService nuri = mock(NuriVoicePdfService.class);
        DiclePdfService dicle = mock(DiclePdfService.class);
        ItApplicantService applicant = mock(ItApplicantService.class);
        when(jdbc.queryForList(anyString(), eq(3)))
                .thenReturn(List.of(Map.of("source_row", 3, "vendor_name", "디클")));
        when(applicant.applicantName("token")).thenReturn("홍길동");
        when(dicle.create("SN123", "전원이 켜지지 않음", "홍길동"))
                .thenReturn("%PDF".getBytes());
        VendorController controller = new VendorController(jdbc, nuri, dicle, mock(FinetechPdfService.class), applicant);

        assertThat(controller.asRequest(3,
                new VendorController.AsRequest(null, "SN123", "전원이 켜지지 않음"), "token")
                .getBody()).isEqualTo("%PDF".getBytes());
        verifyNoInteractions(nuri);
    }

    @Test
    void finetechRequestUsesOnlyTwoQuantities() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FinetechPdfService finetech = mock(FinetechPdfService.class);
        ItApplicantService applicant = mock(ItApplicantService.class);
        when(jdbc.queryForList(anyString(), eq(6)))
                .thenReturn(List.of(Map.of("source_row", 6, "vendor_name", "파인텍")));
        when(finetech.create("2", "13")).thenReturn("%PDF".getBytes());
        VendorController controller = new VendorController(jdbc, mock(NuriVoicePdfService.class),
                mock(DiclePdfService.class), finetech, applicant);

        assertThat(controller.finetechRequest(6, new VendorController.QuantityRequest("2", "13"))
                .getBody()).isEqualTo("%PDF".getBytes());
        verifyNoInteractions(applicant);
    }
}
