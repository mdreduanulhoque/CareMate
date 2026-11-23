package com.carebridge.feed;

import com.carebridge.caretaker.RateType;
import com.carebridge.feed.dto.FeedCaretakerDetailResponse;
import com.carebridge.feed.dto.FeedPageResponse;
import com.carebridge.security.CareBridgeUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/feed/caretakers")
@PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
public class HuntersFeedController {

    private final HuntersFeedService feedService;

    public HuntersFeedController(HuntersFeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ResponseEntity<FeedPageResponse> searchFeed(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @RequestParam(name = "availableOnly", defaultValue = "true") boolean availableOnly,
            @RequestParam(name = "rateType", required = false) RateType rateType,
            @RequestParam(name = "maxBudget", required = false) BigDecimal maxBudget,
            @RequestParam(name = "area", required = false) String area,
            @RequestParam(name = "minRating", required = false) BigDecimal minRating,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sort", defaultValue = "rating") String sort,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size
    ) {
        return ResponseEntity.ok(feedService.searchFeed(
                principal,
                availableOnly,
                rateType,
                maxBudget,
                area,
                minRating,
                keyword,
                sort,
                page,
                size
        ));
    }

    @GetMapping("/{caretakerUserId}")
    public ResponseEntity<FeedCaretakerDetailResponse> getCaretakerDetail(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long caretakerUserId
    ) {
        return ResponseEntity.ok(feedService.getCaretakerPublicDetail(principal, caretakerUserId));
    }
}
