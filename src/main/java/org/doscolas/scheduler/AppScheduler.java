package org.doscolas.scheduler;

import org.doscolas.log.LogManager;
import org.doscolas.log.Logger;
import org.doscolas.model.Payout;
import org.doscolas.model.PayoutStatus;
import org.doscolas.service.PayoutService;
import org.doscolas.service.TakeCareService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Replaces the original Spring {@code @Scheduled} job ({@code TakeCareScheduler}) with a single
 * hand-rolled {@link ScheduledExecutorService}: no framework, no proxies — just
 * {@code scheduleAtFixedRate}. The task body is wrapped in try/catch: an uncaught exception would
 * silently cancel all future runs of a {@code ScheduledExecutorService} task, which
 * {@code @Scheduled} never had to worry about.
 *
 * <p>Two payout tasks are a safety net around {@link PayoutService}'s normally-async flow: a
 * PENDING payout is meant to be processed immediately on creation, but a server restart mid-flight
 * (or a sitter adding their bank account) can leave one sitting; a PROCESSING payout is polled
 * against Fintoc directly since transfer webhook delivery isn't confirmed yet (see
 * {@code FintocWebhookController}).
 */
public final class AppScheduler implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(AppScheduler.class);

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(1, r -> new Thread(r, "scheduler"));

    private final TakeCareService takeCareService;
    private final PayoutService payoutService;
    private final long payoutProcessIntervalMs;
    private final long payoutPollIntervalMs;

    public AppScheduler(TakeCareService takeCareService, PayoutService payoutService,
                         long payoutProcessIntervalMs, long payoutPollIntervalMs) {
        this.takeCareService = takeCareService;
        this.payoutService = payoutService;
        this.payoutProcessIntervalMs = payoutProcessIntervalMs;
        this.payoutPollIntervalMs = payoutPollIntervalMs;
    }

    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    public void start() {
        long initialDelayMs = millisUntilNextMidnight();
        executor.scheduleAtFixedRate(this::completeExpiredTakeCares, initialDelayMs, ONE_DAY_MS, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::processPendingPayouts, payoutProcessIntervalMs, payoutProcessIntervalMs, TimeUnit.MILLISECONDS);
        executor.scheduleAtFixedRate(this::pollProcessingPayouts, payoutPollIntervalMs, payoutPollIntervalMs, TimeUnit.MILLISECONDS);
        log.info("Scheduler started: take-care midnight sweep in {}ms, payout process every {}ms, payout poll every {}ms",
                initialDelayMs, payoutProcessIntervalMs, payoutPollIntervalMs);
    }

    private static long millisUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
        return ChronoUnit.MILLIS.between(now, nextMidnight);
    }

    /** Safety net: completes take-cares whose end date has passed (normally the nightly sweep). */
    private void completeExpiredTakeCares() {
        try {
            int count = takeCareService.completeExpiredTakeCares();
            if (count > 0) log.info("Completed {} expired take-care(s)", count);
        } catch (Exception e) {
            log.error("Failed to complete expired take-cares", e);
        }
    }

    private void processPendingPayouts() {
        try {
            for (Payout payout : payoutService.getByStatus(PayoutStatus.PENDING)) {
                payoutService.processPayout(payout.getId());
            }
        } catch (Exception e) {
            log.error("Failed to sweep pending payouts", e);
        }
    }

    private void pollProcessingPayouts() {
        try {
            for (Payout payout : payoutService.getByStatus(PayoutStatus.PROCESSING)) {
                payoutService.refreshFromProvider(payout.getId());
            }
        } catch (Exception e) {
            log.error("Failed to poll processing payouts", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
