package com.vegayan.airtelmanagement.crqanalytic.service;

import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.crqanalytic.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Level;

@Service
public class CRQDetailService extends BaseService {


    private final Executor executor = Executors.newFixedThreadPool(5);

    public CRQDetailResponse getCrqDetail(String changeId) {
        LOGGER.info("CRQ Detail → changeId=" + changeId);

        Object[] p = { changeId };

        CompletableFuture<CRQDetailMainDto>           fMain     = async(() -> getDetailMain(p),       null,      "DetailMain");
        CompletableFuture<List<CRQTimelineStepDto>>   fTimeline = async(() -> getTimeline(p),         List.of(), "Timeline");

        CompletableFuture.allOf(fMain, fTimeline).join();

        CRQDetailMainDto main = fMain.join();
        if (main == null) {
            CRQDetailResponse empty = new CRQDetailResponse();
            empty.setCrqNo(changeId);
            empty.setStatus("NOT_FOUND");
            return empty;
        }

        CRQDetailResponse resp = new CRQDetailResponse();
        // Map main fields
        resp.setCrqNo(main.getChangeId());
        resp.setTitle(main.getTitle());
        resp.setImpactLabel(main.getImpactLabel());
        resp.setImpactCount(main.getImpactCount());
        resp.setProgressPct(main.getProgressPct());
        resp.setCurrentStage(main.getCurrentStage());
        resp.setPlanNo(main.getPlanNo());
        resp.setLastUpdated(main.getLastUpdated());
        resp.setStatus(main.getStatus());
        resp.setRequestor(main.getRequestor());
        resp.setCategory(main.getCategory());
        resp.setCircle(main.getCircle());
        resp.setPlanType(main.getPlanType());
        resp.setDomain(main.getDomain());
        resp.setScheduledDate(main.getScheduledDate());
        resp.setImpact(main.getImpact());
        resp.setExecutionWindow(main.getExecutionWindow());
        resp.setSubmitDate(main.getSubmitDate());
        resp.setFieldEngineerName(main.getFeName());
        resp.setFieldEngineerMobile(main.getFeMobile());
        resp.setFieldEngineerEmail(main.getFeEmail());
        resp.setFlagB2B(main.isFlagB2b());
        resp.setFlagSA(main.isFlagSa());
        resp.setFlagCoreNode(main.isFlagCoreNode());
        resp.setFlagNSA(main.isFlagNsa());
        resp.setApprovalActionStage(main.getApprovalActionStage());
        resp.setApprovalActionUser(main.getApprovalActionUser());
        resp.setCanApprove(main.isCanApprove());

        // Map related lists

        resp.setTimeline(fTimeline.join());
        return resp;
    }

    private CRQDetailMainDto getDetailMain(Object[] p) {
        LOGGER.info("CALL GetCRQDetailMain('" + p[0] + "');");
        List<CRQDetailMainDto> rows = databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQDetailMain(?)", CRQDetailMainDto.class, p);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<CRQTimelineStepDto> getTimeline(Object[] p) {
        LOGGER.info("CALL GetCRQTimeline('" + p[0] + "');");
        return databaseUtils.executeProcedureAndFetchObjects(
                jdbcTemplateTwo, "CALL GetCRQTimeline(?)", CRQTimelineStepDto.class, p);
    }

    private <T> CompletableFuture<T> async(Supplier<T> fn, T def, String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fn.get();
            } catch (Exception ex) {
                LOGGER.error("SP failed: {}", name, ex);
                return def;
            }
        }, executor);
    }
}