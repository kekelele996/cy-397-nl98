package com.contractapi.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.contractapi.entity.ContractSigner;

public record ContractDetail(
    Long id,
    Long userId,
    Long templateId,
    String title,
    String content,
    String fillParams,
    String status,
    LocalDateTime signedAt,
    List<ContractSigner> signers) {}
