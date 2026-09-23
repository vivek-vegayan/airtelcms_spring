package com.vegayan.airtelmanagement.filetransfer.linux.controller;

import com.vegayan.airtelmanagement.filetransfer.linux.dto.RemoteFileDto;
import com.vegayan.airtelmanagement.filetransfer.linux.dto.SendToLinuxRequestDto;
import com.vegayan.airtelmanagement.filetransfer.linux.dto.SftpConnectionRequestDto;
import com.vegayan.airtelmanagement.filetransfer.linux.exception.SftpOperationException;
import com.vegayan.airtelmanagement.filetransfer.linux.service.LinuxSftpRemoteService;
import lombok.AllArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/sftp/linux/remote")
@AllArgsConstructor
public class LinuxSftpRemoteController {

    private final LinuxSftpRemoteService linuxSftpRemoteService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadToRemoteTmp(
            @RequestParam("file") MultipartFile file,
            @RequestParam("host") String host,
            @RequestParam(value = "port", required = false) Integer port,
            @RequestParam("username") String username,
            @RequestParam("password") String password
    ) {
        try {
            SftpConnectionRequestDto conn = new SftpConnectionRequestDto();
            conn.setHost(host);
            conn.setPort(port);
            conn.setUsername(username);
            conn.setPassword(password);
            validateConnection(conn);

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("file is required");
            }

            List<RemoteFileDto> remoteFiles = linuxSftpRemoteService.uploadFileAndList(conn, file);
            return ResponseEntity.ok(remoteFiles);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SftpOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }


    @PostMapping("/list")
    public ResponseEntity<?> listRemoteTmp(@RequestBody SftpConnectionRequestDto request) {
        try {
            validateConnection(request);
            return ResponseEntity.ok(linuxSftpRemoteService.listRemoteDir(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SftpOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }


    @PostMapping("/download")
    public ResponseEntity<?> downloadFromRemoteTmp(@RequestBody SendToLinuxRequestDto request) {
        try {
            validateConnection(request);
            if (isBlank(request.getFileName())) {
                return ResponseEntity.badRequest().body("fileName is required");
            }

            byte[] content = linuxSftpRemoteService.downloadRemoteFile(request, request.getFileName());
            ByteArrayResource resource = new ByteArrayResource(content);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + request.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SftpOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteFromRemoteTmp(@RequestBody SendToLinuxRequestDto request) {
        try {
            validateConnection(request);
            if (isBlank(request.getFileName())) {
                return ResponseEntity.badRequest().body("fileName is required");
            }

            List<RemoteFileDto> remoteFiles = linuxSftpRemoteService.deleteFileAndList(request, request.getFileName());
            return ResponseEntity.ok(remoteFiles);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (SftpOperationException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private void validateConnection(SftpConnectionRequestDto request) {
        if (isBlank(request.getHost())) {
            throw new IllegalArgumentException("Host/IP address is required");
        }
        if (isBlank(request.getUsername())) {
            throw new IllegalArgumentException("Username is required");
        }
        if (isBlank(request.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
