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
package com.fortify.cli.common.action.runner.processor;

import java.util.List;

import com.formkiq.graalvm.annotations.Reflectable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor @Data @EqualsAndHashCode(callSuper = true) @Reflectable
public abstract class AbstractActionStepProcessorListEntries<T> extends AbstractActionStepProcessorEntries<T> {
    public final void process() {
        var list = getList();
        if ( list!=null ) { list.forEach(e->processEntry(e)); }
    }
    
    protected abstract void process(T entry);
    
    protected abstract List<T> getList();
}
