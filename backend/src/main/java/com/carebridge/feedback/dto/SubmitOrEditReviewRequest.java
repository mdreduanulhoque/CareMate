package com.carebridge.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitOrEditReviewRequest(
        @NotNull(message = "Please choose a star rating from 1 to 5.")
        @Min(value = 1, message = "Star rating must be at least 1.")
        @Max(value = 5, message = "Star rating cannot exceed 5.")
        Integer rating,

        @Size(max = 2000, message = "Written review must be 2000 characters or fewer.")
        String comment,

        @NotNull(message = "Please answer Yes or No to the repeat engagement question.")
        Boolean wouldEngageAgain
) {
}
