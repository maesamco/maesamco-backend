package com.maesamco.judge.presentation.internal_controller;

import com.maesamco.judge.application.facade.ExecutionValidationFacade;
import com.maesamco.judge.application.result.ExecutionValidationResult;
import com.maesamco.judge.global.response.SuccessResponse;
import com.maesamco.judge.global.security.hmac.AllowedInternalCallers;
import com.maesamco.judge.presentation.request.ExecutionValidateRequest;
import com.maesamco.judge.presentation.response.ExecutionValidateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/executions")
@RequiredArgsConstructor
public class ExecutionInternalController {

    private final ExecutionValidationFacade executionValidationFacade;

    @AllowedInternalCallers({"content-service"})
    @PostMapping
    public SuccessResponse<ExecutionValidateResponse> validate(
            @RequestBody @Valid ExecutionValidateRequest request
    ) {
        List<ExecutionValidationResult> results =
                executionValidationFacade.validate(request.code(), request.toCommands());
        return SuccessResponse.success(ExecutionValidateResponse.from(results));
    }
}
