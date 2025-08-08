def call(String reportName = 'Test Report', String reportFilePattern = 'surefire-report.html') {
    script {
        archiveArtifacts artifacts: "target/surefire-reports/*.xml", allowEmptyArchive: true

        if (fileExists('target/surefire-reports')) {
            junit 'target/surefire-reports/*.xml'
        } else {
            echo "No test results found."
        }

        def reportDir = 'target/site'
        def checkFile = "${reportDir}/${reportFilePattern}".replace('**/', '')

        if (fileExists(checkFile)) {
            publishHTML([
                reportName: reportName,
                reportDir: reportDir,
                reportFiles: reportFilePattern,
                keepAll: true,
                alwaysLinkToLastBuild: true,
                allowMissing: true
            ])
        } else {
            echo "No HTML test report found at ${checkFile}"
        }

    }
}
