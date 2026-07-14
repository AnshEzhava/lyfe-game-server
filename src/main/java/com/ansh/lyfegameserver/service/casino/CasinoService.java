package com.ansh.lyfegameserver.service.casino;

import com.ansh.lyfegameserver.data.Users;
import com.ansh.lyfegameserver.dto.casino.CasinoPlayRequest;
import com.ansh.lyfegameserver.dto.casino.CasinoPlayResponse;
import com.ansh.lyfegameserver.dto.user.UserResponse;
import com.ansh.lyfegameserver.repository.UserRepository;
import com.ansh.lyfegameserver.service.activity.ActivityService;
import com.ansh.lyfegameserver.service.stock.StockService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * House-run casino: the player bets Branks against the house across a few games. All outcomes are
 * computed server-side (never trust the client), the balance is updated atomically, and each play
 * is written to the activity feed. Every game carries a small house edge, so the casino is a net
 * Branks sink for the economy.
 */
@Service
public class CasinoService {

    private static final long MIN_BET = 10L;
    private static final long MAX_BET = 1_000_000L;

    /** Correct HIGH/LOW or coin call pays this multiple (fair would be 2.0 → ~2.5% edge). */
    private static final double EVEN_MONEY_PAYOUT = 1.95;
    /** Exact single-die guess pays this (fair would be 6.0). */
    private static final double DICE_EXACT_PAYOUT = 5.0;

    private static final String[] SLOT_SYMBOLS = { "CHERRY", "LEMON", "BELL", "BAR", "STAR", "SEVEN" };

    /** Roulette: single 0 pocket is the whole house edge (~2.7%), so we pay true odds. */
    static final double ROULETTE_EVEN_RETURN = 2.0;   // red/black/odd/even/high/low
    static final double ROULETTE_DOZEN_RETURN = 3.0;  // 1st/2nd/3rd 12 and columns (2:1)
    static final double ROULETTE_STRAIGHT_RETURN = 36.0; // single number = 35:1

    /** Plinko: 8 rows -> 9 buckets. Per-risk return multipliers, index = number of right-bounces.
     *  Tuned so expected return < 1 (see CasinoServiceTest). ponytail: hand-tuned tables; if the
     *  edge needs to be exact, compute from the binomial weights and solve, don't eyeball. */
    static final int PLINKO_ROWS = 8;
    static final double[] PLINKO_LOW  = { 5.6, 2.1, 1.1, 1.0, 0.5, 1.0, 1.1, 2.1, 5.6 };
    static final double[] PLINKO_MED  = { 13,  3,   1.3, 0.7, 0.4, 0.7, 1.3, 3,   13 };
    static final double[] PLINKO_HIGH = { 29,  4,   1.5, 0.3, 0.2, 0.3, 1.5, 4,   29 };

    private final UserRepository userRepository;
    private final StockService stockService;
    private final Random rng = new Random();

    public CasinoService(UserRepository userRepository, StockService stockService) {
        this.userRepository = userRepository;
        this.stockService = stockService;
    }

    public CasinoPlayResponse play(String clerkId, CasinoPlayRequest req) {
        Users user = userRepository.findByClerkId(clerkId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        long bet = req.bet();
        if (bet < MIN_BET) throw new IllegalArgumentException("Minimum bet is " + MIN_BET + " Branks.");
        if (bet > MAX_BET) throw new IllegalArgumentException("Maximum bet is " + MAX_BET + " Branks.");
        if (user.getBranks() < bet) throw new IllegalStateException("Insufficient Branks for this bet.");

        String game = req.game() == null ? "" : req.game().toUpperCase();
        Result result = switch (game) {
            case "COINFLIP" -> coinFlip(bet, req.choice());
            case "DICE" -> dice(bet, req.choice());
            case "SLOTS" -> slots(bet);
            case "ROULETTE" -> rouletteResult(bet, req.choice(), rng);
            case "PLINKO" -> plinkoResult(bet, req.choice(), rng);
            default -> throw new IllegalArgumentException("Unknown game: " + req.game());
        };

        // Settle: stake was already committed; add the payout back (0 on a loss).
        user.setBranks(user.getBranks() - bet + result.payout());
        long net = result.payout() - bet;
        ActivityService.record(user, "CASINO", net,
            capitalize(game) + (result.win() ? " — win" : " — loss"));
        Users saved = userRepository.save(user);

        UserResponse userResp = new UserResponse(
            saved.getId(), saved.getDisplayName(), saved.getBranks(), saved.getStats(),
            stockService.computeNetWorth(saved), saved.getTotalTaxPaid());

        return new CasinoPlayResponse(0, "OK", game, result.win(), bet, result.payout(), net,
            result.outcome(), result.reels(), userResp);
    }

    record Result(boolean win, long payout, String outcome, List<String> reels) {}

    private Result coinFlip(long bet, String choice) {
        String call = "TAILS".equalsIgnoreCase(choice) ? "TAILS" : "HEADS";
        String landed = rng.nextBoolean() ? "HEADS" : "TAILS";
        boolean win = landed.equals(call);
        long payout = win ? Math.round(bet * EVEN_MONEY_PAYOUT) : 0L;
        return new Result(win, payout, landed, List.of());
    }

    private Result dice(long bet, String choice) {
        int roll = rng.nextInt(6) + 1;
        String c = choice == null ? "HIGH" : choice.toUpperCase();
        boolean win;
        long payout;
        if ("HIGH".equals(c)) {
            win = roll >= 4;
            payout = win ? Math.round(bet * EVEN_MONEY_PAYOUT) : 0L;
        } else if ("LOW".equals(c)) {
            win = roll <= 3;
            payout = win ? Math.round(bet * EVEN_MONEY_PAYOUT) : 0L;
        } else {
            // Exact number guess (1-6)
            int target;
            try {
                target = Integer.parseInt(c);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Dice choice must be HIGH, LOW, or 1-6.");
            }
            if (target < 1 || target > 6) throw new IllegalArgumentException("Dice number must be 1-6.");
            win = roll == target;
            payout = win ? Math.round(bet * DICE_EXACT_PAYOUT) : 0L;
        }
        return new Result(win, payout, String.valueOf(roll), List.of());
    }

    /**
     * European single-zero roulette. {@code choice} is an outside bet
     * (RED/BLACK/ODD/EVEN/HIGH/LOW, pays 2.0×) or a straight number "0".."36" (pays 36×).
     * The lone 0 pocket is the house edge, so outside/straight both pay true odds.
     * {@code reels} carries the landed colour so the client can render it.
     */
    Result rouletteResult(long bet, String choice, Random r) {
        int n = r.nextInt(37); // 0..36
        String color = n == 0 ? "GREEN" : (isRedNumber(n) ? "RED" : "BLACK");
        String c = choice == null ? "" : choice.trim().toUpperCase();

        boolean win;
        double payoutMult;
        switch (c) {
            case "RED"   -> { win = "RED".equals(color);   payoutMult = ROULETTE_EVEN_RETURN; }
            case "BLACK" -> { win = "BLACK".equals(color); payoutMult = ROULETTE_EVEN_RETURN; }
            case "ODD"   -> { win = n != 0 && n % 2 == 1;  payoutMult = ROULETTE_EVEN_RETURN; }
            case "EVEN"  -> { win = n != 0 && n % 2 == 0;  payoutMult = ROULETTE_EVEN_RETURN; }
            case "LOW"   -> { win = n >= 1 && n <= 18;     payoutMult = ROULETTE_EVEN_RETURN; }
            case "HIGH"  -> { win = n >= 19 && n <= 36;    payoutMult = ROULETTE_EVEN_RETURN; }
            case "DOZEN1" -> { win = n >= 1 && n <= 12;    payoutMult = ROULETTE_DOZEN_RETURN; }
            case "DOZEN2" -> { win = n >= 13 && n <= 24;   payoutMult = ROULETTE_DOZEN_RETURN; }
            case "DOZEN3" -> { win = n >= 25 && n <= 36;   payoutMult = ROULETTE_DOZEN_RETURN; }
            case "COL1"  -> { win = n != 0 && n % 3 == 1;  payoutMult = ROULETTE_DOZEN_RETURN; }
            case "COL2"  -> { win = n != 0 && n % 3 == 2;  payoutMult = ROULETTE_DOZEN_RETURN; }
            case "COL3"  -> { win = n != 0 && n % 3 == 0;  payoutMult = ROULETTE_DOZEN_RETURN; }
            default -> {
                int target;
                try {
                    target = Integer.parseInt(c);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Roulette bet must be RED/BLACK/ODD/EVEN/HIGH/LOW/DOZEN1-3/COL1-3 or 0-36.");
                }
                if (target < 0 || target > 36) throw new IllegalArgumentException("Roulette number must be 0-36.");
                win = n == target;
                payoutMult = ROULETTE_STRAIGHT_RETURN;
            }
        }
        long payout = win ? Math.round(bet * payoutMult) : 0L;
        return new Result(win, payout, String.valueOf(n), List.of(color));
    }

    /** Red pockets on a European wheel (the rest 1-36 are black). */
    private boolean isRedNumber(int n) {
        return switch (n) {
            case 1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36 -> true;
            default -> false;
        };
    }

    /**
     * Plinko: drop a ball through {@link #PLINKO_ROWS} rows of pegs. Each row bounces left or
     * right 50/50; the landing bucket = number of right-bounces (0..rows). {@code choice} picks the
     * risk table. {@code outcome} is the multiplier, {@code reels} is the L/R path so the client can
     * animate the exact drop.
     */
    Result plinkoResult(long bet, String choice, Random r) {
        double[] table = switch (choice == null ? "" : choice.trim().toUpperCase()) {
            case "LOW" -> PLINKO_LOW;
            case "MED", "MEDIUM" -> PLINKO_MED;
            case "HIGH" -> PLINKO_HIGH;
            default -> throw new IllegalArgumentException("Plinko risk must be LOW, MED, or HIGH.");
        };
        List<String> path = new java.util.ArrayList<>(PLINKO_ROWS);
        int rights = 0;
        for (int i = 0; i < PLINKO_ROWS; i++) {
            boolean right = r.nextBoolean();
            path.add(right ? "R" : "L");
            if (right) rights++;
        }
        double mult = table[rights];
        long payout = Math.round(bet * mult);
        // "Win" = you came out ahead; center buckets pay <1× so they still count as a loss.
        return new Result(payout > bet, payout, formatMult(mult), path);
    }

    private String formatMult(double m) {
        return (m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m)) + "x";
    }

    private Result slots(long bet) {
        String r1 = SLOT_SYMBOLS[rng.nextInt(SLOT_SYMBOLS.length)];
        String r2 = SLOT_SYMBOLS[rng.nextInt(SLOT_SYMBOLS.length)];
        String r3 = SLOT_SYMBOLS[rng.nextInt(SLOT_SYMBOLS.length)];
        List<String> reels = List.of(r1, r2, r3);

        double multiplier;
        if (r1.equals(r2) && r2.equals(r3)) {
            multiplier = threeOfAKindMultiplier(r1);
        } else if (r1.equals(r2) || r2.equals(r3) || r1.equals(r3)) {
            multiplier = 1.5; // any pair returns a small win
        } else {
            multiplier = 0;
        }
        long payout = Math.round(bet * multiplier);
        return new Result(payout > 0, payout, payout > 0 ? "WIN" : "LOSS", reels);
    }

    private double threeOfAKindMultiplier(String symbol) {
        return switch (symbol) {
            case "SEVEN" -> 25;
            case "STAR" -> 15;
            case "BAR" -> 12;
            case "BELL" -> 10;
            case "LEMON" -> 8;
            default -> 6; // CHERRY
        };
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
