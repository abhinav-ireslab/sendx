/**
 * 
 *//*
package com.ireslab.sendx.web;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ireslab.sendx.electra.model.ClientSubscriptionDto;
import com.ireslab.sendx.electra.model.ClientSubscriptionRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionResponse;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateResponse;
import com.ireslab.sendx.exception.BusinessException;
import com.ireslab.sendx.model.AccountVerificationResponse;
import com.ireslab.sendx.model.CompanyCodeResponse;
import com.ireslab.sendx.model.SignupRequest;
import com.ireslab.sendx.model.SignupResponse;
import com.ireslab.sendx.model.SubscriptionPlanResponse;
import com.ireslab.sendx.notification.MailMessage;
import com.ireslab.sendx.notification.MailService;
import com.ireslab.sendx.service.ElectraAppService;
import com.ireslab.sendx.service.TransactionalApiService;
import com.ireslab.sendx.util.AppStatusCodes;
import com.ireslab.sendx.util.PropConstants;

*//**
 * @author iRESlab
 *
 *//*
@RestController
@RequestMapping(value = "/electra/*", produces = MediaType.APPLICATION_JSON_VALUE)
public class ElectraAppController {
	
	@Autowired
	private ElectraAppService electraAppService;
	
	@Autowired
	private ObjectWriter objectWriter;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	@Autowired
	private MailService mailService;
	
	@Autowired
	private TransactionalApiService transactionalApiService;

	private static final Logger LOG = LoggerFactory.getLogger(ElectraAppController.class);
	
	*//**
	 * use to generate the company code.
	 * 
	 * @return
	 * @throws JsonProcessingException
	 *//*
	@RequestMapping(value = "/generateCompanyCode", method = RequestMethod.GET)
	public CompanyCodeResponse generateCompanyCode() throws JsonProcessingException {
			
        LOG.info("Request received for Generating company code ");

        CompanyCodeResponse companyCodeResponse = electraAppService.generateCompanyCode();
		
		 LOG.debug("Response for Generating company code - " + companyCodeResponse.toString());
		

		return companyCodeResponse;
	}
	
	
	*//**
	 * use to check entered mobile number and email already registered or not.
	 * 
	 * @param mobileNumber
	 * @param countryDialCode
	 * @param companyCode
	 * @param email
	 * @return
	 * @throws JsonProcessingException
	 *//*
	@RequestMapping(value = "/checkMobileNumberRegistration", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public AccountVerificationResponse verifyMobileNo(
			@RequestParam(value = "mobileNumber", required = true) Long mobileNumber,
			@RequestParam(value = "countryDialCode", required = true) String countryDialCode,
			@RequestParam(value = "companyCode", required = true) String companyCode,
			@RequestParam(value = "email", required = true) String email)
			throws JsonProcessingException {

		AccountVerificationResponse accVerificationResponse = null;
		LOG.debug("Account verification request received - \n\t mobileNumber : " + mobileNumber + ",\n\t countryCode : "
				+ countryDialCode + ",\n\t companyCode : "
				+ companyCode);

		if (mobileNumber == null || countryDialCode == null || companyCode == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, AppStatusCodes.INVALID_REQUEST,
					PropConstants.INVALID_REQUEST);
		}

		accVerificationResponse = electraAppService.verifyAccountByMobileNo(mobileNumber, countryDialCode, companyCode);
		
		ClientSubscriptionUpdateRequest clientSubscriptionUpdateRequest = new ClientSubscriptionUpdateRequest();
		clientSubscriptionUpdateRequest.setEmail(email);
		clientSubscriptionUpdateRequest.setMobileNo(""+mobileNumber);
		clientSubscriptionUpdateRequest.setCompanyCode(companyCode);
		
		
		
		ClientSubscriptionUpdateResponse clientSubscriptionUpdateResponse = transactionalApiService.updateCheckmailRegistration(clientSubscriptionUpdateRequest);
		if(clientSubscriptionUpdateResponse.getCode() == 102){
			accVerificationResponse.setMessage("Email already Exist");
			accVerificationResponse.setCode(101);
		}else if(clientSubscriptionUpdateResponse.getCode() == 103) {
			accVerificationResponse.setMessage("You are unauthorized to signup.");
			accVerificationResponse.setCode(101);
		}else if(clientSubscriptionUpdateResponse.getCode() == 104) {
			accVerificationResponse.setMessage("Your registration denied. Please contact administrator");
			accVerificationResponse.setCode(101);
		}
		LOG.info("Account verification response sent - " + objectWriter.writeValueAsString(accVerificationResponse));

		return accVerificationResponse;
	}
	
	
	
	*//**
	 * use to register user.
	 * 
	 * @param signupRequest
	 * @param request
	 * @return
	 * @throws JsonProcessingException
	 *//*
	@RequestMapping(value = "/register", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public SignupResponse registerClientUser(@RequestBody SignupRequest signupRequest, HttpServletRequest request)
			throws JsonProcessingException {

		

		LOG.info("JSON Request For Register - ");
		SignupResponse signupResponse = null;

		signupResponse = electraAppService.registerClientUser(signupRequest);
		LOG.info("JSON Response - ");

		return signupResponse;
	}
	
	
	
	*//**
	 * use to save the details of subscribed  plan by client.
	 * 
	 * @param clientSubscriptionRequest
	 * @param request
	 * @return
	 * @throws JsonProcessingException
	 *//*
	@RequestMapping(value = "/clientSubscriptionPlan", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ClientSubscriptionResponse clientSubscriptionPlan(@RequestBody ClientSubscriptionRequest clientSubscriptionRequest, HttpServletRequest request)
			throws JsonProcessingException {

		

		LOG.info("JSON Request For subscription plan - " + clientSubscriptionRequest.getClientSubscriptionDto().toString());
		ClientSubscriptionResponse clientSubscriptionResponse = null;
		ClientSubscriptionDto clientSubscriptionDto = clientSubscriptionRequest.getClientSubscriptionDto();
		String pass = "Client@123";
		clientSubscriptionDto.setPassword(passwordEncoder.encode(pass));
		clientSubscriptionRequest.setClientSubscriptionDto(clientSubscriptionDto);

		clientSubscriptionResponse = electraAppService.clientSubscriptionPlan(clientSubscriptionRequest);
		LOG.info("JSON Response after subscription plan");
		
		if(clientSubscriptionResponse.getCode() == HttpStatus.OK.value()) {
			MailMessage mailMessage = new MailMessage();
			mailMessage.setToEmailAddresses(new String[] { clientSubscriptionDto.getEmail() });
			mailMessage.setSubject("Subscription Plan");
			
			mailMessage.setMessageBody(createMessage(clientSubscriptionDto.getEmail(),pass,clientSubscriptionDto.getFirstName(),clientSubscriptionDto.getLastName()));
			mailService.sendEmailElectraClient(mailMessage);
		}
		
		clientSubscriptionResponse.setCode(AppStatusCodes.SUCCESS);
		return clientSubscriptionResponse;
	}
	
	*//**
	 * use to provide all details of client's subscription plan.
	 * 
	 * @param clientSubscriptionRequest
	 * @param request
	 * @return
	 * @throws JsonProcessingException
	 *//*
	@RequestMapping(value = "/getClientSubscriptionPlan", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ClientSubscriptionResponse getClientSubscriptionPlan(@RequestBody ClientSubscriptionRequest clientSubscriptionRequest, HttpServletRequest request)
			throws JsonProcessingException {

		

		LOG.info("JSON Request For Get Client Subscription Plan - " +  clientSubscriptionRequest.getClientSubscriptionDto().toString());
		ClientSubscriptionResponse clientSubscriptionResponse = null;

		clientSubscriptionResponse = electraAppService.getClientSubscriptionPlan(clientSubscriptionRequest);
		LOG.info("Response sent for client subscription plan " );

		return clientSubscriptionResponse;
	}
	
	
	*//**
	 * use to check logged in app user client or agent.
	 * 
	 * 
	 * @param clientSubscriptionRequest
	 * @param request
	 * @return
	 * @throws JsonProcessingException
	 *//*
	@RequestMapping(value = "/isClientORNot", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public ClientSubscriptionResponse isClientORNot(@RequestBody ClientSubscriptionRequest clientSubscriptionRequest, HttpServletRequest request)
			throws JsonProcessingException {

		

		LOG.info("JSON Request For Check Exist Client OR Not - " +  clientSubscriptionRequest.getClientSubscriptionDto().toString());
		ClientSubscriptionResponse clientSubscriptionResponse = null;

		clientSubscriptionResponse = electraAppService.isClientORNot(clientSubscriptionRequest);
		LOG.info("JSON Response for client or not - " + objectWriter.writeValueAsString(clientSubscriptionRequest));

		return clientSubscriptionResponse;
	}


	*//**
	 * @param email
	 * @param pass
	 * @param firstName
	 * @param lastName
	 * @return
	 *//*
	private String createMessage(String email, String pass, String firstName, String lastName) {

     String mailMessage = "<!DOCTYPE html>\r\n" + 
		"<html>\r\n" + 
		"\r\n" + 
		"<body>\r\n" + 
		"<p>Dear "+firstName+" "+lastName+",</p>\r\n" + 
		"<p>Thank you for subscribing to our Electra platform. Please find your credential and <a href='http://ec2-13-250-226-42.ap-southeast-1.compute.amazonaws.com:4200/login'>link to Electra Client Console</a>. Once you have login, please proceed to change the password.</p>\r\n" + 
		"\r\n" + 
		"<p>Username: "+email+" <br> Password (Temp): "+pass+"</p>" + 
		 
		"<p>*This is a system generated email, please do not reply.</p>\r\n" + 
		"<p>Yours Sincerely</p>\r\n" + 
		"<p>Electra Team</p>\r\n" + 
		"\r\n" + 
		"</body>\r\n" + 
		"</html>";
		return mailMessage;
	}
	
	*//**
	 * use to get all subscription plan list.
	 * 
	 * @return
	 * @throws JsonProcessingException
	 *//*
	@RequestMapping(value = "/getSubscriptionPlanList", method = RequestMethod.GET)
	public SubscriptionPlanResponse getSubscriptionPlanList() throws JsonProcessingException {
			
        LOG.info("Request received for get subscription plan list ");

        SubscriptionPlanResponse subscriptionPlanResponse = electraAppService.getSubscriptionPlanList();
		
		 LOG.info("Response after getting subscription plan list");
		

		return subscriptionPlanResponse;
	}
	
}
*/