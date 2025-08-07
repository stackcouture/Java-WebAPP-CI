def call(String status, String color = '#36a64f') {
    def emojiMap = [
        SUCCESS : "✅ Deployment Successful!",
        FAILURE : "❌ FAILURE Deployment!",
        UNSTABLE: "⚠️ UNSTABLE Deployment!",
        ABORTED : "🛑 ABORTED Deployment!"
    ]

    wrap([$class: 'BuildUser']) {
        slackSend(
            channel: env.SLACK_CHANNEL,
            token: env.SLACK_TOKEN,
            color: color,
            message: """\
                *${emojiMap[status]}*
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