package com.vegayan.airtelmanagement.filetransfer.linux.service;

import com.jcraft.jsch.*;
import com.vegayan.airtelmanagement.common.config.AppPropertiesConfig;
import com.vegayan.airtelmanagement.filetransfer.linux.dto.RemoteFileDto;
import com.vegayan.airtelmanagement.filetransfer.linux.dto.SftpConnectionRequestDto;
import com.vegayan.airtelmanagement.filetransfer.linux.exception.SftpOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

@Service
public class LinuxSftpRemoteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinuxSftpRemoteService.class);
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    @Autowired
    private AppPropertiesConfig config;
     public List<RemoteFileDto> uploadFileAndList(SftpConnectionRequestDto conn, MultipartFile file) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession(conn);
            channel = openSftpChannel(session);

            String remoteDir = config.getSFTP_LINUX_REMOTE_DIR();
            String remotePath = remoteDir + "/" + file.getOriginalFilename();

            LOGGER.info("Uploading '{}' to {}@{}:{}", file.getOriginalFilename(), conn.getUsername(), conn.getHost(), remotePath);
            try (InputStream in = file.getInputStream()) {
                channel.put(in, remotePath, ChannelSftp.OVERWRITE);
            }

            return listDirectory(channel, remoteDir);
        } catch (JSchException e) {
            throw new SftpOperationException("Could not connect to " + conn.getHost() + ": " + e.getMessage(), e);
        } catch (SftpException e) {
            throw new SftpOperationException("Failed to upload file to remote /tmp: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new SftpOperationException("Failed to read the uploaded file: " + e.getMessage(), e);
        } finally {
            disconnect(channel, session);
        }
    }

    public List<RemoteFileDto> listRemoteDir(SftpConnectionRequestDto conn) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession(conn);
            channel = openSftpChannel(session);
            return listDirectory(channel, config.getSFTP_LINUX_REMOTE_DIR());
        } catch (JSchException e) {
            throw new SftpOperationException("Could not connect to " + conn.getHost() + ": " + e.getMessage(), e);
        } catch (SftpException e) {
            throw new SftpOperationException("Failed to list remote /tmp: " + e.getMessage(), e);
        } finally {
            disconnect(channel, session);
        }
    }

    public List<RemoteFileDto> deleteFileAndList(SftpConnectionRequestDto conn, String remoteFileName) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession(conn);
            channel = openSftpChannel(session);

            String remoteDir = config.getSFTP_LINUX_REMOTE_DIR();
            String remotePath = remoteDir + "/" + remoteFileName;

            LOGGER.info("Deleting '{}' on {}@{}", remotePath, conn.getUsername(), conn.getHost());
            channel.rm(remotePath);

            return listDirectory(channel, remoteDir);
        } catch (JSchException e) {
            throw new SftpOperationException("Could not connect to " + conn.getHost() + ": " + e.getMessage(), e);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                throw new SftpOperationException("Remote file not found: " + remoteFileName);
            }
            throw new SftpOperationException("Failed to delete remote file: " + e.getMessage(), e);
        } finally {
            disconnect(channel, session);
        }
    }

    public byte[] downloadRemoteFile(SftpConnectionRequestDto conn, String remoteFileName) {
        Session session = null;
        ChannelSftp channel = null;
        try {
            session = openSession(conn);
            channel = openSftpChannel(session);

            String remotePath = config.getSFTP_LINUX_REMOTE_DIR() + "/" + remoteFileName;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            channel.get(remotePath, buffer);
            return buffer.toByteArray();
        } catch (JSchException e) {
            throw new SftpOperationException("Could not connect to " + conn.getHost() + ": " + e.getMessage(), e);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                throw new SftpOperationException("Remote file not found: " + remoteFileName);
            }
            throw new SftpOperationException("Failed to download remote file: " + e.getMessage(), e);
        } finally {
            disconnect(channel, session);
        }
    }

    @SuppressWarnings("unchecked")
    private List<RemoteFileDto> listDirectory(ChannelSftp channel, String remoteDir) throws SftpException {
        Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);
        List<RemoteFileDto> files = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            SftpATTRS attrs = entry.getAttrs();
            files.add(RemoteFileDto.builder()
                    .fileName(name)
                    .fileSize(readableFileSize(attrs.getSize()))
                    .fileDate(dateFormat.format(new Date(attrs.getMTime() * 1000L)))
                    .directory(attrs.isDir())
                    .build());
        }
        return files;
    }

    private Session openSession(SftpConnectionRequestDto conn) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(conn.getUsername(), conn.getHost(), conn.resolvedPort());
        session.setPassword(conn.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(CONNECT_TIMEOUT_MS);
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    private ChannelSftp openSftpChannel(Session session) throws JSchException {
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(CONNECT_TIMEOUT_MS);
        return channel;
    }

    private void disconnect(ChannelSftp channel, Session session) {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    private String readableFileSize(long size) {
        if (size < 1024) return size + " bytes";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.2f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
