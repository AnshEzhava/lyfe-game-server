package com.ansh.lyfegameserver.seeder;

import com.ansh.lyfegameserver.data.Stock;
import com.ansh.lyfegameserver.repository.StockRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the government bond on startup if it doesn't already exist.
 * The bond uses a large liquidity pool so price impact per trade is minimal.
 */
@Component
public class DatabaseSeeder implements ApplicationRunner {

    /** 200 bps = 2% daily yield paid per game-day (every 24 real-seconds) */
    private static final int BOND_YIELD_BPS = 200;

    /** Initial pool Branks — large so the bond is very price-stable */
    private static final long BOND_INITIAL_BRANKS = 10_000_000L;

    /** Initial pool shares — sets starting price at 10 Branks per share */
    private static final long BOND_INITIAL_SHARES = 1_000_000L;

    private final StockRepository stockRepository;

    public DatabaseSeeder(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (stockRepository.findByTicker("BOND").isEmpty()) {
            Stock bond = new Stock(
                "BOND",
                "Government Bond",
                null,
                true,
                BOND_INITIAL_BRANKS,
                BOND_INITIAL_SHARES,
                BOND_INITIAL_SHARES,
                0L,
                BOND_YIELD_BPS
            );
            bond.appendPrice(bond.getCurrentPrice());
            stockRepository.save(bond);
        }
    }
}
