def call(Map config = [:]) {
    withCredentials([usernamePassword(credentialsId: 'github-pat', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
        def imageTag = config.imageTag ?: env.COMMIT_SHA
        def branch = config.branch ?: 'main'
        def repoDir = 'Java-WebAPP-CD'

        // Clean and clone the repo
        sh "rm -rf ${repoDir}"
        sh """
            git clone https://${GIT_USER}:${GIT_TOKEN}@github.com/stackcouture/Java-WebAPP-CD.git ${repoDir}
        """

        dir(repoDir) {
            echo "The branch tag: ${imageTag}"
            sh "git checkout ${branch}"

            def fileExists = fileExists("java-app-chart/values.yaml")
            if (!fileExists) {
                error("File java-app-chart/values.yaml not found in branch ${branch}")
            }

            // Extract current tag from values.yaml
            def currentTag = sh(
                script: "grep 'tag:' java-app-chart/values.yaml | sed -E 's/^\\s*tag:\\s*\\\"(.*)\\\"/\\1/'",
                returnStdout: true
            ).trim()

            echo "Current tag in values.yaml: ${currentTag}"

            if (currentTag == imageTag) {
                echo "Image tag is already up to date — skipping update and commit."
                return
            }

            echo "Updating image tag from ${currentTag} to ${imageTag}"

            // Apply the sed command
            sh """
                sed -i -E "s|(^\\s*tag:\\s*\\\").*(\\\")|\\1${imageTag}\\2|" java-app-chart/values.yaml
            """

            // Commit and push changes
            sh 'git config user.email "stackcouture@gmail.com"'
            sh 'git config user.name "Naveen"'
            sh 'git add java-app-chart/values.yaml'
            sh "git commit -m 'chore: update image tag to ${imageTag}'"
            sh "git push origin ${branch}"
        }
    }
}
