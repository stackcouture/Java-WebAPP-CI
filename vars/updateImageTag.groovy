def call(Map config = [:]) {
    def imageTag = config.imageTag ?: env.COMMIT_SHA
    def branch = 'main'
    def secretName = config.secretName ?: error("Missing 'secretName'")
    def repoUrl = "https://github.com/stackcouture/Java-WebAPP-CD"
    def repoDir = "Java-WebAPP-CD"

    def secrets = getAwsSecret(secretName, 'ap-south-1')
    def gitToken = secrets.github_pat

    sh "rm -rf ${repoDir}"

    withEnv(["GIT_PAT=${gitToken}"]) {
        retry(2) {
            sh "git clone https://\$GIT_PAT@${repoUrl.replace('https://', '')} ${repoDir}"
        }
    }

    dir(repoDir) {
        sh "git checkout ${branch}"

        if (!fileExists("deploy/dev-values.yaml")) {
            error("File deploy/dev-values.yaml not found in branch ${branch}")
        }

        def currentTag = sh(
            script: "grep '^ *tag:' deploy/dev-values.yaml | awk '{print \$2}'",
            returnStdout: true
        ).trim()

        echo "Current tag in values.yaml: ${currentTag}"

        if (currentTag == imageTag) {
            echo "Image tag is already up to date — skipping update and commit."
            return
        }

        echo "Updating image tag from ${currentTag} to ${imageTag}"
        sh "sed -i 's/^ *tag:.*/tag: ${imageTag}/' deploy/dev-values.yaml"

        sh 'git config user.email "stackcouture@gmail.com"'
        sh 'git config user.name "Naveen"'
        sh "git add deploy/dev-values.yaml"
        sh "git commit -m \"chore: update image tag to ${imageTag}\""

        try {
            sh "git push origin ${branch}"
            def commitSha = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
            echo "Tag update pushed. Commit SHA: ${commitSha}"
        } catch (e) {
            error("Failed to push tag update. Reason:\n${e.message}")
        }
    }
}
