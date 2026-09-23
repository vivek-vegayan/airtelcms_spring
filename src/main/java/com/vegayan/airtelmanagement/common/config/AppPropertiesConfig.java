package com.vegayan.airtelmanagement.common.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

@Getter
@Setter
@Service
@ConfigurationProperties
public class AppPropertiesConfig {

    private String CONFIG_FILE_PATH_WIN;
    private String CONFIG_FILE_PATH_LIN;
    private String CONFIG_FILE_PATH;

    private String DBSOURCE_USERMGMT_IP;
    private String DBSOURCE_USERMGMT_USER;
    private String DBSOURCE_USERMGMT_PASS;
    private String DBSOURCE_USERMGMT_DBNAME;

    private String DBSOURCE1_IP;
    private String DBSOURCE1_USER;
    private String DBSOURCE1_PASS;
    private String DBSOURCE1_DBNAME;
    private String DBSOURCE1_PORT;

    private String SSH_HOST;
    private String SSH_PORT;
    private String SSH_USERNAME;
    private String SSH_PASSWORD;
    private String SSH_SCRIPT_PATH;
    private String SSH_REFETCH_CHECKPOINT_SCRIPT_PATH;

    private String SSH_SERVICE_CSV_SCRIPT_PATH = "/home/vegayan/getCSVasperService.py";
    private String SSH_SHIFT_GEN_VAL_SCRIPT_PATH;
    private String SSH_PDF_VIEW_DOWNLOAD_PATH;

    private String SSH2_HOST;
    private String SSH2_PORT;
    private String SSH2_USERNAME;
    private String SSH2_PASSWORD;
    private String SSH2_SCRIPT_PATH;
    private String SSH2_SHIFT_GEN_VAL_SCRIPT_PATH;
    private String SSH2_SHIFT_GEN_VAL_SCRIPT_PATH_2;
    private String SSH2_PDF_VIEW_DOWNLOAD_PATH;

    private String SFTP_HOST;
    private String SFTP_USERNAME;
    private String SFTP_PORT;
    private String SFTP_PASSWORD;
    private String SFTP_FILE_PATH;

    private String SFTP_JSON_RAW_FILE_PATH;
    private String SFTP_FILE_PATH_CRQ_WEORKLOG_FILE;
    private String SFTP_FILE_PATH_BATCH_CSV_FILE;

    private String SSH_IMPACT_ANALYSIS_SCRIPT_PATH;
    private String SFTP_BATCHWISE_IMPACT_EXCEL_PATH;

    private String SFTP_LOCAL_PATH_WIN = "C:\\vegayan\\simplus\\sftp_uploads";
    private String SFTP_LOCAL_PATH_LIN = "/home/vegayan/simplus/sftp_uploads";
    private String SFTP_LOCAL_PATH = "D:\\Vivek\\Windows SFTP";


    private String SFTP_LINUX_REMOTE_DIR = "/tmp";

    private String PYTHON_SERVER_URL;

    public AppPropertiesConfig() {
        setCONFIG_FILE_PATH_WIN("C:\\vegayan\\simplus\\airtelcms-config.properties");
        setCONFIG_FILE_PATH_LIN("/home/vegayan/simplus/config_airtel.properties");
        initializeConfigFilePath();
        loadProperties();
    }


    private void initializeConfigFilePath() {
        String OS = System.getProperty("os.name").toLowerCase();
        if (OS.contains("win")) {
            setCONFIG_FILE_PATH(CONFIG_FILE_PATH_WIN);
        } else if (OS.contains("nux") || OS.contains("nix")) {
            setCONFIG_FILE_PATH(CONFIG_FILE_PATH_LIN);
        } else {
            throw new RuntimeException("Unsupported operating system detected. Configuration setup failed!");
        }
    }


    private void loadProperties() {
        Properties properties = new Properties();
        try (FileInputStream fileInput = new FileInputStream(new File(CONFIG_FILE_PATH))) {
            properties.load(fileInput);

            setDBSOURCE_USERMGMT_IP(properties.getProperty("DBSOURCE_USERMGMT_IP"));
            setDBSOURCE_USERMGMT_USER(properties.getProperty("DBSOURCE_USERMGMT_USER"));
            setDBSOURCE_USERMGMT_PASS(properties.getProperty("DBSOURCE_USERMGMT_PASS"));
            setDBSOURCE_USERMGMT_DBNAME(properties.getProperty("DBSOURCE_USERMGMT_DBNAME"));

            setDBSOURCE1_IP(properties.getProperty("DBSOURCE1_IP"));
            setDBSOURCE1_USER(properties.getProperty("DBSOURCE1_USER"));
            setDBSOURCE1_PASS(properties.getProperty("DBSOURCE1_PASS"));
            setDBSOURCE1_DBNAME(properties.getProperty("DBSOURCE1_DBNAME"));

            setSSH_HOST(properties.getProperty("SSH_HOST"));
            setSSH_PORT(properties.getProperty("SSH_PORT"));
            setSSH_USERNAME(properties.getProperty("SSH_USERNAME"));
            setSSH_PASSWORD(properties.getProperty("SSH_PASSWORD"));
            setSSH_SCRIPT_PATH(properties.getProperty("SSH_SCRIPT_PATH"));
            setSSH_REFETCH_CHECKPOINT_SCRIPT_PATH(properties.getProperty("SSH_REFETCH_CHECKPOINT_SCRIPT_PATH"));
            setSSH_SERVICE_CSV_SCRIPT_PATH(properties.getProperty(
                    "SSH_SERVICE_CSV_SCRIPT_PATH", getSSH_SERVICE_CSV_SCRIPT_PATH()));

            setSFTP_HOST(properties.getProperty("SFTP_HOST"));
            setSFTP_PORT(properties.getProperty("SFTP_PORT"));
            setSFTP_USERNAME(properties.getProperty("SFTP_USERNAME"));
            setSFTP_PASSWORD(properties.getProperty("SFTP_PASSWORD"));
            setSFTP_FILE_PATH_BATCH_CSV_FILE(properties.getProperty("SFTP_FILE_PATH_BATCH_CSV_FILE"));
            setSFTP_FILE_PATH(properties.getProperty("SFTP_FILE_PATH"));

            setSSH_IMPACT_ANALYSIS_SCRIPT_PATH(properties.getProperty("SSH_IMPACT_ANALYSIS_SCRIPT_PATH"));
            setSFTP_BATCHWISE_IMPACT_EXCEL_PATH(properties.getProperty("SFTP_BATCHWISE_IMPACT_EXCEL_PATH"));

            setSFTP_JSON_RAW_FILE_PATH(properties.getProperty("SFTP_JSON_RAW_FILE_PATH"));

            setSFTP_FILE_PATH_CRQ_WEORKLOG_FILE(properties.getProperty("SFTP_FILE_PATH_CRQ_WEORKLOG_FILE"));
            setSSH_SHIFT_GEN_VAL_SCRIPT_PATH(properties.getProperty("SSH_SHIFT_GEN_VAL_SCRIPT_PATH"));
            setSSH_PDF_VIEW_DOWNLOAD_PATH(properties.getProperty("SSH_PDF_VIEW_DOWNLOAD_PATH"));

            setSSH2_HOST(properties.getProperty("SSH2_HOST"));
            setSSH2_PORT(properties.getProperty("SSH2_PORT"));
            setSSH2_USERNAME(properties.getProperty("SSH2_USERNAME"));
            setSSH2_PASSWORD(properties.getProperty("SSH2_PASSWORD"));
            setSSH2_SCRIPT_PATH(properties.getProperty("SSH2_SCRIPT_PATH"));

            setPYTHON_SERVER_URL(properties.getProperty("PYTHON_SERVER_URL"));

        } catch (IOException e) {
            throw new RuntimeException("Error loading configuration file: " + CONFIG_FILE_PATH + ". Reason: " + e.getMessage(), e);
        }
    }



    @PostConstruct
    public void printLoadedProperties() {
        Logger logger = Logger.getLogger(AppPropertiesConfig.class.getName());
        logger.info("Loaded config:");
        logger.info("UserMgmt DB IP: " + DBSOURCE_USERMGMT_IP);
        logger.info("UserMgmt DB Name: " + DBSOURCE_USERMGMT_DBNAME);
        logger.info("UserMgmt Username: " + DBSOURCE_USERMGMT_USER);

        logger.info("Loaded config 2:");
        logger.info("UserMgmt DB IP: " + DBSOURCE1_IP);
        logger.info("UserMgmt DB Name: " + DBSOURCE1_DBNAME);
        logger.info("UserMgmt Username: " + DBSOURCE1_USER);
    }

}
