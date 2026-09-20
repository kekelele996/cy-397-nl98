package com.contractapi.controller;

import java.util.List;
import com.contractapi.dto.ContractDetail;
import com.contractapi.dto.GenerateContractRequest;
import com.contractapi.dto.InviteSignersRequest;
import com.contractapi.entity.Contract;
import com.contractapi.service.ContractService;
import com.contractapi.service.SigningService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
  private final ContractService contractService;
  private final SigningService signingService;

  public ContractController(ContractService contractService, SigningService signingService) {
    this.contractService = contractService;
    this.signingService = signingService;
  }

  /** 从可用模板生成合同，正文与填充参数一并留存。 */
  @PostMapping("/generate")
  public Contract generate(@RequestBody GenerateContractRequest request) {
    return contractService.generate(request);
  }

  /** 草稿邀请多名签署方：身份、顺序、截止时间一次登记，合同转入待签署。 */
  @PostMapping("/{id}/signers")
  public ContractDetail inviteSigners(@PathVariable Long id, @RequestBody InviteSignersRequest request) {
    return signingService.inviteSigners(id, request);
  }

  /** 签署方确认签署，全部完成后合同自动转为已签署。 */
  @PostMapping("/{id}/sign/{signerId}")
  public ContractDetail sign(@PathVariable Long id, @PathVariable String signerId) {
    return signingService.sign(id, signerId);
  }

  /** 签署方拒绝签署，合同随即转为已过期。 */
  @PostMapping("/{id}/reject/{signerId}")
  public ContractDetail reject(@PathVariable Long id, @PathVariable String signerId) {
    return signingService.reject(id, signerId);
  }

  /** 合同详情：含每位签署方的身份、顺序、截止时间与签署时间。 */
  @GetMapping("/{id}")
  public ContractDetail detail(@PathVariable Long id) {
    return contractService.getDetail(id);
  }

  /** 用户合同库，支持按状态筛选，回读与落盘状态一致。 */
  @GetMapping
  public List<Contract> list(@RequestParam(required = false) Long userId,
                             @RequestParam(required = false) String status) {
    return contractService.list(userId, status);
  }

  @PostMapping("/{id}/pdf")
  public String exportPdf(@PathVariable Long id) {
    return contractService.exportPdf(id);
  }
}
