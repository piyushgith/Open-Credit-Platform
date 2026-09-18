package com.opencredit.platform.kyc.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KycRejectRequest {

    @Size(max = 500)
    private String remarks;
}
