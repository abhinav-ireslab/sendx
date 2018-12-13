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
      agent any
      steps {
        sh 'docker build -t docker030303/sendx:1 .'
      }
    }
	  stage('Push image') {
      	agent any
		  steps{
			  docker.withRegistry('https://hub.docker.com/', '${docker}') {

        		def customImage = docker.build("docker030303/sendx:1")

			/* Push the container to the custom Registry */
			customImage.push()
		    }
		  }
	}      
  }
}
