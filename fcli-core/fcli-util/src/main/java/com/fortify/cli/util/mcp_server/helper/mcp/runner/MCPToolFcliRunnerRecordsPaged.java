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
package com.fortify.cli.util.mcp_server.helper.mcp.runner;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import com.fortify.cli.util.mcp_server.helper.mcp.arg.MCPToolArgHandlerPaging;
import com.fortify.cli.util.mcp_server.helper.mcp.arg.MCPToolArgHandlers;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.Getter;
import picocli.CommandLine.Model.CommandSpec;

/**
 * {@link IMCPToolRunner} implementation that, given offset and limit, returns the requested set of 
 * records together with pagination data in a structured JSON object as described by {@link MCPToolResultRecordsPaged}, 
 * based on the full set of records as produced by the fcli command being executed.
 * This is commonly used to run fcli commands that may return a large number of records.
 *
 * @author Ruud Senden
 */
public final class MCPToolFcliRunnerRecordsPaged extends AbstractMCPToolFcliRunner {
    @Getter private final MCPToolArgHandlers toolSpecArgHelper;
    @Getter private final CommandSpec commandSpec;
    public MCPToolFcliRunnerRecordsPaged(MCPToolArgHandlers toolSpecArgHelper, CommandSpec commandSpec, com.fortify.cli.util.mcp_server.helper.mcp.MCPJobManager jobManager) {
        super(jobManager);
        this.toolSpecArgHelper = toolSpecArgHelper;
        this.commandSpec = commandSpec;
    }
    
    @Override
    protected CallToolResult execute(McpSyncServerExchange exchange, CallToolRequest request, String fullCmd) {
        var refresh = toolArgAsBoolean(request, MCPToolArgHandlerPaging.ARG_REFRESH, false);
        var offset = toolArgAsInt(request, MCPToolArgHandlerPaging.ARG_OFFSET, 0);
        var limit = 20; // Fixed for now
        var cached = MCPToolFcliRecordsCache.INSTANCE.getOrCollect(fullCmd, refresh, getCommandSpec());
        return MCPToolResultRecordsPaged.from(cached, offset, limit).asCallToolResult();
    }

    @Override
    public CallToolResult run(McpSyncServerExchange exchange, CallToolRequest request) {
        final var fullCmd = (getCommandSpec().qualifiedName(" ") + (request!=null && request.arguments()!=null?" "+getToolSpecArgHelper().getFcliCmdArgs(request.arguments()):"")).trim();
        var toolName = getCommandSpec().qualifiedName("_").replace('-', '_');
        var refresh = toolArgAsBoolean(request, MCPToolArgHandlerPaging.ARG_REFRESH, false);
        var offset = toolArgAsInt(request, MCPToolArgHandlerPaging.ARG_OFFSET, 0);
        var limit = 20; // Fixed for now
        try {
            if ( jobManager==null ) { return execute(exchange, request, fullCmd); }
            // If we already have cached records and not refreshing, return synchronously (no async job)
            var cached = refresh?null:MCPToolFcliRecordsCache.INSTANCE.getCached(fullCmd);
            if ( cached!=null ) {
                return MCPToolResultRecordsPaged.from(cached, offset, limit).asCallToolResult();
            }
            var records = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            var counter = new AtomicInteger();
            Callable<CallToolResult> callable = () -> {
                var result = MCPToolFcliRunnerHelper.collectRecords(fullCmd, r->{ counter.incrementAndGet(); records.add(r); }, getCommandSpec());
                var allRecords = MCPToolResultRecords.from(result, records);
                if ( result.getExitCode()==0 ) { MCPToolFcliRecordsCache.INSTANCE.put(fullCmd, allRecords); }
                return MCPToolResultRecordsPaged.from(allRecords, offset, limit).asCallToolResult();
            };
            var progressStrategy = com.fortify.cli.util.mcp_server.helper.mcp.MCPJobManager.recordCounter(counter);
            return jobManager.execute(exchange, toolName, callable, progressStrategy, true);
        } catch ( Exception e ) {
            return new CallToolResult(e.toString(), true);
        }
    }
    
    private static final int toolArgAsInt(CallToolRequest request, String argName, int defaultValue) {
        var o = toolArg(request, argName);
        return o==null ? defaultValue : Integer.parseInt(o.toString());
    }
    
    private static final boolean toolArgAsBoolean(CallToolRequest request, String argName, boolean defaultValue) {
        var o = toolArg(request, argName);
        return o==null ? defaultValue : Boolean.parseBoolean(o.toString());
    }

    private static Object toolArg(CallToolRequest request, String argName) {
        return request==null || request.arguments()==null ? null : request.arguments().get(argName);
    }
}