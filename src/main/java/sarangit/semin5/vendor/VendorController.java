package sarangit.semin5.vendor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class VendorController {
    private static final Map<String, TableSpec> TABLES = Map.of(
            "sites", new TableSpec("work_sites", List.of(
                    "work_name", "site_url", "login_id", "password", "vendor_name",
                    "contact_person", "contact_phone", "remarks"), List.of(
                    "work_name", "site_url", "vendor_name", "contact_phone", "remarks")),
            "vendors", new TableSpec("vendor_info", List.of(
                    "vendor_name", "product", "contact_person", "phone_number", "office_phone",
                    "address", "website", "courier_service"), List.of(
                    "vendor_name", "product", "phone_number", "address", "website", "courier_service")),
            "maintenance", new TableSpec("maintenance_vendors", List.of(
                    "vendor_name", "phone_number"), List.of("vendor_name", "phone_number")),
            "video", new TableSpec("video_monthly_inspections", List.of(
                    "inspection_year", "inspection_month", "change_details"), List.of(
                    "inspection_year", "inspection_month", "change_details")));

    private final JdbcTemplate jdbc;
    private final NuriVoicePdfService pdfService;
    private final DiclePdfService diclePdfService;
    private final FinetechPdfService finetechPdfService;
    private final ItApplicantService applicantService;

    public VendorController(JdbcTemplate jdbc, NuriVoicePdfService pdfService,
                            DiclePdfService diclePdfService, FinetechPdfService finetechPdfService,
                            ItApplicantService applicantService) {
        this.jdbc = jdbc;
        this.pdfService = pdfService;
        this.diclePdfService = diclePdfService;
        this.finetechPdfService = finetechPdfService;
        this.applicantService = applicantService;
    }

    private TableSpec table(String tab) {
        TableSpec spec = TABLES.get(tab);
        if (spec == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return spec;
    }

    @GetMapping("/{tab}")
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable String tab) {
        TableSpec spec = table(tab);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(jdbc.queryForList("SELECT source_row, " + String.join(", ", spec.listColumns())
                        + " FROM " + spec.name() + " ORDER BY source_row"));
    }

    @GetMapping("/{tab}/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String tab, @PathVariable int id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(find(table(tab), id, false));
    }

    private Map<String, Object> find(TableSpec spec, int id, boolean listOnly) {
        List<String> columns = listOnly ? spec.listColumns() : spec.columns();
        return jdbc.queryForList("SELECT source_row, " + String.join(", ", columns)
                + " FROM " + spec.name() + " WHERE source_row = ?", id).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Object value(Map<String, Object> values, String column) {
        Object raw = values.get(column);
        if (raw == null || raw.toString().isBlank()) return null;
        String text = raw.toString().trim();
        if (column.equals("inspection_year") || column.equals("inspection_month")) {
            try {
                int number = Integer.parseInt(text);
                if (column.equals("inspection_month") && (number < 1 || number > 12))
                    throw new NumberFormatException();
                if (column.equals("inspection_year") && (number < 1900 || number > 2100))
                    throw new NumberFormatException();
                return number;
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, column + " has an invalid number");
            }
        }
        int limit = column.equals("remarks") || column.equals("change_details") ? 4000 : 255;
        if (text.length() > limit) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        return text;
    }

    @PostMapping(value = "/{tab}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> create(@PathVariable String tab, @RequestBody Map<String, Object> values) {
        TableSpec spec = table(tab);
        List<Object> parameters = spec.columns().stream().map(column -> value(values, column)).toList();
        String columns = String.join(", ", spec.columns());
        String placeholders = String.join(", ", spec.columns().stream().map(ignored -> "?").toList());
        for (int attempt = 0; attempt < 3; attempt++) {
            Integer id = jdbc.queryForObject("SELECT COALESCE(MAX(source_row), 0) + 1 FROM " + spec.name(), Integer.class);
            List<Object> insert = new ArrayList<>();
            insert.add(id);
            insert.addAll(parameters);
            try {
                jdbc.update("INSERT INTO " + spec.name() + " (source_row, " + columns
                        + ") VALUES (?, " + placeholders + ")", insert.toArray());
                return find(spec, id, true);
            } catch (DuplicateKeyException exception) {
                if (attempt == 2) throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT);
    }

    @PutMapping(value = "/{tab}/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> update(@PathVariable String tab, @PathVariable int id,
                                      @RequestBody Map<String, Object> values) {
        TableSpec spec = table(tab);
        List<Object> parameters = new ArrayList<>();
        for (String column : spec.columns()) parameters.add(value(values, column));
        parameters.add(id);
        String assignments = String.join(", ", spec.columns().stream().map(column -> column + " = ?").toList());
        if (jdbc.update("UPDATE " + spec.name() + " SET " + assignments + " WHERE source_row = ?",
                parameters.toArray()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return find(spec, id, true);
    }

    @DeleteMapping("/{tab}/{id}")
    public void delete(@PathVariable String tab, @PathVariable int id) {
        TableSpec spec = table(tab);
        if (jdbc.update("DELETE FROM " + spec.name() + " WHERE source_row = ?", id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @PostMapping(value = "/vendors/{id}/as-request", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> asRequest(@PathVariable int id, @RequestBody AsRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-IT-Audit-Token", required = false) String token) {
        Map<String, Object> vendor = find(table("vendors"), id, true);
        String vendorName = String.valueOf(vendor.get("vendor_name")).trim();
        if (!"누리보이스(전화기)".equals(vendorName) && !"디클".equals(vendorName))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "지원되는 업체의 신청서만 출력할 수 있습니다");
        String applicantName = applicantService.applicantName(token);
        boolean dicle = "디클".equals(vendorName);
        byte[] pdf = dicle
                ? diclePdfService.create(request.mac(), request.symptoms(), applicantName)
                : pdfService.create(request.deviceName(), request.mac(), request.symptoms(), applicantName);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(dicle ? "dicle-receipt.pdf" : "nuri-voice-as-request.pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    public record AsRequest(String deviceName, String mac, String symptoms) {}

    @PostMapping(value = "/vendors/{id}/finetech-request", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> finetechRequest(@PathVariable int id, @RequestBody QuantityRequest request) {
        Map<String, Object> vendor = find(table("vendors"), id, true);
        if (!"파인텍".equals(String.valueOf(vendor.get("vendor_name")).trim()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "파인텍 양식만 출력할 수 있습니다");
        byte[] pdf = finetechPdfService.create(request.quantityOne(), request.quantityTwo());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("finetech-request.pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    public record QuantityRequest(String quantityOne, String quantityTwo) {}

    private record TableSpec(String name, List<String> columns, List<String> listColumns) {}
}
