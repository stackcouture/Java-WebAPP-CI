pipeline {
    agent {
        label "jenkins-agent"
    }

    environment {
        OPENAI_API_KEY = credentials('openai-api-key')  // Use Jenkins credentials plugin to store your API key securely
    }

    stages {
        stage('Prepare Payload') {
            steps {
                script {
                    // Define file names for the payload and response
                    def promptFile = "openai_prompt.json"
                    def fullResponseFile = "openai_response.json"  // Define this variable
                    def gptReportFile = "ai_report.md"

                    // Sample user prompt data
                    def prompt = "Tell me a joke"  // Example prompt

                    // Build the JSON payload safely
                    def payload = [
                        model: "gpt-4o-mini",  // GPT-4 mini model
                        temperature: 0.4,      // Response creativity
                        messages: [
                            [role: "user", content: prompt]  // User message content
                        ]
                    ]

                    // Write the payload JSON safely to a file
                    writeFile file: promptFile, text: groovy.json.JsonOutput.toJson(payload)

                    // For debugging purposes: Check the contents of the prompt file
                    echo "Payload JSON written to: ${promptFile}"
                    sh "cat ${promptFile}"
                }
            }
        }

        stage('Call OpenAI API') {
            steps {
                script {
                    // Call OpenAI API securely with credentials
                    withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_API_KEY')]) {
                        def apiResponse = sh(script: """
                            curl -s https://api.openai.com/v1/chat/completions \\
                            -H "Authorization: Bearer \$OPENAI_API_KEY" \\
                            -H "Content-Type: application/json" \\
                            -d @openai_prompt.json
                        """, returnStdout: true).trim()

                        // Write the response to a file for debugging
                        writeFile file: fullResponseFile, text: apiResponse
                        
                        // For debugging purposes: Check the contents of the API response
                        echo "API Response written to: ${fullResponseFile}"
                        sh "cat ${fullResponseFile}"

                        // Parse the API response and extract the AI's answer
                        def jsonResponse = readJSON file: fullResponseFile
                        def aiResponse = jsonResponse.choices[0].message.content

                        // Create a markdown file with AI's response
                        def report = """
                        # GPT-4 Response

                        ## Prompt:
                        ${prompt}

                        ## Response:
                        ${aiResponse}
                        """
                        writeFile file: gptReportFile, text: report

                        // Output AI response to console for visibility
                        echo "AI Response: ${aiResponse}"
                    }
                }
            }
        }

        stage('Publish Report') {
            steps {
                script {
                    // Archive the AI report as an artifact for Jenkins job result
                    archiveArtifacts artifacts: "ai_report.md", allowEmptyArchive: true

                    // Optionally, display the report
                    echo "AI Report generated and saved as ai_report.md"
                }
            }
        }
    }

    post {
        always {
            cleanWs()  // Clean workspace after the job
        }
    }
}
