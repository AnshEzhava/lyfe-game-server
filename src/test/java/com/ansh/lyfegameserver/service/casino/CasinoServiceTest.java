package com.ansh.lyfegameserver.service.casino;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Money-path self-check for the two new casino games. Pure math, no Spring/Mongo — the outcome
 * helpers only use their {@link Random} argument and the static tuning tables, so a service built
 * with null collaborators is enough. The one invariant that must never regress: every game returns
 * the house less than the stake in expectation (edge > 0).
 */
class CasinoServiceTest {

    private final CasinoService casino = new CasinoService(null, null);

    /** Expected return of a plinko table = sum over buckets of P(bucket) * multiplier. */
    private static double plinkoExpectedReturn(double[] table) {
        int n = CasinoService.PLINKO_ROWS;
        double total = Math.pow(2, n);
        double exp = 0;
        for (int k = 0; k <= n; k++) {
            exp += (binomial(n, k) / total) * table[k];
        }
        return exp;
    }

    private static double binomial(int n, int k) {
        double c = 1;
        for (int i = 0; i < k; i++) c = c * (n - i) / (i + 1);
        return c;
    }

    @Test
    void plinkoTablesHaveHouseEdge() {
        for (double[] table : new double[][]{
                CasinoService.PLINKO_LOW, CasinoService.PLINKO_MED, CasinoService.PLINKO_HIGH }) {
            assertEquals(CasinoService.PLINKO_ROWS + 1, table.length, "one multiplier per bucket");
            double er = plinkoExpectedReturn(table);
            assertTrue(er < 1.0, "expected return must be < 1 (house edge), was " + er);
            assertTrue(er > 0.90, "edge shouldn't be punishing, was " + er); // sanity
        }
    }

    @Test
    void rouletteBetsHaveHouseEdge() {
        assertTrue(CasinoService.ROULETTE_EVEN_RETURN * 18.0 / 37.0 < 1.0);   // red/black/odd/even/high/low
        assertTrue(CasinoService.ROULETTE_DOZEN_RETURN * 12.0 / 37.0 < 1.0);  // dozens + columns (2:1)
        assertTrue(CasinoService.ROULETTE_STRAIGHT_RETURN * 1.0 / 37.0 < 1.0); // single number
    }

    @Test
    void rouletteDozenAndColumnResolveCorrectly() {
        Random seeded = new Random(3);
        for (int i = 0; i < 300; i++) {
            CasinoService.Result d = casino.rouletteResult(100, "DOZEN2", seeded);
            int dn = Integer.parseInt(d.outcome());
            assertEquals(dn >= 13 && dn <= 24, d.win(), "DOZEN2 wins iff 13-24");

            CasinoService.Result c = casino.rouletteResult(100, "COL3", seeded);
            int cn = Integer.parseInt(c.outcome());
            assertEquals(cn != 0 && cn % 3 == 0, c.win(), "COL3 wins iff number is a multiple of 3");
        }
    }

    @Test
    void plinkoPathMatchesLandingBucket() {
        CasinoService.Result r = casino.plinkoResult(1000, "MED", new Random(42));
        assertEquals(CasinoService.PLINKO_ROWS, r.reels().size(), "one L/R step per row");
        long rights = r.reels().stream().filter("R"::equals).count();
        // payout must equal bet * the multiplier for the bucket the path landed in
        double mult = CasinoService.PLINKO_MED[(int) rights];
        assertEquals(Math.round(1000 * mult), r.payout());
        assertTrue(r.outcome().endsWith("x"));
    }

    @Test
    void rouletteLandsInRangeWithConsistentColour() {
        Random seeded = new Random(7);
        for (int i = 0; i < 200; i++) {
            CasinoService.Result r = casino.rouletteResult(100, "RED", seeded);
            int n = Integer.parseInt(r.outcome());
            assertTrue(n >= 0 && n <= 36);
            String color = r.reels().get(0);
            if (n == 0) assertEquals("GREEN", color);
            // a RED bet wins iff the landed pocket is red
            assertEquals("RED".equals(color), r.win());
        }
    }

    @Test
    void rejectsBadChoices() {
        assertThrows(IllegalArgumentException.class, () -> casino.rouletteResult(100, "PURPLE", new Random()));
        assertThrows(IllegalArgumentException.class, () -> casino.rouletteResult(100, "37", new Random()));
        assertThrows(IllegalArgumentException.class, () -> casino.plinkoResult(100, "EXTREME", new Random()));
    }
}
