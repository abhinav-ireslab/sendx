pipeline {
  agent none
  stages {
    stage('Maven Install') {
      agent {
        docker {
          image 'maven:3.5.0'
        }
      }
      steps {
		echo 'Making build.'
		sh 'mvn clean install'
      }
    } 
  stage('Docker Build') {
      agent {
	docker {
	  withDockerRegistry(credentialsId: 'docker030303', url: 'https://hub.docker.com/r/docker030303/sendx/') {
          // we give the image the same version as the .war package
          def image = docker.build("docker030303/sendx:1")
          image.push()
	} 
	}
    }
  }
}
