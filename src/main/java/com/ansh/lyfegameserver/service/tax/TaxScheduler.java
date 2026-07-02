package com.ansh.lyfegameserver.service.tax;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the annual tax assessment. A game-year is 8,760,000 ms (~2h26m), so checking every
 * 60 real-seconds is more than granular enough to catch each user's year boundary.
 */
@Component
public class TaxScheduler {

    private final TaxService taxService;

    public TaxScheduler(TaxService taxService) {
        this.taxService = taxService;
    }

    @Scheduled(fixedRate = 60_000)
    public void collectAnnualTaxes() {
        taxService.collectAnnualTaxes();
    }
}
