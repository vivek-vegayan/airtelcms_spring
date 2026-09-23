package com.vegayan.airtelmanagement.common.service;
import com.vegayan.airtelmanagement.common.config.AppPropertiesConfig;

import com.vegayan.airtelmanagement.common.util.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;


public class BaseService {

    @Autowired
    @Qualifier("jdbcTemplateOne")
    protected JdbcTemplate jdbcTemplateOne;

    @Autowired
    @Qualifier("jdbcTemplateTwo")
    protected JdbcTemplate jdbcTemplateTwo;

    @Autowired
    protected DatabaseUtils databaseUtils;

    @Autowired
    protected WebClient webClient;


    @Autowired
    protected CommonService commonService;

    @Autowired
    protected AppPropertiesConfig config;

    protected final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final Logger cancelCrqLog = LoggerFactory.getLogger("Cancel_Crq_Logger");

    protected final Logger submitPlanExternalLogger = LoggerFactory.getLogger("Submit_Plan_External_Logger");

    protected final Logger crqUpdateToChm = LoggerFactory.getLogger("CRQ_Update_To_Chm");

    protected final Logger crqScheduleReschedule = LoggerFactory.getLogger("Schedule_Reschedule_Crq");



}
