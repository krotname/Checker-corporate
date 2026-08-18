package com.krotname.checker;

import com.krotname.checker.ui.CheckerUiServer;
import com.krotname.checker.validation.InnValidator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class Main {
    private static final int DEFAULT_SERVER_PORT = 8080;
    static final String PORT_ENV = "CHECKER_PORT";
    static final String BIND_ENV = "CHECKER_BIND_ADDRESS";

    /**
     * Supports three startup modes:
     * - no args: print usage/help;
     * - --help: print usage/help;
     * - --server [port]: start embedded HTTP UI (port also readable from CHECKER_PORT);
     * - otherwise: treat the first arg as INN and print a single check result.
     *
     * The UI listens on the loopback interface unless CHECKER_BIND_ADDRESS says otherwise;
     * /api/check is unauthenticated and consumes the operator's DaData quota, so it must
     * not be published to a network by accident.
     */
    public static void main(String[] args) {
        if (args.length == 0 || "--help".equalsIgnoreCase(args[0])) {
            printHelp();
            return;
        }

        if ("--server".equalsIgnoreCase(args[0])) {
            String rawPort = args.length >= 2 ? args[1] : System.getenv(PORT_ENV);
            int port = DEFAULT_SERVER_PORT;
            if (rawPort != null && !rawPort.isBlank()) {
                try {
                    port = Integer.parseInt(rawPort.trim());
                } catch (NumberFormatException e) {
                    System.out.println("Port should be an integer.");
                    printHelp();
                    return;
                }
            }
            if (port < 1 || port > 65_535) {
                System.out.println("Port should be between 1 and 65535.");
                printHelp();
                return;
            }
            InetAddress bindAddress;
            try {
                bindAddress = resolveBindAddress(System.getenv(BIND_ENV));
            } catch (UnknownHostException e) {
                System.out.printf("Cannot resolve %s: %s%n", BIND_ENV, e.getMessage());
                printHelp();
                return;
            }
            if (!runServer(bindAddress, port)) {
                System.exit(1);
            }
            return;
        }

        String inn = args[0];
        if (!new InnValidator().isValid(inn)) {
            System.out.println(com.krotname.checker.model.CheckResult.invalidInput(inn).message());
            return;
        }
        try {
            CheckerCorporate checker = new CheckerCorporate();
            System.out.println(checker.check(inn).message());
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.printf("Configuration error: %s%n", e.getMessage());
        }
    }

    /**
     * Empty or unset value keeps the loopback default; "0.0.0.0" (or any explicit address)
     * opts into wildcard binding, which is what the container image does.
     */
    static InetAddress resolveBindAddress(String configured) throws UnknownHostException {
        if (configured == null || configured.isBlank()) {
            return InetAddress.getLoopbackAddress();
        }
        return InetAddress.getByName(configured.trim());
    }

    /**
     * Isolated startup path: all CLI server orchestration is centralized here for
     * predictable local run behavior. Returns false when the server did not start,
     * so the process can exit with a non-zero status instead of pretending success.
     */
    private static boolean runServer(InetAddress bindAddress, int port) {
        try {
            CheckerCorporate checker = new CheckerCorporate();
            CheckerUiServer server = new CheckerUiServer(checker, bindAddress, port);
            int boundPort = server.start();
            System.out.printf("Checker UI server started at http://%s:%d%n", bindAddress.getHostAddress(), boundPort);
            // Keep the process alive; this is a demo-only console process container.
            Thread.currentThread().join();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Server thread interrupted.");
            return false;
        } catch (IOException e) {
            String message = e.getMessage() == null ? "unknown IO error" : e.getMessage();
            System.out.printf("Failed to start server: %s%n", message);
            return false;
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.printf("Configuration error: %s%n", e.getMessage());
            return false;
        }
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  java -jar checker-corporate.jar <ИНН>");
        System.out.println("  java -jar checker-corporate.jar --server [port]");
        System.out.println("  java -jar checker-corporate.jar --help");
        System.out.println("");
        System.out.println("");
        System.out.println("Environment variables:");
        System.out.println("  DADATA_TOKEN          - DaData API token (or resources/checker.properties with key token=...)");
        System.out.println("  CHECKER_PORT          - server port when --server is used without an explicit port");
        System.out.println("  CHECKER_BIND_ADDRESS  - listen address, default loopback; set 0.0.0.0 only inside a container");
    }
}
