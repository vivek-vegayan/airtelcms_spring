package com.vegayan.airtelmanagement.filetransfer.linux.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** A single entry returned when listing the remote Linux server's /tmp directory. */
@Getter
@Builder
@AllArgsConstructor
public class RemoteFileDto {
    private String fileName;
    private String fileSize;
    private String fileDate;
    private boolean directory;
}
