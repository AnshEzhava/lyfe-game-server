package com.ansh.lyfegameserver.service.tax;

import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.repository.UserRepository;
import com.ansh.lyfegameserver.service.stock.StockService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Assesses a progressive annual tax on each user's net-worth growth.
 *
 * <p>One game-year = 365 game-days = 365 × 24 real-minutes = 525,600,000 ms (~6.08 real days),
 * on the 60× game clock (1 real-second = 1 game-minute).
 * Each user is taxed independently once their own tax year elapses: profit for the year is
 * {@code currentNetWorth - taxAnchorNetWorth}, taxed with the marginal brackets below and
 * deducted from Branks (never below zero). The anchor is then reset to "now" and the
 * post-tax net worth.
 */
@Service
public class TaxService {

    /** One game-year in real milliseconds (365 game-days × 24 real-minutes on the 60× clock). */
    private static final long GAME_YEAR_MS = 365L * 1_440_000L;

    /**
     * Progressive marginal tax brackets on annual profit (Branks).
     * Each element is {upperBound, rate}; the final bracket uses Long.MAX_VALUE as an open top.
     */
    private static final long[][] BRACKETS = {
        {       100_000L, 0 },   // 0%   on the first 100k
        {     1_000_000L, 10 },  // 10%  on 100k – 1M
        {    10_000_000L, 20 },  // 20%  on 1M – 10M
        {   100_000_000L, 30 },  // 30%  on 10M – 100M
        { Long.MAX_VALUE, 40 },  // 40%  above 100M
    };

    private final UserRepository userRepository;
    private final StockService stockService;

    public TaxService(UserRepository userRepository, StockService stockService) {
        this.userRepository = userRepository;
        this.stockService = stockService;
    }

    /** Called by the scheduler. Taxes every user whose tax year has elapsed. */
    public void collectAnnualTaxes() {
        long now = System.currentTimeMillis();
        List<Users> allUsers = userRepository.findAll();

        for (Users user : allUsers) {
            // Initialize the anchor for legacy users that predate taxation.
            if (user.getTaxAnchorAt() == 0) {
                user.setTaxAnchorAt(now);
                user.setTaxAnchorNetWorth(stockService.computeNetWorth(user));
                userRepository.save(user);
                continue;
            }

            if (now - user.getTaxAnchorAt() < GAME_YEAR_MS) continue;

            long currentNetWorth = stockService.computeNetWorth(user);
            long profit = currentNetWorth - user.getTaxAnchorNetWorth();

            long tax = computeTax(profit);
            if (tax > 0) {
                long paid = Math.min(tax, user.getBranks());
                user.setBranks(user.getBranks() - paid);
                user.setTotalTaxPaid(user.getTotalTaxPaid() + paid);
                com.ansh.lyfegameserver.service.activity.ActivityService.record(
                    user, "TAX", -paid, "Annual tax on " + profit + " profit");
            }

            // Re-anchor for the next year using post-tax net worth.
            user.setTaxAnchorAt(now);
            user.setTaxAnchorNetWorth(stockService.computeNetWorth(user));
            userRepository.save(user);
        }
    }

    /** Progressive marginal tax on a year's profit. Returns 0 for non-positive profit. */
    static long computeTax(long profit) {
        if (profit <= 0) return 0L;

        long tax = 0L;
        long lowerBound = 0L;
        for (long[] bracket : BRACKETS) {
            long upperBound = bracket[0];
            long rate = bracket[1];
            if (profit <= lowerBound) break;

            long taxableInBracket = Math.min(profit, upperBound) - lowerBound;
            tax += (long) Math.floor(taxableInBracket * (rate / 100.0));
            lowerBound = upperBound;
        }
        return tax;
    }
}
