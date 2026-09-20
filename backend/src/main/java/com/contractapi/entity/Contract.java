package com.contractapi.entity;

import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("contracts")
public class Contract {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Long templateId;
  private String title;
  private String content;
  /** 模板填充参数（JSON） */
  private String variables;
  private String format;
  private String status;
  /** 签署截止时间 */
  private LocalDateTime deadline;
  /** 全部签署完成时间 */
  private LocalDateTime signedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  /** 签署方列表（非表字段，详情查询时填充） */
  @TableField(exist = false)
  private List<ContractSigner> signers;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Long getTemplateId() { return templateId; }
  public void setTemplateId(Long templateId) { this.templateId = templateId; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }
  public String getVariables() { return variables; }
  public void setVariables(String variables) { this.variables = variables; }
  public String getFormat() { return format; }
  public void setFormat(String format) { this.format = format; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDateTime getDeadline() { return deadline; }
  public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
  public LocalDateTime getSignedAt() { return signedAt; }
  public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public List<ContractSigner> getSigners() { return signers; }
  public void setSigners(List<ContractSigner> signers) { this.signers = signers; }
}
