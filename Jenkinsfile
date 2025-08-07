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

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ."
            }
        }

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
                        runGptSecuritySummary(
                            "my-app", 
                            env.COMMIT_SHA, 
                            env.BUILD_NUMBER
                        )
                }   
            } 
        } 
    }

    post {
        always {
            archiveArtifacts artifacts: '**/fs.html', allowEmptyArchive: true
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

def runGptSecuritySummary(String projectName, String gitSha, String buildNumber) {

    // def dtJsonPath = getDependencyTrackFindings()
    // def findings = readJSON file: dtJsonPath
    // def dtSummary = extractTopVulnsFromDt(findings.findings)

    def findingsJson = getDependencyTrackFindings()

    if (!findingsJson || !findingsJson.findings) {
        echo "⚠️ No findings retrieved from Dependency-Track"
        findingsJson = [findings: []]
    }

    def dtSummary = extractTopVulnsFromDt(findingsJson.findings)
    
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
    - **Dependency-Track Findings (MUST be shown if present)*
    - License Issues (e.g., GPL, AGPL, LGPL)
    - Recommendations (2–4 practical points)
    - One line with: <p><strong>Status:</strong> OK</p> or <p><strong>Status:</strong> Issues Found</p>

    Context:
    Project: ${projectName}
    Commit SHA: ${gitSha}
    Build Number: ${buildNumber}

    Scan Status Summary:
    - Dependency-Track: ${dtStatus}

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

def parseStatusBadge(String gptContent) {
    echo "Raw GPT content for badge parsing:\n${gptContent}"
    def matcher = gptContent =~ /(?i)<strong>Status:<\/strong>\s*(OK|Issues Found)/
    def statusText = matcher.find() ? matcher.group(1).toUpperCase() : "ISSUES FOUND"
    def badgeColor = statusText == "OK" ? "✅" : "❌"
    def badgeClass = statusText == "OK" ? "badge-ok" : "badge-fail"
    return [statusText, badgeColor, badgeClass]
}

def getDependencyTrackFindings() {
    def projectName = params.ECR_REPO_NAME
    def projectVersion = env.COMMIT_SHA
    def dtrackHost = 'http://13.201.191.212:8081'
    def dtrackToken = env.DEP_TRACK_API_KEY

    echo "Getting Dependency-Track findings for ${projectName}:${projectVersion}"

    // Step 1: Get project UUID
    def projectUrl = "${dtrackHost}/api/v1/project?name=${projectName}&version=${projectVersion}"
    def projectJson = sh(
        script: """
            curl -s -X GET "${projectUrl}" \\
            -H "X-Api-Key: ${dtrackToken}" \\
            -H "Content-Type: application/json"
        """,
        returnStdout: true
    ).trim()

    def projectList = readJSON text: projectJson

    if (projectList.size() == 0) {
        error "No Dependency-Track project found for ${projectName}:${projectVersion}"
    }

    def projectUuid = projectList[0].uuid
    echo "Found project UUID: ${projectUuid}"

    // Step 2: Get findings
    def findingsUrl = "${dtrackHost}/api/v1/project/${projectUuid}/findings"
    def findingsJson = sh(
        script: """
            curl -s -X GET "${findingsUrl}" \\
            -H "X-Api-Key: ${dtrackToken}" \\
            -H "Content-Type: application/json"
        """,
        returnStdout: true
    ).trim()

    writeFile file: "dependency-track-findings.json", text: findingsJson
    echo "Dependency-Track findings saved to dependency-track-findings.json"

    return findingsJson
}


def extractTopVulnsFromDt(List findings) {
    if (!(findings instanceof List) || findings.isEmpty()) {
        return "<h2>Dependency-Track Summary</h2><p>No vulnerabilities found by Dependency-Track.</p>"
    }

    // Only keep CRITICAL and HIGH severity findings
    def topFindings = findings.findAll { f ->
        try {
            f instanceof Map &&
            f?.severity instanceof String &&
            f.severity?.toUpperCase() in ["CRITICAL", "HIGH"]
        } catch (Exception ignored) {
            return false
        }
    }

    if (topFindings.isEmpty()) {
        return "<h2>Dependency-Track Summary</h2><p>No high or critical vulnerabilities found by Dependency-Track.</p>"
    }

    def grouped = topFindings.groupBy { it.severity.toUpperCase() }
    def report = new StringBuilder("<h2>Dependency-Track Summary</h2>")

    ["CRITICAL", "HIGH"].each { severity ->
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