import groovy.json.JsonOutput

pipeline {
    agent {
        label "jenkins-agent"
    }

    parameters {
        string(name: 'ECR_REPO_NAME', defaultValue: 'javaspring', description: 'Enter repository name')
        string(name: 'AWS_ACCOUNT_ID', defaultValue: '104824081961', description: 'Enter AWS Account ID')
        string(name: 'BRANCH', defaultValue: 'dev', description: 'Deployment branch for CD repo')
    }

    environment {
        SLACK_CHANNEL = '#all-jenkins'
        SLACK_TOKEN = credentials('slack-token')
        REGION = 'ap-south-1'
        SNYK_TOKEN = credentials('SNYK_TOKEN')
        OPENAI_API_KEY = credentials('openai-api-key') 
        PDF_REPORT = 'ai_report.pdf'
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {

        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }

        stage('Checkout Code') {
            steps {
                git branch: "${params.BRANCH}", credentialsId: 'github-pat', url: 'https://github.com/stackcouture/Java-WebAPP-CI.git'
            }
        }

        stage('Commit SHA') {
            steps {
                script {
                    env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                }
            }
        }

        stage('Build + Test + SBOM') {
            steps {
                sh 'mvn clean verify'
            }
        }

        stage('Publish SBOM') {
            steps {
                script {
                    def sbomFile = 'target/bom.xml'
                    if (fileExists(sbomFile)) {
                        archiveArtifacts artifacts: sbomFile, allowEmptyArchive: true
                        echo "SBOM archived: ${sbomFile}"
                    }
                    else {
                        error "SBOM not found: ${sbomFile}"
                    }
                }
            }
        }

        stage('Prepare Trivy Template') {
            steps {
                sh """
                    mkdir -p contrib
                    curl -sSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl
                """
            }
        }

        stage('Trivy File System Scan') {
            options {
                timeout(time: 10, unit: 'MINUTES')
            }
            steps {
                script {
                    try {
                        runTrivyScanUnified("filesystem-scan",".", "fs")
                    } catch (err) {
                        echo "Trivy File System Scan failed: ${err}"
                    }
                }
            }
        }

        // stage('Sonar Analysis') {
        //     steps {
        //         withSonarQubeEnv('sonar-server') {
	    //            sh ''' 
        //         		mvn clean verify sonar:sonar \
        //         		-Dsonar.projectKey=Java-App
	    //                '''
        //             }
        //     }
        // }

        stage('SonarQube Analysis') {
            steps {
                script {
                    def scannerHome = tool 'sonar-scanner'
                    withSonarQubeEnv('sonar-server') {
                        sh """
                            ${scannerHome}/bin/sonar-scanner \
                                -X \
                                -Dsonar.projectKey=Java-App \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.sources=src/main/java,src/test/java \
                                -Dsonar.exclusions=**/*.js
                        """
                    }
                }
            }
        }

        stage('Quality Gates') {
            steps {
                script {
                    timeout(time: 5, unit: 'MINUTES') {
                            def qualityGate = waitForQualityGate abortPipeline: true, credentialsId: 'sonar-token' 
                            if (qualityGate.status != 'OK') {
                                error "SonarQube Quality Gate failed: ${qualityGate.status}"
                            }   
                            else {
                                echo "SonarQube Quality Gate passed: ${qualityGate.status}"
                            }
                        }
                    }	
                }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ."
            }
        }

        // stage('Security Scans Before Push') {
        //     parallel {
        //         stage('Trivy Before Push') {
        //             options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 script {
        //                     def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
        //                     runTrivyScanUnified("before-push", localTag, "image")
        //                 }
        //             }
        //         }
        //         stage('Snyk Before Push') {
        //             options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 script {
        //                     def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
        //                     runSnykScan("before-push", localTag)
        //                 }
        //             }
        //         }
        //     }
        // }

        stage('Docker Push') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-jenkins-creds',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                ]]) {
                    script {
                        def fullTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                        sh """
                            aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                            aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                            aws ecr get-login-password --region ${env.REGION} | docker login --username AWS --password-stdin ${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com
                            docker tag ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ${fullTag}
                            docker push ${fullTag}
                        """
                    }
                }
            }
        }

        stage('Security Scans After Push') {
            parallel {
                stage('Trivy After Push') {
                     options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runTrivyScanUnified("after-push", pushedTag, "image")
                        }
                    }
                }
                stage('Snyk After Push') {
                     options {
                        timeout(time: 15, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            retry(2) {
                                runSnykScan("after-push", pushedTag)
                            }
                        }
                    }
                }
            }
        }
        
        stage('Cleanup Local Image Tags') {
            steps {
                sh """
                    docker rmi ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} || true
                    docker rmi ${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA} || true
                """
            }
        }

        stage('Generate GPT Report') {
            steps {
                script {

                    def trivyHtmlPath = "reports/trivy/${env.BUILD_NUMBER}/after-push/trivy-image-scan-${env.COMMIT_SHA}.html"
                    def snykJsonPath = "reports/snyk/${env.BUILD_NUMBER}/after-push/snyk-report-${env.COMMIT_SHA}.json"

                    // Check if necessary environment variables are set
                    if (!env.COMMIT_SHA || !env.BUILD_NUMBER) {
                        error("Environment variables COMMIT_SHA or BUILD_NUMBER are not set.")
                    }

                    // Check if the report files exist
                    if (fileExists(trivyHtmlPath) && fileExists(snykJsonPath)) {
                        runGptSecuritySummary("My Java App", env.COMMIT_SHA, env.BUILD_NUMBER, trivyHtmlPath, snykJsonPath)
                    } else {
                        error("One or more required files do not exist: ${trivyHtmlPath}, ${snykJsonPath}")
                    }
                    // runGptSecuritySummary(
                    //     "My Java App",
                    //     env.COMMIT_SHA,
                    //     env.BUILD_NUMBER,
                    //     "reports/trivy/${env.BUILD_NUMBER}/after-push/trivy-image-scan-${env.COMMIT_SHA}.html",
                    //     "reports/snyk/${env.BUILD_NUMBER}/after-push/snyk-report-${env.COMMIT_SHA}.json"
                    // )
                }   
            } 
        } 
    }

    post {
        always {
            archiveArtifacts artifacts: '**/fs.html, **/trivy-image-scan-*.html, **/snyk-report-*.json', allowEmptyArchive: true
            script {
                if (fileExists('target/surefire-reports')) {
                    junit 'target/surefire-reports/*.xml'
                } else {
                    echo "No test results found."
                }
            }
        }

        success {
            script {
                if (fileExists("ai_report.html")) {
                    // sh "pandoc ai_report.html -f html -t pdf -o ${env.PDF_REPORT} --standalone --pdf-engine=wkhtmltopdf"
                    sh "wkhtmltopdf --zoom 1.3 --enable-local-file-access ai_report.html ai_report.pdf"

                    emailext(
                        subject: "Security Report - Build #${env.BUILD_NUMBER} - SUCCESS",
                          body: """
                                 <html>
                                    <body style="font-family: Arial, sans-serif; font-size: 15px; line-height: 1.6; padding: 10px;">
                                        <h2 style="color: #2c3e50;">Hello Team,</h2>
                                        <p>
                                            Please find attached the <strong>AI-generated security report</strong> for <strong>Build #${env.BUILD_NUMBER}</strong>.
                                        </p>
                                        <p>
                                            This report summarizes the security scan results from <strong>Trivy</strong> and <strong>Snyk</strong> for the current build.
                                        </p>
                                        <p>
                                            <strong>Project:</strong> ${env.JOB_NAME}<br/>
                                            <strong>Branch:</strong> ${params.BRANCH}<br/>
                                            <strong>Commit:</strong> ${env.COMMIT_SHA}
                                        </p>
                                        <p>
                                            For detailed insights, please open the attached PDF report.
                                        </p>
                                        <p>
                                            Regards,<br/>
                                            <strong>Jenkins CI/CD</strong> 🤖
                                        </p>
                                    </body>
                                    </html>
                            """,
                        mimeType: 'text/html',
                        attachmentsPattern: 'ai_report.pdf',
                        to: 'naveenramlu@gmail.com',
                        attachLog: false
                    )
                }
                sendSlackNotification('SUCCESS', 'good')
            }
        }

        failure {
            script {
                sendSlackNotification('FAILURE', 'danger')
            }
        }

        unstable {
            script {
                sendSlackNotification('UNSTABLE', 'warning')
            }
        }

        aborted {
            script {
                sendSlackNotification('ABORTED', '#808080')
            }
        }
    }
}

def runSnykScan(stageName, imageTag) {
    def reportDir = "reports/snyk/${env.BUILD_NUMBER}/${stageName}"
    def jsonFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.json"
    def htmlFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.html"

    withCredentials([string(credentialsId: 'SNYK_TOKEN', variable: 'SNYK_TOKEN')]) {
        sh """
            mkdir -p ${reportDir}

            snyk auth $SNYK_TOKEN
            snyk container test ${imageTag} --severity-threshold=high --exclude-base-image-vulns --json > ${jsonFile} || true

            echo "<html><body><pre>" > ${htmlFile}
            if [ -s ${jsonFile} ]; then
                cat ${jsonFile} | jq . >> ${htmlFile}
            else
                echo "Snyk scan failed or returned no data. Please check Jenkins logs or retry." >> ${htmlFile}
            fi
            echo "</pre></body></html>" >> ${htmlFile}
        """
    }

    publishHTML(target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: htmlFile.replace("${reportDir}/", ""),
        reportName: "Snyk Image Scan (${stageName}) - Build ${env.BUILD_NUMBER}"
    ])
    archiveArtifacts artifacts: "${jsonFile},${htmlFile}", allowEmptyArchive: true
}

def runTrivyScanUnified(stageName, scanTarget, scanType) {
    def reportDir = "reports/trivy/${env.BUILD_NUMBER}/${stageName}"
    def htmlReport = scanType == 'fs' 
        ? "${reportDir}/trivy-fs-scan-${env.COMMIT_SHA}.html"
        : "${reportDir}/trivy-image-scan-${env.COMMIT_SHA}.html"

    def jsonReport = scanType == 'fs' 
        ? "${reportDir}/trivy-fs-scan-${env.COMMIT_SHA}.json"
        : "${reportDir}/trivy-image-scan-${env.COMMIT_SHA}.json"

    sh """
        mkdir -p ${reportDir}
        trivy ${scanType} --format template --template "@contrib/html.tpl" -o ${htmlReport} ${scanTarget}
        trivy ${scanType} --format json -o ${jsonReport} ${scanTarget}
    """

    publishHTML(target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: "*.html",
        reportName: "Trivy ${scanType == 'fs' ? 'File System' : 'Image'} Scan (${stageName}) - Build ${env.BUILD_NUMBER}"
    ])

    archiveArtifacts artifacts: "${reportDir}/*.html,${reportDir}/*.json", allowEmptyArchive: true
}


def sendSlackNotification(String status, String color) {
    def emojiMap = [
        SUCCESS : "✅ Deployment Successful!",
        FAILURE : "❌ FAILURE Deployment!",
        UNSTABLE: "⚠️ UNSTABLE Deployment!",
        ABORTED : "🛑 ABORTED Deployment!"
    ]

    wrap([$class: 'BuildUser']) {
        slackSend(
            channel: env.SLACK_CHANNEL,
            token: env.SLACK_TOKEN,
            color: color,
            message: """\
                *${emojiMap[status]}*
                *Project:* `${env.JOB_NAME}`
                *Commit:* `${env.COMMIT_SHA}`
                *Build Number:* #${env.BUILD_NUMBER}
                *Branch:* `${params.BRANCH}`
                *Triggered By:* ${BUILD_USER} 👤
                *Build Tag:* <${env.BUILD_URL}|Click to view in Jenkins>
                _This is an automated notification from Jenkins 🤖_
            """
        )
    }
}

def runGptSecuritySummary(String projectName, String gitSha, String buildNumber, String trivyHtmlPath, String snykJsonPath) {
    def trivyJsonPath = trivyHtmlPath.replace(".html", ".json")
    def trivySummary = extractTopVulns(trivyJsonPath, "Trivy")
    def snykSummary = extractTopVulns(snykJsonPath, "Snyk")

    def trivyStatus = (
        trivySummary.toLowerCase().contains("no high") ||
        trivySummary.toLowerCase().contains("no critical")
    ) ? "OK" : "Issues Found"

    def snykLower = snykSummary.toLowerCase()
    def snykStatus = (
        snykLower.contains("no high") &&
        snykLower.contains("no critical") &&
        snykLower.contains("no medium")
    ) ? "OK" : "Issues Found"

    if (!snykSummary?.trim()) {
        snykSummary = "No high or critical vulnerabilities found by Snyk."
        snykStatus = "OK"
    }

    def sonarSummary = getSonarQubeSummary()
    def sonarIssues = sonarSummary?.codeSmells + sonarSummary?.vulnerabilities

    // Summarize SonarQube Code Smells and Vulnerabilities
    def sonarCodeSmellsSummary = sonarSummary.codeSmells.collect { 
        return "Severity: ${it.severity}, Message: ${it.message}" 
    }.join("\n")

    def sonarVulnerabilitiesSummary = sonarSummary.vulnerabilities.collect { 
        return "Severity: ${it.severity}, Message: ${it.message}" 
    }.join("\n")

    echo "Trivy Summary:\n${trivySummary}"
    echo "Snyk Summary:\n${snykSummary}"
    echo "SonarQube Summary:\n${sonarSummary}"

    def prompt = """
    You are a security analyst assistant.

    Generate a clean HTML security report based on the following scan data. Use only <h2>, <ul>, <p>, and <strong> tags. Avoid Markdown or code blocks.

    Include these sections:
    - Project Overview (project name, SHA, build number)
    - Vulnerabilities Summary (grouped by severity: Critical, High, Medium)
    - Code Smells Summary
    - License Issues (e.g., GPL, AGPL, LGPL)
    - Recommendations (2–4 practical points)
    - One line with: <p><strong>Status:</strong> OK</p> or <p><strong>Status:</strong> Issues Found</p>

    Context:
    Project: ${projectName}
    Commit SHA: ${gitSha}
    Build Number: ${buildNumber}

    Scan Status Summary:
    - Trivy: ${trivyStatus}
    - Snyk: ${snykStatus}
    - SonarQube: ${sonarSummary.qualityGateSummary}

    --- Trivy Top Issues ---
    ${trivySummary}

    --- Snyk Top Issues ---
    ${snykSummary}

    --- SonarQube Issues ---
    Code Smells:
    ${sonarCodeSmellsSummary}

    Vulnerabilities:
    ${sonarVulnerabilitiesSummary}
    """
    echo "GPT Prompt:\n${prompt}"

    def gptPromptFile = "openai_prompt.json"
    def gptOutputFile = "openai_response.json"
    def gptReportFile = "ai_report.html"

    def payload = [
        model: "gpt-4o-mini",
        messages: [[role: "user", content: prompt]]
    ]

    writeFile file: gptPromptFile, text: groovy.json.JsonOutput.toJson(payload)

    withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_API_KEY')]) {
        def responseJson = sh(script: """
            curl -s https://api.openai.com/v1/chat/completions \\
            -H "Authorization: Bearer \$OPENAI_API_KEY" \\
            -H "Content-Type: application/json" \\
            -d @${gptPromptFile}
        """, returnStdout: true).trim()

        echo "Response from OpenAI API: ${responseJson}"

        // Check if the response is empty or invalid
        if (responseJson == null || responseJson.isEmpty()) {
            error("Received empty or invalid response from OpenAI API")
        }

        writeFile file: gptOutputFile, text: responseJson

        try {
            // Check if the response is a valid JSON string
            def response = null
            try {
                response = readJSON text: responseJson
            } catch (Exception e) {
                error("Failed to parse JSON response: ${e.getMessage()}")
            }

            // Check if the expected 'choices' and 'message' content are in the response
            def gptContent = response?.choices?.get(0)?.message?.content
            if (!gptContent) {
                error("GPT response is missing the expected content field")
            }

            // Clean the GPT content
            gptContent = gptContent
                .replaceAll(/(?m)^```html\s*/, "")
                .replaceAll(/(?m)^```$/, "")
                .trim()

            def (statusText, badgeColor, badgeClass) = parseStatusBadge(gptContent)

            def htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Security Report - Build Summary</title>
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f9f9f9; margin: 40px; }
                    h1, h2 { color: #2c3e50; }
                    .section { margin-bottom: 25px; }
                    ul { margin-top: 0; padding-left: 20px; }
                    .highlight { background: #f9f9f9; padding: 10px; border-left: 5px solid #2c3e50; white-space: pre-wrap; word-wrap: break-word; }
                    .badge-ok { color: green; font-weight: bold; }
                    .badge-fail { color: red; font-weight: bold; }
                    a { color: #2c3e50; text-decoration: underline; }
                    footer { margin-top: 40px; font-size: 0.9em; color: #888; }
                </style>
            </head>
            <body>
                <img src="https://www.jenkins.io/images/logos/jenkins/jenkins.png" alt="Jenkins" height="70" />
                <h1>Security Scan Summary</h1>

                <div class="section">
                    <h2>Trivy Scan</h2>
                    <p>Full Trivy scan results are archived. <a href="${env.BUILD_URL}artifact/${trivyHtmlPath}">View full report</a></p>
                </div>

                <div class="section">
                    <h2>Snyk Summary</h2>
                    <p><strong>Status:</strong> <span class="${badgeClass}">${statusText} ${badgeColor}</span></p>
                </div>

                <div class="section">
                    <h2>SonarQube Issues</h2>
                    <h3>Code Smells</h3>
                    <ul>
                        ${formatSonarQubeIssues(sonarSummary.codeSmells)}
                    </ul>

                    <h3>Vulnerabilities</h3>
                    <ul>
                         ${formatSonarQubeIssues(sonarSummary.vulnerabilities)}
                    </ul>
                </div>

                <div class="section">
                    <h2>AI Recommendations</h2>
                    <div class="highlight">
                        ${gptContent}
                    </div>
                </div>

                <footer>
                    <p>Generated by Jenkins | AI Security Summary | Build #${buildNumber}</p>
                </footer>
            </body>
            </html>
            """
            writeFile file: gptReportFile, text: htmlContent
            echo "AI-powered GPT report generated: ${gptReportFile}"
        } catch (Exception e) {
            error("Failed to parse or process the GPT response: ${e.getMessage()}")
        }
    }
}


def formatSonarQubeIssues(issues) {
    return issues.collect { issue ->
        "<li><strong>${issue.severity}:</strong> ${issue.message} | <strong>Component:</strong> ${issue.component}</li>"
    }.join("\n")
}

def extractTopVulns(String jsonPath, String toolName) {
    if (!fileExists(jsonPath) || readFile(jsonPath).trim().isEmpty()) {
        return "${toolName} JSON file not found or is empty."
    }

   if (toolName == "Snyk") {
        return sh(
            script: """#!/bin/bash
                jq -r '
                    .vulnerabilities? // [] |
                    map(select(.severity == "high" or .severity == "critical" or .severity == "medium")) |
                    sort_by(.severity)[:5][] |
                    "* ID: \\(.id) | Title: \\(.title) [\\(.severity)] in \\(.name)"
                ' ${jsonPath} || echo "No high, critical or medium issues found in ${toolName}."
            """,
            returnStdout: true
        ).trim()
    } else if (toolName == "Trivy") {
        return sh(
            script: """#!/bin/bash
                jq -r '
                    .Results[]?.Vulnerabilities? // [] |
                    map(select(.Severity == "HIGH" or .Severity == "CRITICAL")) |
                    sort_by(.Severity)[:5][] |
                    "* ID: \\(.VulnerabilityID) | Title: \\(.Title) [\\(.Severity)] in \\(.PkgName)"
                ' ${jsonPath} || echo "No high or critical issues found in ${toolName}."
            """,
            returnStdout: true
        ).trim()
    } else {
        return "Unsupported tool: ${toolName}"
    }
}

def parseStatusBadge(String gptContent) {
    echo "Raw GPT content for badge parsing:\n${gptContent}"
    def matcher = gptContent =~ /(?i)<strong>Status:<\/strong>\s*(OK|Issues Found)/
    def statusText = matcher.find() ? matcher.group(1).toUpperCase() : "ISSUES FOUND"
    def badgeColor = statusText == "OK" ? "✅" : "❌"
    def badgeClass = statusText == "OK" ? "badge-ok" : "badge-fail"
    return [statusText, badgeColor, badgeClass]
}

def getSonarQubeSummary() {
    def projectKey = "Java-App"
    def sonarHost = "http://35.154.164.253/:9000"

    def apiQualityGateUrl = "${sonarHost}/api/qualitygates/project_status?projectKey=${projectKey}"
    def qualityGateResponse = sh(script: "curl -s ${apiQualityGateUrl}", returnStdout: true).trim()
    echo "Quality Gate Response: ${qualityGateResponse}"

    def qualityGateJson
    try {
        qualityGateJson = readJSON text: qualityGateResponse
    } catch (Exception e) {
        echo "Error parsing quality gate JSON: ${e.message}"
        return
    }

    def qualityGateStatus = qualityGateJson?.projectStatus?.status
    def qualityGateSummary = qualityGateStatus == "OK" ? "SonarQube Quality Gate Passed" : "SonarQube Quality Gate Failed: ${qualityGateStatus}"

    def apiIssuesUrl = "${sonarHost}/api/issues/search?projectKeys=${projectKey}&types=CODE_SMELL,VULNERABILITY&severities=BLOCKER,CRITICAL,MAJOR&ps=1000"
    def issuesResponse = sh(script: "curl -s ${apiIssuesUrl}", returnStdout: true).trim()

    def issuesJson
    try {
        issuesJson = readJSON text: issuesResponse
    } catch (Exception e) {
        echo "Error parsing issues JSON: ${e.message}"
        return
    }

    def codeSmells = []
    def vulnerabilities = []

    issuesJson?.issues?.each { issue ->
        def issueEntry = [
            id: issue.key,
            message: issue.message,
            severity: issue.severity,
            component: issue.componentKey
        ]

        if (issue.type == "CODE_SMELL") {
            codeSmells.add(issueEntry)
        } else if (issue.type == "VULNERABILITY") {
            vulnerabilities.add(issueEntry)
        }
    }

    return [
        codeSmells: codeSmells,
        vulnerabilities: vulnerabilities,
        qualityGateSummary: qualityGateSummary,
        qualityGateStatus: qualityGateStatus
    ]
}
