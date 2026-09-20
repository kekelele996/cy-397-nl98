package com.contractapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("contract_signers")
public class ContractSigner {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long contractId;
  private String signerId;
  private String signerName;
  private Integer signOrder;
  private LocalDateTime deadline;
  private String status;
  private LocalDateTime signedAt;
  private LocalDateTime rejectedAt;
  private LocalDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getContractId() { return contractId; }
  public void setContractId(Long contractId) { this.contractId = contractId; }
  public String getSignerId() { return signerId; }
  public void setSignerId(String signerId) { this.signerId = signerId; }
  public String getSignerName() { return signerName; }
  public void setSignerName(String signerName) { this.signerName = signerName; }
  public Integer getSignOrder() { return signOrder; }
  public void setSignOrder(Integer signOrder) { this.signOrder = signOrder; }
  public LocalDateTime getDeadline() { return deadline; }
  public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDateTime getSignedAt() { return signedAt; }
  public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
  public LocalDateTime getRejectedAt() { return rejectedAt; }
  public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
