package com.fortify.cli.fod.issue.cli.mixin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fortify.cli.common.json.JsonHelper;
import com.fortify.cli.common.output.transform.IRecordTransformer;
import com.fortify.cli.common.rest.unirest.IHttpRequestUpdater;
import com.fortify.cli.common.util.DisableTest;
import com.fortify.cli.common.util.DisableTest.TestType;

import kong.unirest.HttpRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import picocli.CommandLine.Option;

public class FoDIssueIncludeMixin implements IHttpRequestUpdater, IRecordTransformer {
    @DisableTest(TestType.MULTI_OPT_PLURAL_NAME)
    @Option(names = {"--include", "-i"}, split = ",", defaultValue = "visible", descriptionKey = "fcli.fod.issue.list.includeIssue", paramLabel="<status>")
    private Set<FoDIssueInclude> includes;

    public HttpRequest<?> updateRequest(HttpRequest<?> request) {
        // TODO Potentially if 'includes' contains ONLY suppressed, we could also
        //      add filters=isSuppressed:true to perform server-side filtering
        //      (note that FoD doesn't allow for server-side filtering on closedStatus,
        //      so we can't do the same for fixed issues).
        //      However, we'd need to check how to integrate this with any 
        //      FoDFiltersParamGenerator defined on the command, i.e., how FoD
        //      reacts if there are multiple 'filters' parameters, and/or do
        //      some refactoring to generate only a single 'filters' parameter
        //      from multiple sources. Alternatively, instead of directly generating
        //      a 'filters' parameter, we could potentially amend the client-side
        //      query, which would then be picked up by any FoDFiltersParamGenerator,
        //      and also remove the need for having an explicit transformRecord()
        //      method to perform client-side filtering.
        if ( includes!=null ) {
            for ( var include : includes) {
                var queryParameterName = include.getRequestParameterName();
                if ( queryParameterName!=null ) {
                    request = request.queryString(queryParameterName, "true");
                }
            }
        }
        return request;
    }
    
    @Override
    public JsonNode transformRecord(JsonNode record) {
        var objectNode = (ObjectNode)record;
        objectNode.put("location", JsonHelper.evaluateSpelExpression(record, "primaryLocation+(lineNumber==null?'':(':'+lineNumber))", String.class));
        // If includes doesn't include 'visible', we return null for any visible (non-suppressed
        // & non-fixed) issues. We don't need explicit handling for other cases, as suppressed or
        // fixed issues won't be returned by FoD if not explicitly specified through the --include
        // option.
        return !includes.contains(FoDIssueInclude.visible)
                && JsonHelper.evaluateSpelExpression(objectNode, "!isSuppressed && !closedStatus", Boolean.class)
                ? null
                : addVisibilityProperties(objectNode);
    }
    
    private ObjectNode addVisibilityProperties(ObjectNode record) {
        Map<String,String> visibility = new LinkedHashMap<>();
        if ( getBoolean(record, "isSuppressed") ) { visibility.put("suppressed", "(S)"); }
        if ( getBoolean(record, "closedStatus") ) { visibility.put("fixed", "(F)"); } 
        if ( visibility.isEmpty() )               { visibility.put("visible", " "); }
        record.put("visibility", String.join(",", visibility.keySet()))
            .put("visibilityMarker", String.join("", visibility.values()));
        return record;
    }

    private boolean getBoolean(ObjectNode record, String propertyName) {
        var field = record.get(propertyName);
        return field==null ? false : field.asBoolean();
    }

    @RequiredArgsConstructor
    public static enum FoDIssueInclude {
        visible(null), fixed("includeFixed"), suppressed("includeSuppressed");

        @Getter
        private final String requestParameterName;
    }
}
