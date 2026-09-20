package com.contractapi.dto;

import java.util.List;

public record InviteSignersRequest(Long userId, List<SignerInviteRequest> signers) {}
