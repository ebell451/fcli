/*
 * Copyright 2021-2025 Open Text.
 *
 * The only warranties for products and services of Open Text
 * and its affiliates and licensors ("Open Text") are as may
 * be set forth in the express warranty statements accompanying
 * such products and services. Nothing herein should be construed
 * as constituting an additional warranty. Open Text shall not be
 * liable for technical or editorial errors or omissions contained
 * herein. The information contained herein is subject to change
 * without notice.
 */
package com.fortify.cli.app;

import java.io.OutputStream;
import java.io.PrintStream;

import com.fortify.cli.app.runner.DefaultFortifyCLIRunner;
import com.fortify.cli.common.util.ConsoleHelper;

import lombok.SneakyThrows;

/**
 * <p>This class provides the {@link #main(String[])} entrypoint into the application,
 * and also registers some GraalVM features, allowing the application to run properly 
 * as GraalVM native images.</p>
 * 
 * @author Ruud Senden
 */
public class FortifyCLI {
    // JAnsi enablement/disablement handled centrally in ConsoleHelper

    /**
     * This is the main entry point for executing the Fortify CLI.
     * @param args Command line options passed to Fortify CLI
     */
    public static final void main(String[] args) {
        System.exit(execute(args));
    }

    private static final int execute(String[] args) {
        var orgOut = System.out;
        var orgErr = System.err;
        try {
            ConsoleHelper.installAnsiConsole();
            // Avoid any fcli code from closing stdout/stderr streams
            System.setOut(new NonClosingPrintStream(orgOut));
            System.setErr(new NonClosingPrintStream(orgErr));
            return DefaultFortifyCLIRunner.run(args);
        } finally {
            System.setOut(orgOut);
            System.setErr(orgErr);
            ConsoleHelper.uninstallAnsiConsole();
        }
    }
    
    private static final class NonClosingPrintStream extends PrintStream {
        public NonClosingPrintStream(OutputStream out) {
            super(out); 
        }
        
        @Override @SneakyThrows
        public void close() {
            out.flush();
            // Only flush, don't close underlying stream
        }
    }
}
