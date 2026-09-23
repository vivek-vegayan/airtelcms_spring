package com.vegayan.airtelmanagement.filetransfer.linux.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RemoteFileDto {
    private String fileName;
    private String fileSize;
    private String fileDate;
    private boolean directory;
}
