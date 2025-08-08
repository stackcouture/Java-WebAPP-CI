def call(String reportName = 'Test Report', String reportFilePattern = 'surefire-report.html') {
    script {
        archiveArtifacts artifacts: "target/surefire-reports/*.xml", allowEmptyArchive: true

        if (fileExists('target/surefire-reports')) {
            junit 'target/surefire-reports/*.xml'
        } else {
            echo "No test results found."
        }

        def reportDir = 'target/site'
        def resolved = findFiles(glob: "${reportDir}/**/${reportFilePattern}")

        if (resolved.length > 0) {
            def actualFile = resolved[0]
            def reportDirPath = new File(actualFile.path).getParent()
            def reportFileName = new File(actualFile.path).getName()

            publishHTML([
                reportName: reportName,
                reportDir: reportDirPath,
                reportFiles: reportFileName,
                keepAll: true,
                alwaysLinkToLastBuild: true,
                allowMissing: true
            ])
        } else {
            echo "No HTML test report found matching: ${reportDir}/**/${reportFilePattern}"
        }
    }
}
