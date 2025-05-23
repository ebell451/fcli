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
package com.fortify.cli.fod.dast_scan.cli.cmd;

import java.util.ArrayList;
import java.util.Set;

import com.fortify.cli.common.cli.util.CommandGroup;
import com.fortify.cli.fod._common.output.cli.mixin.FoDOutputHelperMixins;
import com.fortify.cli.fod._common.rest.FoDUrls;
import com.fortify.cli.fod._common.scan.cli.cmd.AbstractFoDScanSetupCommand;
import com.fortify.cli.fod._common.scan.helper.FoDScanAssessmentTypeDescriptor;
import com.fortify.cli.fod._common.scan.helper.FoDScanHelper;
import com.fortify.cli.fod._common.scan.helper.FoDScanType;
import com.fortify.cli.fod._common.scan.helper.dast.FoDScanDastAutomatedSetupWebsiteRequest;
import com.fortify.cli.fod._common.util.FoDEnums;

import kong.unirest.HttpRequest;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = FoDOutputHelperMixins.SetupWebsite.CMD_NAME) @CommandGroup("*-scan-setup")
public class FoDDastAutomatedScanSetupWebsiteCommand extends AbstractFoDScanSetupCommand {
    @Getter @Mixin private FoDOutputHelperMixins.SetupWebsite outputHelper;
    private final static FoDEnums.DastAutomatedFileTypes dastFileType = FoDEnums.DastAutomatedFileTypes.LoginMacro;

    @Option(names = {"--url", "--site-url"}, required = true)
    private String siteUrl;
    @Option(names = {"--redundant-page-detection"})
    private Boolean redundantPageProtection;
    @Option(names = {"--file-id"})
    private Integer loginMacroFileId;
    @Option(names={"-e", "--exclusions"}, split=",")
    private Set<String> exclusions;
    @Option(names={"--restrict"})
    private Boolean restrictToDirectoryAndSubdirectories;
    @Option(names={"--policy"}, required = true, defaultValue = "Standard")
    private String scanPolicy;
    @Option(names={"--timebox"})
    private Integer timebox;
    @Option(names={"--environment"}, defaultValue = "External")
    private FoDEnums.DynamicScanEnvironmentFacingType environmentFacingType;
    @Option(names = {"--timezone"})
    private String timezone;
    @Option(names = {"--network-auth-type"})
    private FoDEnums.DynamicScanNetworkAuthenticationType networkAuthenticationType;
    @Option(names = {"-u", "--network-username"})
    private String username;
    @Option(names = {"-p", "--network-password"})
    private String password;
    @Option(names = {"--false-positive-removal"})
    private Boolean requestFalsePositiveRemoval;
    @Option(names = {"--create-login-macro"})
    private Boolean createLoginMacro;
    @Option(names = {"--macro-primary-username"})
    private String macroPrimaryUsername;
    @Option(names = {"--macro-primary-password"})
    private String macroPrimaryPassword;
    @Option(names = {"--macro-secondary-username"})
    private String macroSecondaryUsername;
    @Option(names = {"--macro-secondary-password"})
    private String macroSecondaryPassword;
    @Option(names = {"--vpn"})
    private String fodConnectNetwork;

    @Override
    protected String getScanType() {
        return "DAST Automated";
    }
    @Override
    protected String getSetupType() {
        return "Website";
    }

    @Override
    protected HttpRequest<?> getBaseRequest(UnirestInstance unirest, String releaseId) {
        boolean requiresSiteAuthentication = false;
        boolean requiresNetworkAuthentication = false;
        boolean requiresLoginMacroCreation = false;
        int fileIdToUse = (loginMacroFileId != null ? loginMacroFileId : 0);

        if (loginMacroFileId != null && loginMacroFileId > 0) {
            requiresSiteAuthentication = true;
        }
        if (uploadFileMixin != null && uploadFileMixin.getFile() != null) {
            requiresSiteAuthentication = true;
            fileIdToUse = uploadFileToUse(unirest, releaseId, FoDScanType.Dynamic, dastFileType.name());
        }
        FoDScanDastAutomatedSetupWebsiteRequest.NetworkAuthenticationType networkAuthenticationSettings = null;
        if (networkAuthenticationType != null) {
            requiresNetworkAuthentication = true;
            networkAuthenticationSettings = new FoDScanDastAutomatedSetupWebsiteRequest.NetworkAuthenticationType(networkAuthenticationType, username, password);
        }
        ArrayList<FoDScanDastAutomatedSetupWebsiteRequest.Exclusion> exclusionsList = new ArrayList<>();
        if (exclusions != null && !exclusions.isEmpty()) {
            for (String s: exclusions) {
                exclusionsList.add(new FoDScanDastAutomatedSetupWebsiteRequest.Exclusion(s));
            }
        }
        String timeZoneToUse = FoDScanHelper.validateTimezone(unirest, timezone);
        FoDScanDastAutomatedSetupWebsiteRequest.LoginMacroFileCreationType loginMacroFileCreationSettings = null;
        if (createLoginMacro != null) {
            requiresSiteAuthentication = true;
            requiresLoginMacroCreation = createLoginMacro;
            loginMacroFileCreationSettings = new FoDScanDastAutomatedSetupWebsiteRequest.LoginMacroFileCreationType(macroPrimaryUsername,
                    macroPrimaryPassword, macroSecondaryUsername, macroSecondaryPassword);
        }
        if (fodConnectNetwork != null) {
            // if Fortify Connect network site override environmentFacingType to Internal
            environmentFacingType = FoDEnums.DynamicScanEnvironmentFacingType.Internal;
        }

        FoDScanAssessmentTypeDescriptor assessmentTypeDescriptor = FoDScanHelper.getEntitlementToUse(unirest, releaseId, FoDScanType.Dynamic,
                assessmentType, entitlementFrequencyTypeMixin.getEntitlementFrequencyType(), entitlementId);
        entitlementId = assessmentTypeDescriptor.getEntitlementId();
        FoDScanDastAutomatedSetupWebsiteRequest setupRequest = FoDScanDastAutomatedSetupWebsiteRequest.builder()
                .dynamicSiteUrl(siteUrl)
                .enableRedundantPageDetection(redundantPageProtection != null ? redundantPageProtection : false)
                .requiresSiteAuthentication(requiresSiteAuthentication)
                .loginMacroFileId(fileIdToUse)
                .exclusionsList(exclusionsList)
                .restrictToDirectoryAndSubdirectories(restrictToDirectoryAndSubdirectories != null ? restrictToDirectoryAndSubdirectories : false)
                .policy(scanPolicy)
                .timeBoxInHours(timebox)
                .dynamicScanEnvironmentFacingType(environmentFacingType != null ? environmentFacingType : FoDEnums.DynamicScanEnvironmentFacingType.External)
                .timeZone(timeZoneToUse)
                .requiresNetworkAuthentication(requiresNetworkAuthentication)
                .networkAuthenticationSettings(networkAuthenticationSettings)
                .assessmentTypeId(assessmentTypeDescriptor.getAssessmentTypeId())
                .entitlementId(entitlementId)
                .entitlementFrequencyType(FoDEnums.EntitlementFrequencyType.valueOf(assessmentTypeDescriptor.getFrequencyType()))
                .requestFalsePositiveRemoval(requestFalsePositiveRemoval != null ? requestFalsePositiveRemoval : false)
                .requestLoginMacroFileCreation(requiresLoginMacroCreation)
                .loginMacroFileCreationDetails(loginMacroFileCreationSettings)
                .networkName(fodConnectNetwork != null ? fodConnectNetwork : "")
                .build();

        return unirest.put(FoDUrls.DAST_AUTOMATED_SCANS + "/website-scan-setup")
                .routeParam("relId", releaseId)
                .body(setupRequest);
    }

}
