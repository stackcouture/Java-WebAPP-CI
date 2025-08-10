def call(Map config = [:]) {
    def imageTag = config.imageTag ?: env.COMMIT_SHA
    def branch = config.branch ?: 'main'
    def secretName = config.secretName ?: error("Missing 'secretName'")
    def repoUrl = "https://github.com/stackcouture/Java-WebAPP-CD"
    def repoDir = "Java-WebAPP-CD"

    def secrets = getAwsSecret(secretName, 'ap-south-1')
    def gitToken = secrets.github_pat

    sh "rm -rf ${repoDir}"
    sh "git clone https://${gitToken}@${repoUrl.replace('https://', '')} ${repoDir}"

    dir(repoDir) {
        sh "git checkout ${branch}"

        if (!fileExists("java-app-chart/values.yaml")) {
            error("File java-app-chart/values.yaml not found in branch ${branch}")
        }

        def currentTag = sh(
            script: "grep '^ *tag:' java-app-chart/values.yaml | awk '{print \$2}'",
            returnStdout: true
        ).trim()

        echo "Current tag in values.yaml: ${currentTag}"

        if (currentTag == imageTag) {
            echo "Image tag is already up to date — skipping update and commit."
            return
        }

        echo "Updating image tag from ${currentTag} to ${imageTag}"
        sh "sed -i 's/^ *tag:.*/tag: ${imageTag}/' java-app-chart/values.yaml"

        sh 'git config user.email "stackcouture@gmail.com"'
        sh 'git config user.name "Naveen"'
        sh "git add java-app-chart/values.yaml"
        sh "git commit -m \"chore: update image tag to ${imageTag}\""
        sh "git push origin ${branch}"
    }
}
