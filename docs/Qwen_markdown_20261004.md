# Spring Boot Polymorphic REST API & Generic Response Wrapper

This document outlines the architecture and implementation for a Spring Boot REST API that dynamically handles different request and response payloads based on a "product type" (e.g., Personal Loan vs. Vehicle Loan) without using messy conditional logic, wrapped in a standardized generic response envelope.

---

## Part 1: Handling Different Request/Response Types

To handle different request and response bodies based on a "product type" without writing messy `if-else` or `switch` statements, we combine the **Strategy Pattern** with **Jackson’s Polymorphic Deserialization**.

This allows us to have a **single REST endpoint** that automatically maps the incoming JSON to the correct specific Request DTO, routes it to the correct Service, and returns the correct specific Response DTO.

### Step 1: Define the Base and Specific DTOs (Request & Response)
We use Jackson's `@JsonTypeInfo` to tell Spring Boot to look at the `productType` field in the JSON to decide which specific class to instantiate.

```java
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

// --- REQUEST DTOs ---

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "productType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PersonalLoanRequest.class, name = "PERSONAL"),
    @JsonSubTypes.Type(value = VehicleLoanRequest.class, name = "VEHICLE")
})
@Data
public abstract class LoanRequest {
    private String applicantId;
    private double requestedAmount;
    // The 'productType' field is required in the JSON for Jackson to route correctly
    private String productType; 
}

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalLoanRequest extends LoanRequest {
    private String employmentType;
    private int creditScore;
}

@Data
@EqualsAndHashCode(callSuper = true)
public class VehicleLoanRequest extends LoanRequest {
    private String vehicleType; // Car, Bike, Commercial
    private String vehicleModel;
    private int downPayment;
}


// --- RESPONSE DTOs ---

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "productType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PersonalLoanResponse.class, name = "PERSONAL"),
    @JsonSubTypes.Type(value = VehicleLoanResponse.class, name = "VEHICLE")
})
@Data
public abstract class LoanResponse {
    private String applicationId;
    private String status;
    private String productType;
}

@Data
@EqualsAndHashCode(callSuper = true)
public class PersonalLoanResponse extends LoanResponse {
    private String approvedTenureMonths;
}

@Data
@EqualsAndHashCode(callSuper = true)
public class VehicleLoanResponse extends LoanResponse {
    private String vehicleRtoRegistrationStatus;
    private double interestRate;
}
```

### Step 2: Create the Strategy Interface
Define a common interface that all your specific loan services will implement.

```java
public interface LoanProcessingStrategy {
    // Returns the product type this strategy handles (e.g., "PERSONAL", "VEHICLE")
    String getProductType();
    
    // Processes the request and returns a response
    LoanResponse processLoan(LoanRequest request);
}
```

### Step 3: Implement the Specific Services
Create a service for each product type. Because of the Strategy pattern, you can inject specific repositories or external APIs into each service.

```java
import org.springframework.stereotype.Service;

@Service
public class PersonalLoanStrategy implements LoanProcessingStrategy {

    @Override
    public String getProductType() {
        return "PERSONAL";
    }

    @Override
    public LoanResponse processLoan(LoanRequest request) {
        // Cast to specific type to access specific fields
        PersonalLoanRequest pRequest = (PersonalLoanRequest) request;
        
        // TODO: Call personal loan specific logic/repositories
        
        PersonalLoanResponse response = new PersonalLoanResponse();
        response.setApplicationId("PL-" + System.currentTimeMillis());
        response.setStatus("APPROVED");
        response.setProductType("PERSONAL");
        response.setApprovedTenureMonths("36");
        
        return response;
    }
}

@Service
public class VehicleLoanStrategy implements LoanProcessingStrategy {

    @Override
    public String getProductType() {
        return "VEHICLE";
    }

    @Override
    public LoanResponse processLoan(LoanRequest request) {
        VehicleLoanRequest vRequest = (VehicleLoanRequest) request;
        
        // TODO: Call vehicle loan specific logic/repositories
        
        VehicleLoanResponse response = new VehicleLoanResponse();
        response.setApplicationId("VL-" + System.currentTimeMillis());
        response.setStatus("PENDING_VERIFICATION");
        response.setProductType("VEHICLE");
        response.setVehicleRtoRegistrationStatus("INITIATED");
        response.setInterestRate(8.5);
        
        return response;
    }
}
```

### Step 4: Create the Strategy Registry (Context)
Instead of writing `if (type.equals("PERSONAL"))`, we let Spring inject all implementations of `LoanProcessingStrategy` into a `Map`. This makes the code **Open/Closed** (you can add new loan types without modifying existing code).

```java
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LoanServiceContext {

    private final Map<String, LoanProcessingStrategy> strategies;

    // Spring automatically injects all beans that implement LoanProcessingStrategy
    public LoanServiceContext(List<LoanProcessingStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        LoanProcessingStrategy::getProductType, 
                        Function.identity()
                ));
    }

    public LoanProcessingStrategy getStrategy(String productType) {
        LoanProcessingStrategy strategy = strategies.get(productType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported product type: " + productType);
        }
        return strategy;
    }
}
```

### Step 5: The REST Controller
The controller receives the base class, delegates to the context, and returns the base class. Jackson handles the serialization.

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanServiceContext loanServiceContext;

    public LoanController(LoanServiceContext loanServiceContext) {
        this.loanServiceContext = loanServiceContext;
    }

    @PostMapping("/apply")
    public ResponseEntity<LoanResponse> applyForLoan(@RequestBody LoanRequest request) {
        // 1. Get the correct strategy based on the productType in the JSON
        LoanProcessingStrategy strategy = loanServiceContext.getStrategy(request.getProductType());
        
        // 2. Process the loan
        LoanResponse response = strategy.processLoan(request);
        
        // 3. Return response (Jackson will serialize it into the correct JSON structure)
        return ResponseEntity.ok(response);
    }
}
```

### JSON Examples in Practice
Because we used `@JsonTypeInfo`, the client **must** include the `productType` field in the root of the JSON payload.

**Personal Loan Request:**
```json
POST /api/loans/apply
{
    "productType": "PERSONAL",
    "applicantId": "APP123",
    "requestedAmount": 500000,
    "employmentType": "SALARIED",
    "creditScore": 750
}
```
**Personal Loan Response:**
```json
{
    "productType": "PERSONAL",
    "applicationId": "PL-1695123456",
    "status": "APPROVED",
    "approvedTenureMonths": "36"
}
```

---

## Part 2: Standardizing Responses with Generics

Wrapping responses in a generic `ApiResponse<T>` (an "Envelope" pattern) ensures that every API response has a consistent structure regardless of whether it's returning a `PersonalLoanResponse`, a `VehicleLoanResponse`, or an error.

### Step 1: Create the Generic `ApiResponse<T>`
We use Java Generics for the `data` payload and static factory methods to make creating responses clean.

```java
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Prevents sending null fields (like 'errors' on success)
public class ApiResponse<T> {

    private String status;       // e.g., "SUCCESS", "ERROR", "VALIDATION_FAILED"
    private String message;      // Human-readable message
    private LocalDateTime timestamp;
    private T data;              // The generic payload (e.g., PersonalLoanResponse)
    private List<ApiError> errors; // List of errors (if any)

    // --- Static Factory Methods ---

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message("Operation completed successfully")
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .timestamp(LocalDateTime.now())
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, List<ApiError> errors) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();
    }
    
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return error(message, Collections.singletonList(new ApiError(errorCode, message)));
    }
}
```

### Step 2: Create a Structured Error Object
```java
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {
    private String code;    // e.g., "INVALID_CREDIT_SCORE", "MISSING_FIELD"
    private String message; // e.g., "Credit score must be above 650"
}
```

### Step 3: Update the Controller
```java
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanServiceContext loanServiceContext;

    public LoanController(LoanServiceContext loanServiceContext) {
        this.loanServiceContext = loanServiceContext;
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<LoanResponse>> applyForLoan(@RequestBody LoanRequest request) {
        
        LoanProcessingStrategy strategy = loanServiceContext.getStrategy(request.getProductType());
        LoanResponse loanResponse = strategy.processLoan(request);
        
        ApiResponse<LoanResponse> response = ApiResponse.success(
            "Loan application received and is being processed", 
            loanResponse
        );
        
        return ResponseEntity.ok(response);
    }
}
```

### Step 4: Add a Global Exception Handler
Catch **all** exceptions globally and return them in the exact same generic format.

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        ApiResponse<Void> response = ApiResponse.error(
            ex.getMessage(), 
            "INVALID_REQUEST"
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        ApiResponse<Void> response = ApiResponse.error(
            "An unexpected error occurred. Please try again later.", 
            "INTERNAL_SERVER_ERROR"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
```

### Final JSON Output Examples
Because of `@JsonInclude(JsonInclude.Include.NON_NULL)`, the JSON hides `errors` on success and `data` on failure.

**Success:**
```json
{
    "status": "SUCCESS",
    "message": "Loan application received and is being processed",
    "timestamp": "2026-10-05T10:30:00",
    "data": {
        "productType": "PERSONAL",
        "applicationId": "PL-1695123456",
        "status": "APPROVED",
        "approvedTenureMonths": "36"
    }
}
```

**Error:**
```json
{
    "status": "ERROR",
    "message": "Unsupported product type: MORTGAGE",
    "timestamp": "2026-10-05T10:35:00",
    "errors": [
        {
            "code": "INVALID_REQUEST",
            "message": "Unsupported product type: MORTGAGE"
        }
    ]
}
```

---

## Part 3: Architecture Summary (Context Passing)

**Project Goal:** 
Build a Spring Boot REST API that processes different types of loans through a single endpoint, dynamically routing to specific business logic and handling polymorphic request/response JSON payloads, wrapped in a standardized generic response envelope.

**Architecture & Design Patterns Used:**
1. **Jackson Polymorphic Deserialization:** For handling different Request/Response DTOs in a single endpoint.
2. **Strategy Pattern:** For routing business logic based on product type without `if-else` chains.
3. **Generic Envelope Pattern:** For standardizing all API success/error responses.
4. **Global Exception Handling:** Using `@RestControllerAdvice` to enforce the envelope pattern on errors.

**Key Components:**
*   **Polymorphic DTOs:** Base classes `LoanRequest` and `LoanResponse` using `@JsonTypeInfo` and `@JsonSubTypes`. Subclasses contain product-specific fields.
*   **Strategy Pattern:** `LoanProcessingStrategy` interface. Implementations (`PersonalLoanStrategy`, `VehicleLoanStrategy`) cast the base request to access specific fields.
*   **Context/Registry:** `LoanServiceContext` uses Spring constructor injection to build a `Map<String, LoanProcessingStrategy>` at startup for O(1) lookup.
*   **REST Controller:** `POST /api/loans/apply` accepts `LoanRequest`, looks up the strategy, executes it, and wraps the result in `ApiResponse<T>`.
*   **Generic Response Wrapper:** `ApiResponse<T>` contains `status`, `message`, `timestamp`, `data` (Generic `<T>`), and `List<ApiError> errors`. Uses `@JsonInclude(NON_NULL)`.
*   **Global Exception Handler:** `@RestControllerAdvice` catches exceptions and returns them wrapped in `ApiResponse<Void>`.