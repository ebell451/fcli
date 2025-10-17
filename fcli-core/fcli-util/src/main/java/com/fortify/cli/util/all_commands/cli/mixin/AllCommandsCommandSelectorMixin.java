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
package com.fortify.cli.util.all_commands.cli.mixin;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fortify.cli.common.cli.util.FcliCommandSpecHelper;
import com.fortify.cli.common.json.JsonHelper;
import com.fortify.cli.common.json.producer.IObjectNodeProducer;
import com.fortify.cli.common.json.producer.StreamingObjectNodeProducer;
import com.fortify.cli.common.spel.query.QueryExpression;
import com.fortify.cli.common.spel.query.QueryExpressionTypeConverter;

import lombok.Data;
import lombok.Getter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

/**
 *
 * @author Ruud Senden
 */
public class AllCommandsCommandSelectorMixin {
    @Option(names = {"-q", "--query"}, order=1, converter = QueryExpressionTypeConverter.class, paramLabel = "<SpEL expression>")
    @Getter private QueryExpression queryExpression;

    public final IObjectNodeProducer getObjectNodeProducer() {
        return StreamingObjectNodeProducer.builder()
                .streamSupplier(this::createObjectNodeStream)
                .build();
    }

    public final Stream<ObjectNode> createObjectNodeStream() {
        return createStream().map(n->n.getNode());
    }
    
    public final Stream<CommandSpec> createCommandSpecStream() {
        return createStream().map(n->n.getSpec());
    }
    
    private final Stream<CommandSpecAndNode> createStream() {
        return FcliCommandSpecHelper.rootCommandTreeStream()
            .map(CommandSpecAndNode::new)
            .filter(n->n.matches(queryExpression))
            .distinct();
    }
    
    @Data
    private static final class CommandSpecAndNode  {
        private final CommandSpec spec;
        private final ObjectNode node;
        
        private CommandSpecAndNode(CommandSpec spec) {
            this.spec = spec;
            this.node = createNode(spec);
        }

        public boolean matches(QueryExpression queryExpression) {
            return queryExpression==null || queryExpression.matches(node);
        }
    }

    private static final ObjectNode createNode(CommandSpec spec) {
        var hiddenParent = FcliCommandSpecHelper.hasHiddenParent(spec);
        var hiddenSelf = FcliCommandSpecHelper.isHiddenSelf(spec);
        var hidden = FcliCommandSpecHelper.isHiddenSelfOrParent(spec);
        var mcpIgnored = FcliCommandSpecHelper.isMcpIgnored(spec);
        var nameComponents = spec.qualifiedName(" ").split(" ");
        var module = nameComponents.length>1 ? nameComponents[1] : "";
        var entity = nameComponents.length>2 ? nameComponents[2] : "";
        var action = nameComponents.length>3 ? nameComponents[3] : "";
        ObjectNode result = JsonHelper.getObjectMapper().createObjectNode();
        result.put("command", spec.qualifiedName(" "));
        result.put("module", module);
        result.put("entity", entity);
        result.put("action", action);
        result.put("hidden", hidden);
        result.put("hiddenParent", hiddenParent);
        result.put("hiddenSelf", hiddenSelf);
        result.put("mcpIgnored", mcpIgnored);
        result.put("runnable", FcliCommandSpecHelper.isRunnable(spec));
        result.put("usageHeader", String.join("\n", spec.usageMessage().header()));
        result.set("aliases", Stream.of(spec.aliases()).map(TextNode::new).collect(JsonHelper.arrayNodeCollector()));
        result.put("aliasesString", Stream.of(spec.aliases()).collect(Collectors.joining(", ")));
        result.set("options", spec.optionsMap().keySet().stream().map(TextNode::new).collect(JsonHelper.arrayNodeCollector()));
        result.put("optionsString", spec.optionsMap().keySet().stream().collect(Collectors.joining(", ")));
        return result;
    }
}
