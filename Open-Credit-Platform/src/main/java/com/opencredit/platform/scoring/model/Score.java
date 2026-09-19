package com.opencredit.platform.scoring.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One deterministic scoring of a {@code FinancialAnalysisRun} against a {@link Scorecard}.
 * Unique on {@code (analysisRunId, scorecardId)} — re-scoring the same run under the same
 * scorecard is rejected rather than silently overwritten; scoring under a different (e.g.
 * newly activated) scorecard produces a new row.
 */
@Entity
@Table(name = "score")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Score {

    @Id
    private UUID id;

    @Column(name = "analysis_run_id", nullable = false)
    private UUID analysisRunId;

    @Column(name = "scorecard_id", nullable = false)
    private UUID scorecardId;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_grade", nullable = false)
    private RiskGrade riskGrade;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
