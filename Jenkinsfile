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

        stage('Docker Build') {
    steps {
        sh 'docker build -t registry.sokhin.site/docker-hosted/taskpluse-api:${BUILD_NUMBER} .'
    }
}

stage('Docker Push') {
    steps {
        withCredentials([
            usernamePassword(
                credentialsId: 'nexus-registry',
                usernameVariable: 'NEXUS_USERNAME',
                passwordVariable: 'NEXUS_PASSWORD'
            )
        ]) {
            sh '''
                echo "$NEXUS_PASSWORD" | docker login registry.sokhin.site \
                    -u "$NEXUS_USERNAME" \
                    --password-stdin

                docker push registry.sokhin.site/docker-hosted/taskpluse-api:${BUILD_NUMBER}

                docker logout registry.sokhin.site
            '''
        }
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