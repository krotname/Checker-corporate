package com.krotname.checker;

import com.krotname.checker.client.DadataClient;
import com.krotname.checker.client.HttpDadataClient;
import com.krotname.checker.config.CorporateCheckerConfig;
import com.krotname.checker.model.CheckResult;
import com.krotname.checker.model.CompanyStatus;
import com.krotname.checker.validation.InnValidator;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Primary domain service:
 * validates INN format, calls DaData, and maps response status into explicit domain outcomes.
 */
public final class CheckerCorporate {
    private final InnValidator innValidator;
    private final DadataClient dadataClient;

    public CheckerCorporate() {
        this(new InnValidator(), new HttpDadataClient(CorporateCheckerConfig.fromEnvironmentOrResource()));
    }

    public CheckerCorporate(InnValidator innValidator, DadataClient dadataClient) {
        this.innValidator = Objects.requireNonNull(innValidator, "innValidator");
        this.dadataClient = Objects.requireNonNull(dadataClient, "dadataClient");
    }

    public static CheckerCorporate withToken(String token) {
        return new CheckerCorporate(new InnValidator(), new HttpDadataClient(CorporateCheckerConfig.fromToken(token)));
    }

    /**
     * Validates INN, queries DaData and maps returned raw status into domain outcome.
     * The method never throws checked exceptions; all integration and validation errors
     * are represented as explicit {@link CheckResult} states.
     */
    public CheckResult check(String inn) {
        if (!innValidator.isValid(inn)) {
            return CheckResult.invalidInput(inn);
        }
        // Validator accepts surrounding whitespace; normalize once so DaData and the result see the same value.
        String normalizedInn = inn.trim();
        try {
            Optional<String> state = dadataClient.fetchCompanyState(normalizedInn);
            if (state.isEmpty()) {
                return CheckResult.notFound(normalizedInn);
            }
            return fromDadataStatus(normalizedInn, state.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckResult.serviceUnavailable(normalizedInn, "interrupted");
        } catch (IOException e) {
            return CheckResult.serviceUnavailable(normalizedInn, e.getMessage() != null ? e.getMessage() : "io error");
        }
    }

    public static boolean isActive(String inn) {
        return new CheckerCorporate().check(inn).isActive();
    }

    private CheckResult fromDadataStatus(String inn, String statusValue) {
        // Keep mapping rules explicit in one place to avoid leaking raw DaData statuses into domain logic.
        String normalized = statusValue == null ? "" : statusValue.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE" -> CheckResult.active(inn, statusValue);
            case "LIQUIDATING", "LIQUIDATED", "BANKRUPT", "REORGANIZING" -> CheckResult.notActive(inn, statusValue);
            default -> CheckResult.unknown(inn, statusValue);
        };
    }
}
