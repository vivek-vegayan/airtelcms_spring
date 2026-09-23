package com.vegayan.airtelmanagement.filetransfer.linux.dto;

import jakarta.validation.constraints.NotBlank;

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
