pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('CI Test') {
            steps {
                echo '================================='
                echo 'TaskPluse CI Pipeline'
                echo 'Repository checkout successful!'
                echo '================================='
            }
        }
    }

    post {
        success {
            echo '✅ CI BUILD SUCCESS'
        }

        failure {
            echo '❌ CI BUILD FAILED'
        }
    }
}