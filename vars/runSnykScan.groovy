def call(Map config = [:]) {

    def stageName = config.stageName ?: error("Missing 'stageName'")
    def imageTag = config.imageTag ?: error("Missing 'imageTag'")
    def secretName = config.secretName ?: error("Missing 'secretName'")

    def reportDir = "reports/snyk/${env.BUILD_NUMBER}/${stageName}"
    def jsonFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.json"
    def htmlFile = "${reportDir}/snyk-report-${env.COMMIT_SHA}.html"

    def secrets = getAwsSecret(secretName, 'ap-south-1')
    def SNYK_TOKEN = secrets.SNYK_TOKEN

 withEnv(["SNYK_TOKEN=${SNYK_TOKEN}"]) {
sh """#!/bin/bash
    set -e
    mkdir -p '${reportDir}'

    export SNYK_TOKEN='${SNYK_TOKEN}'

    snyk auth \$SNYK_TOKEN > /dev/null 2>&1 || { echo "Snyk auth failed"; exit 1; }
    snyk container test '${imageTag}' --severity-threshold=high --exclude-base-image-vulns --json > '${jsonFile}' || true

    cat <<EOF > '${htmlFile}'
<html><body><pre>
EOF

    if [ -s '${jsonFile}' ]; then
        cat '${jsonFile}' | jq . >> '${htmlFile}'
    else
        echo "Snyk scan failed or returned no data. Please check Jenkins logs or retry." >> '${htmlFile}'
    fi

    echo "</pre></body></html>" >> '${htmlFile}'
"""
}


    publishHTML(target: [
        allowMissing: true,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: reportDir,
        reportFiles: htmlFile.replace("${reportDir}/", ""),
        reportName: "Snyk Image Scan (${stageName}) - Build ${env.BUILD_NUMBER}"
    ])
    
    archiveArtifacts artifacts: "${jsonFile},${htmlFile}", allowEmptyArchive: true
}
