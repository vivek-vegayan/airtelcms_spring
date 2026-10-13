package com.vegayan.airtelmanagement.cfm.service;

import com.vegayan.airtelmanagement.cfm.dto.CfmCrqDetailsDto;
import com.vegayan.airtelmanagement.common.exception.BusinessException;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CfmCrqDetailsService extends BaseService {

    private static final String CFM_CRQ_DETAILS_PATH = "/cfm-mise/cm-api/crqDetails";

    private final SessionFactory<?> sessionFactory;

    @Value("${cfm.sit-url}")
    private String cfmSitUrl;

    @Value("${cfm.prod-url}")
    private String cfmProdUrl;

    @Value("${cfm.env}")
    private String cfmEnv;

    private String getCfmBaseUrl() {
        return "prod".equalsIgnoreCase(cfmEnv) ? cfmProdUrl : cfmSitUrl;
    }


    private WebClient webClient;

    @PostConstruct
    public void init() {
        try {
            this.webClient = SslWebClientUtil.buildTrustAllWebClient(60000, 120000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WebClient", e);
        }
    }

    public String sendCrqDetails(String crqNo, String batchNo) {

        // Step 1 : Fetch CRQ details from DB
        CfmCrqDetailsDto details = fetchCrqDetailsFromDb(crqNo);

        // Step 2 : Build multipart form
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("CRQ_No", nvl(details.getCrqNo()));
        builder.part("CHM_status", nvl(details.getChmStatus()));
        builder.part("Scheduled_Start_Date", toIsoDate(details.getScheduledStartDate()));
        builder.part("Scheduled_End_Date", toIsoDate(details.getScheduledEndDate()));
        builder.part("Duration", nvl(details.getDuration()));
        builder.part("ChangeImpact", nvl(details.getChangeImpact()));
        builder.part("Remark", nvl(details.getRemark()));
        builder.part("Description", nvl(details.getDescription()));
        builder.part("Outage_Reason", nvl(details.getOutageReason()));
        builder.part("Type_of_Change", nvl(details.getTypeOfChange()));
        builder.part("IP_File_updated", "false");
        builder.part("PACKET_File_updated", "false");

        // Step 3 : Attach IP / PACKET files from SFTP (same path as impact batch excel)
        try (Session<?> session = sessionFactory.getSession()) {
            addFilePart(builder, session, "IP_File", "IP_" + crqNo + "_" + batchNo + ".csv");
            addFilePart(builder, session, "PACKET_File", "PACKET_" + crqNo + "_" + batchNo + ".csv");
        } catch (Exception e) {
            cfmCrqDetailsApi.error("[CFM] SFTP read failed for CRQ {} batch {}", crqNo, batchNo, e);
            throw new RuntimeException("Failed to read impact files from SFTP", e);
        }

        // Step 4 : Send to CFM
        URI uri = UriComponentsBuilder
                .fromUriString(getCfmBaseUrl())
                .path(CFM_CRQ_DETAILS_PATH)
                .build()
                .toUri();
        cfmCrqDetailsApi.info("[CFM] Sending CRQ Final URI: {}", uri);
        try {
            String response = webClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .exchangeToMono(clientResponse -> {
                        cfmCrqDetailsApi.info("[CFM] HTTP Status: {}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class).defaultIfEmpty("");
                    })
                    .block();

            cfmCrqDetailsApi.info("[CFM] Response for CRQ {}: {}", crqNo, response);
            return response;

        } catch (Exception e) {
            cfmCrqDetailsApi.error("[CFM] Call failed for CRQ {}", crqNo, e);
            throw new RuntimeException("Failed to call CFM crqDetails API", e);
        }
    }

    private CfmCrqDetailsDto fetchCrqDetailsFromDb(String crqNo) {
        String sql = "CALL SP_GetCFMApiDetails(?)";

        cfmCrqDetailsApi.info("call SP_GetCFMApiDetails('{}');", crqNo);
        List<CfmCrqDetailsDto> result =
                databaseUtils.executeProcedureAndFetchObjects(
                        jdbcTemplateTwo,
                        sql,
                        CfmCrqDetailsDto.class,
                        crqNo
                );

        return result.stream()
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException("CRQ not found: " + crqNo));
    }

    private void addFilePart(MultipartBodyBuilder builder, Session<?> session,
                             String partName, String fileName) throws Exception {

        String remoteFile = config.getSFTP_BATCHWISE_IMPACT_EXCEL_PATH() + "/" + fileName;

        if (!session.exists(remoteFile)) {
            cfmCrqDetailsApi.warn("[CFM] File not found: {}", remoteFile);
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        session.read(remoteFile, out);

        builder.part(partName, new ByteArrayResource(out.toByteArray()) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }).contentType(MediaType.parseMediaType("text/csv"));
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    // "2026-07-19 04:00:00" -> "2026-07-19T04:00:00"
    private String toIsoDate(String value) {
        return nvl(value).trim().replace(" ", "T");
    }
}
