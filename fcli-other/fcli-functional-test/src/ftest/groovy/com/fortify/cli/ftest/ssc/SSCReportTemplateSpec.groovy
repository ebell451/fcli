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
package com.fortify.cli.ftest.ssc

import static com.fortify.cli.ftest._common.spec.FcliSession.FcliSessionType.SSC

import com.fortify.cli.ftest._common.Fcli
import com.fortify.cli.ftest._common.spec.FcliBaseSpec
import com.fortify.cli.ftest._common.spec.FcliSession
import com.fortify.cli.ftest._common.spec.Prefix
import com.fortify.cli.ftest._common.spec.TestResource

import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Stepwise

@Prefix("ssc.report-template") @FcliSession(SSC) @Stepwise
class SSCReportTemplateSpec extends FcliBaseSpec {
    @Shared @TestResource("runtime/ssc/project_report.rptdesign") String sampleTemplate
    @Shared @TestResource("runtime/ssc/ReportTemplateConfig.yml") String sampleConfig
    @Shared String random = System.currentTimeMillis()
    @Shared private String templateName = "fcli-${random}"
    
    def setupSpec() {
        def configFile = new File(sampleConfig)
        def configContents = configFile.text.replace('${templateName}', templateName)
        configFile.text = configContents
    }
    
    def "generate-config"() {
        def args = "ssc report create-template-config -y"
        when:
            def result = Fcli.run(args)
        then:
            verifyAll(result.stdout) {
                size()>=2
                it[1].contains("GENERATED")
            }
    }
    
    def "create"() {
        def args = "ssc report create-template --template $sampleTemplate --config $sampleConfig"
        when:
            def result = Fcli.run(args)
        then:
            verifyAll(result.stdout) {
                size()>=2
                it.last().contains("CREATED")
            }
    }
    
    def "list"() {
        def args = "ssc report list-templates --store templates"
        when:
            def result = Fcli.run(args)
        then:
            verifyAll(result.stdout) {
                size()>0
                it[0].replace(' ', '').equals("IdNameTypeTemplatedocidInuse")
                it.any { it.contains(templateName) }
            }
    }
    
    def "get.byName"() {
        def args = "ssc report get-template ${templateName} --store template"
        when:
            def result = Fcli.run(args)
        then:
            verifyAll(result.stdout) {
                size()>=2
                it[2].contains(templateName)
            }
    }
    
    def "get.byId"() {
        def args = "ssc report get-template ::template::"
        when:
            def result = Fcli.run(args)
        then:
            verifyAll(result.stdout) {
                size()>=2
                it[2].contains(templateName)
            }
    }
    
    def "download"() {
        def args = "ssc report download-template ::template::"
        when:
            def result = Fcli.run(args)
        then:
            verifyAll(result.stdout) {
                size()>0
                it.last().contains("DOWNLOADED")
            }
    }
    
    def "delete"() {
        def args = "ssc report delete-template ::template::"
        when:
            def result = Fcli.run(args)
        then:
            verifyAll(result.stdout) {
                size()>0
                it.last().contains("DELETED")
            }
    }
    
    def "verifyDeleted"() {
            def args = "ssc report list-templates"
            when:
                def result = Fcli.run(args)
            then:
                verifyAll(result.stdout) {
                    size()>0
                    !it.any { it.contains(templateName) }
                }
    }
    
}
