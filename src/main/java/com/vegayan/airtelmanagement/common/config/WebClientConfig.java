package com.vegayan.airtelmanagement.common.config;


import com.vegayan.airtelmanagement.common.util.SslWebClientUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;


import javax.net.ssl.SSLException;

@Configuration
public class WebClientConfig {

    @Bean(name = "trustAllWebClient")
    public WebClient trustAllWebClient() throws SSLException {
        return SslWebClientUtil.buildTrustAllWebClient(120000, 120000);
    }

}
