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
                        error("Stopping pipeline due to checkout failure")
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
                // stage('Publish SBOM') {
                //     steps {
                //         script {
                //             if (!fileExists('target/bom.xml')) {
                //                 error "SBOM file target/bom.xml not found!"
                //             }
                //             echo "Uploading SBOM to Dependency Track..."
                //             uploadSbomToDependencyTrack(
                //                 sbomFile: 'target/bom.xml',
                //                 projectName: "${params.ECR_REPO_NAME}",
                //                 projectVersion: "${env.COMMIT_SHA.take(8)}",
                //                 dependencyTrackUrl: "${env.DEPENDENCY_TRACK_URL}",
                //                 secretName: 'my-app/secrets'
                //             )
                //         }
                //     }
                // }
                stage('Trivy FS Scan') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    steps {
                        echo "Running Trivy filesystem scan..."
                        sh "mkdir -p contrib && curl -sSL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl -o contrib/html.tpl"
                        
                        script {
                            runTrivyScan(
                                stageName: "filesystem-scan",
                                scanTarget: ".",
                                scanType: "fs",
                                fileName: env.COMMIT_SHA.take(8)
                            )
                        }
                    }
                }
            }
        }

        // stage('SonarQube Analysis & Gate') {
        //     steps {
        //         echo "Running SonarQube scan..."
        //         sonarScan(
        //             projectKey: env.SONAR_PROJECT_KEY,
        //             sources: 'src/main/java,src/test/java',
        //             binaries: 'target/classes',
        //             exclusions: '**/*.js',
        //             scannerTool: 'sonar-scanner',
        //             sonarEnv: 'sonar-server',
        //             jacocoReportPath: 'target/site/jacoco/jacoco.xml'
        //         )
        //         echo "Checking SonarQube quality gate..."
        //         sonarQualityGateCheck(
        //             projectKey: env.SONAR_PROJECT_KEY,
        //             secretName: 'my-app/secrets',
        //             timeoutMinutes: 5
        //         )
        //     }
        // }

        stage('Build Docker Image') {
            steps {
                 script {
                    def localImageTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA.take(8)}"
                    
                    env.ECR_IMAGE_DIGEST = checkEcrDigestExists(
                        params.ECR_REPO_NAME, 
                        env.COMMIT_SHA.take(8), 
                        params.AWS_ACCOUNT_ID, 
                        env.REGION
                    ) ?: ''

                    if (env.ECR_IMAGE_DIGEST) {
                        echo "Docker image already exists with digest: ${env.ECR_IMAGE_DIGEST}. Skipping build."
                    } else {
                        echo "Image does not exist. Building new Docker image..."
                        buildDockerImage(localImageTag)
                    }
                 }
            }
        }

        stage('Security Scans Before Push') {
            parallel {
                stage('Trivy Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    when {
                        expression { return !env.ECR_IMAGE_DIGEST }
                    }
                    steps {
                        echo "Image does not exist. Running Trivy scan before push..."

                        script {
                            runTrivyScan(
                                stageName: "before-push",
                                scanTarget: "${params.ECR_REPO_NAME}:${env.COMMIT_SHA.take(8)}",
                                scanType: "image",
                                fileName: env.COMMIT_SHA.take(8)
                            )
                        }
                    }
                }
                stage('Snyk Before Push') {
                    options {
                        timeout(time: 10, unit: 'MINUTES')
                    }
                    when {
                        expression { return !env.ECR_IMAGE_DIGEST }
                    }
                    steps {
                        echo "Image does not exist. Running Snyk scan before push..."
                        runSnykScan(
                            stageName: "before-push",
                            imageTag: "${params.ECR_REPO_NAME}:${env.COMMIT_SHA.take(8)}",
                            secretName: 'my-app/secrets'
                        )
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

                        // script {
                        //     runTrivyScan(
                        //         stageName: "after-push",
                        //         scanTarget: "${params.ECR_REPO_NAME}:${env.COMMIT_SHA.take(8)}",
                        //         scanType: "image",
                        //         fileName: env.ECR_IMAGE_DIGEST
                        //     )
                        // }
                    }
                }
                stage('Snyk After Push') {
                     options {
                        timeout(time: 15, unit: 'MINUTES')
                    }
                    steps {
                        retry(2) {
                            echo "Running Snyk scan after push..."
                            runSnykScan(
                                stageName: "after-push",
                                imageTag: "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA.take(8)}",
                                secretName: 'my-app/secrets'
                            )
                        }
                    }
                }
            }
        }

        // stage('Confirm YAML Update') {
        //     steps {
        //         script {
        //             def approver = confirmYamlUpdate()
        //             echo "YAML update approved by: ${approver}"
        //         }
        //     }
        // }

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

        // Testing 
        // stage('Deploy App') {
        //     steps {
        //         echo "Deploying application..."
        //         deployApp()
        //     }
        // }

        // stage('Generate GPT Security Report') {
        //     steps {
        //         script {

        //             def trivyHtmlPath = "reports/trivy/${env.BUILD_NUMBER}/after-push/trivy-image-scan-${env.COMMIT_SHA.take(8)}.html"
        //             def snykJsonPath = "reports/snyk/${env.BUILD_NUMBER}/after-push/snyk-report-${env.COMMIT_SHA.take(8)}.json"

        //             if (fileExists(trivyHtmlPath) && fileExists(snykJsonPath)) {
        //                 echo "Generating GPT security report..."
        //                 runGptSecuritySummary(
        //                     projectKey: env.SONAR_PROJECT_KEY, 
        //                     gitSha: "${env.COMMIT_SHA.take(8)}",
        //                     buildNumber: "${env.BUILD_NUMBER}",
        //                     trivyHtmlPath: trivyHtmlPath,
        //                     snykJsonPath: snykJsonPath,
        //                     sonarHost: "${env.SONAR_HOST}",
        //                     secretName: 'my-app/secrets'
        //                 )

        //             } else {
        //                 error("Missing scan reports.")
        //             }
        //         }   
        //     } 
        // }

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
                // sendAiReportEmail(
                //     branch: params.BRANCH,
                //     commit: env.COMMIT_SHA.take(8) ?: 'unknown',
                //     to: 'naveenramlu@gmail.com'
                // )

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
                    color: '#808080',
                    channel: env.SLACK_CHANNEL,
                    secretName: 'my-app/secrets'
                )
            }
        }

    }
}
