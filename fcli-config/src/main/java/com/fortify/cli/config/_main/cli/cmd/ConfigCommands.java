package com.fortify.cli.config._main.cli.cmd;

import com.fortify.cli.common.cli.cmd.AbstractFortifyCLICommand;
import com.fortify.cli.config.language.cli.cmd.LanguageCommands;
import com.fortify.cli.config.variable.cli.cmd.VariableCommands;

import picocli.CommandLine.Command;

@Command(
        name = "config",
        description = "Commands for configuring fcli",
        resourceBundle = "com.fortify.cli.config.i18n.ConfigMessages",
        subcommands = {
                LanguageCommands.class,
                VariableCommands.class
        }
)
public class ConfigCommands extends AbstractFortifyCLICommand {
}
