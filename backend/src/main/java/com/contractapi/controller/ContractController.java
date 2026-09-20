package com.contractapi.controller;

import java.util.List;
import com.contractapi.dto.GenerateContractRequest;
import com.contractapi.dto.InviteSignersRequest;
import com.contractapi.dto.SignConfirmRequest;
import com.contractapi.entity.Contract;
import com.contractapi.service.ContractService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
  private final ContractService service;
  public ContractController(ContractService service) { this.service = service; }
  @PostMapping("/generate") public Contract generate(@RequestBody GenerateContractRequest request) { return service.generate(request); }
  @PostMapping("/{id}/invite") public Contract invite(@PathVariable Long id, @RequestBody InviteSignersRequest request) { return service.invite(id, request); }
  @PostMapping("/{id}/sign") public Contract sign(@PathVariable Long id, @RequestBody SignConfirmRequest request) { return service.sign(id, request); }
  @PostMapping("/{id}/reject") public Contract reject(@PathVariable Long id, @RequestBody SignConfirmRequest request) { return service.reject(id, request); }
  @GetMapping public List<Contract> list(@RequestParam(required = false) Long userId, @RequestParam(required = false) String status) { return service.list(userId, status); }
  @GetMapping("/{id}") public Contract detail(@PathVariable Long id) { return service.detail(id); }
  @PostMapping("/{id}/pdf") public String exportPdf(@PathVariable Long id) { return service.exportPdf(id); }
}
