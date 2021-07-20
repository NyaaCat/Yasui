pipeline {
    agent any
    stages {
        stage('Build') {
            tools {
                jdk "jdk16"
            }
            steps {
                sh './gradlew publish'
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
            cleanWs()
        }
    }
}
