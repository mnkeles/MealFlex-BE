package com.mealflex.admin.controller;

import com.mealflex.risk.entity.RiskCase;
import com.mealflex.risk.service.RiskCaseService;
import com.mealflex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/v1/admin/risk-cases") @RequiredArgsConstructor
public class AdminRiskController {
 private final RiskCaseService service;
 @GetMapping public List<Map<String,Object>> list(){return service.listAndScan().stream().map(this::view).toList();}
 @PostMapping("/{id}/decision") public Map<String,Object> decide(@AuthenticationPrincipal UserPrincipal p,@PathVariable Long id,@RequestBody Map<String,String> body){return view(service.decide(p.getId(),id,body.getOrDefault("decision",""),body.getOrDefault("note","")));}
 private Map<String,Object> view(RiskCase item){Map<String,Object> result=new LinkedHashMap<>();result.put("id",item.getId());result.put("type",item.getRiskType());result.put("severity",item.getSeverity());result.put("referenceType",item.getReferenceType());result.put("referenceId",item.getReferenceId());result.put("summary",item.getSummary());result.put("status",item.getStatus());result.put("note",item.getResolutionNote()==null?"":item.getResolutionNote());result.put("createdAt",item.getCreatedAt());result.put("resolvedAt",item.getResolvedAt());result.put("assignedAdminName",item.getAssignedAdmin()==null?null:item.getAssignedAdmin().getFirstName()+" "+item.getAssignedAdmin().getLastName());return result;}
}
