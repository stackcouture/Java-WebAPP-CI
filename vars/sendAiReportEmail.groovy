def call(Map args = [:]) {
    def branch = args.get('branch', 'main')
    def commitSha = args.get('commit', 'N/A')
    def toEmail = args.get('to', 'you@example.com')

    if (fileExists("ai_report.html")) {
        echo "AI Report found. Converting to PDF..."

        // Convert HTML to PDF using wkhtmltopdf
        sh "wkhtmltopdf --zoom 1.3 --enable-local-file-access ai_report.html ai_report.pdf"

        echo "Sending email with PDF attachment..."

        emailext(
            subject: "Security Report - Build #${env.BUILD_NUMBER} - SUCCESS",
            body: """
                <html>
                    <body style="font-family: Arial, sans-serif; font-size: 15px; line-height: 1.6; padding: 10px;">
                        <h2 style="color: #2c3e50;">Hello Team,</h2>
                        <p>
                            Please find attached the <strong>AI-generated security report</strong> for <strong>Build #${env.BUILD_NUMBER}</strong>.
                        </p>
                        <p>
                            This report summarizes the security scan results from <strong>Trivy</strong> and <strong>Snyk</strong> for the current build.
                        </p>
                        <p>
                            <strong>Project:</strong> ${env.JOB_NAME}<br/>
                            <strong>Branch:</strong> ${branch}<br/>
                            <strong>Commit:</strong> ${commitSha}
                        </p>
                        <p>
                            For detailed insights, please open the attached PDF report.
                        </p>
                        <p>
                            Regards,<br/>
                            <strong>Jenkins CI/CD</strong> 🤖
                        </p>
                    </body>
                </html>
            """,
            mimeType: 'text/html',
            attachmentsPattern: 'ai_report.pdf',
            to: toEmail,
            attachLog: false
        )
    } else {
        echo "No ai_report.html found. Skipping email."
    }
}