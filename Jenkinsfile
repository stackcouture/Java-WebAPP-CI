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

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ."
            }
        }

        stage('Security Scans Before Push') {
            parallel {
                stage('Trivy Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runTrivyScanUnified("before-push", localTag, "image")
                        }
                    }
                }
                stage('Snyk Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runSnykScan("before-push", localTag)
                        }
                    }
                }
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
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                            runSnykScan("after-push", pushedTag)
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
                    def projectName = "My Java App" // Or pull dynamically from env or Jenkins param
                    def gitSha = env.COMMIT_SHA
                    def buildNumber = env.BUILD_NUMBER

                    def trivyPath = "reports/trivy/${buildNumber}/after-push/trivy-image-scan-${gitSha}.html"
                    def snykPath = "reports/snyk/${buildNumber}/after-push/snyk-report-${gitSha}.json"

                    def trivyScanOutput = readFile(trivyPath)
                    def snykScanOutput = readFile(snykPath)

                    def trivyShort = trivyScanOutput.take(2000)
                    def snykShort = snykScanOutput.take(2000)

                    // Determine status badge logic from Snyk or Trivy scan
                    def badgeColor = "✅"
                    if (trivyScanOutput.toLowerCase().contains("critical") || 
                        trivyScanOutput.toLowerCase().contains("high") || 
                        snykScanOutput.toLowerCase().contains("critical") || 
                        snykScanOutput.toLowerCase().contains("high")) {
                        badgeColor = "❌"
                    }

                    def prompt = """
                        Summarize the following security scan results and highlight risks and suggestions:

                        Project: ${projectName}
                        Commit SHA: ${gitSha}
                        Build Number: ${buildNumber}

                        Trivy Scan:
                        ${trivyShort}

                        Snyk Scan:
                        ${snykShort}
                    """

                    def promptFile = "openai_prompt.json"
                    def fullResponseFile = "openai_response.json"
                    def gptReportFile = "ai_report.html"

                    def payload = [
                        model: "gpt-4o-mini",
                        messages: [
                            [role: "user", content: prompt]
                        ]
                    ]

                    writeFile file: promptFile, text: groovy.json.JsonOutput.toJson(payload)

                    withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_API_KEY')]) {
                        def apiResponse = sh(script: """
                            curl -s https://api.openai.com/v1/chat/completions \\
                            -H "Authorization: Bearer \$OPENAI_API_KEY" \\
                            -H "Content-Type: application/json" \\
                            -d @${promptFile}
                        """, returnStdout: true).trim()

                        writeFile file: fullResponseFile, text: apiResponse
                        echo "OpenAI API Response: ${apiResponse}"

                        def response = readJSON text: apiResponse
                        if (response?.choices?.size() > 0) {
                            def gptContent = response.choices[0].message.content
                            if (!gptContent) {
                                error "GPT response does not contain content."
                            } else {
                                echo "GPT response received."

                                def htmlContent = """
                                    <html>
                                    <head>
                                        <title>Security Report - AI Summary</title>
                                        <style>
                                            body { font-family: Arial, sans-serif; padding: 20px; line-height: 1.6; }
                                            h1, h2 { color: #2c3e50; }
                                            .badge { font-size: 18px; margin-top: 10px; }
                                            pre { background: #f4f4f4; padding: 10px; border-left: 5px solid #ccc; }
                                            .meta { font-size: 14px; color: #555; margin-bottom: 10px; }
                                        </style>
                                    </head>
                                    <body>
                                        <img src="https://www.jenkins.io/images/logos/jenkins/jenkins.png" height="80" alt="Jenkins Logo"/>
                                        <h1>Security Scan Report Summary (AI-Generated)</h1>
                                        <div class="meta">
                                            <strong>Project:</strong> ${projectName}<br>
                                            <strong>Build Number:</strong> ${buildNumber}<br>
                                            <strong>Commit SHA:</strong> ${gitSha}<br>
                                            <strong>Status:</strong> <span class="badge">${badgeColor}</span>
                                        </div>

                                        <h2>Table of Contents</h2>
                                        <ul>
                                            <li><a href="#summary">1. Summary</a></li>
                                            <li><a href="#details">2. Detailed Analysis</a></li>
                                        </ul>

                                        <h2 id="summary">1. Summary</h2>
                                        <p>Scan results summarized below based on Trivy and Snyk data.</p>

                                        <h2 id="details">2. Detailed Analysis</h2>
                                        <pre>${gptContent.replaceAll("<", "&lt;").replaceAll(">", "&gt;")}</pre>
                                    </body>
                                    </html>
                                """

                                writeFile file: gptReportFile, text: htmlContent
                            }
                        } else {
                            error "GPT response missing choices or content."
                        }
                    }

                    echo "HTML GPT-based report generated: ${gptReportFile}"
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

                if (fileExists("ai_report.html")) {
                    def htmlContent = readFile("ai_report.html")

                    emailext(
                        subject: "Security Report - Build #${env.BUILD_NUMBER}",
                          body: """
                                <p>Hello Team,</p>
                                <p>Please find the attached AI-generated <b>Security Report</b> for build #${env.BUILD_NUMBER}.</p>
                                <p>Key highlights:</p>
                                <ul>
                                    <li>Project: <b>my-java-app</b></li>
                                    <li>Git SHA: <b>${env.COMMIT_SHA}</b></li>
                                </ul>
                                <p>For full details, open the <b>ai_report.html</b> attachment.</p>
                                <hr/>
                                <small>This is an automated message from Jenkins CI.</small>
                            """,
                        mimeType: 'text/html',
                        attachmentsPattern: 'ai_report.html',
                        to: 'naveenramlu@gmail.com',
                        attachLog: false
                    )
                } else {
                    echo "ai_report.html not found"
                }
            }
        }

        failure {
            mail to: 'naveenramlu@gmail.com',
                subject: "Build #${env.BUILD_NUMBER} Failed",
                body: "Build ${env.BUILD_NUMBER} for commit ${env.COMMIT_SHA} failed. Please check: ${env.BUILD_URL}"
        }
    }
}

def runSnykScan(stageName, imageTag) {
    def reportDir = "reports/snyk/${env.BUILD_NUMBER}/${stageName}"
    def jsonFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.json"
    def htmlFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.html"

    withCredentials([string(credentialsId: 'SNYK_TOKEN', variable: 'SNYK_TOKEN')]) {
    // withEnv(["SNYK_TOKEN=${env.SNYK_TOKEN}"]) {
        sh """
            mkdir -p ${reportDir}

            snyk auth $SNYK_TOKEN
            snyk container test ${imageTag} --severity-threshold=high --json > ${jsonFile} || true

            echo "<html><body><pre>" > ${htmlFile}
            cat ${jsonFile} | jq . >> ${htmlFile}
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
    def reportFile = scanType == 'fs' 
        ? "${reportDir}/trivy-fs-scan-${env.COMMIT_SHA}.html"
        : "${reportDir}/trivy-image-scan-${env.COMMIT_SHA}.html"

    sh """
        mkdir -p ${reportDir}
        trivy ${scanType} --format template --template "@contrib/html.tpl" -o ${reportFile} ${scanTarget}
    """

    publishHTML(target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: "*.html",
        reportName: "Trivy ${scanType == 'fs' ? 'File System' : 'Image'} Scan (${stageName}) - Build ${env.BUILD_NUMBER}"
    ])
    
    archiveArtifacts artifacts: "${reportDir}/*.html", allowEmptyArchive: true
}

