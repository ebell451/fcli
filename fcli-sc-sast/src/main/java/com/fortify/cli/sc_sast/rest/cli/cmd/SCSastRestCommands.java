package com.fortify.cli.sc_sast.rest.cli.cmd;

import picocli.CommandLine.Command;

@Command(
        name = "rest",
        aliases = {},
        subcommands = {
                SCSastRestCallCommand.class
        }

)
public class SCSastRestCommands {
}
