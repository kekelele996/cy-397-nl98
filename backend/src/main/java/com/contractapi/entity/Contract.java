package com.contractapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("contracts")
public class Contract {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Long templateId;
  private String title;
  private String content;
  private String fillParams;
  private String status;
  private LocalDateTime signedAt;
  private String signers;
  private LocalDateTime createdAt;

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
  public String getFillParams() { return fillParams; }
  public void setFillParams(String fillParams) { this.fillParams = fillParams; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDateTime getSignedAt() { return signedAt; }
  public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
  public String getSigners() { return signers; }
  public void setSigners(String signers) { this.signers = signers; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
