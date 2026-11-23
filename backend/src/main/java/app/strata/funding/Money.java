package app.strata.funding;

import java.util.List;
import java.util.Map;

/**
 * An amount of money in integer minor units (cents) of an ISO 4217 currency. Never floating point.
 * Every amount the API returns carries {@code demo} and, for demo money, a label that clients must show next to it.
 */
public record Money(long amountMinor, String currency, boolean demo, String label) {
    public static final String DEMO_LABEL = "DEMO, no real money";

    /** Supported currencies and their minor-unit exponent. Kept small on purpose; demo money needs no FX. */
    public static final Map<String, Integer> CURRENCIES = Map.of("USD", 2, "EUR", 2, "GBP", 2, "JPY", 0);
    public static final List<String> CURRENCY_ORDER = List.of("USD", "EUR", "GBP", "JPY");

    public static Money of(long amountMinor, String currency, boolean demo) {
        return new Money(amountMinor, currency, demo, demo ? DEMO_LABEL : null);
    }

    public static boolean supported(String currency) {
        return currency != null && CURRENCIES.containsKey(currency);
    }

    /** One major unit (e.g. 100 cents, or 1 yen). */
    public static long unit(String currency) {
        long u = 1;
        for (int i = 0; i < CURRENCIES.get(currency); i++) u *= 10;
        return u;
    }

    /** Smallest contribution: one major unit. */
    public static long minContribution(String currency) {
        return unit(currency);
    }

    /** Largest single contribution in demo mode: 10,000 major units. */
    public static long maxContribution(String currency) {
        return Math.multiplyExact(10_000L, unit(currency));
    }

    /** Largest pool goal: 10,000,000 major units (keeps every sum far below 2^53, safe for JSON clients). */
    public static long maxGoal(String currency) {
        return Math.multiplyExact(10_000_000L, unit(currency));
    }
}
