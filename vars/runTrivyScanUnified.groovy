def call(String stageName, String scanTarget, String scanType) {
    if (!['image', 'fs'].contains(scanType)) {
        error("Invalid scanType: ${scanType}. Allowed values are 'image' or 'fs'.")
    }

    def reportDir = "reports/trivy/${env.BUILD_NUMBER}/${stageName}"
    def htmlReport = scanType == 'fs' 
        ? "${reportDir}/trivy-fs-scan-${env.COMMIT_SHA}.html"
        : "${reportDir}/trivy-image-scan-${env.COMMIT_SHA}.html"

    def jsonReport = scanType == 'fs' 
        ? "${reportDir}/trivy-fs-scan-${env.COMMIT_SHA}.json"
        : "${reportDir}/trivy-image-scan-${env.COMMIT_SHA}.json"

    sh """
        set -e
        mkdir -p ${reportDir}
        trivy ${scanType} --format template --template "@contrib/html.tpl" -o ${htmlReport} ${scanTarget}
        trivy ${scanType} --format json -o ${jsonReport} ${scanTarget}
    """

    publishHTML(target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: "*.html",
        reportName: "Trivy ${scanType == 'fs' ? 'File System' : 'Image'} Scan (${stageName}) - Build ${env.BUILD_NUMBER}"
    ])

    archiveArtifacts artifacts: "${reportDir}/*.html,${reportDir}/*.json", allowEmptyArchive: true
}