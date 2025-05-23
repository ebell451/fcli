/**
 * Copyright 2023 Open Text.
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
package com.fortify.cli.aviator._common.session.user.cli.mixin;

import com.fortify.cli.common.session.cli.mixin.ISessionNameSupplier;

import lombok.Getter;
import picocli.CommandLine.Option;

public final class AviatorUserSessionNameArgGroup implements ISessionNameSupplier { 
    @Getter @Option(names={"--aviator-session", "--av-session"}, defaultValue="default", required=false)
    private String sessionName;
}