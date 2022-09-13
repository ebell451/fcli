/*******************************************************************************
 * (c) Copyright 2021 Micro Focus or one of its affiliates
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the 
 * "Software"), to deal in the Software without restriction, including without 
 * limitation the rights to use, copy, modify, merge, publish, distribute, 
 * sublicense, and/or sell copies of the Software, and to permit persons to 
 * whom the Software is furnished to do so, subject to the following 
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be included 
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY 
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE 
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
 * IN THE SOFTWARE.
 ******************************************************************************/
package com.fortify.cli.common.session.manager.spi;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fortify.cli.common.rest.runner.IUrlConfig;
import com.fortify.cli.common.rest.runner.UrlConfig;
import com.fortify.cli.common.session.manager.api.ISessionData;
import com.fortify.cli.common.session.manager.api.SessionSummary;

import io.micronaut.core.annotation.ReflectiveAccess;
import lombok.Data;

@Data @ReflectiveAccess @JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractSessionData implements ISessionData {
    private UrlConfig urlConfig;
    private Date created = new Date();
    
    public AbstractSessionData() {}
    
    public AbstractSessionData(IUrlConfig urlConfig) {
        this.urlConfig = UrlConfig.from(urlConfig);
    }

    @JsonIgnore public final SessionSummary getSummary(String authSessionName) {
        return SessionSummary.builder()
                .name(authSessionName)
                .type(getSessionType())
                .url(getUrlConfig().getUrl())
                .created(getCreated())
                .expires(getSessionExpiryDate())
                .build();
    }
    
    /**
     * Subclasses may override this method to provide an actual session expiration date/time if available 
     * @return Date/time when this session will expire
     */
    @JsonIgnore protected Date getSessionExpiryDate() {
        return null;
    }
}