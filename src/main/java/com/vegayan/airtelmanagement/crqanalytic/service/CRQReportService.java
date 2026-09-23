package com.vegayan.airtelmanagement.crqanalytic.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CRQReportService extends BaseService {

    public Map<String, Object> getCrqReport(Long actorUserId, String startDate, String endDate, Integer page, Integer size) {
        int limit  = size  != null ? size  : 200;
        int offset = page  != null ? page * limit : 0;
        String sql = "call sp_show_crq_report(?,?,?,?,?)";
        LOGGER.info("call sp_show_crq_report('{}','{}','{}','{}','{}');", actorUserId, startDate, endDate, offset, limit);
        return databaseUtils.executeProcedureAndProvideKeyValueWithHeadersFormat(jdbcTemplateTwo, sql, actorUserId, startDate, endDate, offset, limit);
    }
}
