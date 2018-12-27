package com.ireslab.sendx.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.ireslab.sendx.model.UserProfile;
import com.ireslab.sendx.notification.SendxConfig;
import com.ireslab.sendx.service.ProfileImageService;

@Service
public class ProfileImageServiceImpl implements ProfileImageService {
	private static final Logger LOG = LoggerFactory.getLogger(ProfileImageServiceImpl.class);

	private static final String URI_SEPARATOR = "/";

	@Autowired
	private SendxConfig sendexConfig;

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.ProfileImageService#saveProfileImage(com.ireslab.sendx.model.UserProfile, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public UserProfile saveProfileImage(UserProfile userProfile, HttpServletRequest request) {

		Date date = new Date();

		String time = "" + date.getTime();

		

		String imageValueInBase64String = userProfile.getProfileImageValue();
		

		if (imageValueInBase64String != null) {

			// This will decode the String which is encoded by using Base64 class
			String formatName = "profile-" + userProfile.getMobileNumber() + "-" + time + ".jpg";
			byte[] imageByte = Base64.decodeBase64(imageValueInBase64String);
			

			String catalinaHome = System.getenv("CATALINA_HOME");
			catalinaHome = (catalinaHome == null) ? System.getProperty("catalina.home") : catalinaHome;

			StringBuilder directory = new StringBuilder();
			directory.append(catalinaHome);
			directory.append(File.separator);
			directory.append("webapps");
			directory.append(File.separator);
			directory.append(sendexConfig.imageDirectoryRelativePath);
			directory.append(File.separator);
			directory.append(formatName);

			FileOutputStream fileOutputStream = null;

			try {
				LOG.info("Saving Image to directory - " + directory);
				fileOutputStream = new FileOutputStream(directory.toString());
				fileOutputStream.write(imageByte);
				String imageUrl = sendexConfig.appBaseUrl + URI_SEPARATOR + sendexConfig.imageDirectoryRelativePath
						+ URI_SEPARATOR + formatName;

				
				userProfile.setProfileImageUrl(imageUrl);
			} catch (IOException e) {
				LOG.info("error occured while writting image to directory :" + directory);
				e.printStackTrace();
			} finally {

				try {
					fileOutputStream.flush();
					fileOutputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

		return userProfile;
	}

	

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.ProfileImageService#saveImage(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public String saveImage(String imageName, String mobileNumber, String imageBase64) {

		Date date = new Date();
		String time = "" + date.getTime();
		String imageUrl = null;

		String formatImageName = null;

		if (imageName.equals("profile")) {
			formatImageName = "profile-" + mobileNumber + "-" + time + ".jpg";
		} else if (imageName.equals("idproof")) {
			formatImageName = "idproof-" + mobileNumber + "-" + time + ".jpg";
		} else if (imageName.equals("residentialproof")) {
			formatImageName = "residentialproof-" + mobileNumber + "-" + time + ".jpg";
		}

		if (imageBase64 != null) {

			// This will decode the String which is encoded by using Base64 class
			// Base64.isBase64(imageBase64);

			byte[] imageByte = Base64.decodeBase64(imageBase64);

			String catalinaHome = System.getenv("CATALINA_HOME");
			catalinaHome = (catalinaHome == null) ? System.getProperty("catalina.home") : catalinaHome;

			StringBuilder directory = new StringBuilder();
			directory.append(catalinaHome);
			directory.append(File.separator);
			directory.append("webapps");
			directory.append(File.separator);
			directory.append(sendexConfig.imageDirectoryRelativePath);
			directory.append(File.separator);
			directory.append(formatImageName);

			FileOutputStream fileOutputStream = null;

			try {
				LOG.info("Saving Image to directory - " + directory);

				fileOutputStream = new FileOutputStream(directory.toString());
				fileOutputStream.write(imageByte);
				imageUrl = sendexConfig.appBaseUrl + URI_SEPARATOR + sendexConfig.imageDirectoryRelativePath
						+ URI_SEPARATOR + formatImageName;

			} catch (IOException e) {
				LOG.info("Error occured while writting image to directory :" + directory);
				e.printStackTrace();
			} finally {

				try {
					fileOutputStream.flush();
					fileOutputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return imageUrl;
	}
}
