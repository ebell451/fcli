package com.fortify.cli.common.cli.util;

import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine;

public class FcliRootCommandLineHelper {
    @Getter @Setter
    private static CommandLine rootCommandLine;
}
