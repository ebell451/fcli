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
package com.fortify.cli.common.action.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.formkiq.graalvm.annotations.Reflectable;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * This class describes action usage header and description.
 */
@Reflectable @NoArgsConstructor
@Data
public final class ActionUsage implements IActionElement {
    @JsonPropertyDescription("Required string: Action usage header, displayed in list and help outputs")
    @JsonProperty(required = true) private String header;
    
    @JsonPropertyDescription("Required string: Action usage description, displayed in help output")
    @JsonProperty(required = true) private String description;
    
    public void postLoad(Action action) {
        Action.checkNotBlank("usage header", header, this);
        Action.checkNotBlank("usage description", description, this);
    }
}