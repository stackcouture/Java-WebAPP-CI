pipeline {
    agent {
        label "jenkins-agent"
    }

    environment {
        OPENAI_API_KEY = credentials('openai-api-key')  // Use Jenkins credentials plugin to store your API key securely
    }

    stages {
        stage('Test OpenAI API') {
            steps {
                script {
                    // Define a simple prompt
                    def prompt = "Tell me a joke"  // Example prompt
                    def promptFile = "openai_prompt.json"

                    // Build the JSON payload safely
                    def payload = [
                        model: "gpt-4",  // Use GPT-4 model
                        messages: [
                            [role: "user", content: prompt]  // User message content
                        ]
                    ]

                    // Write the payload JSON safely to a file
                    writeFile file: promptFile, text: groovy.json.JsonOutput.toJson(payload)

                    // For debugging purposes: Check the contents of the prompt file
                    echo "Payload JSON written to: ${promptFile}"
                    sh "cat ${promptFile}"

                    // Call OpenAI API securely with credentials
                    withCredentials([string(credentialsId: 'openai-api-key', variable: 'OPENAI_API_KEY')]) {
                        try {
                            // Send API request and capture the response
                            def apiResponse = sh(script: """
                                curl -s https://api.openai.com/v1/chat/completions \\
                                -H "Authorization: Bearer \$OPENAI_API_KEY" \\
                                -H "Content-Type: application/json" \\
                                -d @openai_prompt.json
                            """, returnStdout: true).trim()

                            // Log the full API response for debugging
                            echo "API Response: ${apiResponse}"

                            // Check if response contains error
                            def responseJson = readJSON text: apiResponse
                            if (responseJson.error) {
                                error "OpenAI API returned an error: ${responseJson.error.message}"
                            }

                            // Parse the response for the AI's generated content
                            def aiResponse = responseJson.choices[0].message.content
                            echo "AI's Response: ${aiResponse}"

                        } catch (Exception e) {
                            // Handle errors (e.g., API issues or invalid response)
                            echo "Error occurred while calling OpenAI API: ${e.message}"
                        }
                    }
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
