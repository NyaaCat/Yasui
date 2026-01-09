pipeline {
    agent any
    stages {
        stage('Build') {
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
