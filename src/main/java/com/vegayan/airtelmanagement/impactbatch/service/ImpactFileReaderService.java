package com.vegayan.airtelmanagement.impactbatch.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.impactbatch.dto.BatchStatusResponseDTO;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImpactFileReaderService extends BaseService {


    public Map<String, BatchStatusResponseDTO> getBatchWiseStatus(
            String crqNo
    ) throws Exception {

        Session session = null;
        ChannelSftp sftp = null;

        Pattern batchPattern =
                Pattern.compile(".*" + crqNo + "_(\\d+)\\.csv");

        Map<String, BatchStatusResponseDTO> batchMap =
                new LinkedHashMap<>();

        try {

            // SFTP CONNECT
            JSch jsch = new JSch();

            session = jsch.getSession(
                    config.getSFTP_USERNAME(),
                    config.getSFTP_HOST(),
                    Integer.parseInt(config.getSFTP_PORT())
            );

            session.setPassword(config.getSFTP_PASSWORD());

            Properties props = new Properties();
            props.put("StrictHostKeyChecking", "no");

            session.setConfig(props);
            session.connect();

            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();

            // 📂 LIST FILES
            Vector<ChannelSftp.LsEntry> files =
                    sftp.ls(config.getSFTP_FILE_PATH_BATCH_CSV_FILE());

            for (ChannelSftp.LsEntry entry : files) {

                String fileName = entry.getFilename();

                if (!fileName.endsWith(".csv")) continue;

                if (!fileName.contains(crqNo)) continue;

                Matcher matcher = batchPattern.matcher(fileName);

                if (!matcher.find()) continue;

                String batchNo = matcher.group(1);

                String batchKey = "Batch_" + batchNo;

                BatchStatusResponseDTO batch =
                        batchMap.computeIfAbsent(batchKey, k -> {

                            BatchStatusResponseDTO dto =
                                    new BatchStatusResponseDTO();

                            dto.setFiles(new ArrayList<>());

                            String modifiedDate =
                                    Instant.ofEpochSecond(
                                                    entry.getAttrs().getMTime()
                                            )
                                            .atZone(ZoneId.systemDefault())
                                            .format(
                                                    DateTimeFormatter.ofPattern(
                                                            "yyyy-MM-dd HH:mm:ss"
                                                    )
                                            );

                            dto.setModifiedDate(modifiedDate);

                            return dto;
                        });

                batch.getFiles().add(fileName);
            }

            if (batchMap.isEmpty()) {
                throw new RuntimeException(
                        "No batch CSV files found for CRQ: " + crqNo
                );
            }

            //  SORT RESPONSE
            return batchMap.entrySet()
                    .stream()
                    .sorted(Comparator.comparingInt(e ->
                            Integer.parseInt(
                                    e.getKey().replace("Batch_", "")
                            )
                    ))
                    .collect(
                            LinkedHashMap::new,
                            (map, entry) ->
                                    map.put(entry.getKey(), entry.getValue()),
                            Map::putAll
                    );

        } finally {

            if (sftp != null) {
                sftp.disconnect();
            }

            if (session != null) {
                session.disconnect();
            }
        }
    }
}
