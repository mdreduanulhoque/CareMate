package com.carebridge.caretaker.dto;

import java.util.List;

public record CaretakerApplicationStatusResponse(
        boolean approved,
        boolean canSubmitNewApplication,
        String latestStatus,
        int nextApplicationNumber,
        List<CaretakerApplicationResponse> applications
) {
}
