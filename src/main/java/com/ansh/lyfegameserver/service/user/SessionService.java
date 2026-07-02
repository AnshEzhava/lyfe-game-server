package com.ansh.lyfegameserver.service.user;

import com.ansh.lyfegameserver.catalog.CourseDefinition;
import com.ansh.lyfegameserver.catalog.GameCatalog;
import com.ansh.lyfegameserver.data.NewsItem;
import com.ansh.lyfegameserver.data.UserCourse;
import com.ansh.lyfegameserver.data.UserSettings;
import com.ansh.lyfegameserver.data.UserStockHolding;
import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.dto.user.NewsHighlight;
import com.ansh.lyfegameserver.dto.user.UserResponse;
import com.ansh.lyfegameserver.dto.user.WhileAwaySummary;
import com.ansh.lyfegameserver.repository.NewsItemRepository;
import com.ansh.lyfegameserver.repository.UserRepository;
import com.ansh.lyfegameserver.service.stock.StockService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the "while you were away" summary and applies the AFK automations (auto-claim wages,
 * auto-reinvest into the government bond) when a player resumes their session.
 *
 * <p>Summary figures are computed from lifetime counters versus the snapshot taken at
 * {@code lastSeenAt}, so they stay accurate no matter how long the player was away.
 */
@Service
public class SessionService {

    /** 1 game-day = 24 real-minutes on the 60× clock (see StockScheduler). */
    private static final long GAME_DAY_MS = 1_440_000L;

    private final UserRepository userRepository;
    private final UserService userService;
    private final StockService stockService;
    private final NewsItemRepository newsItemRepository;
    private final GameCatalog gameCatalog;

    public SessionService(UserRepository userRepository,
                          UserService userService,
                          StockService stockService,
                          NewsItemRepository newsItemRepository,
                          GameCatalog gameCatalog) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.stockService = stockService;
        this.newsItemRepository = newsItemRepository;
        this.gameCatalog = gameCatalog;
    }

    public WhileAwaySummary resume(String clerkId) {
        Users user = requireUser(clerkId);

        long now = System.currentTimeMillis();
        long oldLastSeen = user.getLastSeenAt();
        long oldNetWorth = user.getLastSeenNetWorth();
        long oldWages = user.getLifetimeWagesEarned();
        long oldBond = user.getLifetimeBondYield();
        long oldTax = user.getTotalTaxPaid();

        long elapsedMs = Math.max(0, now - oldLastSeen);
        long gameDaysAway = elapsedMs / GAME_DAY_MS;

        UserSettings settings = user.getSettings() != null ? user.getSettings() : new UserSettings();

        boolean autoClaimed = false;
        long autoReinvested = 0L;

        // Auto-claim wages (and optionally auto-reinvest them into the bond). Both are best-effort.
        if (settings.isAutoClaimWages() && user.getActiveJob() != null) {
            try {
                long claimed = userService.claimWage(clerkId).wages();
                if (claimed > 0) {
                    autoClaimed = true;
                    if (settings.isAutoReinvest()) {
                        autoReinvested = stockService.reinvestIntoBond(clerkId, claimed);
                    }
                }
            } catch (Exception ignored) {
                // no active job, cooldown, etc. — skip automation silently
            }
        }

        // Reload after any sub-operations saved the user, so deltas and re-anchoring use fresh state.
        user = requireUser(clerkId);

        long wagesEarned = Math.max(0, user.getLifetimeWagesEarned() - oldWages);
        long bondYield = Math.max(0, user.getLifetimeBondYield() - oldBond);
        long taxPaid = Math.max(0, user.getTotalTaxPaid() - oldTax);
        long currentNetWorth = stockService.computeNetWorth(user);
        long netWorthChange = currentNetWorth - oldNetWorth;

        List<NewsHighlight> highlights = buildNewsHighlights(user, oldLastSeen);
        String courseReady = courseReadyToClaim(user, now);

        boolean hasSummary = gameDaysAway >= 1 && (
            wagesEarned > 0 || bondYield > 0 || taxPaid > 0
                || netWorthChange != 0 || !highlights.isEmpty() || courseReady != null);

        // Re-anchor the snapshot for the next return.
        user.setLastSeenAt(now);
        user.setLastSeenBranks(user.getBranks());
        user.setLastSeenNetWorth(currentNetWorth);
        Users saved = userRepository.save(user);

        UserResponse userResp = new UserResponse(
            saved.getId(), saved.getDisplayName(), saved.getBranks(), saved.getStats(),
            currentNetWorth, saved.getTotalTaxPaid());

        return new WhileAwaySummary(
            0, "Success", hasSummary, gameDaysAway,
            wagesEarned, bondYield, taxPaid, netWorthChange,
            autoClaimed, autoReinvested, highlights, courseReady, userResp);
    }

    /** Top 3 company news items (by absolute impact) that hit the player's holdings since sinceMs. */
    private List<NewsHighlight> buildNewsHighlights(Users user, long sinceMs) {
        Set<String> heldStockIds = new HashSet<>();
        for (UserStockHolding h : user.getStockHoldings()) {
            if (h.getSharesOwned() > 0) heldStockIds.add(h.getStockId());
        }
        if (user.getIpo() != null) heldStockIds.add(user.getIpo().getStockId());
        if (heldStockIds.isEmpty()) return List.of();

        return newsItemRepository.findByPublishedAtGreaterThanEqualOrderByPublishedAtDesc(sinceMs).stream()
            .filter(n -> "COMPANY".equals(n.getTargetType()) && heldStockIds.contains(n.getTargetId()))
            .sorted((a, b) -> Double.compare(Math.abs(b.getImpactPct()), Math.abs(a.getImpactPct())))
            .limit(3)
            .map(n -> new NewsHighlight(n.getHeadline(), n.getTicker(), n.getImpactPct()))
            .toList();
    }

    private String courseReadyToClaim(Users user, long now) {
        UserCourse c = user.getActiveCourse();
        if (c == null || c.isRewardClaimed() || now < c.getCompletesAt()) return null;
        return gameCatalog.findCourse(c.getCourseId())
            .map(CourseDefinition::getName)
            .orElse(c.getCourseId());
    }

    private Users requireUser(String clerkId) {
        return userRepository.findByClerkId(clerkId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
