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

// Removed unused logging imports

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fortify.cli.common.exception.FcliSimpleException;
import com.fortify.cli.common.json.JsonHelper;
import com.fortify.cli.common.json.producer.EmptyObjectNodeProducer;
import com.fortify.cli.common.json.producer.IObjectNodeProducer;
import com.fortify.cli.common.json.producer.ObjectNodeProducerApplyFrom;
import com.fortify.cli.common.output.cli.mixin.OutputHelperMixins;
import com.fortify.cli.common.rest.query.IServerSideQueryParamGeneratorSupplier;
import com.fortify.cli.common.rest.query.IServerSideQueryParamValueGenerator;
import com.fortify.cli.common.util.Break;
import com.fortify.cli.fod._common.cli.mixin.FoDDelimiterMixin;
import com.fortify.cli.fod._common.output.cli.cmd.AbstractFoDOutputCommand;
import com.fortify.cli.fod._common.rest.FoDUrls;
import com.fortify.cli.fod._common.rest.query.FoDFiltersParamGenerator;
import com.fortify.cli.fod._common.rest.query.cli.mixin.FoDFiltersParamMixin;
import com.fortify.cli.fod.app.cli.mixin.FoDAppResolverMixin;
import com.fortify.cli.fod.issue.cli.mixin.FoDIssueEmbedMixin;
import com.fortify.cli.fod.issue.cli.mixin.FoDIssueIncludeMixin;
import com.fortify.cli.fod.issue.helper.FoDIssueHelper;
import com.fortify.cli.fod.issue.helper.FoDIssueHelper.IssueAggregationData;
import com.fortify.cli.fod.release.cli.mixin.FoDReleaseByQualifiedNameOrIdResolverMixin;
import com.fortify.cli.fod.release.helper.FoDReleaseDescriptor;
import com.fortify.cli.fod.release.helper.FoDReleaseHelper;

import kong.unirest.HttpRequest;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = OutputHelperMixins.List.CMD_NAME)
public class FoDIssueListCommand extends AbstractFoDOutputCommand implements IServerSideQueryParamGeneratorSupplier {

    @Getter @Mixin private OutputHelperMixins.List outputHelper;
    @Mixin private FoDDelimiterMixin delimiterMixin; // injected in resolvers
    @Mixin private FoDAppResolverMixin.OptionalOption appResolver;
    @Mixin private FoDReleaseByQualifiedNameOrIdResolverMixin.OptionalOption releaseResolver;
    @Mixin private FoDFiltersParamMixin filterParamMixin;
    @Mixin private FoDIssueEmbedMixin embedMixin;
    @Mixin private FoDIssueIncludeMixin includeMixin;
    @Getter private final IServerSideQueryParamValueGenerator serverSideQueryParamGenerator = new FoDFiltersParamGenerator()
            .add("id","id")
            .add("vulnId","vulnId")
            .add("instanceId","instanceId")
            .add("scanType","scanType")
            .add("status","status")
            .add("developerStatus","developerStatus")
            .add("auditorStatus","auditorStatus")
            .add("severity","severity")
            .add("severityString","severityString")
            .add("category","category");

    @Override
    protected IObjectNodeProducer getObjectNodeProducer(UnirestInstance unirest) {
        boolean releaseSpecified = releaseResolver.getQualifiedReleaseNameOrId() != null;
        boolean appSpecified = appResolver.getAppNameOrId() != null;
        if ( releaseSpecified && appSpecified ) {
            throw new FcliSimpleException("Cannot specify both an application and release");
        }
        if ( !releaseSpecified && !appSpecified ) {
            throw new FcliSimpleException("Either an application or release must be specified");
        }
        return releaseSpecified
                ? buildSingleReleaseProducer(unirest, releaseResolver.getReleaseId(unirest))
                : buildApplicationProducer(unirest, appResolver.getAppId(unirest));
    }

    /**
     * Build a streaming producer for a single release. Uses requestObjectNodeProducerBuilder(SPEC)
     * to benefit from paging & transformations; server-side filtering is applied via
     * {@link FoDFiltersParamMixin} acting as an {@code IHttpRequestUpdater} when the builder applies SPEC.
     *
     * Enrichments added per record:
     * <ul>
     *   <li>releaseName (looked up once)</li>
     *   <li>issueUrl (browser convenience)</li>
     *   <li>Embed data if --embed specified</li>
     * </ul>
     * Record transformations from SPEC (query filtering etc) still apply after enrichment.
     *
     * @param unirest FoD REST client
     * @param releaseId Selected release id
     * @return Producer streaming transformed issue records for the release
     */
    private IObjectNodeProducer buildSingleReleaseProducer(UnirestInstance unirest, String releaseId) {
        return buildReleaseIssuesProducer(unirest, releaseId);
    }

    /**
     * Build a producer that lists merged issues across all releases for an application. We aggregate
     * issues per release first (reusing existing helper logic), then perform merge & stream results.
     * Streaming begins once all releases have been processed (merging requires full set).
     *
     * The merge operation combines issues with identical instanceId across releases, adding fields:
     * vulnIds|vulnIdsString, foundInReleases|foundInReleasesString, foundInReleaseIds|foundInReleaseIdsString,
     * ids|idsString. Ordering is applied by helper (severity desc, category, releaseId).
     *
     * NOTE: We currently need all issues loaded before streaming because merge logic correlates
     * across releases. Future optimization could stream partially if helper supports incremental merging.
     *
     * Server-side filters are computed per release using the same logic as the single-release path,
     * but we must pass the resulting string explicitly to {@link FoDIssueHelper#getReleaseIssues}.
     *
     * @param unirest FoD REST client
     * @param appId Application identifier
     * @return Producer streaming merged issue records (empty if no releases)
     */
    private IObjectNodeProducer buildApplicationProducer(UnirestInstance unirest, String appId) {
        List<String> releaseIds = loadReleaseIdsForApp(unirest, appId);
        if ( releaseIds.isEmpty() ) { return EmptyObjectNodeProducer.INSTANCE; }
        ArrayNode aggregated = JsonHelper.getObjectMapper().createArrayNode();
        for ( String releaseId : releaseIds ) {
            buildReleaseIssuesProducer(unirest, releaseId)
                .forEach(node -> { aggregated.add(node); return Break.FALSE; });
        }
        Supplier<Stream<ObjectNode>> streamSupplier = () -> JsonHelper.stream(FoDIssueHelper.mergeReleaseIssues(aggregated))
                .filter(n -> n instanceof ObjectNode)
                .map(n -> (ObjectNode)n);
        return streamingObjectNodeProducerBuilder(ObjectNodeProducerApplyFrom.SPEC)
                .streamSupplier(streamSupplier)
                .build();
    }

    /** Load release ids for given application using requestObjectNodeProducerBuilder(PRODUCT) for paging/product transformations. */
    private List<String> loadReleaseIdsForApp(UnirestInstance unirest, String appId) {
        List<String> releaseIds = new ArrayList<>();
        var producer = requestObjectNodeProducerBuilder(ObjectNodeProducerApplyFrom.PRODUCT)
                .baseRequest(unirest.get(FoDUrls.RELEASES).queryString("filters", "applicationId:"+appId))
                .build();
        producer.forEach(node -> {
            if ( node.has("releaseId") ) {
                releaseIds.add(node.get("releaseId").asText());
            }
            return Break.FALSE; // continue
        });
        return releaseIds;
    }

    @Override
    public boolean isSingular() { return false; }

    // Shared per-release issues producer builder
    private IObjectNodeProducer buildReleaseIssuesProducer(UnirestInstance unirest, String releaseId) {
        FoDReleaseDescriptor releaseDescriptor = FoDReleaseHelper.getReleaseDescriptorFromId(unirest, Integer.parseInt(releaseId), true);
        String releaseName = releaseDescriptor.getReleaseName();
        HttpRequest<?> request = unirest.get(FoDUrls.VULNERABILITIES)
                .routeParam("relId", releaseId)
                .queryString("limit", "10")
                .queryString("orderBy", "severity")
                .queryString("orderDirection", "ASC");
        return requestObjectNodeProducerBuilder(ObjectNodeProducerApplyFrom.SPEC)
                .baseRequest(request)
                .recordTransformer(n -> enrichIssueRecord(unirest, releaseName, n))
                .build();
    }

    private JsonNode enrichIssueRecord(UnirestInstance unirest, String releaseName, com.fasterxml.jackson.databind.JsonNode n) {
        if ( n instanceof ObjectNode node ) {
            node.put("releaseName", releaseName);
            node.put("issueUrl", FoDIssueHelper.getIssueUrl(unirest, node.get("id").asText()));
            IssueAggregationData data = IssueAggregationData.forSingleRelease(node);
            FoDIssueHelper.transformRecord(node, data);
        }
        return n;
    }
}
