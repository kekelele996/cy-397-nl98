package com.contractapi.dto;

import java.time.LocalDateTime;

public record SignerInviteRequest(String signerId, String signerName, Integer signOrder, LocalDateTime deadline) {}
