package com.vegayan.airtelmanagement.remedy.controller;

import com.vegayan.airtelmanagement.common.dto.LogType;
import com.vegayan.airtelmanagement.common.service.BaseService;
import com.vegayan.airtelmanagement.remedy.dto.CrqUpdateChmDto;
import com.vegayan.airtelmanagement.remedy.dto.CrqUpdateChmResponse;
import com.vegayan.airtelmanagement.remedy.service.RemedyService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/remedy")
public class RemedyController extends BaseService {

    private final RemedyService remedyService;

    public RemedyController(RemedyService remedyService) {
        this.remedyService = remedyService;
    }


    @LogType("CRQ_Update_To_Chm")
    @PostMapping("/update")
    public CrqUpdateChmResponse crqUpdateToChmApi(
            @RequestBody CrqUpdateChmDto body) {

        return remedyService.crqUpdateToChmApi(body);
    }

}
