pipeline {
    agent any
    stages {
        stage('Build') {
            tools {
                jdk "jdk8"
            }
            steps {
                sh './gradlew build'
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
