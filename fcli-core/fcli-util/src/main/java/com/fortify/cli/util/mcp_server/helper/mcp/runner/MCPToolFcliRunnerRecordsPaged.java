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

import com.fortify.cli.util.mcp_server.helper.mcp.MCPJobManager;
import com.fortify.cli.util.mcp_server.helper.mcp.arg.MCPToolArgHandlerPaging;
import com.fortify.cli.util.mcp_server.helper.mcp.arg.MCPToolArgHandlers;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Model.CommandSpec;

/**
 * {@link IMCPToolRunner} implementation that, given offset and limit, returns the requested set of 
 * records together with pagination data in a structured JSON object as described by {@link MCPToolResultRecordsPaged}, 
 * based on the full set of records as produced by the fcli command being executed.
 * This is commonly used to run fcli commands that may return a large number of records.
 *
 * @author Ruud Senden
 */
@Slf4j
public final class MCPToolFcliRunnerRecordsPaged extends AbstractMCPToolFcliRunner {
    @Getter private final MCPToolArgHandlers toolSpecArgHelper;
    @Getter private final CommandSpec commandSpec;
    public MCPToolFcliRunnerRecordsPaged(MCPToolArgHandlers toolSpecArgHelper, CommandSpec commandSpec, MCPJobManager jobManager) {
        super(jobManager);
        this.toolSpecArgHelper = toolSpecArgHelper;
        this.commandSpec = commandSpec;
    }
    
    @Override
    protected CallToolResult execute(McpSyncServerExchange exchange, CallToolRequest request, String fullCmd) {
        var refresh = toolArgAsBoolean(request, MCPToolArgHandlerPaging.ARG_REFRESH, false);
        var offset = toolArgAsInt(request, MCPToolArgHandlerPaging.ARG_OFFSET, 0);
        var limit = 20; // Fixed for now
        var cached = jobManager.getRecordsCache().getOrCollect(fullCmd, refresh, getCommandSpec());
        return MCPToolResultRecordsPaged.from(cached, offset, limit).asCallToolResult();
    }

    @Override
    public CallToolResult run(McpSyncServerExchange exchange, CallToolRequest request) {
        final var fullCmd = (getCommandSpec().qualifiedName(" ") + (request!=null && request.arguments()!=null?" "+getToolSpecArgHelper().getFcliCmdArgs(request.arguments()):"")).trim();
        var refresh = toolArgAsBoolean(request, MCPToolArgHandlerPaging.ARG_REFRESH, false);
        var offset = toolArgAsInt(request, MCPToolArgHandlerPaging.ARG_OFFSET, 0);
        var limit = 20; // Fixed for now
        try {
            // Return cached full result if available
            var cached = refresh?null:jobManager.getRecordsCache().getCached(fullCmd);
            if ( cached!=null ) {
                log.debug("Paged runner cached hit cmd='{}' offset={} limit={} total={}", fullCmd, offset, limit, cached.getRecords().size());
                return MCPToolResultRecordsPaged.from(cached, offset, limit).asCallToolResult();
            }
            // Get or start background collection
            var inProg = jobManager.getRecordsCache().getOrStartBackground(fullCmd, refresh, getCommandSpec());
            if ( inProg==null ) { // Means we had cached result but refresh requested & already collected synchronously
                var full = jobManager.getRecordsCache().getCached(fullCmd);
                return full==null ? new CallToolResult("No result", true) : MCPToolResultRecordsPaged.from(full, offset, limit).asCallToolResult();
            }
            var records = inProg.getRecords();
            var requiredForHasMore = offset + limit + 1;
            while ( records.size() < requiredForHasMore && !inProg.isCompleted() ) {
                Thread.sleep(50);
            }
            var complete = inProg.isCompleted();
            if ( complete ) {
                var full = jobManager.getRecordsCache().getCached(fullCmd);
                if ( full!=null ) {
                    log.debug("Returning COMPLETE paged result cmd='{}' offset={} limit={} loaded={} total={}",
                            fullCmd, offset, limit, records.size(), full.getRecords().size());
                    return MCPToolResultRecordsPaged.from(full, offset, limit).asCallToolResult();
                }
                log.warn("Background collection completed without cache entry cmd='{}'", fullCmd);
            }
            log.debug("Returning PARTIAL paged result cmd='{}' offset={} limit={} loaded={} need>={} jobToken={}",
                    fullCmd, offset, limit, records.size(), requiredForHasMore, inProg.getJobToken());
            return MCPToolResultRecordsPaged.fromPartial(records, offset, limit, false, inProg.getJobToken()).asCallToolResult();
        } catch ( Exception e ) {
            log.warn("Paged runner failed cmd='{}' offset={} limit={} error={}", fullCmd, offset, limit, e.toString());
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