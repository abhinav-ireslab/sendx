pipeline {
	
environment {
    registry = "docker030303/sendx"
    registryCredential = 'docker030303'
  }
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
	  steps {
		  script {
		  	def image = docker.build registry + ":latest"
			  image.push()
		  }
	  }
}
}
}
