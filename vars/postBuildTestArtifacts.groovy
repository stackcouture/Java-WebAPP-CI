def call(String reportName = 'Test Report', String reportFilePattern = '**/*.html') {
    script {
        archiveArtifacts artifacts: "target/surefire-reports/*.xml", allowEmptyArchive: true

        if (fileExists('target/surefire-reports')) {
            junit 'target/surefire-reports/*.xml'
        } else {
            echo "No test results found."
        }

        if (fileExists('target/test-report.html')) {
            publishHTML([
                reportName: reportName,
                reportDir: 'target',
                reportFiles: reportFilePattern,
                keepAll: true,
                alwaysLinkToLastBuild: true,
                allowMissing: true
            ])
        } else {
            echo "No HTML test report found to publish."
        }
    }
}
