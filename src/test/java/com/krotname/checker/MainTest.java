package com.krotname.checker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class MainTest {
    @Test
    void shouldPrintHelpWithoutArguments() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            Main.main(new String[]{});
            String output = out.toString();
            assertTrue(output.contains("Usage:"));
            assertTrue(output.contains("java -jar checker-corporate.jar"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldDefaultBindAddressToLoopback() throws UnknownHostException {
        assertTrue(Main.resolveBindAddress(null).isLoopbackAddress());
        assertTrue(Main.resolveBindAddress("   ").isLoopbackAddress());
    }

    @Test
    void shouldHonourConfiguredBindAddress() throws UnknownHostException {
        InetAddress wildcard = Main.resolveBindAddress(" 0.0.0.0 ");
        assertTrue(wildcard.isAnyLocalAddress());
        assertEquals("127.0.0.1", Main.resolveBindAddress("127.0.0.1").getHostAddress());
    }

    @Test
    void shouldRejectUnresolvableBindAddress() {
        assertThrows(UnknownHostException.class, () -> Main.resolveBindAddress("no.such.host.invalid"));
    }

    @Test
    void shouldReportInvalidPortForServerMode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            Main.main(new String[]{"--server", "abc"});
            String output = out.toString();
            assertTrue(output.contains("Port should be an integer."));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldReportOutOfRangePortForServerMode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            Main.main(new String[]{"--server", "70000"});
            String output = out.toString();
            assertTrue(output.contains("Port should be between 1 and 65535."));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldExposeHelpCommand() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            Main.main(new String[]{"--help"});
            String output = out.toString();
            assertTrue(output.contains("java -jar checker-corporate.jar --help"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void shouldPrintValidationErrorForInvalidInnArgument() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            Main.main(new String[]{"123"});
            String output = out.toString();
            assertTrue(output.contains("ИНН должен содержать 10 или 12 цифр."));
        } finally {
            System.setOut(originalOut);
        }
    }
}
