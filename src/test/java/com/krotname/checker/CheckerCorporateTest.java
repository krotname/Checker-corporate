package com.krotname.checker;

import com.krotname.checker.client.DadataClient;
import com.krotname.checker.model.CheckResult;
import com.krotname.checker.model.CompanyStatus;
import com.krotname.checker.validation.InnValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit")
class CheckerCorporateTest {
    @Test
    void shouldReturnActiveWhenApiReportsActive() throws IOException, InterruptedException {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient("ACTIVE")
        );
        CheckResult result = checker.check("not-an-inn");
        assertEquals(CompanyStatus.ACTIVE, result.status());
        assertEquals("ACTIVE", result.dadataStatus());
        assertTrue(result.isActive());
    }

    @Test
    void shouldReturnNotActiveOnLiquidated() throws IOException, InterruptedException {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient("LIQUIDATED")
        );
        CheckResult result = checker.check("not-an-inn");
        assertEquals(CompanyStatus.NOT_ACTIVE, result.status());
        assertEquals("LIQUIDATED", result.dadataStatus());
        assertEquals("Организация не активна.", result.message());
    }

    @Test
    void shouldQueryDadataWithNormalizedInnAndReportIt() throws IOException, InterruptedException {
        java.util.concurrent.atomic.AtomicReference<String> queried = new java.util.concurrent.atomic.AtomicReference<>();
        CheckerCorporate checker = new CheckerCorporate(new InnValidator(), inn -> {
            queried.set(inn);
            return Optional.of("ACTIVE");
        });

        CheckResult result = checker.check("  9710083390 ");

        assertEquals("9710083390", queried.get());
        assertEquals("9710083390", result.inn());
        assertEquals(CompanyStatus.ACTIVE, result.status());
    }

    @Test
    void shouldReturnNotFoundWhenApiDoesNotProvideState() {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                inn -> Optional.empty()
        );

        CheckResult result = checker.check("not-an-inn");

        assertEquals(CompanyStatus.NOT_FOUND, result.status());
        assertEquals("Организация не найдена.", result.message());
    }

    @Test
    void shouldReturnServiceUnavailableWhenApiThrowsIOExceptionWithoutMessage() {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                inn -> {
                    throw new IOException();
                }
        );

        CheckResult result = checker.check("not-an-inn");

        assertEquals(CompanyStatus.SERVICE_UNAVAILABLE, result.status());
        assertTrue(result.message().contains("io error"));
    }

    @Test
    void shouldReturnServiceUnavailableAndRestoreInterruptFlag() {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                inn -> {
                    throw new InterruptedException("stop");
                }
        );

        try {
            CheckResult result = checker.check("not-an-inn");

            assertEquals(CompanyStatus.SERVICE_UNAVAILABLE, result.status());
            assertTrue(result.message().contains("interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "'ACTIVE',        true",
            "' active ',      true",
            "'LIQUIDATED',    false",
            "'BANKRUPT',      false"
    })
    void shouldMapDadataStatuses(String dadataStatus, boolean expectedActive) throws IOException, InterruptedException {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient(dadataStatus)
        );
        CheckResult result = checker.check("x");
        assertEquals(expectedActive, result.isActive());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LIQUIDATING", "LIQUIDATED", "BANKRUPT", "REORGANIZING"})
    void shouldMapKnownInactiveDadataStatuses(String dadataStatus) throws IOException, InterruptedException {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient(dadataStatus)
        );

        assertEquals(CompanyStatus.NOT_ACTIVE, checker.check("x").status());
    }

    @Test
    void shouldNotTreatUnknownDadataStatusAsInactive() throws IOException, InterruptedException {
        CheckerCorporate checker = new CheckerCorporate(
                new AlwaysValidInnValidator(),
                stubClient("FUTURE_STATUS")
        );

        CheckResult result = checker.check("x");

        assertEquals(CompanyStatus.UNKNOWN, result.status());
        assertEquals("FUTURE_STATUS", result.dadataStatus());
    }

    @Test
    void shouldCreateCheckerUsingTokenFactory() {
        CheckerCorporate checker = CheckerCorporate.withToken("test-token");
        assertNotNull(checker);
    }

    @Test
    void shouldUseDefaultConstructorForValidationWithoutNetwork() {
        System.setProperty("DADATA_TOKEN", "token-from-test");
        try {
            CheckerCorporate checker = new CheckerCorporate();
            assertEquals(CheckResult.invalidInput("!!!").status(), checker.check("!!!").status());
        } finally {
            System.clearProperty("DADATA_TOKEN");
        }
    }

    @Test
    void shouldEvaluateIsActiveWithoutNetwork() {
        System.setProperty("DADATA_TOKEN", "token-from-test");
        try {
            assertFalse(CheckerCorporate.isActive("bad-inn"));
        } finally {
            System.clearProperty("DADATA_TOKEN");
        }
    }

    private DadataClient stubClient(String value) {
        return inn -> Optional.of(value);
    }

    private static class AlwaysValidInnValidator extends InnValidator {
        @Override
        public boolean isValid(String inn) {
            return true;
        }
    }
}
