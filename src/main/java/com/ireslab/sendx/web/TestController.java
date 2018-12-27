package com.ireslab.sendx.web;


import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.ireslab.sendx.dto.Communication;
import com.ireslab.sendx.dto.DataFields;
import com.ireslab.sendx.dto.DriverLicence;
import com.ireslab.sendx.dto.Location;
import com.ireslab.sendx.dto.LocationAdditionalFields;
import com.ireslab.sendx.dto.NationalId;
import com.ireslab.sendx.dto.PersonInfo;
import com.ireslab.sendx.dto.VerifyRequest;
import com.ireslab.sendx.electra.model.SendxElectraRequest;
import com.ireslab.sendx.entity.OAuthAccessToken;
import com.ireslab.sendx.notification.AndroidPushNotificationsService;
import com.ireslab.sendx.repository.OAuthAccessTokenRepository;
import com.ireslab.sendx.service.impl.TestServiceImpl;

@RestController
@RequestMapping(value = "/test/*")
public class TestController {

	@Autowired
	OAuthAccessTokenRepository accessTokenRepo;
	
	@Autowired
	private TestServiceImpl testServiceImpl;
	
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	Gson gson;

	@RequestMapping(value = "deleteAccessToken", method = RequestMethod.GET)
	public void deleteAccessToken(@RequestParam(value = "userName") String userName) {
		
		
		System.out.println("TestController.deleteAccessToken(), username - " + userName);
		OAuthAccessToken authAccessToken = accessTokenRepo.findByUserName(userName);

		System.out.println(authAccessToken.toString());
		accessTokenRepo.delete(authAccessToken);

		//System.out.println("Deleted");
	}
	
	
	@RequestMapping(value = "getAllTransactionDetails", method = RequestMethod.POST)
	public void getAllTransactionDetails(@RequestBody SendxElectraRequest sendxElectraRequest) {
	
		testServiceImpl.getAllTransactionalDetails(sendxElectraRequest);

		//System.out.println("Deleted");
	}
	
	
private final String TOPIC = "f78d420a9cc2c449";
	
	@Autowired
	AndroidPushNotificationsService androidPushNotificationsService;

	@RequestMapping(value = "send", method = RequestMethod.GET, produces = "application/json")
	public ResponseEntity<String> send() throws JSONException {

		JSONObject body = new JSONObject();
		body.put("to", "/topics/" + TOPIC);
		body.put("priority", "high");

		JSONObject notification = new JSONObject();
		notification.put("title", "JSA Notification");
		notification.put("body", "Happy Message!");
		
		JSONObject data = new JSONObject();
		data.put("Key-1", "JSA Data 1");
		data.put("Key-2", "JSA Data 2");

		body.put("notification", notification);
		body.put("data", data);

		HttpEntity<String> request = new HttpEntity<>(body.toString());

		CompletableFuture<String> pushNotification = androidPushNotificationsService.send(request,"safsd");
		CompletableFuture.allOf(pushNotification).join();

		try {
			String firebaseResponse = pushNotification.get();
			
			System.out.println(firebaseResponse);
			
			return new ResponseEntity<>(firebaseResponse, HttpStatus.OK);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

		return new ResponseEntity<>("Push Notification ERROR!", HttpStatus.BAD_REQUEST);
	}
	
	
	@RequestMapping(value = "truliooTest", method = RequestMethod.GET, produces = "application/json")
	public String truliooTest() throws JSONException {
		
/*
		// Trulioo - code for connection check
		String connCheckUrl = "https://api.globaldatacompany.com/connection/v1/sayhello/sach";
		ResponseEntity<String> response= restTemplate.getForEntity( connCheckUrl, String.class);
		
		System.out.println("Response : body - "+response.getBody()+"\n Code - "+response.getStatusCodeValue());*/
		
		
		// Trulioo - code for test authentication
		String connTestUrl = "https://api.globaldatacompany.com/configuration/v1/countrycodes/india";
		String  password = "Sendxteam@12345";
		
		//identity authentication
		String  username = "SendX_Demo_API";
		//HttpHeaders headers = createHeaders(username, password);
		HttpEntity<String> requestHeader = new HttpEntity<String>(createHeaders(username, password));
		ResponseEntity<String> response = restTemplate.exchange(connTestUrl, HttpMethod.GET, requestHeader, String.class);
		
		System.out.println("Authentication Url - "+connTestUrl);
		System.out.println("\n Identity Authentication Response : body - "+response.getBody()+", Code - "+response.getStatusCodeValue());
		
		/*//document authentication verify
        username = "SendX_DemoDocV_API";
		requestHeader = new HttpEntity<String>(createHeaders(username, password));
	    response = restTemplate.exchange(connTestUrl, HttpMethod.GET, requestHeader, String.class);
		System.out.println("\n Document Authentication Response : body - "+response.getBody()+", Code - "+response.getStatusCodeValue());
		
		//bussiness authentication verify
		username = "SendX_KYB_API";
		requestHeader = new HttpEntity<String>(createHeaders(username, password));
	    response = restTemplate.exchange(connTestUrl, HttpMethod.GET, requestHeader, String.class);
		System.out.println("\n Bussiness Authentication Response : body - "+response.getBody()+", Code - "+response.getStatusCodeValue());*/

		
		/*PersonInfo personInfo = new PersonInfo();
		personInfo.setFirstGivenName("Sachin");
		personInfo.setFirstSurName("Chauhan");
		personInfo.setGender("M");
		personInfo.setDayOfBirth(01);
		personInfo.setMonthOfBirth(11);
		personInfo.setYearOfBirth(1990);
		
		Communication communication = new Communication();
		communication.setMobileNumber("8750857419");
		communication.setEmailAddress("sachinchn111@gmail.com");
		
		Location location = new Location();
		location.setCounty("India");
		location.setPostalCode("201001");
		
		LocationAdditionalFields locationAdditionField = new LocationAdditionalFields();
		locationAdditionField.setAddress1("Pratap Vihar, Ghaziabad, UP");
		location.setAdditionalFields(locationAdditionField);
		
		List<NationalIds> nationIdList = new ArrayList<NationalIds>();
		NationalIds nationalIds = new NationalIds();
		nationalIds.setNumber("1965452636549874");
		nationalIds.setType("Aadhaar Card Number");
		
		DriverLicence driverLicence = new DriverLicence();
		driverLicence.setNumber("UP12536975122");
		
		DataFields dataFields = new DataFields();
		dataFields.setNationalIds(nationIdList);
		dataFields.setCommunication(communication);
		dataFields.setDriverLicence(driverLicence);
		dataFields.setPersonInfo(personInfo);
		dataFields.setLocation(location);
		
		VerifyRequest verifyRequest = new VerifyRequest();
		verifyRequest.setCountryCode("IN");
		verifyRequest.setDataFields(dataFields);
		
		String verifyRequestJson = gson.toJson(verifyRequest);
		
		String connTestUrl = "https://api.globaldatacompany.com/verifications/v1/verify/";
		String  password = "Sendxteam@12345";
		String  username = "SendX_DemoDocV_API";
		//HttpHeaders headers = createHeaders(username, password);
		//HttpEntity<String> requestHeader = new HttpEntity<String>(createHeaders(username, password));
		HttpHeaders headers = createHeaders(username, password);
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<String>(verifyRequestJson, headers);
		ResponseEntity<String> response = restTemplate.exchange(connTestUrl, HttpMethod.POST, request, String.class);*/		
		
		return "Response : body - "+response.getBody()+", Code - "+response.getStatusCodeValue();
	}
	
	private HttpHeaders createHeaders(String username, String password) {
		return new HttpHeaders() {
			private static final long serialVersionUID = -1190260176568104021L;
			

			{
				String auth = username + ":" + password;
				byte[] encodedAuth = Base64.encodeBase64(auth.getBytes());
				String authHeader = "Basic " + new String(encodedAuth);
				set("Authorization", authHeader);
			}
		};
	}
	
	@RequestMapping(value = "imageTest", method = RequestMethod.GET, produces = "application/json")
	public String imageTest() throws JSONException {
		
		
		
		return "";
		
	}
	
	/*
	public static BufferedImage decodeToImage(String imageString) {
		 
        BufferedImage image = null;
        byte[] imageByte;
        try {
            BASE64Decoder decoder = new BASE64Decoder();
            imageByte = decoder.decodeBuffer(imageString);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageByte);
            image = ImageIO.read(bis);
            bis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return image;
    }*/
	
}
