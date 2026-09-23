package com.vegayan.airtelmanagement.filetransfer.linux.dto;

import jakarta.validation.constraints.NotBlank;

/** "Send to Linux Server" request: which staged file to push to the remote /tmp, plus where to push it. */
public class SendToLinuxRequestDto extends SftpConnectionRequestDto {

    @NotBlank(message = "fileName is required")
    private String fileName;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
