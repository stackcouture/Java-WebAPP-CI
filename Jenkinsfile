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
        string(name: 'COMMIT_SHA', defaultValue: '', description: 'Commit SHA')
    }

    environment {
        REGION = 'ap-south-1'
        GIT_URL = 'https://github.com/stackcouture/Java-WebAPP-CI.git'
        SLACK_CHANNEL = '#java-app'
        DEPENDENCY_TRACK_URL = 'http://15.207.71.114:8081/api/v1/bom'
        SONAR_HOST = "http://13.127.193.165:9000"
        SONAR_PROJECT_KEY = 'Java-App'
        COSIGN_PASSWORD = "admin123"
        ECR_URI = "${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
    }

    tools {
        jdk 'Jdk17'
        maven 'Maven3'
    }

    stages {
        stage('Init & Checkout') {
            steps {
                echo "Cleaning workspace..."
                cleanWs()
                script {
                    echo "Calling checkoutGit..."
                    try {
                        checkoutGit(params.BRANCH, env.GIT_URL, 'my-app/secrets')
                        env.COMMIT_SHA = params.COMMIT_SHA ?: sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        error("Stopping pipeline due to checkout failure: ${e.message}")
                    }
                }
            }
        }

        stage('Build + Test') {
            steps {
                echo "Building and running tests..."
                sh 'mvn clean verify jacoco:report'
            }
        }

        stage('Javadoc') {
            steps {
                echo "Generating Javadoc..."
                sh 'mvn javadoc:javadoc'
            }
        }

        stage('SBOM + FS Scan') {
            parallel {
                stage('Trivy FS Scan') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        echo "Running Trivy filesystem scan..."
                        sh "mkdir -p contrib && curl -sSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl"
                        
                        script {
                            def shortSha = env.COMMIT_SHA.take(8)
                            runTrivyScanUnified("filesystem-scan",".", "fs", shortSha)
                        }
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    def localImageTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA.take(8)}"
                    echo "Building Docker image locally..."
                    buildDockerImage(localImageTag)
                    env.IMAGE_TAG = localImageTag
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
                            def shortSha = env.COMMIT_SHA.take(8)
                            def imageTag = env.IMAGE_TAG
                            runTrivyScanUnified("before-push", env.IMAGE_TAG, "image", shortSha)
                        }
                    }
                }

                stage('Snyk Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        script {
                            echo "Running Snyk scan on the locally built image..."

                            runSnykScan(
                                stageName: "before-push",
                                imageTag: env.IMAGE_TAG,
                                secretName: 'my-app/secrets'
                            )
                        }
                    }
                }
            }
        }

        stage('Docker Push & Digest') {
            steps {
                script {
                    if (env.ECR_IMAGE_DIGEST) {
                        echo "Image already pushed to ECR with digest: ${env.ECR_IMAGE_DIGEST}. Skipping push."
                    } else {
                        dockerPush(
                            imageTag: env.COMMIT_SHA.take(8),
                            ecrRepoName: params.ECR_REPO_NAME,
                            awsAccountId: params.AWS_ACCOUNT_ID,
                            region: env.REGION,
                            secretName: 'my-app/secrets'
                        )
                    }
                }
            }
        }

        stage('Sign Image with Cosign') {
            steps {
                script {
                    if (env.ECR_IMAGE_DIGEST) {
                        echo "Image already signed with digest: ${env.ECR_IMAGE_DIGEST}. Skipping signing."
                    } else {
                        signImageWithCosign(
                            imageTag: env.COMMIT_SHA.take(8),
                            ecrRepoName: params.ECR_REPO_NAME,
                            awsAccountId: params.AWS_ACCOUNT_ID,
                            region: env.REGION,
                            cosignPassword: COSIGN_PASSWORD
                        )
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
                        echo "Running Trivy scan after push..."
                        script {
                            def imageRef = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}@${env.ECR_IMAGE_DIGEST}"
                            runTrivyScanUnified("after-push", imageRef, "image", env.ECR_IMAGE_DIGEST)
                        }
                    }
                }

                stage('Snyk After Push') {
                    options {
                        timeout(time: 15, unit: 'MINUTES')
                    }
                    steps {
                        retry(2) {
                            echo "Running Snyk scan after push..."
                            script {
                                def imageRef = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}@${env.ECR_IMAGE_DIGEST}"
                                runSnykScan(
                                    stageName: "after-push",
                                    imageTag: imageRef,
                                    secretName: 'my-app/secrets'
                                )
                            }
                        }
                    }
                }
            }
        }

        stage('Update Deployment Files') {
            steps {
                script {
                    echo "Updating deployment YAML with image tag: ${env.COMMIT_SHA.take(8)}"
                    updateImageTag(
                        imageTag: env.COMMIT_SHA.take(8),
                        secretName: 'my-app/secrets'
                    )
                }
            }
        }

        stage('Cleanup') {
            steps {
                script {
                    echo "Cleaning up Docker images..."
                    cleanupDockerImages(
                        imageTag: env.COMMIT_SHA.take(8),
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
            echo "Archiving test reports..."
            postBuildTestArtifacts('Unit Report', '**/surefire-report.html')
        }

        success {
            script {
                echo "Build SUCCESS - sending reports and notifications..."
                sendSlackNotification(
                    status: 'SUCCESS',
                    color: 'good',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

        failure {
            script {
                echo "Build FAILURE - sending failure notification..."
                sendSlackNotification(
                    status: 'FAILURE',
                    color: 'danger',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

        unstable {
            script {
                echo "Build UNSTABLE - sending unstable notification..."
                sendSlackNotification(
                    status: 'UNSTABLE',
                    color: 'warning',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

        aborted {
            script {
                echo "Build ABORTED - sending aborted notification..."
                sendSlackNotification(
                    status: 'ABORTED',
                    color: 'danger',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }
    }
}
