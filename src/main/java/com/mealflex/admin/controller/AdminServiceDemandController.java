package com.mealflex.admin.controller;

import com.mealflex.demand.dto.ServiceDemandSummaryResponse;
import com.mealflex.demand.service.ServiceDemandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/service-demands")
@RequiredArgsConstructor
public class AdminServiceDemandController {
    private final ServiceDemandService service;
    @GetMapping public List<ServiceDemandSummaryResponse> report() { return service.report(); }
}
