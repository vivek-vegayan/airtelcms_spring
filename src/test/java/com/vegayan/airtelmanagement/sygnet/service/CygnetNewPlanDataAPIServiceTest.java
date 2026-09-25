package com.vegayan.airtelmanagement.sygnet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegayan.airtelmanagement.common.util.ProcedureCallFormatter;
import com.vegayan.airtelmanagement.sygnet.dto.PlanFetchResultDto;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CygnetNewPlanDataAPIServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CygnetNewPlanDataAPIService service = new CygnetNewPlanDataAPIService(null, mapper);

    private PlanFetchResultDto extract(String json) throws Exception {
        return service.extractNodesAndPairs(mapper.readTree(json));
    }

    @Test
    void realPlanResponse() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/cygnet/plan_MPL_T5_NA-NP_MOB_17092026_002.json")) {
            root = mapper.readTree(in);
        }
        PlanFetchResultDto r = service.extractNodesAndPairs(root);

        assertEquals("BQG-BCLJK-HOB-T5-ER61.227,BQG-BCLJK-IAI-T5-ER9.121,BQG-BCLJK-IRA-T5-ER9.38,"
                        + "BQG-BCLJK-RCI-T5-ER9.104,GSND-BCLJK-MSCN-T5-ER61.222,SIG_HCO_018_1AG_A_CNCS55A2R001",
                r.getNodeName());
        assertEquals("BQG-BCLJK-HOB-T5-ER61.227$BS:MCIPS300C 25GE-ETY Port 22,"
                        + "BQG-BCLJK-IAI-T5-ER9.121$MCIPS300C:SFP:p=21,"
                        + "BQG-BCLJK-IAI-T5-ER9.121$MCIPS300C:SFP:p=22,"
                        + "BQG-BCLJK-IRA-T5-ER9.38$BS:MCIPS300C 25GE-ETY Port 22,"
                        + "BQG-BCLJK-RCI-T5-ER9.104$MCIPS300C:SFP:p=21,"
                        + "BQG-BCLJK-RCI-T5-ER9.104$MCIPS300C:SFP:p=22,"
                        + "GSND-BCLJK-MSCN-T5-ER61.222$BS:MCIPS300C 25GE-ETY Port 21,"
                        + "SIG_HCO_018_1AG_A_CNCS55A2R001$TwentyFiveGigE0/0/0/24,"
                        + "SIG_HCO_018_1AG_A_CNCS55A2R001$TwentyFiveGigE0/0/0/24:1",
                r.getNameInterfacePair());
        assertEquals(6, r.getNodeCount());
        assertEquals(9, r.getPairCount());
        assertEquals(14, r.getEquipmentCount());
        assertEquals(6, r.getLinkCount());
        assertEquals(0, r.getDummySkipped());
    }

    @Test
    void nullMissingOrWrongTypeArraysGiveEmptyResult() throws Exception {
        String[] responses = {
                "{\"status\":\"SUCCESS\",\"data\":{\"equipmentData\":null,\"linkSummary\":null}}",
                "{\"status\":\"SUCCESS\",\"data\":{}}",
                "{\"status\":\"SUCCESS\",\"data\":null}",
                "{\"status\":\"SUCCESS\"}",
                "{\"status\":\"SUCCESS\",\"data\":{\"equipmentData\":{},\"linkSummary\":\"x\"}}"
        };
        for (String json : responses) {
            PlanFetchResultDto r = extract(json);
            assertNull(r.getNodeName(), json);
            assertNull(r.getNameInterfacePair(), json);
            assertEquals(0, r.getNodeCount(), json);
        }
    }

    @Test
    void dummyInterfaceDroppedButNodeKept() throws Exception {
        PlanFetchResultDto r = extract("{\"data\":{\"linkSummary\":["
                + "{\"aEndNeLabel\":\"A\",\"aEndPtpMoName\":\"dummy\",\"zEndNeLabel\":\"B\",\"zEndPtpMoName\":\"Port 1\"}]}}");
        assertEquals("A,B", r.getNodeName());
        assertEquals("B$Port 1", r.getNameInterfacePair());
        assertEquals(1, r.getDummySkipped());
    }

    @Test
    void blanksDashesNullsAndBadElementsIgnored() throws Exception {
        PlanFetchResultDto r = extract("{\"data\":{"
                + "\"equipmentData\":[null, 5, {\"neLabel\":\"  \"}, {\"neLabel\":\"-\"}, {\"neLabel\":null}, {}, {\"neLabel\":\" N1 \"}],"
                + "\"linkSummary\":[{\"aEndNeLabel\":\"\",\"aEndPtpMoName\":\"P1\","
                + "\"zEndNeLabel\":\"N2\",\"zEndPtpMoName\":null},"
                + "{\"aEndNeLabel\":{\"x\":1},\"zEndNeLabel\":\"N3\",\"zEndPtpMoName\":\"-\"}]}}");
        assertEquals("N1,N2,N3", r.getNodeName());
        assertNull(r.getNameInterfacePair());
    }

    @Test
    void internalSpacesKeptRepeatedSpacesCollapsed() throws Exception {
        PlanFetchResultDto r = extract("{\"data\":{\"linkSummary\":["
                + "{\"aEndNeLabel\":\"  N1 \",\"aEndPtpMoName\":\" TS3:DHXE_4   10GE-MoE\\u00A0Port 2 \"}]}}");
        assertEquals("N1$TS3:DHXE_4 10GE-MoE Port 2", r.getNameInterfacePair());
    }

    @Test
    void duplicatesAcrossEquipmentAndLinksCountedOnce() throws Exception {
        PlanFetchResultDto r = extract("{\"data\":{"
                + "\"equipmentData\":[{\"neLabel\":\"N1\"},{\"neLabel\":\"N1\"}],"
                + "\"linkSummary\":[{\"aEndNeLabel\":\"N1\",\"aEndPtpMoName\":\"P\"},"
                + "{\"aEndNeLabel\":\"N1\",\"aEndPtpMoName\":\"P\"}]}}");
        assertEquals("N1", r.getNodeName());
        assertEquals("N1$P", r.getNameInterfacePair());
    }

    @Test
    void fullLogLineIsRunnableForLongJson() {
        String longJson = "{\"message\":\"it's " + "x".repeat(1000) + "\"}";
        String line = ProcedureCallFormatter.renderPreparedFull("CALL p(?, ?)", "CRQ1", longJson);
        assertFalse(line.contains("truncated"));
        assertEquals("CALL p('CRQ1', '{\"message\":\"it\\'s " + "x".repeat(1000) + "\"}');", line);
    }
}
