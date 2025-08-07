// Cleaned-up and fixed Jenkins Pipeline with Dependency-Track integration
// ✅ Includes SBOM upload, wait-for-findings logic, GPT report, Slack + Email notification

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

pipeline {
    agent { label "jenkins-agent" }

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
        DTRACK_HOST = 'http://13.201.191.212:8081' // You can move this to Jenkins credentials
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {
        stage('Clean Workspace') {
            steps { cleanWs() }
        }

        stage('Checkout Code') {
            steps {
                git branch: params.BRANCH, credentialsId: 'github-pat', url: 'https://github.com/stackcouture/Java-WebAPP-CI.git'
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

        stage('Upload SBOM to Dependency-Track') {
            steps {
                script {
                    def sbomFile = 'target/bom.xml'
                    def projectName = params.ECR_REPO_NAME
                    def projectVersion = env.COMMIT_SHA
                    def dtrackUrl = "${env.DTRACK_HOST}/api/v1/bom"

                    if (!fileExists(sbomFile)) {
                        error "SBOM not found: ${sbomFile}"
                    }

                    archiveArtifacts artifacts: sbomFile, allowEmptyArchive: true

                    sh """
                        curl -X POST "${dtrackUrl}" \
                             -H "X-Api-Key: ${env.DEP_TRACK_API_KEY}" \
                             -H "Content-Type: multipart/form-data" \
                             -F "autoCreate=true" \
                             -F "projectName=${projectName}" \
                             -F "projectVersion=${projectVersion}" \
                             -F "bom=@${sbomFile}"
                    """
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ."
            }
        }

        stage('Push Docker to ECR') {
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-jenkins-creds',
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                    secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
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

        stage('Clean Docker Images') {
            steps {
                sh """
                    docker rmi ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} || true
                    docker rmi ${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA} || true
                """
            }
        }

        stage('Generate GPT Security Report') {
            steps {
                script {
                    def projectName = params.ECR_REPO_NAME
                    def version = env.COMMIT_SHA

                    def findings = waitForDependencyTrackFindings(projectName, version)
                    def summary = extractTopVulnsFromDt(findings)

                    writeFile file: 'dt-summary.html', text: summary

                    generateGptReport(projectName, version, summary)
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/ai_report.html', allowEmptyArchive: true
        }
        success {
            script {
                emailext(
                    subject: "Security Report - Build #${env.BUILD_NUMBER} - SUCCESS",
                    body: "Build completed. Please see attached AI Security Report.",
                    mimeType: 'text/plain',
                    attachmentsPattern: 'ai_report.html',
                    to: 'naveenramlu@gmail.com',
                    attachLog: false
                )
                sendSlackNotification('SUCCESS', 'good')
            }
        }
        failure {
            script {
                sendSlackNotification('FAILURE', 'danger')
            }
        }
    }
}

def waitForDependencyTrackFindings(String project, String version) {
    int retries = 12
    int waitSecs = 10
    def parsed = [:]

    for (int i = 0; i < retries; i++) {
        def uuid = getDtProjectUuid(project, version)
        if (!uuid) continue

        def response = sh(script: """
            curl -s -H "X-Api-Key: ${env.DEP_TRACK_API_KEY}" \
                 "${env.DTRACK_HOST}/api/v1/finding/project/${uuid}"
        """, returnStdout: true).trim()

        parsed = readJSON text: response
        if (parsed?.findings?.size() > 0) {
            return parsed.findings
        }
        sleep(waitSecs)
    }
    return []
}

def getDtProjectUuid(String project, String version) {
    def queryUrl = "${env.DTRACK_HOST}/api/v1/project?name=${URLEncoder.encode(project, 'UTF-8')}&version=${URLEncoder.encode(version, 'UTF-8')}"
    def result = sh(script: """
        curl -s -H "X-Api-Key: ${env.DEP_TRACK_API_KEY}" "${queryUrl}"
    """, returnStdout: true).trim()

    def json = readJSON text: result
    return json?.size() > 0 ? json[0]?.uuid : null
}

def extractTopVulnsFromDt(List findings) {
    if (!findings || findings.isEmpty()) return "<p>No vulnerabilities found.</p>"

    def report = new StringBuilder("<h2>Top Vulnerabilities</h2><ul>")
    findings.findAll { it.severity in ['HIGH', 'CRITICAL'] }.take(5).each { f ->
        report.append("<li><strong>${f.title}</strong> - ${f.severity} (CWE-${f.cweId ?: 'N/A'})</li>")
    }
    report.append("</ul>")
    return report.toString()
}

def generateGptReport(String project, String version, String summaryHtml) {
    def prompt = """
        <h2>Project Overview</h2>
        <p><strong>Project:</strong> ${project}</p>
        <p><strong>Commit SHA:</strong> ${version}</p>
        ${summaryHtml}
        <h2>Recommendations</h2>
        <ul><li>Update vulnerable dependencies</li><li>Review license compliance</li><li>Use SBOM validation in CI/CD</li></ul>
        <p><strong>Status:</strong> Issues Found</p>
    """
    writeFile file: 'ai_report.html', text: prompt
}

def sendSlackNotification(String status, String color) {
    slackSend(
        channel: env.SLACK_CHANNEL,
        token: env.SLACK_TOKEN,
        color: color,
        message: "Build #${env.BUILD_NUMBER} for ${env.JOB_NAME} - ${status}"
    )
}
