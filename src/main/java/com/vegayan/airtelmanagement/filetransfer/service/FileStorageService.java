package com.vegayan.airtelmanagement.filetransfer.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FileStorageService extends BaseService {

    /** Upload file to local folder only */
    public void uploadFile(MultipartFile multipartFile, boolean replace) throws Exception {
        File file = prepareLocalFile(multipartFile, replace);

        try (InputStream inputStream = multipartFile.getInputStream();
             OutputStream outputStream = new FileOutputStream(file)) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        System.out.println("File saved locally at: " + file.getAbsolutePath());
    }


    private File prepareLocalFile(MultipartFile multipartFile, boolean replace) throws IOException {
        if (multipartFile == null || multipartFile.getOriginalFilename() == null || multipartFile.getOriginalFilename().isEmpty()) {
            throw new IllegalArgumentException("File must not be null and must have a valid name");
        }

        String localPath = config.getSFTP_LOCAL_PATH();
        File dir = new File(localPath);

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create directory: " + localPath);
        }

        File file = new File(dir, multipartFile.getOriginalFilename());

        if (file.exists() && !replace) {
            throw new IOException("File already exists: " + file.getName());
        }

        return file;
    }


    /** List files from local folder */
    public List<Map<String, String>> listFiles() {
        List<Map<String, String>> filesList = new ArrayList<>();
        File dir = new File(config.getSFTP_LOCAL_PATH());
        if (!dir.exists() || !dir.isDirectory()) {
            return filesList; // Return empty list if folder doesn't exist
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    Map<String, String> fileData = new HashMap<>();
                    fileData.put("fileName", file.getName());
                    fileData.put("fileDate",
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file.lastModified())));
                    fileData.put("fileSize", readableFileSize(file.length()));
                    filesList.add(fileData);
                }
            }
        }
        return filesList;
    }

    /** Load a file from the local folder as a downloadable Resource */
    public Resource loadFileAsResource(String fileName) throws IOException {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("File name must not be null or empty");
        }

        File file = new File(config.getSFTP_LOCAL_PATH(), fileName);
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("File not found: " + fileName);
        }

        return new FileSystemResource(file);
    }

    /** Delete file from local folder */
    public void deleteFile(String fileName) throws IOException {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("File name must not be null or empty");
        }

        File file = new File(config.getSFTP_LOCAL_PATH(), fileName);
        if (file.exists()) {
            if (!file.delete()) throw new IOException("Failed to delete file: " + file.getName());
        } else {
            throw new FileNotFoundException("File not found: " + fileName);
        }
    }

    /** Convert file size to human-readable format */
    private String readableFileSize(long size) {
        if (size < 1024) return size + " bytes";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.2f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }


}
