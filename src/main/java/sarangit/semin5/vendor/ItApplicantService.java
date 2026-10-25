package sarangit.semin5.vendor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItApplicantService {
    private final ObjectMapper mapper;
    private final URI identityUri;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public ItApplicantService(ObjectMapper mapper,
            @Value("${app.audit-identity-url:http://127.0.0.1:13013/it/api/auth/audit-identity}") String identityUrl) {
        this.mapper = mapper;
        this.identityUri = URI.create(identityUrl);
    }

    public String applicantName(String token) {
        if (token == null || token.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "IT에 로그인한 뒤 신청서를 출력해 주세요");
        try {
            HttpRequest request = HttpRequest.newBuilder(identityUri).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + token.trim()).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "IT 로그인 정보를 확인할 수 없습니다");
            var identity = mapper.readTree(response.body());
            String username = identity.path("username").asText("").trim();
            String name = identity.path("displayName").asText("").trim();
            if (username.isBlank())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "IT 로그인 정보를 확인할 수 없습니다");
            return name.isBlank() ? username : name;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "IT 로그인 검증 서버에 연결할 수 없습니다", exception);
        }
    }
}
