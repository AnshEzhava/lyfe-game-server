package com.ansh.lyfegameserver.controller.dev;

import com.ansh.lyfegameserver.data.Sector;
import com.ansh.lyfegameserver.data.Stock;
import com.ansh.lyfegameserver.repository.StockRepository;
import com.ansh.lyfegameserver.websocket.StockPriceBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev")
public class DevController {

    private static final Logger logger = LoggerFactory.getLogger(DevController.class);
    private static final String SEED_TAG = "__SEED__";
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @org.springframework.beans.factory.annotation.Value("${dev.passkey.hash}")
    private String passkeyHash;

    private final StockRepository stockRepository;
    private final StockPriceBroadcaster broadcaster;

    public DevController(StockRepository stockRepository, StockPriceBroadcaster broadcaster) {
        this.stockRepository = stockRepository;
        this.broadcaster = broadcaster;
    }

    record DevRequest(String passkey) {}

    private void verifyPasskey(DevRequest req) {
        if (req == null || req.passkey() == null || !encoder.matches(req.passkey(), passkeyHash)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid passkey");
        }
    }

    @PostMapping("/seed-stocks")
    public Map<String, Object> seedStocks(@RequestBody DevRequest req) {
        verifyPasskey(req);

        List<Stock> seeded = new ArrayList<>();

        record Def(String ticker, String name, Sector sector,
                   long poolBranks, long poolShares, long totalSupply) {}

        List<Def> defs = List.of(
            new Def("NOVA",  "Nova Dynamics",          Sector.IT,            500_000, 50_000, 100_000),
            new Def("APEX",  "Apex Financial Group",   Sector.FINANCE,       800_000, 40_000, 100_000),
            new Def("MEDI",  "MediCore Labs",          Sector.HEALTHCARE,    300_000, 60_000, 120_000),
            new Def("VOLT",  "Volt Energy Corp",       Sector.ENERGY,        600_000, 30_000,  80_000),
            new Def("GRWN",  "GreenGrow Agritech",     Sector.AGRICULTURE,   200_000, 50_000, 100_000),
            new Def("BAZR",  "Bazaar Retail Inc",      Sector.TRADE,         400_000, 40_000,  90_000),
            new Def("QBIT",  "Qubit Research",         Sector.SCIENCE,       700_000, 35_000,  70_000),
            new Def("STRM",  "StreamWave Media",       Sector.ENTERTAINMENT, 350_000, 70_000, 140_000),
            new Def("FORG",  "Forge Industries",       Sector.MANUFACTURING, 450_000, 45_000,  90_000),
            new Def("SKYR",  "Skyrise Properties",     Sector.REAL_ESTATE,   900_000, 30_000,  80_000)
        );

        for (Def d : defs) {
            if (stockRepository.findByTicker(d.ticker()).isPresent()) {
                logger.info("Seed stock {} already exists, skipping", d.ticker());
                continue;
            }

            Stock stock = new Stock(
                d.ticker(), d.name(), SEED_TAG, false,
                d.poolBranks(), d.poolShares(), d.totalSupply(),
                0, 0
            );
            stock.setSector(d.sector());
            double price = stock.getCurrentPrice();
            stock.appendPrice(price);
            stock.getHourlySnapshots().add(price);

            Stock saved = stockRepository.save(stock);
            broadcaster.broadcast(saved);
            seeded.add(saved);
            logger.info("Seeded stock: {} ({}) @ {} B", d.ticker(), d.name(), String.format("%.2f", price));
        }

        return Map.of(
            "message", seeded.size() + " stocks seeded",
            "tickers", seeded.stream().map(Stock::getTicker).toList()
        );
    }

    @DeleteMapping("/seed-stocks")
    public Map<String, Object> deleteSeedStocks(@RequestBody DevRequest req) {
        verifyPasskey(req);

        List<Stock> seeds = stockRepository.findAll().stream()
            .filter(s -> SEED_TAG.equals(s.getFounderClerkId()))
            .toList();

        for (Stock s : seeds) {
            stockRepository.delete(s);
            logger.info("Deleted seed stock: {} ({})", s.getTicker(), s.getName());
        }

        return Map.of(
            "message", seeds.size() + " seed stocks deleted",
            "tickers", seeds.stream().map(Stock::getTicker).toList()
        );
    }
}
