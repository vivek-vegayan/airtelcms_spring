package com.vegayan.airtelmanagement.common.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import com.vegayan.airtelmanagement.common.util.LoggingJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

@Configuration
public class MultiDataSourceConfig {

    private static final Logger logger = Logger.getLogger(MultiDataSourceConfig.class.getName());

    private final AppPropertiesConfig config;
    private HikariDataSource hikariDataSourceOne;
    private HikariDataSource hikariDataSourceTwo;

    @Autowired
    public MultiDataSourceConfig(AppPropertiesConfig config) {
        this.config = config;
    }

    @Bean(name = "dataSourceOne")
    @Primary
    public DataSource dataSourceOne() {
        hikariDataSourceOne = new HikariDataSource(); // Create a new HikariDataSource instance
        hikariDataSourceOne.setJdbcUrl("jdbc:mysql://" + config.getDBSOURCE_USERMGMT_IP() + "/" + config.getDBSOURCE_USERMGMT_DBNAME());
        hikariDataSourceOne.setUsername(config.getDBSOURCE_USERMGMT_USER());
        hikariDataSourceOne.setPassword(config.getDBSOURCE_USERMGMT_PASS());
        hikariDataSourceOne.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariDataSourceOne.setMaximumPoolSize(50); // Set a more conservative maximum pool size
        hikariDataSourceOne.setMinimumIdle(2); // Set the minimum idle connections
        hikariDataSourceOne.setMaxLifetime(1800000);   // 30 minutes
        hikariDataSourceOne.setConnectionTimeout(30000); // 30 seconds to get a  connection timeout
        hikariDataSourceOne.setIdleTimeout(600000); // Set 10 minutes idle timeout
        hikariDataSourceOne.setLeakDetectionThreshold(30000); // 30 seconds
        hikariDataSourceOne.setValidationTimeout(5000);       // 5 seconds
        validateConnection(hikariDataSourceOne, "DataSourceOne"); // Validate the connection
        return hikariDataSourceOne; // Return the configured data source


    }


    @Bean(name = "dataSourceTwo")
    public DataSource dataSourceTwo() {
        hikariDataSourceTwo = new HikariDataSource();
        hikariDataSourceTwo.setJdbcUrl("jdbc:mysql://" + config.getDBSOURCE1_IP() + "/" + config.getDBSOURCE1_DBNAME());
        hikariDataSourceTwo.setUsername(config.getDBSOURCE1_USER());
        hikariDataSourceTwo.setPassword(config.getDBSOURCE1_PASS());
        hikariDataSourceTwo.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariDataSourceTwo.setMaximumPoolSize(50); // Set a more conservative maximum pool size
        hikariDataSourceTwo.setMinimumIdle(1); // Set the minimum idle connections
        hikariDataSourceTwo.setMaxLifetime(1800000); // Set the maximum lifetime of a connection
        hikariDataSourceTwo.setConnectionTimeout(30000); // Set connection timeout
        hikariDataSourceTwo.setIdleTimeout(600000); // Set idle timeout
        hikariDataSourceTwo.setLeakDetectionThreshold(30000); // 30 seconds
        hikariDataSourceTwo.setValidationTimeout(5000);       // 5 seconds
        validateConnection(hikariDataSourceTwo, "DataSourceTwo");
        return hikariDataSourceTwo;
    }


    @Bean(name = "jdbcTemplateOne")
    public JdbcTemplate jdbcTemplateOne(@Qualifier("dataSourceOne") DataSource dataSource) {
        return new LoggingJdbcTemplate(dataSource, "DB1");
    }

    @Bean(name = "jdbcTemplateTwo")
    public JdbcTemplate jdbcTemplateTwo(@Qualifier("dataSourceTwo") DataSource dataSource) {
        return new LoggingJdbcTemplate(dataSource, "DB2");
    }

    @PreDestroy  // This method will be called before the bean is destroyed
    public void shutdown() {
        if (hikariDataSourceOne != null) {
            hikariDataSourceOne.close();
            System.out.println("DataSourceOne shut down properly.");
        }
        if (hikariDataSourceTwo != null) {
            hikariDataSourceTwo.close();
            System.out.println("DataSourceTwo shut down properly.");
        }
    }

    private void validateConnection(HikariDataSource dataSource, String dataSourceName) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(5)) {
                System.out.println(dataSourceName + " has been validated successfully!");
            }
        } catch (SQLException e) {
            System.err.println("Error validating " + dataSourceName + ": " + e.getMessage());
            throw new RuntimeException("Failed to validate " + dataSourceName + ": " + e.getMessage(), e);
        }
    }


}