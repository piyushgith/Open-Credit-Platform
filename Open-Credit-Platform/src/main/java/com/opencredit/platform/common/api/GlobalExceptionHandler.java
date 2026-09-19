package com.opencredit.platform.common.api;

import com.opencredit.platform.authority.exception.ApprovalCaseNotFoundException;
import com.opencredit.platform.authority.exception.ApprovalNotRequiredException;
import com.opencredit.platform.authority.exception.AuthorityMatrixEntryNotFoundException;
import com.opencredit.platform.authority.exception.ConcurrentApprovalConflictException;
import com.opencredit.platform.authority.exception.DuplicateApprovalCaseException;
import com.opencredit.platform.authority.exception.DuplicateAuthorityMatrixMatchOrderException;
import com.opencredit.platform.authority.exception.IllegalApprovalTransitionException;
import com.opencredit.platform.authority.exception.InsufficientApprovalAuthorityException;
import com.opencredit.platform.authority.exception.NoMatchingAuthorityMatrixEntryException;
import com.opencredit.platform.authority.exception.SelfApprovalException;
import com.opencredit.platform.customer.exception.CustomerNotFoundException;
import com.opencredit.platform.customer.exception.DuplicateCustomerException;
import com.opencredit.platform.disbursement.exception.ConcurrentDisbursementConflictException;
import com.opencredit.platform.disbursement.exception.DisbursementAlreadyCompletedException;
import com.opencredit.platform.disbursement.exception.DisbursementExceedsSanctionedAmountException;
import com.opencredit.platform.disbursement.exception.DisbursementNotFoundException;
import com.opencredit.platform.disbursement.exception.DuplicateDisbursementRequestException;
import com.opencredit.platform.decision.exception.ConcurrentCreditPolicyActivationException;
import com.opencredit.platform.decision.exception.CreditDecisionNotFoundException;
import com.opencredit.platform.decision.exception.CreditPolicyInUseException;
import com.opencredit.platform.decision.exception.CreditPolicyNotFoundException;
import com.opencredit.platform.decision.exception.CreditRuleNotFoundException;
import com.opencredit.platform.decision.exception.DuplicateCreditDecisionException;
import com.opencredit.platform.decision.exception.DuplicateCreditPolicyNameException;
import com.opencredit.platform.decision.exception.DuplicateCreditRuleFactorException;
import com.opencredit.platform.decision.exception.NoActiveCreditPolicyException;
import com.opencredit.platform.document.exception.IllegalDocumentTransitionException;
import com.opencredit.platform.document.exception.LoanDocumentNotFoundException;
import com.opencredit.platform.financial.exception.FinancialAnalysisRunNotFoundException;
import com.opencredit.platform.financial.exception.FinancialStatementNotFoundException;
import com.opencredit.platform.financial.exception.InvalidFinancialPeriodException;
import com.opencredit.platform.kyc.exception.DuplicateKycCaseException;
import com.opencredit.platform.kyc.exception.IllegalKycTransitionException;
import com.opencredit.platform.kyc.exception.KycCaseNotFoundException;
import com.opencredit.platform.loan.ProductType;
import com.opencredit.platform.loan.exception.IllegalApplicationTransitionException;
import com.opencredit.platform.loan.exception.LoanApplicationNotFoundException;
import com.opencredit.platform.loan.exception.LoanProductConstraintViolationException;
import com.opencredit.platform.loan.exception.LoanProductNotFoundException;
import com.opencredit.platform.loan.exception.UnsupportedProductTypeException;
import com.opencredit.platform.offer.exception.DuplicateSanctionException;
import com.opencredit.platform.offer.exception.NoOfferSelectedException;
import com.opencredit.platform.offer.exception.OfferAlreadySelectedException;
import com.opencredit.platform.offer.exception.OfferNotEligibleException;
import com.opencredit.platform.offer.exception.OfferNotEligibleForSelectionException;
import com.opencredit.platform.offer.exception.OfferNotFoundException;
import com.opencredit.platform.offer.exception.SanctionNotFoundException;
import com.opencredit.platform.scoring.exception.ConcurrentScorecardActivationException;
import com.opencredit.platform.scoring.exception.DuplicateScoreException;
import com.opencredit.platform.scoring.exception.DuplicateScorecardNameException;
import com.opencredit.platform.scoring.exception.InvalidScorecardRangeException;
import com.opencredit.platform.scoring.exception.InvalidScorecardRuleRangeException;
import com.opencredit.platform.scoring.exception.NoActiveScorecardException;
import com.opencredit.platform.scoring.exception.OverlappingScorecardRuleException;
import com.opencredit.platform.scoring.exception.ScoreNotFoundException;
import com.opencredit.platform.scoring.exception.ScorecardInUseException;
import com.opencredit.platform.scoring.exception.ScorecardNotFoundException;
import com.opencredit.platform.scoring.exception.ScorecardRuleNotFoundException;
import com.opencredit.platform.underwriting.exception.IllegalUnderwritingAttemptTransitionException;
import com.opencredit.platform.underwriting.exception.InvalidUnderwritingStateException;
import com.opencredit.platform.security.exception.InvalidCredentialsException;
import com.opencredit.platform.underwriting.exception.UnderwritingCaseNotFoundException;
import com.opencredit.platform.underwriting.exception.UnderwritingNotRetryableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidTypeIdException;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ensures the {@link ApiResponse} envelope holds on every error path, not just on success.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@code @PreAuthorize} throws {@link AccessDeniedException} from inside the controller
     * method's own AOP proxy, so it surfaces here — through normal Spring MVC exception handling —
     * rather than through {@code SecurityConfig}'s {@code RestAccessDeniedHandler}, which only
     * sees an {@code authorizeHttpRequests} denial (one that never reaches a controller method at
     * all). Without this handler, this specific case would fall through to
     * {@link #handleUnexpected}: a wrong-role request would come back as 500, not 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "You do not have the required role for this operation",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> Objects.requireNonNullElse(fe.getDefaultMessage(), "invalid"),
                        (a, b) -> a
                ));

        ApiError error = ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "VALIDATION_ERROR",
                "Request validation failed",
                request.getRequestURI()
        ).withFieldErrors(fieldErrors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(error));
    }

    /**
     * An unrecognized {@code productType} never reaches {@code LoanServiceContext}: Jackson
     * fails during deserialization with {@link InvalidTypeIdException}, which Spring wraps
     * here. We unwrap the cause to tell that apart from ordinary malformed JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex,
                                                                HttpServletRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof InvalidTypeIdException typeIdEx) {
            String supported = Arrays.stream(ProductType.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            ApiError error = ApiError.of(
                    HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_PRODUCT_TYPE",
                    "Unsupported productType '" + typeIdEx.getTypeId() + "'. Supported values: " + supported,
                    request.getRequestURI()
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
        }

        ApiError error = ApiError.of(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Request body is malformed or missing required fields",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(UnsupportedProductTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedProductType(UnsupportedProductTypeException ex,
                                                                            HttpServletRequest request) {
        log.warn("Product type recognized but no strategy bean registered: {}", ex.getProductType());
        ApiError error = ApiError.of(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_PRODUCT_TYPE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(LoanApplicationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(LoanApplicationNotFoundException ex,
                                                               HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "LOAN_APPLICATION_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomerNotFound(CustomerNotFoundException ex,
                                                                       HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "CUSTOMER_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateCustomer(DuplicateCustomerException ex,
                                                                        HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_CUSTOMER",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                         HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(LoanProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoanProductNotFound(LoanProductNotFoundException ex,
                                                                          HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "LOAN_PRODUCT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(LoanProductConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoanProductConstraintViolation(
            LoanProductConstraintViolationException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "PRODUCT_CONSTRAINT_VIOLATION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(IllegalApplicationTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalApplicationTransition(
            IllegalApplicationTransitionException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "ILLEGAL_APPLICATION_TRANSITION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(LoanDocumentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleLoanDocumentNotFound(LoanDocumentNotFoundException ex,
                                                                          HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "LOAN_DOCUMENT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(IllegalDocumentTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalDocumentTransition(IllegalDocumentTransitionException ex,
                                                                               HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "ILLEGAL_DOCUMENT_TRANSITION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(KycCaseNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleKycCaseNotFound(KycCaseNotFoundException ex,
                                                                      HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "KYC_CASE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateKycCaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKycCase(DuplicateKycCaseException ex,
                                                                       HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_KYC_CASE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(IllegalKycTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalKycTransition(IllegalKycTransitionException ex,
                                                                          HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "ILLEGAL_KYC_TRANSITION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(UnderwritingCaseNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnderwritingCaseNotFound(UnderwritingCaseNotFoundException ex,
                                                                              HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "UNDERWRITING_CASE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(InvalidUnderwritingStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidUnderwritingState(InvalidUnderwritingStateException ex,
                                                                              HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "INVALID_UNDERWRITING_STATE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(UnderwritingNotRetryableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnderwritingNotRetryable(UnderwritingNotRetryableException ex,
                                                                              HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "UNDERWRITING_NOT_RETRYABLE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(IllegalUnderwritingAttemptTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalUnderwritingAttemptTransition(
            IllegalUnderwritingAttemptTransitionException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "ILLEGAL_UNDERWRITING_ATTEMPT_TRANSITION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(FinancialStatementNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFinancialStatementNotFound(FinancialStatementNotFoundException ex,
                                                                                 HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "FINANCIAL_STATEMENT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(InvalidFinancialPeriodException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFinancialPeriod(InvalidFinancialPeriodException ex,
                                                                             HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "INVALID_FINANCIAL_PERIOD",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(FinancialAnalysisRunNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFinancialAnalysisRunNotFound(FinancialAnalysisRunNotFoundException ex,
                                                                                    HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "FINANCIAL_ANALYSIS_RUN_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(NoActiveScorecardException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoActiveScorecard(NoActiveScorecardException ex,
                                                                        HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "NO_ACTIVE_SCORECARD",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateScoreException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateScore(DuplicateScoreException ex,
                                                                     HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_SCORE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ScoreNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleScoreNotFound(ScoreNotFoundException ex,
                                                                     HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "SCORE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ScorecardNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleScorecardNotFound(ScorecardNotFoundException ex,
                                                                        HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "SCORECARD_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateScorecardNameException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateScorecardName(DuplicateScorecardNameException ex,
                                                                             HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_SCORECARD_NAME",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(InvalidScorecardRangeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidScorecardRange(InvalidScorecardRangeException ex,
                                                                            HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "INVALID_SCORECARD_RANGE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(InvalidScorecardRuleRangeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidScorecardRuleRange(InvalidScorecardRuleRangeException ex,
                                                                                HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "INVALID_SCORECARD_RULE_RANGE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(OverlappingScorecardRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleOverlappingScorecardRule(OverlappingScorecardRuleException ex,
                                                                               HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "OVERLAPPING_SCORECARD_RULE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ScorecardRuleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleScorecardRuleNotFound(ScorecardRuleNotFoundException ex,
                                                                            HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "SCORECARD_RULE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ConcurrentScorecardActivationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentScorecardActivation(
            ConcurrentScorecardActivationException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "CONCURRENT_SCORECARD_ACTIVATION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ScorecardInUseException.class)
    public ResponseEntity<ApiResponse<Void>> handleScorecardInUse(ScorecardInUseException ex,
                                                                     HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "SCORECARD_IN_USE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(NoActiveCreditPolicyException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoActiveCreditPolicy(NoActiveCreditPolicyException ex,
                                                                           HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "NO_ACTIVE_CREDIT_POLICY",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateCreditDecisionException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateCreditDecision(DuplicateCreditDecisionException ex,
                                                                              HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_CREDIT_DECISION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(CreditPolicyNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditPolicyNotFound(CreditPolicyNotFoundException ex,
                                                                           HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "CREDIT_POLICY_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateCreditPolicyNameException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateCreditPolicyName(DuplicateCreditPolicyNameException ex,
                                                                                HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_CREDIT_POLICY_NAME",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(CreditRuleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditRuleNotFound(CreditRuleNotFoundException ex,
                                                                         HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "CREDIT_RULE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateCreditRuleFactorException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateCreditRuleFactor(DuplicateCreditRuleFactorException ex,
                                                                                HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_CREDIT_RULE_FACTOR",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ConcurrentCreditPolicyActivationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentCreditPolicyActivation(
            ConcurrentCreditPolicyActivationException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "CONCURRENT_CREDIT_POLICY_ACTIVATION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(CreditPolicyInUseException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditPolicyInUse(CreditPolicyInUseException ex,
                                                                         HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "CREDIT_POLICY_IN_USE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(CreditDecisionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCreditDecisionNotFound(CreditDecisionNotFoundException ex,
                                                                             HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "CREDIT_DECISION_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(AuthorityMatrixEntryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorityMatrixEntryNotFound(AuthorityMatrixEntryNotFoundException ex,
                                                                                   HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "AUTHORITY_MATRIX_ENTRY_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(NoMatchingAuthorityMatrixEntryException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoMatchingAuthorityMatrixEntry(NoMatchingAuthorityMatrixEntryException ex,
                                                                                     HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "NO_MATCHING_AUTHORITY_MATRIX_ENTRY",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ApprovalCaseNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleApprovalCaseNotFound(ApprovalCaseNotFoundException ex,
                                                                           HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "APPROVAL_CASE_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateApprovalCaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateApprovalCase(DuplicateApprovalCaseException ex,
                                                                            HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_APPROVAL_CASE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateAuthorityMatrixMatchOrderException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateAuthorityMatrixMatchOrder(
            DuplicateAuthorityMatrixMatchOrderException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_AUTHORITY_MATRIX_MATCH_ORDER",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ApprovalNotRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleApprovalNotRequired(ApprovalNotRequiredException ex,
                                                                          HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "APPROVAL_NOT_REQUIRED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(IllegalApprovalTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalApprovalTransition(IllegalApprovalTransitionException ex,
                                                                                HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "ILLEGAL_APPROVAL_TRANSITION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(SelfApprovalException.class)
    public ResponseEntity<ApiResponse<Void>> handleSelfApproval(SelfApprovalException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "SELF_APPROVAL_NOT_ALLOWED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(InsufficientApprovalAuthorityException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientApprovalAuthority(InsufficientApprovalAuthorityException ex,
                                                                                    HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_APPROVAL_AUTHORITY",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ConcurrentApprovalConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentApprovalConflict(ConcurrentApprovalConflictException ex,
                                                                                 HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "CONCURRENT_APPROVAL_CONFLICT",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(OfferNotEligibleException.class)
    public ResponseEntity<ApiResponse<Void>> handleOfferNotEligible(OfferNotEligibleException ex,
                                                                       HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "OFFER_NOT_ELIGIBLE",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(OfferNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOfferNotFound(OfferNotFoundException ex,
                                                                     HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "OFFER_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(OfferNotEligibleForSelectionException.class)
    public ResponseEntity<ApiResponse<Void>> handleOfferNotEligibleForSelection(
            OfferNotEligibleForSelectionException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "OFFER_NOT_ELIGIBLE_FOR_SELECTION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(OfferAlreadySelectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOfferAlreadySelected(OfferAlreadySelectedException ex,
                                                                           HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "OFFER_ALREADY_SELECTED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(NoOfferSelectedException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoOfferSelected(NoOfferSelectedException ex,
                                                                       HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "NO_OFFER_SELECTED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateSanctionException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateSanction(DuplicateSanctionException ex,
                                                                        HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_SANCTION",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(SanctionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSanctionNotFound(SanctionNotFoundException ex,
                                                                       HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "SANCTION_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DisbursementNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisbursementNotFound(DisbursementNotFoundException ex,
                                                                           HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.NOT_FOUND,
                "DISBURSEMENT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DisbursementAlreadyCompletedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisbursementAlreadyCompleted(
            DisbursementAlreadyCompletedException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DISBURSEMENT_ALREADY_COMPLETED",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DisbursementExceedsSanctionedAmountException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisbursementExceedsSanctionedAmount(
            DisbursementExceedsSanctionedAmountException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "DISBURSEMENT_EXCEEDS_SANCTIONED_AMOUNT",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(DuplicateDisbursementRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateDisbursementRequest(
            DuplicateDisbursementRequestException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "DUPLICATE_DISBURSEMENT_REQUEST",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(ConcurrentDisbursementConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentDisbursementConflict(
            ConcurrentDisbursementConflictException ex, HttpServletRequest request) {
        ApiError error = ApiError.of(
                HttpStatus.CONFLICT,
                "CONCURRENT_DISBURSEMENT_CONFLICT",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request {}", request.getRequestURI(), ex);
        ApiError error = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(error));
    }
}
