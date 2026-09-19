package com.opencredit.platform.financial.dto;

import com.opencredit.platform.financial.model.RiskIndicatorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskIndicatorResponse {

    private UUID id;
    private RiskIndicatorCode indicatorCode;
    private boolean triggered;
}
