package com.contractapi.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("contract_signers")
public class ContractSigner {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long contractId;
  /** 签署方身份 */
  private String signerName;
  /** 签署方用户ID（邀请时登记，签署时校验） */
  private Long signerUserId;
  /** 签署顺序，需按顺序签署 */
  private Integer signOrder;
  private String status;
  /** 签署时间 */
  private LocalDateTime signedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getContractId() { return contractId; }
  public void setContractId(Long contractId) { this.contractId = contractId; }
  public String getSignerName() { return signerName; }
  public void setSignerName(String signerName) { this.signerName = signerName; }
  public Long getSignerUserId() { return signerUserId; }
  public void setSignerUserId(Long signerUserId) { this.signerUserId = signerUserId; }
  public Integer getSignOrder() { return signOrder; }
  public void setSignOrder(Integer signOrder) { this.signOrder = signOrder; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDateTime getSignedAt() { return signedAt; }
  public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
}
