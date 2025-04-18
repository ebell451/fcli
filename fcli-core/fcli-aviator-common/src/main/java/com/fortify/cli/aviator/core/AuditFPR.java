package com.fortify.cli.aviator.core;

import com.fortify.cli.aviator._common.exception.AviatorSimpleException;
import com.fortify.cli.aviator._common.exception.AviatorTechnicalException;
import com.fortify.cli.aviator.config.IAviatorLogger;
import com.fortify.cli.aviator.core.model.AuditResponse;
import com.fortify.cli.aviator.fpr.*;
import com.fortify.cli.aviator.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuditFPR {
    private static final Logger LOG = LoggerFactory.getLogger(AuditFPR.class);

    public static File auditFPR(File file, String token, String url, String appVersion, IAviatorLogger logger)
            throws AviatorSimpleException, AviatorTechnicalException {

        LOG.info("Starting FPR audit process for file: {}", file.getPath());

        Path extractedPath;
        try {
            extractedPath = ZipUtils.extractZip(file.getPath());
            LOG.debug("Extracted FPR to path: {}", extractedPath);
        } catch (IOException e) {
            LOG.error("Failed to extract FPR file: {}", file.getPath(), e);
            throw new AviatorTechnicalException("Unable to extract FPR file due to an I/O error.", e);
        }

        try {
            if (!FPRLoadingUtil.isValidFpr(file.getPath())) {
                LOG.error("Invalid FPR file: {}", file);
                throw new AviatorSimpleException("Invalid FPR file format.");
            }
            if (!FPRLoadingUtil.hasSource(file)) {
                LOG.error("FPR file does not contain source code: {}", file);
                throw new AviatorSimpleException("FPR file does not contain source code.");
            }
        } catch(IOException e) {
            LOG.error("I/O error checking FPR source presence: {}", file.getPath(), e);
            throw new AviatorTechnicalException("I/O error checking FPR source presence.", e);
        }
        LOG.info("FPR validation successful");

        ExtensionsConfig extensionsConfig;
        try {
            extensionsConfig = ResourceUtil.loadYamlConfig("extensions_config.yaml", ExtensionsConfig.class);
            if (extensionsConfig == null) {
                LOG.error("Failed to load extensions configuration (result was null)");
                throw new AviatorTechnicalException("Failed to load required extensions configuration.");
            }
        } catch (IOException e) {
            LOG.error("Failed to load extensions configuration file: {}", e.getMessage(), e);
            throw new AviatorTechnicalException("Unable to load extensions configuration due to an I/O error.", e);
        }

        try {
            FileTypeLanguageMapperUtil.initializeConfig(extensionsConfig);

            AuditProcessor auditProcessor = new AuditProcessor(extractedPath, file.getPath());
            Map<String, AuditIssue> auditIssueMap = auditProcessor.processAuditXML();

            FPRProcessor fprProcessor = new FPRProcessor(file.getPath(), extractedPath, auditIssueMap, auditProcessor);
            List<Vulnerability> vulnerabilities = fprProcessor.process();
            FPRInfo fprInfo = fprProcessor.getFprInfo();

            Map<String, AuditResponse> auditResponses = new ConcurrentHashMap<>();
            IssueAuditor issueAuditor = new IssueAuditor(vulnerabilities, auditProcessor, auditIssueMap, fprInfo, false, logger);
            issueAuditor.performAudit(auditResponses, token, appVersion, fprInfo.getBuildId(), url);

            LOG.info("Completed audit process, received {} responses", auditResponses.size());

            if (auditResponses.isEmpty()) {
                LOG.info("No issues were audited, skipping update and upload");
                return null;
            } else {
                File updatedFile = auditProcessor.updateAndSaveAuditXml(auditResponses, fprInfo.getResultsTag());
                LOG.info("FPR audit process completed successfully");
                return updatedFile;
            }
        } catch (AviatorTechnicalException e) {
            LOG.error("A technical error occurred during FPR processing: {}", e.getMessage(), e);
            throw e;
        }
    }
}