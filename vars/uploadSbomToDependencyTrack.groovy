// vars/uploadSbomToDependencyTrack.groovy
def call(Map config = [:]) {
    def sbomFile = config.sbomFile ?: 'target/bom.xml'
    def projectName = config.projectName ?: error("Missing 'projectName'")
    def projectVersion = config.projectVersion ?: error("Missing 'projectVersion'")
    def dependencyTrackUrl = config.dependencyTrackUrl ?: error("Missing 'dependencyTrackUrl'")
    def secretName = config.secretName ?: error("Missing 'secretName'")
    def region = config.region ?: 'ap-south-1'

    def secrets = getAwsSecret(secretName, region)
    def DT_API_KEY = secrets.dependency_track_api_key

    if (!fileExists(sbomFile)) {
        error "SBOM not found: ${sbomFile}"
    }

    archiveArtifacts artifacts: sbomFile, allowEmptyArchive: true

    retry(3) {
        withEnv(["DT_API_KEY=${DT_API_KEY}"]) {
            sh (
                script: """
                    curl -sSf -X POST "${dependencyTrackUrl}" \\
                        -H "X-Api-Key: \$DT_API_KEY" \\
                        -H "Content-Type: multipart/form-data" \\
                        -F "autoCreate=true" \\
                        -F "projectName=${projectName}" \\
                        -F "projectVersion=${projectVersion}" \\
                        -F "bom=@${sbomFile}"
                """,
                returnStatus: false,
                label: "Uploading SBOM securely to Dependency Track"
            )
        }
    }
}
