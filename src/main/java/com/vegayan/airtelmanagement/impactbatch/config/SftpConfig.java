package com.vegayan.airtelmanagement.impactbatch.config;


import com.vegayan.airtelmanagement.common.service.BaseService;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;

@Configuration
public class SftpConfig extends BaseService {

    @Bean
    public SessionFactory<SftpClient.DirEntry> sftpSessionFactory() {

        DefaultSftpSessionFactory factory =
                new DefaultSftpSessionFactory(true);

        factory.setHost(
                config.getSFTP_HOST()
        );

        factory.setPort(
                Integer.parseInt(
                        config.getSFTP_PORT()
                )
        );

        factory.setUser(
                config.getSFTP_USERNAME()
        );

        factory.setPassword(
                config.getSFTP_PASSWORD()
        );

        factory.setAllowUnknownKeys(true);

        return new CachingSessionFactory<>(factory);
    }
}
