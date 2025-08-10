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
        DEPENDENCY_TRACK_URL = 'http://65.0.179.180:8081/api/v1/bom'
        SONAR_HOST = "http://15.206.27.224:9000"
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

        // stage('Publish and Upload SBOM to Dependency-Track') {
        //     steps {
        //         script {
        //             uploadSbomToDependencyTrack(
        //                 sbomFile: 'target/bom.xml',
        //                 projectName: "${params.ECR_REPO_NAME}",
        //                 projectVersion: "${env.COMMIT_SHA}",
        //                 dependencyTrackUrl: "${env.DEPENDENCY_TRACK_URL}",
        //                 secretName: 'my-app/secrets'
        //             )
        //         }
        //     }
        // }

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

        // stage('SonarQube Analysis') {
        //     steps {
        //         script {
        //             sonarScan(
        //                 projectKey: 'Java-App',
        //                 sources: 'src/main/java,src/test/java',
        //                 binaries: 'target/classes',
        //                 exclusions: '**/*.js',
        //                 scannerTool: 'sonar-scanner',
        //                 sonarEnv: 'sonar-server'
        //             )
        //         }
        //     }
        // }

        // stage('SonarQube Quality Gate') {
        //     steps {
        //         script {
        //             sonarQualityGateCheck(
        //                 secretName: 'my-app/secrets',
        //                 timeoutMinutes: 5
        //             )
        //         }
        //     }
        // }

        stage('Build Docker Image') {
            steps {
                 script {
                    buildDockerImage("${params.ECR_REPO_NAME}:${env.COMMIT_SHA}")
                 }
            }
        }

        // stage('Security Scans Before Push') {
        //     parallel {
        //         stage('Trivy Before Push') {
        //             options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 script {
        //                     def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
        //                     runTrivyScanUnified("before-push", localTag, "image")
        //                 }
        //             }
        //         }
        //         stage('Snyk Before Push') {
        //             options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 script {
        //                     def localTag = "${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
        //                     runSnykScan(
        //                         stageName: "before-push",
        //                         imageTag: localTag,
        //                         secretName: 'my-app/secrets'
        //                     )
        //                 }
        //             }
        //         }
        //     }
        // }

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

        // stage('Security Scans After Push') {
        //     parallel {
        //         stage('Trivy After Push') {
        //              options {
        //                 timeout(time: 10, unit: 'MINUTES')
        //             }
        //             steps {
        //                 script {
        //                     def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
        //                     runTrivyScanUnified("after-push", pushedTag, "image")
        //                 }
        //             }
        //         }
        //         stage('Snyk After Push') {
        //              options {
        //                 timeout(time: 15, unit: 'MINUTES')
        //             }
        //             steps {
        //                 script {
        //                     def pushedTag = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${env.REGION}.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
        //                     retry(2) {
        //                         runSnykScan(
        //                             stageName: "after-push",
        //                             imageTag: pushedTag,
        //                             secretName: 'my-app/secrets'
        //                         )
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }

        // stage('Deploy') {
        //     steps {
        //         deployApp()
        //     }
        // }

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

        stage('Update File - FINAL') {
            steps {
                script {
                     updateImageTag(
                        imageTag: env.COMMIT_SHA,
                        branch: params.BRANCH
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

        // stage('Generate GPT Report') {
        //     steps {
        //         script {

        //             def trivyHtmlPath = "reports/trivy/${env.BUILD_NUMBER}/after-push/trivy-image-scan-${env.COMMIT_SHA}.html"
        //             def snykJsonPath = "reports/snyk/${env.BUILD_NUMBER}/after-push/snyk-report-${env.COMMIT_SHA}.json"

        //             if (fileExists(trivyHtmlPath) && fileExists(snykJsonPath)) {

        //                 runGptSecuritySummary(
        //                     projectKey: "Java-App", 
        //                     gitSha: "${env.COMMIT_SHA}",
        //                     buildNumber: "${env.BUILD_NUMBER}",
        //                     trivyHtmlPath: trivyHtmlPath,
        //                     snykJsonPath: snykJsonPath,
        //                     sonarHost: "${env.SONAR_HOST}",
        //                     secretName: 'my-app/secrets'
        //                 )

        //             } else {
        //                 error("One or more required files do not exist: ${trivyHtmlPath}, ${snykJsonPath}")
        //             }
        //         }   
        //     } 
        // }
    }

    post {
        always {
            postBuildTestArtifacts('My Test Report', '**/surefire-report.html')
        }

        success {
            // script {
            //     sendAiReportEmail(
            //         branch: params.BRANCH,
            //         commit: env.COMMIT_SHA ?: 'unknown',
            //         to: 'naveenramlu@gmail.com'
            //     )
            // }
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
