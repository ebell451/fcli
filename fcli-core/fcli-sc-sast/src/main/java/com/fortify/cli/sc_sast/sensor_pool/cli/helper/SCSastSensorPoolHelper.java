/*******************************************************************************
 * Copyright 2021, 2023 Open Text.
 *
 * The only warranties for products and services of Open Text 
 * and its affiliates and licensors ("Open Text") are as may 
 * be set forth in the express warranty statements accompanying 
 * such products and services. Nothing herein should be construed 
 * as constituting an additional warranty. Open Text shall not be 
 * liable for technical or editorial errors or omissions contained 
 * herein. The information contained herein is subject to change 
 * without notice.
 *******************************************************************************/
package com.fortify.cli.sc_sast.sensor_pool.cli.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fortify.cli.common.exception.FcliSimpleException;
import com.fortify.cli.common.json.JsonHelper;

import kong.unirest.GetRequest;
import kong.unirest.UnirestInstance;

public class SCSastSensorPoolHelper {
    public static final SCSastSensorPoolDescriptor getRequiredSensorPool(UnirestInstance unirest, String sensorPoolNameOrUuid) {
        SCSastSensorPoolDescriptor descriptor = getOptionalSensorPool(unirest, sensorPoolNameOrUuid);
        if ( descriptor==null ) {
            throw new FcliSimpleException("No sensor pool found for name or uuid: "+sensorPoolNameOrUuid);
        }
        return descriptor;
    }
    
    public static final SCSastSensorPoolDescriptor getOptionalSensorPool(UnirestInstance unirest, String sensorPoolNameOrUuid) {
        JsonNode sensorPools = getBaseRequest(unirest).asObject(ObjectNode.class).getBody().get("beans");
        JsonNode sensorPool = JsonHelper.evaluateSpelExpression(sensorPools,String.format("#this.?[#this.name=='%s' || #this.uuid=='%s' ]", sensorPoolNameOrUuid, sensorPoolNameOrUuid),ArrayNode.class);

        if ( sensorPool.size()>1 ) {
            throw new FcliSimpleException("Multiple sensor pools found");
        }

        return sensorPool.size()==0 ? null : JsonHelper.treeToValue(sensorPool.get(0), SCSastSensorPoolDescriptor.class);
    }

    private static GetRequest getBaseRequest(UnirestInstance unirest) {
        GetRequest request = unirest.get("/rest/v4/info/pools");
        return request;
    }
}
