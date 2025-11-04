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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.formkiq.graalvm.annotations.Reflectable;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Data class representing the output of the {@link MCPToolFcliRunnerRecordsPaged},
 * storing records, pagination info, stderr, and exit code.
 * 
 * @author Ruud Senden
 */
@Data @EqualsAndHashCode(callSuper = false) @Builder
@Reflectable
public class MCPToolResultRecordsPaged extends AbstractMCPToolResult {
    private final List<JsonNode> records;          // Page records
    private final PageInfo pagination;             // Paging metadata
    private final String stderr;                   // fcli stderr (empty for partial in-progress pages)
    private final int exitCode;                    // fcli exit code (0 for partial until background completes)
    
    /**
     * Create a full (complete) paged result once all records have been collected.
     */
    public static final MCPToolResultRecordsPaged from(MCPToolResultRecords plainResult, int offset, int limit) {
        var allRecords = plainResult.getRecords();
        var pageInfo = PageInfo.complete(allRecords.size(), offset, limit);
        var endIndexExclusive = Math.min(offset+limit, allRecords.size());
        List<JsonNode> pageRecords = offset>=endIndexExclusive ? List.<JsonNode>of() : allRecords.subList(offset, endIndexExclusive);
        return builder()
            .exitCode(plainResult.getExitCode())
            .stderr(plainResult.getStderr())
            .records(pageRecords)
            .pagination(pageInfo)
            .build();
    }
    
    /**
     * Create a partial paged result while background collection is still running.
     * totalRecords/totalPages/lastPageOffset are null until complete. hasMore is
     * determined by presence of at least (offset+limit+1) loaded records.
     * exitCode/stderr represent interim values (0 + empty) as underlying fcli
     * process hasn't completed yet.
     */
    public static final MCPToolResultRecordsPaged fromPartial(List<JsonNode> loadedRecords, int offset, int limit, boolean complete, String jobToken) {
        if ( complete ) { // Delegate to full builder for final consistency
            return from(MCPToolResultRecords.builder().exitCode(0).stderr("").records(loadedRecords).build(), offset, limit);
        }
        var endIndexExclusive = Math.min(offset+limit, loadedRecords.size());
        List<JsonNode> pageRecords = offset>=endIndexExclusive ? List.<JsonNode>of() : loadedRecords.subList(offset, endIndexExclusive);
        var hasMore = loadedRecords.size() > offset+limit; // page size + 1 rule
        var pageInfo = PageInfo.partial(offset, limit, hasMore).toBuilder().jobToken(jobToken).build();
        return builder()
            .exitCode(0) // Interim until full collection finishes
            .stderr("")
            .records(pageRecords)
            .pagination(pageInfo)
            .build();
    }
    
    @Data @Builder(toBuilder = true)
    @Reflectable
    private static final class PageInfo {
        private final Integer totalRecords;   // null if not complete
        private final Integer totalPages;     // null if not complete
        private final int currentOffset;      // requested offset
        private final int currentLimit;       // requested limit
        private final Integer nextPageOffset; // null if last page or incomplete without extra record
        private final Integer lastPageOffset; // null if not complete
        private final boolean hasMore;        // true if we have pageSize+1 loaded or final total indicates more
        private final boolean complete;       // dataset fully loaded
        private final String jobToken;        // optional: background job token (partial only)
        private final String guidance;        // optional: message guiding client/LLM
        
        private static final PageInfo complete(int totalRecords, int offset, int limit) {
            var totalPages = (int)Math.ceil((double)totalRecords / (double)limit);
            var lastPageOffset = (totalPages - 1) * limit;
            var nextPageOffset = offset+limit;
            var hasMore = totalRecords>nextPageOffset;
            return PageInfo.builder()
                .currentLimit(limit)
                .currentOffset(offset)
                .lastPageOffset(lastPageOffset)
                .nextPageOffset(hasMore ? nextPageOffset : null)
                .hasMore(hasMore)
                .totalRecords(totalRecords)
                .totalPages(totalPages)
                .complete(true)
                .guidance("All records loaded; totals available.")
                .build();
        }
        
        private static final PageInfo partial(int offset, int limit, boolean hasMore) {
            return PageInfo.builder()
                .currentLimit(limit)
                .currentOffset(offset)
                .nextPageOffset(hasMore ? offset+limit : null)
                .hasMore(hasMore)
                .complete(false)
                .guidance("Partial page; totals unavailable. Call job tool with the provided job_token (if present) using operation=wait to finalize loading for totalRecords/totalPages.")
                .build();
        }
        
        @JsonIgnore
        public final boolean isComplete() { return complete; }
    }
}
