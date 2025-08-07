import groovy.json.JsonOutput

@Library('my-shared-library') _

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
        REGION = 'ap-south-1'
        GIT_URL = 'https://github.com/stackcouture/Java-WebAPP-CI.git'
        SLACK_CHANNEL = '#all-jenkins'
        SLACK_TOKEN = credentials('slack-token')
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {

        stage('Clean Workspace') {
            steps {
                cleanWorkspace()
            }
        }

        stage('Checkout Code') {
            steps {
                script {
                    checkoutGit(params.BRANCH, env.GIT_URL) 
                }
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
                 script {
                    buildDockerImage("${params.ECR_REPO_NAME}:${env.COMMIT_SHA}")
                    //sh "docker build -t ${params.ECR_REPO_NAME}:${env.COMMIT_SHA} ."
                 }
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
                script {
                    dockerPush("docker-push", "${env.COMMIT_SHA}", params.ECR_REPO_NAME, params.AWS_ACCOUNT_ID, "${env.REGION}")
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
            script {
                archiveArtifacts artifacts: "target/surefire-reports/*.xml", allowEmptyArchive: true

                if (fileExists('target/surefire-reports')) {
                    junit 'target/surefire-reports/*.xml'
                }

                publishHTML([
                        reportFiles: 'target/**.html',
                ])
            }
        }

        success {
            script {
                sendAiReportEmail(
                    branch: params.BRANCH,
                    commit: env.COMMIT_SHA ?: 'unknown',
                    to: 'naveenramlu@gmail.com'
                )
            }
            sendSlackNotification('SUCCESS', 'good')
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
