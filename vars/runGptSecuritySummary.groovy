def call(Map config = [:]) {
    def projectKey = config.projectKey ?: error("Missing 'projectKey'")
    def gitSha = config.gitSha ?: error("Missing 'gitSha'")
    def buildNumber = config.buildNumber ?: error("Missing 'buildNumber'")
    def trivyHtmlPath = config.trivyHtmlPath ?: error("Missing 'trivyHtmlPath'")
    def snykJsonPath = config.snykJsonPath ?: error("Missing 'snykJsonPath'")
    def sonarHost = config.sonarHost ?: error("Missing 'sonarHost'")
    def secretName = config.secretName ?: error("Missing 'secretName'")

    def secrets = getAwsSecret(secretName, 'ap-south-1')
    def openai_api_key = secrets.openai_api_key

    if (!openai_api_key) {
        error("OpenAI API key is missing from secrets. Please check the secret: ${secretName}")
    }

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

    if (!snykSummary?.trim() || snykSummary.contains("JSON file not found or is empty")) {
        snykSummary = "No high, critical or medium vulnerabilities found by Snyk."
        snykStatus = "OK"
    }

    def sonarSummary = getSonarQubeSummary(sonarHost, projectKey)
    def sonarCodeSmellsSummary = sonarSummary.sonarCodeSmellsSummary
    def sonarVulnerabilitiesSummary = sonarSummary.sonarVulnerabilitiesSummary

    def prompt = """
    You are a security analyst assistant.

    Generate a clean HTML security report based on the following scan data. Use only <h2>, <ul>, <p>, and <strong> tags. Avoid Markdown or code blocks.

    Include these sections:
    - Project Overview (project name, SHA, build number)
    - Vulnerabilities Summary (grouped by severity: Critical, High, Medium)
    - Code Smells Summary
    - Recommendations (2–4 practical points as an unordered list)

    Context:
    Project: ${projectKey}
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

    def gptPromptFile = "openai_prompt.json"
    def gptOutputFile = "openai_response.json"
    def gptReportFile = "ai_report.html"

    def payload = [
        model: "gpt-3.5-turbo",
        messages: [[role: "user", content: prompt]],
        temperature: 0.7
    ]

    writeFile file: gptPromptFile, text: groovy.json.JsonOutput.toJson(payload)

    def responseJson = ''
    withEnv(["OPENAI_API_KEY=${openai_api_key}"]) {
        responseJson = sh(script: """
            curl -s https://api.openai.com/v1/chat/completions \\
                -H "Authorization: Bearer \$OPENAI_API_KEY" \\
                -H "Content-Type: application/json" \\
                -d @${gptPromptFile}
        """, returnStdout: true).trim()
    }

    if (!responseJson || responseJson.contains('"error"')) {
        echo "Raw response from OpenAI:\n${responseJson}"
        error("Received error or empty response from OpenAI API")
    }

    writeFile file: gptOutputFile, text: responseJson

    try {
        def response = readJSON text: responseJson
        def gptContent = response?.choices?.get(0)?.message?.content

        if (!gptContent?.trim()) {
            echo "Raw GPT response: ${responseJson}"
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
                body {
                    font-family: Arial, sans-serif;
                    background-color: #fff;
                    margin: 20px;
                    line-height: 1.4;
                    font-size: 14px;
                }

                h1, h2 {
                    color: #2c3e50;
                    margin-bottom: 10px;
                }

                .section {
                    margin-bottom: 20px;
                }

                ul {
                    margin: 0;
                    padding-left: 18px;
                }

                .highlight {
                    background: #f5f5f5;
                    padding: 12px;
                    border-left: 4px solid #2c3e50;
                    white-space: pre-wrap;
                    word-wrap: break-word;
                    font-family: monospace;
                    font-size: 13px;
                }

                .badge-ok {
                    color: green;
                    font-weight: bold;
                }

                .badge-fail {
                    color: red;
                    font-weight: bold;
                }

                a {
                    color: #2c3e50;
                    text-decoration: underline;
                }

                footer {
                    margin-top: 30px;
                    font-size: 12px;
                    color: #888;
                }

                img {
                    max-height: 60px;
                    margin-bottom: 20px;
                }
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

        archiveArtifacts artifacts: gptReportFile, fingerprint: true
    } catch (Exception e) {
        error("Failed to parse or process the GPT response: ${e.getMessage()}")
    }
    
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

def getSonarQubeSummary(String sonarHost, String projectKey) {
    // def projectKey = "Java-App"
    // def sonarHost = sonarHost
    def apiQualityGateUrl = "${sonarHost}/api/qualitygates/project_status?projectKey=${projectKey}"
    def apiIssuesUrl = "${sonarHost}/api/issues/search?componentKeys=${projectKey}&types=CODE_SMELL,VULNERABILITY&ps=100"

    def qualityGateJson = null
    def issuesJson = null

    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
        try {
            def qualityGateResponse = sh(
                script: "curl -sf -u \$SONAR_TOKEN: ${apiQualityGateUrl}",
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
                script: "curl -sf -u \$SONAR_TOKEN: ${apiIssuesUrl}",
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
    }

    def issues = issuesJson.issues ?: []

    def codeSmells = issues.findAll { it.type == 'CODE_SMELL' }.collect {
        [severity: it.severity, message: it.message]
    }

    def vulnerabilities = issues.findAll { it.type == 'VULNERABILITY' }.collect {
        [severity: it.severity, message: it.message]
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