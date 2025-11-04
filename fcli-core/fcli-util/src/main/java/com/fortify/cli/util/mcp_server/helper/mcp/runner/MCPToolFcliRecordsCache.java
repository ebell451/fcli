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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.LRUMap;
import com.fortify.cli.util.mcp_server.helper.mcp.MCPJobManager;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Cache for fcli record-collecting MCP tools. A single instance is owned by {@link MCPJobManager}
 * and constructed with a mandatory {@link MCPJobManager} reference so background collection jobs
 * are always tracked (job manager integration is no longer optional).
 */
public class MCPToolFcliRecordsCache {
    private static final long TTL = 10*60*1000; // 10 minutes in milliseconds
    private static final int MAX_CACHE_ENTRIES = 5; // Keep small; large sets expensive
    private static final int BG_THREADS = 2; // Single-threaded background collection
    private final LRUMap<String, CacheEntry> cache = new LRUMap<>(0, MAX_CACHE_ENTRIES);
    private final Map<String, InProgressEntry> inProgress = new ConcurrentHashMap<>();
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(BG_THREADS, r->{
        var t = new Thread(r, "fcli-mcp-cache-loader");
        t.setDaemon(true); // Allow JVM exit
        return t;
    });
    private final MCPJobManager jobManager; // Required integration

    public MCPToolFcliRecordsCache(MCPJobManager jobManager) {
        this.jobManager = jobManager;
    }

    /**
     * Synchronously collect records (legacy path) or return cached value.
     * Prefer {@link #getOrStartBackground(String, boolean, CommandSpec)} for
     * paged scenarios.
     */
    public final MCPToolResultRecords getOrCollect(String fullCmd, boolean refresh, CommandSpec spec) {
        synchronized(cache) {
            var cacheEntry = cache.get(fullCmd);
            var result = ( cacheEntry==null || cacheEntry.isExpired() || refresh ) ? null : cacheEntry.getFullResult();
            if ( result==null ) {
                result = MCPToolFcliRunnerHelper.collectRecords(fullCmd, spec);
                if ( result.getExitCode()==0 ) { cache.put(fullCmd, new CacheEntry(result)); }
            }
            return result;
        }
    }

    /**
     * Return cached full result if present and valid (respecting refresh). Otherwise
     * start (or reuse) an asynchronous background collection and return the in-progress
     * entry for partial access.
     */
    public final InProgressEntry getOrStartBackground(String fullCmd, boolean refresh, CommandSpec spec) {
        // First check cache
        var cached = getCached(fullCmd);
        if ( !refresh && cached!=null ) { return null; }
        // Existing in-progress?
        var existing = inProgress.get(fullCmd);
        if ( existing!=null && !existing.isExpired() ) { return existing; }
        // Create new in-progress entry
        var newEntry = new InProgressEntry(fullCmd);
        inProgress.put(fullCmd, newEntry);
        CompletableFuture<MCPToolResultRecords> future = CompletableFuture.supplyAsync(() -> {
            var records = newEntry.getRecords();
            var result = MCPToolFcliRunnerHelper.collectRecords(fullCmd, n->{
                // Interrupt check for cancellation
                if ( Thread.currentThread().isInterrupted() ) { return; }
                records.add(n);
            }, spec);
            if ( Thread.currentThread().isInterrupted() ) { // Cancelled mid-way
                return null; // Don't cache partial as final
            }
            var full = MCPToolResultRecords.from(result, records);
            if ( result.getExitCode()==0 ) { put(fullCmd, full); }
            return full;
        }, backgroundExecutor).whenComplete((r,t)->{
            newEntry.setCompleted(true);
            newEntry.setExitCode(r==null?999:r.getExitCode());
            newEntry.setStderr(r==null?"Cancelled" : r.getStderr());
            // Remove if failed/cancelled so subsequent request can retry
            if ( r==null || newEntry.getExitCode()!=0 ) { inProgress.remove(fullCmd); }
        });
        newEntry.setFuture(future);
        // Always track job via job manager
        var counter = new AtomicInteger(); // Placeholder if we later compute deltas
        var token = jobManager.trackFuture("cache_loader", future, () -> {
            counter.set(newEntry.getRecords().size());
            return newEntry.getRecords().size();
        });
        newEntry.setJobToken(token);
        return newEntry;
    }
    
    public final void put(String fullCmd, MCPToolResultRecords records) {
        if ( records==null ) { return; }
        synchronized(cache) {
            cache.put(fullCmd, new CacheEntry(records));
        }
    }
    
    public final MCPToolResultRecords getCached(String fullCmd) {
        synchronized(cache) {
            var entry = cache.get(fullCmd);
            return entry==null || entry.isExpired() ? null : entry.getFullResult();
        }
    }

    /** Cancel a background collection if running. */
    public final void cancel(String fullCmd) {
        var inProg = inProgress.get(fullCmd);
        if ( inProg!=null ) { inProg.cancel(); }
    }

    /** Shutdown background executor gracefully. */
    public final void shutdown() {
        backgroundExecutor.shutdown();
        try { backgroundExecutor.awaitTermination(2, TimeUnit.SECONDS); } catch ( InterruptedException e ) { Thread.currentThread().interrupt(); }
        backgroundExecutor.shutdownNow();
    }

    /** In-progress tracking entry giving access to partial records list. */
    @Data
    public static final class InProgressEntry {
        private final String cmd;
        private final long created = System.currentTimeMillis();
        private final CopyOnWriteArrayList<JsonNode> records = new CopyOnWriteArrayList<>();
        private volatile CompletableFuture<MCPToolResultRecords> future;
        private volatile boolean completed = false;
        private volatile int exitCode = 0; // Interim
        private volatile String stderr = ""; // Interim
        private volatile String jobToken; // Optional job token for tracking
        InProgressEntry(String cmd) { this.cmd = cmd; }
        boolean isExpired() { return System.currentTimeMillis() > created + TTL; }
        void setFuture(CompletableFuture<MCPToolResultRecords> f) { this.future = f; }
        void cancel() { if ( future!=null ) { future.cancel(true); } }
        void setJobToken(String jt) { this.jobToken = jt; }
    }
    
    @Data @RequiredArgsConstructor
    private static final class CacheEntry {
        private final MCPToolResultRecords fullResult;
        private final long created = System.currentTimeMillis();
        
        public final boolean isExpired() {
            return System.currentTimeMillis() > created + TTL; 
        }
    }
}
