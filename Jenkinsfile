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

        stage('Docker Build & Push') {
    steps {
        withCredentials([
            usernamePassword(
                credentialsId: 'nexus-registry',
                usernameVariable: 'NEXUS_USER',
                passwordVariable: 'NEXUS_PASSWORD'
            )
        ]) {
            sh '''
                set -e

                IMAGE="registry.sokhin.site/docker-hosted/taskpluse-api:${BUILD_NUMBER}"

                echo "$NEXUS_PASSWORD" | docker login registry.sokhin.site \
                    -u "$NEXUS_USER" \
                    --password-stdin

                echo "Building: $IMAGE"

                docker build \
                    -t "$IMAGE" \
                    .

                docker push "$IMAGE"

                docker logout registry.sokhin.site
            '''
        }
    }
}
stage('Trigger Deployment') {
    steps {
        echo "Triggering TaskPluse-Deploy with image tag ${BUILD_NUMBER}"

        build job: 'TaskPluse-Deploy',
              wait: true,
              parameters: [
                  string(
                      name: 'IMAGE_TAG',
                      value: "${BUILD_NUMBER}"
                  )
              ]
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