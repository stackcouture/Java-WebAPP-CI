# scripts/generate_gpt_report.py

import json
import openai
import sys
import os

def load_json(filepath):
    with open(filepath, 'r') as f:
        return json.load(f)

def call_gpt(prompt):
    openai.api_key = os.getenv("OPENAI_API_KEY")
    response = openai.ChatCompletion.create(
        model="gpt-4",
        messages=[{"role": "system", "content": "You are a DevSecOps expert. Summarize this vulnerability report for a CTO in HTML."},
                  {"role": "user", "content": prompt}],
        temperature=0.3
    )
    return response.choices[0].message.content

def main(trivy_path, snyk_path, output_path):
    trivy_data = load_json(trivy_path)
    snyk_data = load_json(snyk_path)

    prompt = f"""
Summarize the following two security scan results into a short, readable **HTML report** for a DevSecOps team.
Focus on critical vulnerabilities, CVEs, and remediation advice.

=== Trivy Scan Results ===
{json.dumps(trivy_data)}

=== Snyk Scan Results ===
{json.dumps(snyk_data)}
    """

    result_html = call_gpt(prompt)
    
    with open(output_path, 'w') as f:
        f.write(result_html)

if __name__ == '__main__':
    trivy_json = sys.argv[1]
    snyk_json = sys.argv[2]
    output_html = sys.argv[3]
    main(trivy_json, snyk_json, output_html)
