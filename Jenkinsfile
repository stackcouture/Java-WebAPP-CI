import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.net.URLEncoder

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
        SONAR_TOKEN = credentials('sonar-token')
        DEP_TRACK_API_KEY = credentials('dependency-track-api-key')
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

        stage('Publish and Upload SBOM to Dependency-Track') {
            steps {
                withCredentials([string(credentialsId: 'dependency-track-api-key', variable: 'DT_API_KEY')]) {
                    script {
                        def sbomFile = 'target/bom.xml'
                        def projectName = "${params.ECR_REPO_NAME}"
                        def projectVersion = "${env.COMMIT_SHA}"
                        def dependencyTrackUrl = 'http://13.201.191.212:8081/api/v1/bom'

                        if (!fileExists(sbomFile)) {
                            error "❌ SBOM not found: ${sbomFile}"
                        }

                        // Archive the SBOM file
                        archiveArtifacts artifacts: sbomFile, allowEmptyArchive: true
                        echo "📦 SBOM archived: ${sbomFile}"

                        // Upload to Dependency-Track
                        echo "📤 Uploading SBOM to Dependency-Track for ${projectName}:${projectVersion}"
                        sh """
                            curl -X POST "${dependencyTrackUrl}" \
                                -H "X-Api-Key: ${DT_API_KEY}" \
                                -H "Content-Type: multipart/form-data" \
                                -F "autoCreate=true" \
                                -F "projectName=${projectName}" \
                                -F "projectVersion=${projectVersion}" \
                                -F "bom=@${sbomFile}"
                        """
                    }
                }
            }
        }


        // stage('Publish SBOM') {
        //     steps {
        //         script {
        //             def sbomFile = 'target/bom.xml'
        //             if (fileExists(sbomFile)) {
        //                 archiveArtifacts artifacts: sbomFile, allowEmptyArchive: true
        //                 echo "SBOM archived: ${sbomFile}"
        //             }
        //             else {
        //                 error "SBOM not found: ${sbomFile}"
        //             }
        //         }
        //     }
        // }

        // stage('Upload SBOM to Dependency-Track') {
        //     steps {
        //         withCredentials([string(credentialsId: 'dependency-track-api-key', variable: 'DT_API_KEY')]) {
        //             script {
        //                 def sbomFile = 'target/bom.xml'
        //                 if (!fileExists(sbomFile)) {
        //                     error "❌ SBOM file not found: ${sbomFile}"
        //                 }

        //                 def projectName = "${params.ECR_REPO_NAME}"
        //                 def projectVersion = "${env.COMMIT_SHA}"
        //                 def dependencyTrackUrl = 'http://13.201.191.212:8081//api/v1/bom'

        //                 echo "🔐 Uploading SBOM for ${projectName}:${projectVersion}"

        //                 withEnv([
        //                     "DEPTRACK_URL=${dependencyTrackUrl}",
        //                     "PROJECT_NAME=${projectName}",
        //                     "PROJECT_VERSION=${projectVersion}"
        //                 ]) {
        //                     sh '''#!/bin/bash
        //                         curl -X POST "$DEPTRACK_URL" \
        //                             -H "X-Api-Key: $DT_API_KEY" \
        //                             -H "Content-Type: multipart/form-data" \
        //                             -F "autoCreate=true" \
        //                             -F "projectName=$PROJECT_NAME" \
        //                             -F "projectVersion=$PROJECT_VERSION" \
        //                             -F "bom=@target/bom.xml"
        //                     '''
        //                 }
        //             }
        //         }
        //     }
        // }

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
    
        // stage('SonarQube Analysis') {
        //     steps {
        //         script {
        //             def scannerHome = tool 'sonar-scanner'
        //             withSonarQubeEnv('sonar-server') {
        //                 sh """
        //                     ${scannerHome}/bin/sonar-scanner \
        //                         -Dsonar.projectKey=Java-App \
        //                         -Dsonar.java.binaries=target/classes \
        //                         -Dsonar.sources=src/main/java,src/test/java \
        //                         -Dsonar.exclusions=**/*.js
        //                 """
        //             }
        //         }
        //     }
        // }

        // stage('Quality Gates') {
        //     steps {
        //         script {
        //             timeout(time: 5, unit: 'MINUTES') {
        //                     def qualityGate = waitForQualityGate abortPipeline: true, credentialsId: 'sonar-token' 
        //                     if (qualityGate.status != 'OK') {
        //                         error "SonarQube Quality Gate failed: ${qualityGate.status}"
        //                     }   
        //                     else {
        //                         echo "SonarQube Quality Gate passed: ${qualityGate.status}"
        //                     }
        //                 }
        //             }	
        //         }
        // }

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

                    if (fileExists(trivyHtmlPath) && fileExists(snykJsonPath)) {
                        runGptSecuritySummary(
                            "my-app", 
                            env.COMMIT_SHA, 
                            env.BUILD_NUMBER, 
                            trivyHtmlPath, 
                            snykJsonPath
                        )
                    } else {
                        error("One or more required files do not exist: ${trivyHtmlPath}, ${snykJsonPath}")
                    }
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

// def runGptSecuritySummary(String projectName, String gitSha, String buildNumber, String trivyHtmlPath, String snykJsonPath) {
//     def trivyJsonPath = trivyHtmlPath.replace(".html", ".json")
//     def trivySummary = extractTopVulns(trivyJsonPath, "Trivy")
//     def snykSummary = extractTopVulns(snykJsonPath, "Snyk")

//     def trivyStatus = (
//         trivySummary.toLowerCase().contains("no high") ||
//         trivySummary.toLowerCase().contains("no critical")
//     ) ? "OK" : "Issues Found"

//     def snykLower = snykSummary.toLowerCase()
//     def snykStatus = (
//         snykLower.contains("no high") &&
//         snykLower.contains("no critical") &&
//         snykLower.contains("no medium")
//     ) ? "OK" : "Issues Found"

//     if (!snykSummary?.trim()) {
//         snykSummary = "No high or critical vulnerabilities found by Snyk."
//         snykStatus = "OK"
//     }

//     def sonarSummary = getSonarQubeSummary()
//     def sonarCodeSmellsSummary = sonarSummary.sonarCodeSmellsSummary
//     def sonarVulnerabilitiesSummary = sonarSummary.sonarVulnerabilitiesSummary

//     def prompt = """
//     You are a security analyst assistant.

//     Generate a clean HTML security report based on the following scan data. Use only <h2>, <ul>, <p>, and <strong> tags. Avoid Markdown or code blocks.

//     Include these sections:
//     - Project Overview (project name, SHA, build number)
//     - Vulnerabilities Summary (grouped by severity: Critical, High, Medium)
//     - Code Smells Summary
//     - License Issues (e.g., GPL, AGPL, LGPL)
//     - Recommendations (2–4 practical points)
//     - One line with: <p><strong>Status:</strong> OK</p> or <p><strong>Status:</strong> Issues Found</p>

//     Context:
//     Project: ${projectName}
//     Commit SHA: ${gitSha}
//     Build Number: ${buildNumber}

//     Scan Status Summary:
//     - Trivy: ${trivyStatus}
//     - Snyk: ${snykStatus}
//     - SonarQube: ${sonarSummary.qualityGateSummary}

//     --- Trivy Top Issues ---
//     ${trivySummary}

//     --- Snyk Top Issues ---
//     ${snykSummary}

//     --- SonarQube Issues ---
//     Code Smells:
//     ${sonarCodeSmellsSummary}

//     Vulnerabilities:
//     ${sonarVulnerabilitiesSummary}
//     """

//     def gptPromptFile = "openai_prompt.json"
//     def gptOutputFile = "openai_response.json"
//     def gptReportFile = "ai_report.html"

//     def payload = [
//         model: "gpt-4o-mini",
//         messages: [[role: "user", content: prompt]]
//     ]

//     writeFile file: gptPromptFile, text: groovy.json.JsonOutput.toJson(payload)

//     withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_API_KEY')]) {
//         def responseJson = sh(script: """
//             curl -s https://api.openai.com/v1/chat/completions \\
//             -H "Authorization: Bearer \$OPENAI_API_KEY" \\
//             -H "Content-Type: application/json" \\
//             -d @${gptPromptFile}
//         """, returnStdout: true).trim()

//         echo "Response from OpenAI API: ${responseJson}"

//         if (!responseJson) {
//             error("Received empty or invalid response from OpenAI API")
//         }

//         writeFile file: gptOutputFile, text: responseJson

//         try {
//             def response = readJSON text: responseJson
//             def gptContent = response?.choices?.get(0)?.message?.content

//             if (!gptContent) {
//                 error("GPT response is missing the expected content field")
//             }

//             gptContent = gptContent
//                 .replaceAll(/(?m)^```html\s*/, "")
//                 .replaceAll(/(?m)^```$/, "")
//                 .trim()

//             def (statusText, badgeColor, badgeClass) = parseStatusBadge(gptContent)

//             def htmlContent = """
//             <!DOCTYPE html>
//             <html>
//             <head>
//                 <meta charset="UTF-8">
//                 <title>Security Report - Build Summary</title>
//                 <style>
//                     body { font-family: Arial, sans-serif; background-color: #f9f9f9; margin: 40px; }
//                     h1, h2 { color: #2c3e50; }
//                     .section { margin-bottom: 25px; }
//                     ul { margin-top: 0; padding-left: 20px; }
//                     .highlight { background: #f9f9f9; padding: 10px; border-left: 5px solid #2c3e50; white-space: pre-wrap; word-wrap: break-word; }
//                     .badge-ok { color: green; font-weight: bold; }
//                     .badge-fail { color: red; font-weight: bold; }
//                     a { color: #2c3e50; text-decoration: underline; }
//                     footer { margin-top: 40px; font-size: 0.9em; color: #888; }
//                 </style>
//             </head>
//             <body>
//                 <img src="https://www.jenkins.io/images/logos/jenkins/jenkins.png" alt="Jenkins" height="70" />

//                 <div class="section">
//                     <h2>AI Recommendations - Security Scan Summary</h2>
//                     <div class="highlight">
//                         ${gptContent}
//                     </div>
//                 </div>

//                 <footer>
//                     <p>Generated by Jenkins | AI Security Summary | Build #${buildNumber}</p>
//                 </footer>
//             </body>
//             </html>
//             """
//             writeFile file: gptReportFile, text: htmlContent
//             echo "AI-powered GPT report generated: ${gptReportFile}"
//         } catch (Exception e) {
//             error("Failed to parse or process the GPT response: ${e.getMessage()}")
//         }
//     }
// }

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

    // def dtJsonPath = getDependencyTrackFindings()
    // def dtSummary = extractTopVulnsFromDt(dtJsonPath)

    def dtJsonPath = getDependencyTrackFindings()
    def findings = readJSON file: dtJsonPath
    def dtSummary = extractTopVulnsFromDt(findings.findings)
    
    def dtLower = dtSummary.toLowerCase()
    def dtStatus = (
        dtLower.contains("no high") &&
        dtLower.contains("no critical")
    ) ? "OK" : "Issues Found"

    if (!dtSummary?.trim()) {
        dtSummary = "No high or critical vulnerabilities found by Dependency-Track."
        dtStatus = "OK"
    }

    echo "Dependency-Track Summary:\n${dtSummary}"

    def prompt = """
    You are a security analyst assistant.

    Generate a clean HTML security report based on the following scan data. Use only <h2>, <ul>, <p>, and <strong> tags. Avoid Markdown or code blocks.

    Include these sections:
    - Project Overview (project name, SHA, build number)
    - Vulnerabilities Summary (grouped by severity: Critical, High, Medium)
    - **Dependency-Track Findings (MUST be shown if present)**
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
    - Dependency-Track: ${dtStatus}

    --- Trivy Top Issues ---
    ${trivySummary}

    --- Snyk Top Issues ---
    ${snykSummary}

    --- Dependency-Track Top Issues ---
    ${dtSummary}

    """

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

        if (!responseJson) {
            error("Received empty or invalid response from OpenAI API")
        }

        writeFile file: gptOutputFile, text: responseJson

        try {
            def response = readJSON text: responseJson
            def gptContent = response?.choices?.get(0)?.message?.content

            if (!gptContent) {
                error("GPT response is missing the expected content field")
            }

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

                <div class="section">
                    <h2>AI Recommendations - Security Scan Summary</h2>
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
    def sonarHost = "http://13.234.37.254:9000"
    def sonarToken = env.SONAR_TOKEN
    def apiQualityGateUrl = "${sonarHost}/api/qualitygates/project_status?projectKey=${projectKey}"
    def apiIssuesUrl = "${sonarHost}/api/issues/search?componentKeys=${projectKey}&types=CODE_SMELL,VULNERABILITY&ps=100"

    def qualityGateJson = null
    def issuesJson = null

    try {
        def qualityGateResponse = sh(
            script: "curl -sf -u ${sonarToken}: ${apiQualityGateUrl}",
            returnStdout: true
        ).trim()

        if (!qualityGateResponse) {
            echo "Empty response from SonarQube Quality Gate API: ${apiQualityGateUrl}"
            return getSonarFallbackResult()
        }

        qualityGateJson = readJSON text: qualityGateResponse

    } catch (Exception e) {
        echo "Error fetching or parsing SonarQube Quality Gate response: ${e.message}"
        return getSonarFallbackResult()
    }

    try {
        def issuesResponse = sh(
            script: "curl -sf -u ${sonarToken}: ${apiIssuesUrl}",
            returnStdout: true
        ).trim()

        if (!issuesResponse) {
            echo "Empty response from SonarQube Issues API: ${apiIssuesUrl}"
            return getSonarFallbackResult()
        }

        issuesJson = readJSON text: issuesResponse

    } catch (Exception e) {
        echo "Error fetching or parsing SonarQube Issues response: ${e.message}"
        return getSonarFallbackResult()
    }

    def issues = issuesJson.issues ?: []

    def codeSmells = issues.findAll { it.type == 'CODE_SMELL' }.collect {
        [
            severity: it.severity,
            message: it.message
        ]
    }

    def vulnerabilities = issues.findAll { it.type == 'VULNERABILITY' }.collect {
        [
            severity: it.severity,
            message: it.message
        ]
    }

    def sonarCodeSmellsSummary = codeSmells.collect {
        "Severity: ${it.severity}, Message: ${it.message}"
    }.join("\n")

    def sonarVulnerabilitiesSummary = vulnerabilities.collect {
        "Severity: ${it.severity}, Message: ${it.message}"
    }.join("\n")

    def qualityGateStatus = qualityGateJson?.projectStatus?.status ?: "UNKNOWN"
    def qualityGateSummary = "Quality Gate Status: ${qualityGateStatus}"

    return [
        codeSmells: codeSmells,
        vulnerabilities: vulnerabilities,
        qualityGateSummary: qualityGateSummary,
        qualityGateStatus: qualityGateStatus,
        sonarCodeSmellsSummary: sonarCodeSmellsSummary,
        sonarVulnerabilitiesSummary: sonarVulnerabilitiesSummary
    ]
}

// Fallback result if API fails
def getSonarFallbackResult() {
    return [
        codeSmells: [],
        vulnerabilities: [],
        qualityGateSummary: "SonarQube analysis failed or returned no data.",
        qualityGateStatus: "ERROR",
        sonarCodeSmellsSummary: "No code smells data available.",
        sonarVulnerabilitiesSummary: "No vulnerability data available."
    ]
}

def getDependencyTrackFindings() {
    def projectName = params.ECR_REPO_NAME
    def projectVersion = env.COMMIT_SHA
    def dtrackHost = 'http://13.201.191.212:8081'
    def dtrackToken = env.DEP_TRACK_API_KEY

    def encodedName = URLEncoder.encode(projectName, "UTF-8")
    def encodedVersion = URLEncoder.encode(projectVersion, "UTF-8")

    def projectUuid = ''
    def findingsJson = ''

    script {
        echo "🔍 Looking up Dependency-Track project: ${projectName}:${projectVersion}"

        withEnv(["DTRACK_TOKEN=${dtrackToken}"]) {
            def lookupResponse = sh(
                script: """
                    curl -s -H "X-Api-Key: \$DTRACK_TOKEN" \
                    "${dtrackHost}/api/v1/project/lookup?name=${encodedName}&version=${encodedVersion}"
                """,
                returnStdout: true
            ).trim()

            if (!lookupResponse || lookupResponse.contains("Not Found") || lookupResponse.startsWith("<html")) {
                error "❌ Project not found in Dependency-Track for ${projectName}:${projectVersion}"
            }

            def projectInfo = new JsonSlurper().parseText(lookupResponse)
            projectUuid = projectInfo.uuid

            if (!projectUuid) {
                error "❌ UUID not found in Dependency-Track response for project ${projectName}"
            }

            echo "✅ Found project UUID: ${projectUuid}"
        }
    }

    script {
        echo "📥 Fetching findings for project UUID: ${projectUuid}"

        withEnv(["DTRACK_TOKEN=${dtrackToken}"]) {
            findingsJson = sh(
                script: """
                    curl -s -H "X-Api-Key: \$DTRACK_TOKEN" \
                    "${dtrackHost}/api/v1/finding/project/${projectUuid}"
                """,
                returnStdout: true
            ).trim()

            if (!findingsJson || !findingsJson.startsWith("[")) {
                error "❌ Invalid findings JSON from Dependency-Track: ${findingsJson.take(200)}"
            }

            writeFile file: 'dependency-track-findings.json', text: findingsJson
            echo "✅ Findings written to dependency-track-findings.json"
        }
    }

    return 'dependency-track-findings.json'
}


// def getDependencyTrackFindings() {
//     def projectName = "${params.ECR_REPO_NAME}"
//     def projectVersion = "${env.COMMIT_SHA}"
//     def dtrackHost = 'http://13.201.191.212:8081'
//     def dtrackToken = "${env.DEP_TRACK_API_KEY}"

//     def encodedName = URLEncoder.encode(projectName, "UTF-8")
//     def encodedVersion = URLEncoder.encode(projectVersion, "UTF-8")

//     def projectUuid = ''
//     def findingsJson = ''

//     script {
//         // 🔹 Step 1: Get project by name and version
//         def projectJson = sh(
//             script: """
//                 curl -s -H "X-Api-Key: ${dtrackToken}" \
//                 "${dtrackHost}/api/v1/project/lookup?name=${encodedName}&version=${encodedVersion}"
//             """,
//             returnStdout: true
//         ).trim()

//         if (!projectJson || projectJson.contains("Not Found") || projectJson.startsWith("<html")) {
//             error "❌ Project not found in Dependency-Track for ${projectName}:${projectVersion}"
//         }

//         def projectInfo = new JsonSlurper().parseText(projectJson)
//         projectUuid = projectInfo.uuid

//         echo "✅ Found project UUID: ${projectUuid}"
//     }

//     script {
//         // 🔹 Step 2: Get findings for project UUID
//         findingsJson = sh(
//             script: """
//                 curl -s -H "X-Api-Key: ${dtrackToken}" \
//                 "${dtrackHost}/api/v1/finding/project/${projectUuid}"
//             """,
//             returnStdout: true
//         ).trim()

//         if (!findingsJson.startsWith("[")) {
//             error "❌ Invalid findings JSON: ${findingsJson.take(200)}"
//         }

//         writeFile file: 'dependency-track-findings.json', text: findingsJson
//         echo "✅ Findings written to dependency-track-findings.json"
//     }

//     return 'dependency-track-findings.json'
// }

// def getDependencyTrackFindings() {
//     def projectName = "${params.ECR_REPO_NAME}"
//     def projectVersion = "${env.COMMIT_SHA}"
//     def dtrackHost = 'http://13.201.191.212:8081'
//     def dtrackToken = "${env.DEP_TRACK_API_KEY}"

//     def encodedProjectName = URLEncoder.encode(projectName, "UTF-8")
//     def encodedVersion = URLEncoder.encode(projectVersion, "UTF-8")

//     def projectUuid = ''
//     def findingsJson = ''

//     script {
//         def projectUuidJson = ''

//         withEnv(["DTRACK_TOKEN=${dtrackToken}"]) {
//             projectUuidJson = sh(
//                 script: """
//                     curl -s -H "X-Api-Key: \$DTRACK_TOKEN" \
//                     "${dtrackHost}/api/v1/project?name=${encodedProjectName}"
//                 """,
//                 returnStdout: true
//             ).trim()
//         }

//         if (!projectUuidJson?.startsWith("[")) {
//             error "❌ Invalid response from Dependency-Track project lookup: ${projectUuidJson}"
//         }

//         def projectList = new groovy.json.JsonSlurper().parseText(projectUuidJson)
//         def matchedProject = projectList.find { it.version == projectVersion }

//         if (!matchedProject) {
//             error "❌ Dependency-Track project not found for ${projectName}:${projectVersion}"
//         }

//         projectUuid = matchedProject.uuid
//     }

//     script {
//         withEnv(["DTRACK_TOKEN=${dtrackToken}"]) {
//             findingsJson = sh(
//                 script: """
//                     curl -s -H "X-Api-Key: \$DTRACK_TOKEN" \
//                     "${dtrackHost}/api/v1/finding/project/${projectUuid}"
//                 """,
//                 returnStdout: true
//             ).trim()
//         }

//         if (!findingsJson?.startsWith("[")) {
//             error "❌ Invalid JSON from Dependency-Track findings: ${findingsJson.take(200)}"
//         }

//         writeFile file: 'dependency-track-findings.json', text: findingsJson
//     }

//     return 'dependency-track-findings.json'
// }

// def extractTopVulnsFromDt(String dtJsonPath) {
//     if (!fileExists(dtJsonPath)) {
//         return "No Dependency-Track data available."
//     }

//     def json = readJSON file: dtJsonPath
//     if (!json || json.isEmpty()) {
//         return "No high or critical vulnerabilities found by Dependency-Track."
//     }

//     def highSeverityFindings = json.findAll {
//         it.vulnerability?.severity in ['Critical', 'High']
//     }.take(5)

//     if (highSeverityFindings.isEmpty()) {
//         return "No high or critical vulnerabilities found by Dependency-Track."
//     }

//     def topFindings = generatePlainTextFromFindings(highSeverityFindings)
//     return topFindings.join("\n\n")
// }

def extractTopVulnsFromDt(List findings) {
    if (!(findings instanceof List) || findings.isEmpty()) {
        return "<h2>Dependency-Track Summary</h2><p>No vulnerabilities found by Dependency-Track.</p>"
    }

    // Defensive filtering
    def topFindings = findings.findAll { f ->
        try {
            f instanceof Map &&
            f?.severity instanceof String &&
            f.severity?.toUpperCase() in ["CRITICAL", "HIGH", "MEDIUM"]
        } catch (Exception ignored) {
            return false
        }
    }

    if (topFindings.isEmpty()) {
        return "<h2>Dependency-Track Summary</h2><p>No high, critical, or medium vulnerabilities found by Dependency-Track.</p>"
    }

    // Ensure we only group valid entries
    def grouped = topFindings.findAll { it?.severity }.groupBy { it.severity.toUpperCase() }
    def report = new StringBuilder("<h2>Dependency-Track Summary</h2>")

    ["CRITICAL", "HIGH", "MEDIUM"].each { severity ->
        def items = grouped[severity]
        if (items) {
            report.append("<h3>${severity} Issues (${items.size()}):</h3><ul>")

            items.take(5).each { f ->
                def title = (f?.title instanceof String) ? f.title : "No Title"
                def cwe = f?.cweId ? "CWE-${f.cweId}" : "No CWE"
                def score = (f?.cvssV3Score instanceof Number) ? String.format('%.1f', f.cvssV3Score) : "N/A"
                report.append("<li><strong>${title}</strong> — ${cwe}, CVSS: ${score}</li>")
            }

            if (items.size() > 5) {
                report.append("<li><em>...and ${items.size() - 5} more</em></li>")
            }

            report.append("</ul>")
        }
    }

    return report.toString()
}



def generatePlainTextFromFindings(List findings) {
    return findings.collect {
        def component = it.component?.name ?: "unknown"
        def vuln = it.vulnerability?.vulnId ?: "unknown"
        def severity = it.vulnerability?.severity ?: "unknown"
        def desc = it.vulnerability?.description ?: ""
        return "- ${vuln} in ${component} (Severity: ${severity})\n  ${desc}"
    }
}
