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
                        echo "✅ SBOM archived: ${sbomFile}"
                    }
                    else {
                        error "❌ SBOM not found: ${sbomFile}"
                    }
                }
            }
        }

        stage('Upload SBOM to Dependency-Track') {
            steps {
                withCredentials([string(credentialsId: 'dependency-track-api-key', variable: 'DT_API_KEY')]) {
                    script {
                        def sbomFile = 'target/bom.xml'
                        if (!fileExists(sbomFile)) {
                            error "❌ SBOM file not found: ${sbomFile}"
                        }

                        def projectName = "${params.ECR_REPO_NAME}"
                        def projectVersion = "${env.COMMIT_SHA}"
                        def dependencyTrackUrl = 'http://13.233.157.56:8081/api/v1/bom'

                        echo "🔐 Uploading SBOM for ${projectName}:${projectVersion}"

                        withEnv([
                            "DEPTRACK_URL=${dependencyTrackUrl}",
                            "PROJECT_NAME=${projectName}",
                            "PROJECT_VERSION=${projectVersion}"
                        ]) {
                            sh '''#!/bin/bash
                                curl -X POST "$DEPTRACK_URL" \
                                    -H "X-Api-Key: $DT_API_KEY" \
                                    -H "Content-Type: multipart/form-data" \
                                    -F "autoCreate=true" \
                                    -F "projectName=$PROJECT_NAME" \
                                    -F "projectVersion=$PROJECT_VERSION" \
                                    -F "bom=@target/bom.xml"
                            '''
                        }
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

        stage('AI-Powered GPT Report') {
            steps {
                withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_API_KEY')]) {
                    script {
                        def reportDir = "reports/ai/${env.BUILD_NUMBER}"
                        def gptReport = "${reportDir}/gpt-security-summary-${env.COMMIT_SHA}.html"
                        def snykJson = findFiles(glob: "reports/snyk/${env.BUILD_NUMBER}/**/snyk-report-${env.COMMIT_SHA}.json")[0].path
                        def trivyJson = findFiles(glob: "reports/trivy/${env.BUILD_NUMBER}/**/trivy-image-scan-${env.COMMIT_SHA}.json")[0].path

                        sh """
                            mkdir -p ${reportDir}
                            python3 scripts/generate_gpt_report.py ${trivyJson} ${snykJson} ${gptReport}
                        """

                        publishHTML(target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: reportDir,
                            reportFiles: "gpt-security-summary-${env.COMMIT_SHA}.html",
                            reportName: "AI-Powered GPT Security Summary"
                        ])
                        
                        // Save path for email stage
                        env.GPT_REPORT_PATH = gptReport
                    }
                }
            }
        }


        stage('Update YAML File - FINAL') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-pat', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                    script {
                        def imageTag = env.COMMIT_SHA
                        def branch = params.BRANCH
                        def repoDir = 'Java-WebAPP-CD'

                        sh "rm -rf ${repoDir}"
                        sh "git clone https://${GIT_USER}:${GIT_TOKEN}@github.com/stackcouture/Java-WebAPP-CD.git ${repoDir}"

                        dir(repoDir) {
                            sh "git checkout ${branch}"

                            if (!fileExists("java-app-chart/values.yaml")) {
                                error("File java-app-chart/values.yaml not found in branch ${branch}")
                            }

                            def tagExists = sh(script: "grep -q '^\\s*tag:' java-app-chart/values.yaml && echo found || echo notfound", returnStdout: true).trim()
                            if (tagExists != 'found') {
                                error("Could not find 'tag:' field in values.yaml — aborting.")
                            }

                            sh """
                                sed -i -E "s|(^\\s*tag:\\s*\\\").*(\\\")|\\1${imageTag}\\2|" java-app-chart/values.yaml
                            """

                            sh 'git config user.email "naveenramlu@gmail.com"'
                            sh 'git config user.name "Naveen"'
                            sh 'git add java-app-chart/values.yaml'

                            def changes = sh(script: 'git diff --cached --quiet || echo "changed"', returnStdout: true).trim()
                            if (changes == "changed") {
                                echo "Changes detected — committing and pushing."
                                sh "git commit -m 'chore: update image tag to ${imageTag}'"
                                sh "git push origin ${branch}"
                            } else {
                                echo "No changes detected — skipping commit."
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
    }

    post {
        always {
            archiveArtifacts artifacts: '**/fs.html, **/trivy-image-scan-*.html, **/snyk-report-*.json', allowEmptyArchive: true
            script {
                if (fileExists('target/surefire-reports')) {
                    junit 'target/surefire-reports/*.xml'
                } else {
                    echo "ℹNo test results found."
                }

                  // ✅ Proper place for script block in post section
                if (env.GPT_REPORT_PATH && fileExists(env.GPT_REPORT_PATH)) {
                    emailext(
                        subject: "🛡️ AI Security Summary - Build ${env.BUILD_NUMBER}",
                        body: "Attached is the GPT-generated security summary for build #${env.BUILD_NUMBER}.",
                        to: "naveenramlu@gmail.com",
                        attachmentsPattern: "${env.GPT_REPORT_PATH}"
                    )
                } else {
                    echo "GPT Report not found — skipping email attachment."
                }
            }
        }
        // success {
        // mail to: 'naveenramlu@gmail.com',
        //      subject: "Build #${env.BUILD_NUMBER} Succeeded",
        //      body: "Build ${env.BUILD_NUMBER} for commit ${env.COMMIT_SHA} succeeded. View in Jenkins: ${env.BUILD_URL}"
        // }

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

    withEnv(["SNYK_TOKEN=${env.SNYK_TOKEN}"]) {
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

