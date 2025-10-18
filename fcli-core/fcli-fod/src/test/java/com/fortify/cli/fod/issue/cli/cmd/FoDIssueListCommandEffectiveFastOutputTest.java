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
package com.fortify.cli.fod.issue.cli.cmd;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fortify.cli.common.output.cli.mixin.OutputHelperMixins;

/**
 * Tests for FoDIssueListCommand.isEffectiveFastOutput logic.
 * We inject mixin fields and fastOutput flag via reflection and override outputHelper with a stub
 * that can toggle streaming capability.
 */
public class FoDIssueListCommandEffectiveFastOutputTest {
    private FoDIssueListCommand cmd;
    private StreamingStubOutputHelper streamingStub;

    @BeforeEach
    void init() throws Exception {
        cmd = new FoDIssueListCommand();
        streamingStub = new StreamingStubOutputHelper();
        setField(cmd, "outputHelper", streamingStub); // inject stub
        // Provide empty mixins so reflection can set their private fields
        setField(cmd, "appResolver", new com.fortify.cli.fod.app.cli.mixin.FoDAppResolverMixin.OptionalOption());
        setField(cmd, "releaseResolver", new com.fortify.cli.fod.release.cli.mixin.FoDReleaseByQualifiedNameOrIdResolverMixin.OptionalOption());
    }

    @Test
    void fastOutputActiveWhenAppAndStreaming() throws Exception {
        streamingStub.streamingSupported = true;
        setFastOutput(true);
        setApp("myApp");
        assertTrue(invokeIsEffectiveFastOutput());
    }

    @Test
    void fastOutputInactiveWhenStreamingUnsupported() throws Exception {
        streamingStub.streamingSupported = false;
        setFastOutput(true);
        setApp("myApp");
        assertFalse(invokeIsEffectiveFastOutput());
    }

    @Test
    void fastOutputInactiveWithoutApp() throws Exception {
        streamingStub.streamingSupported = true;
        setFastOutput(true);
        // no app set
        assertFalse(invokeIsEffectiveFastOutput());
    }

    @Test
    void fastOutputInactiveWithRelease() throws Exception {
        streamingStub.streamingSupported = true;
        setFastOutput(true);
        setApp("myApp");
        setRelease("123");
        assertFalse(invokeIsEffectiveFastOutput());
    }

    @Test
    void fastOutputInactiveWhenFlagFalse() throws Exception {
        streamingStub.streamingSupported = true;
        setFastOutput(false);
        setApp("myApp");
        assertFalse(invokeIsEffectiveFastOutput());
    }

    // Reflection helpers
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void setFastOutput(boolean v) throws Exception { setField(cmd, "fastOutput", v); }
    private void setApp(String app) throws Exception {
        Object appResolver = getField(cmd, "appResolver");
        setField(appResolver, "appNameOrId", app);
    }
    private void setRelease(String rel) throws Exception {
        Object relResolver = getField(cmd, "releaseResolver");
        setField(relResolver, "qualifiedReleaseNameOrId", rel);
    }
    private Object getField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private boolean invokeIsEffectiveFastOutput() throws Exception {
    // Call private method via reflection
        var method = FoDIssueListCommand.class.getDeclaredMethod("isEffectiveFastOutput");
        method.setAccessible(true);
        return (boolean)method.invoke(cmd);
    }

    // Stub output helper that only toggles streaming capability
    private static class StreamingStubOutputHelper extends OutputHelperMixins.List {
        boolean streamingSupported;
        @Override
        public boolean isStreamingOutputSupported() { return streamingSupported; }
    }
}