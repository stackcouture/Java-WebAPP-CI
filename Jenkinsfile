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
        SONAR_PROJECT_KEY = 'Java-App'
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {

        stage('Init & Checkout') {
            steps {
                cleanWorkspace()
                script {
                    checkoutGit(params.BRANCH, env.GIT_URL, 'my-app/secrets')
                    env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                }
            }
        }

        stage('Build + Test') {
            steps {
                sh 'mvn clean verify'
            }
        }

        stage('SBOM + FS Scan') {
            parallel {
                stage('Publish SBOM') {
                    steps {
                        script {
                            if (!fileExists('target/bom.xml')) {
                                error "SBOM file target/bom.xml not found!"
                            }
                            uploadSbomToDependencyTrack(
                                sbomFile: 'target/bom.xml',
                                projectName: "${params.ECR_REPO_NAME}",
                                projectVersion: "${env.COMMIT_SHA}",
                                dependencyTrackUrl: "${env.DEPENDENCY_TRACK_URL}",
                                secretName: 'my-app/secrets'
                            )
                        }
                    }
                }
                stage('Trivy FS Scan') {
                    steps {
                        sh "mkdir -p contrib && curl -sSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl"
                        runTrivyScanUnified("filesystem-scan",".", "fs")
                    }
                }
            }
        }

        stage('SonarQube Analysis & Gate') {
            steps {
                sonarScan(
                    projectKey: env.SONAR_PROJECT_KEY,
                    sources: 'src/main/java,src/test/java',
                    binaries: 'target/classes',
                    exclusions: '**/*.js',
                    scannerTool: 'sonar-scanner',
                    sonarEnv: 'sonar-server'
                )
                sonarQualityGateCheck(
                    projectKey: env.SONAR_PROJECT_KEY,
                    secretName: 'my-app/secrets',
                    timeoutMinutes: 5
                )
            }
        }

        stage('Build Docker Image') {
            steps {
                 script {
                    buildDockerImage("${params.ECR_REPO_NAME}:${env.COMMIT_SHA}")
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
                        runTrivyScanUnified("before-push", "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}", "image")
                    }
                }
                stage('Snyk Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        runSnykScan(
                            stageName: "before-push",
                            imageTag: "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}",
                            secretName: 'my-app/secrets'
                        )
                    }
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

        stage('Security Scans After Push') {
            parallel {
                stage('Trivy After Push') {
                     options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        runTrivyScanUnified("after-push",
                            "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}", "image")
                    }
                }
                stage('Snyk After Push') {
                     options {
                        timeout(time: 15, unit: 'MINUTES')
                    }
                    steps {
                        retry(2) {
                            runSnykScan(
                                stageName: "after-push",
                                imageTag: "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}",
                                secretName: 'my-app/secrets'
                            )
                        }
                    }
                }
            }
        }

        stage('Confirm YAML Update') {
            steps {
                script {
                    def confirm = input message: 'Update deployment YAML with new Docker tag?', parameters: [
                        choice(name: 'Confirmation', choices: ['Yes', 'No'], description: 'Proceed with update?')
                    ]
                    if (confirm == 'No') {
                        error 'Aborted by user.'
                    }
                }
            }
        }

        stage('Update Deployment Files') {
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

        stage('Deploy App') {
            steps {
                deployApp()
            }
        }

        stage('Generate GPT Security Report') {
            steps {
                script {

                    def trivyHtmlPath = "reports/trivy/${env.BUILD_NUMBER}/after-push/trivy-image-scan-${env.COMMIT_SHA}.html"
                    def snykJsonPath = "reports/snyk/${env.BUILD_NUMBER}/after-push/snyk-report-${env.COMMIT_SHA}.json"

                    if (fileExists(trivyHtmlPath) && fileExists(snykJsonPath)) {

                        runGptSecuritySummary(
                            projectKey: env.SONAR_PROJECT_KEY, 
                            gitSha: "${env.COMMIT_SHA}",
                            buildNumber: "${env.BUILD_NUMBER}",
                            trivyHtmlPath: trivyHtmlPath,
                            snykJsonPath: snykJsonPath,
                            sonarHost: "${env.SONAR_HOST}",
                            secretName: 'my-app/secrets'
                        )

                    } else {
                        error("Missing scan reports.")
                    }
                }   
            } 
        }

        stage('Cleanup') {
            steps {
                script {
                    cleanupDockerImages(
                        imageTag: env.COMMIT_SHA,
                        repoName: params.ECR_REPO_NAME,
                        awsAccountId: params.AWS_ACCOUNT_ID,
                        region: env.REGION
                    )
                }
            }
        }
    }

    post {
        always {
            postBuildTestArtifacts('My Test Report', '**/surefire-report.html')
        }

        success {
            script {
                sendAiReportEmail(
                    branch: params.BRANCH,
                    commit: env.COMMIT_SHA ?: 'unknown',
                    to: 'naveenramlu@gmail.com'
                )

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
