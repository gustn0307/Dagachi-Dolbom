package com.dagachi.backend.user.report.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCreateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    @DisplayName("REQ-RPT-01, REQ-RPT-02, REQ-RPT-04 - 주소가 null이면 제보 요청 검증에 실패한다")
    void address가_null이면_검증에_실패한다() {
        ReportCreateRequest request = new ReportCreateRequest(
                "제보 내용",
                null,
                null,
                null,
                "010-1234-5678"
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("address")
                                && violation.getMessage().equals("주소는 필수입니다.")
                );
    }

    @Test
    @DisplayName("REQ-RPT-01, REQ-RPT-02, REQ-RPT-04 - 주소가 공백이면 제보 요청 검증에 실패한다")
    void address가_공백이면_검증에_실패한다() {
        ReportCreateRequest request = new ReportCreateRequest(
                "제보 내용",
                "   ",
                null,
                null,
                "010-1234-5678"
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation ->
                        violation.getPropertyPath().toString().equals("address")
                                && violation.getMessage().equals("주소는 필수입니다.")
                );
    }
}