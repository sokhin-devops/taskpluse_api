pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh './mvnw clean test'
            }
        }

        stage('Package') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }
    }

    post {
        success {
            echo '================================='
            echo '✅ TaskPluse CI SUCCESS'
            echo '================================='
        }

        failure {
            echo '================================='
            echo '❌ TaskPluse CI FAILED'
            echo '================================='
        }
    }
}