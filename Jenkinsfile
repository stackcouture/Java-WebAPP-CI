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

        stage('Get Commit SHA') {
            steps {
                script {
                    env.COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    echo "Using commit SHA: ${env.COMMIT_SHA}"
                }
            }
        }

        stage('Build with Maven') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('Generate SBOM') {
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
                        echo "SBOM generated: ${sbomFile}"
                    } else {
                        echo "No SBOM file generated."
                    }
                }
            }
        }

        stage('File System Scan') {
            steps {
                script {
                    try {
                        sh 'trivy fs --format table -o fs.html .'
                        publishHTML(target: [
                            allowMissing: true,
                            alwaysLinkToLastBuild: true,
                            keepAll: true,
                            reportDir: '.',
                            reportFiles: 'fs.html',
                            reportName: 'Trivy Filesystem Scan'
                        ])
                    } catch (err) {
                        echo "Trivy scan failed: ${err}"
                    }
                }
            }
        }

        // stage('Sonar Analysis') {
        //     steps {
        //         withSonarQubeEnv('sonar-server') {
	    //            sh ''' 
        //         		mvn clean verify sonar:sonar \
        //         		-Dsonar.projectKey=Java-App
	    //                '''
        //             }
        //     }
        // }

        // stage('Quality Gates') {
        //     steps {
        //         script {
        //                 waitForQualityGate abortPipeline: true, credentialsId: 'sonar-token' 
        //             }	
        //         }
        // }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${params.ECR_REPO_NAME} ."
            }
        }

        stage('Login to ECR & Tag Image') {
            steps {
                 withCredentials([[
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-jenkins-creds',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                 ]]) {
                        sh """
                            aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com
                            docker tag ${params.ECR_REPO_NAME} ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}
                            docker tag ${params.ECR_REPO_NAME} ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:latest
                        """
                }
            }
        }

        stage('Security Scans') {
            parallel {
                stage('Trivy Image Scan') {
                    steps {
                        withCredentials([[
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: 'aws-jenkins-creds',
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ]]) {
                            script {
                                def TAG = "${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                                def reportDir = "reports/trivy/${env.BUILD_NUMBER}"
                                def scanFile = "${reportDir}/trivy-image-scan-${env.COMMIT_SHA}.html"

                                sh """
                                    mkdir -p ${reportDir}
                                    aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com
                                    trivy image --format table -o ${scanFile} ${TAG}
                                """
                                publishHTML(target: [
                                    allowMissing: true,
                                    alwaysLinkToLastBuild: true,
                                    keepAll: true,
                                    reportDir: reportDir,
                                    reportFiles: "*.html",
                                    reportName: "Trivy Image Scan - Build ${env.BUILD_NUMBER}"
                                ])

                                archiveArtifacts artifacts: "${reportDir}/*.html", allowEmptyArchive: true
                            }
                        }
                    }
                }
                stage('Snyk Image Scan') {
                    environment {
                        SNYK_TOKEN = credentials('SNYK_TOKEN')
                    }
                    steps {
                        withCredentials([[
                            $class: 'AmazonWebServicesCredentialsBinding',
                            credentialsId: 'aws-jenkins-creds',
                            accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                            secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                        ]]) {
                            script {
                                def TAG = "${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}"
                                def reportDir = "reports/snyk/${env.BUILD_NUMBER}"
                                def jsonFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.json"
                                def htmlFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.html"

                                withEnv(["SNYK_TOKEN=${env.SNYK_TOKEN}"]) {
                                    sh """
                                        mkdir -p ${reportDir}
                                        aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com
                                        snyk auth $SNYK_TOKEN
                                        snyk container test ${TAG} --severity-threshold=high --json > ${jsonFile} || true
                                        
                                        # Convert JSON to HTML
                                        echo "<html><body><pre>" > ${htmlFile}
                                        cat ${jsonFile} | jq . >> ${htmlFile}
                                        echo "</pre></body></html>" >> ${htmlFile}
                                    """
                                        publishHTML(target: [
                                            allowMissing: true,
                                            alwaysLinkToLastBuild: true,
                                            keepAll: true,
                                            reportDir: reportDir,
                                            reportFiles: htmlFile.replace("${reportDir}/", ""),
                                            reportName: "Snyk Image Scan - Build ${env.BUILD_NUMBER}"
                                        ])

                                        archiveArtifacts artifacts: "${jsonFile},${htmlFile}", allowEmptyArchive: true
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Push Image to ECR') {
            steps {
                  withCredentials([[
                        $class: 'AmazonWebServicesCredentialsBinding',
                        credentialsId: 'aws-jenkins-creds',
                        accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                        secretKeyVariable: 'AWS_SECRET_ACCESS_KEY'
                 ]]) {
                retry(2) {
                        timeout(time: 5, unit: 'MINUTES') {
                        sh """
                            docker push ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:${env.COMMIT_SHA}
                            docker push ${params.AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/${params.ECR_REPO_NAME}:latest
                        """
                        }
                    }
                }
            }
        }

        // stage('Deploy') {
        //     steps {
        //         withMaven(globalMavenSettingsConfig: 'maven-setting-javaapp', jdk: 'Jdk17', maven: 'Maven3', mavenSettingsConfig: '', traceability: true) {
        //             sh 'mvn deploy -DskipTests=true'
        //         }
        //     }
        // }

        // stage('Confirm YAML Update') {
        //     when {
        //         expression { return params.BRANCH == 'dev' }
        //     }
        //     steps {
        //         script {
        //             def confirm = input message: 'Update deployment YAML with new Docker tag?', parameters: [
        //                 choice(name: 'Confirmation', choices: ['Yes', 'No'], description: 'Proceed with update?')
        //             ]
        //             if (confirm == 'No') {
        //                 error 'Aborted by user.'
        //             }
        //         }
        //     }
        // }

        stage('Update YAML File - FINAL') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'github-pat', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                script {
                    def imageTag = env.COMMIT_SHA
                    def branch = params.BRANCH
                    def repoDir = 'Java-WebAPP-CD'

                    // Clean and clone repo
                    sh "rm -rf ${repoDir}"
                    sh "git clone https://${GIT_USER}:${GIT_TOKEN}@github.com/stackcouture/Java-WebAPP-CD.git ${repoDir}"

                    dir(repoDir) {
                        sh "git checkout ${branch}"

                        // Check if file exists
                        if (!fileExists("java-app-chart/values.yaml")) {
                            error("File java-app-chart/values.yaml not found in branch ${branch}")
                        }

                        // Optional safety check
                        def tagExists = sh(script: "grep -q '^\\s*tag:' java-app-chart/values.yaml && echo found || echo notfound", returnStdout: true).trim()
                        if (tagExists != 'found') {
                            error("Could not find 'tag:' field in values.yaml — aborting.")
                        }

                        // Update the tag
                        sh """
                            sed -i -E "s|(^\\s*tag:\\s*\\\").*(\\\")|\\1${imageTag}\\2|" java-app-chart/values.yaml
                        """

                        // Git config + commit
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
            }
        }

        success {
            script {
                def SHORT_SHA = env.COMMIT_SHA.take(7)
                emailext(
                    attachLog: true,
                    attachmentsPattern: 'target/surefire-reports/*.xml, **/trivy-image-scan-*.html, **/snyk-report-*.json',
                    subject: "${env.JOB_NAME} - Build #${SHORT_SHA} - SUCCESS",
                    body: """
                        <p><strong>Build Status:</strong> SUCCESS</p>
                        <p><strong>Project:</strong> ${env.JOB_NAME}</p>
                        <p><strong>Commit:</strong> ${env.COMMIT_SHA}</p>
                        <p><a href="${env.BUILD_URL}">View Build in Jenkins</a></p>
                        <p> Attached the reports </p>
                    """,
                    to: 'naveenramlu@gmail.com',
                    mimeType: 'text/html'
                )

                wrap([$class: 'BuildUser']) {
                    slackSend(
                        channel: env.SLACK_CHANNEL,
                        token: env.SLACK_TOKEN,
                        color: 'good',
                        message: """\
                            *✅ Deployment Successful!*
                            *Project:* `${env.JOB_NAME}`
                            *Commit:* `${env.COMMIT_SHA}`
                            *Build Number:* #${env.BUILD_NUMBER}
                            *Branch:* `${params.BRANCH}`
                            *Triggered By:* ${BUILD_USER} 👤
                            *Build Tag:* <${env.BUILD_URL}|Click to view in Jenkins>
                            _This is an automated notification from Jenkins 🤖_
                            """
                    )
                }
            }
        }

        failure {
            script {
                 wrap([$class: 'BuildUser']) {
                    slackSend(
                        channel: env.SLACK_CHANNEL,
                        token: env.SLACK_TOKEN,
                        color: 'danger',
                        message: """\
                            *❌ FAILURE Deployment!*
                            *Project:* `${env.JOB_NAME}`
                            *Commit:* `${env.COMMIT_SHA}`
                            *Build Number:* #${env.BUILD_NUMBER}
                            *Branch:* `${params.BRANCH}`
                            *Triggered By:* ${BUILD_USER} 👤
                            *Build Tag:* <${env.BUILD_URL}|Click to view in Jenkins>
                            _This is an automated notification from Jenkins 🤖_
                            """
                    )
                }
            }
        }

        unstable {
            script {
                 wrap([$class: 'BuildUser']) {
                    slackSend(
                        channel: env.SLACK_CHANNEL,
                        token: env.SLACK_TOKEN,
                        color: 'warning',
                        message: """\
                            *⚠️ UNSTABLE Deployment!*
                            *Project:* `${env.JOB_NAME}`
                            *Commit:* `${env.COMMIT_SHA}`
                            *Build Number:* #${env.BUILD_NUMBER}
                            *Branch:* `${params.BRANCH}`
                            *Triggered By:* ${BUILD_USER} 👤
                            *Build Tag:* <${env.BUILD_URL}|Click to view in Jenkins>
                            _This is an automated notification from Jenkins 🤖_
                            """
                    )
                }
            }
        }

        aborted {
            script {
                  wrap([$class: 'BuildUser']) {
                    slackSend(
                        channel: env.SLACK_CHANNEL,
                        token: env.SLACK_TOKEN,
                        color: '#808080',
                        message: """\
                            *🛑 ABORTED Deployment!*
                            *Project:* `${env.JOB_NAME}`
                            *Commit:* `${env.COMMIT_SHA}`
                            *Build Number:* #${env.BUILD_NUMBER}
                            *Branch:* `${params.BRANCH}`
                            *Triggered By:* ${BUILD_USER} 👤
                            *Build Tag:* <${env.BUILD_URL}|Click to view in Jenkins>
                            _This is an automated notification from Jenkins 🤖_
                            """
                    )
                }
            }
        }
    }
}
