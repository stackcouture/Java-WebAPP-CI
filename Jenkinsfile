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
        SLACK_CHANNEL = '#app-demo'
        DEPENDENCY_TRACK_URL = 'http://65.0.179.180:8081/api/v1/bom'
        SONAR_HOST = "http://35.154.183.6:9000"
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
                    checkoutGit(params.BRANCH, env.GIT_URL, 'my-app/secrets') 
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
                 }
            }
        }

        stage('Docker Push') {
            steps {
                script {
                    dockerPush(
                        imageTag: "${env.COMMIT_SHA}",
                        ecrRepoName: params.ECR_REPO_NAME,
                        awsAccountId: params.AWS_ACCOUNT_ID,
                        region: "${env.REGION}",
                        secretName: 'my-app/secrets'
                    )
                }
            }
        }

        stage('Update File - FINAL') {
            steps {
                script {
                    updateImageTag(
                        imageTag: env.COMMIT_SHA,
                        branch: params.BRANCH,
                        secretName: 'my-app/secrets'
                    )
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
            postBuildTestArtifacts('My Test Report', '**/surefire-report.html')
        }

        success {
            script {
                sendSlackNotification(
                    status: 'SUCCESS',
                    color: 'good',
                    secretName: 'my-app/secrets'
                )
            }
        }

        failure {
            script {
                sendSlackNotification(
                    status: 'FAILURE',
                    color: 'danger',
                    secretName: 'my-app/secrets'
                )
            }
        }

        unstable {
            script {
                sendSlackNotification(
                    status: 'UNSTABLE',
                    color: 'warning',
                    secretName: 'my-app/secrets'
                )
            }
        }

        aborted {
            script {
                sendSlackNotification(
                    status: 'ABORTED',
                    color: '#808080',
                    secretName: 'my-app/secrets'
                )
            }
        }

    }
}
