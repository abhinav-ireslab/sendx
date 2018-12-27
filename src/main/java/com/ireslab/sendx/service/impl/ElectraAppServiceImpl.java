/*package com.ireslab.sendx.service.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ireslab.sendx.electra.Status;
import com.ireslab.sendx.electra.model.ClientProfile;
import com.ireslab.sendx.electra.model.ClientRegistrationRequest;
import com.ireslab.sendx.electra.model.ClientRegistrationResponse;
import com.ireslab.sendx.electra.model.ClientSubscriptionRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionResponse;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateResponse;
import com.ireslab.sendx.entity.Account;
import com.ireslab.sendx.entity.OAuthAccessToken;
import com.ireslab.sendx.entity.OAuthRefreshToken;
import com.ireslab.sendx.model.AccountVerificationResponse;
import com.ireslab.sendx.model.CompanyCodeResponse;
import com.ireslab.sendx.model.SignupRequest;
import com.ireslab.sendx.model.SignupResponse;
import com.ireslab.sendx.model.SubscriptionPlanResponse;
import com.ireslab.sendx.model.UserProfile;
import com.ireslab.sendx.repository.AccountRepository;
import com.ireslab.sendx.repository.OAuthAccessTokenRepository;
import com.ireslab.sendx.repository.OAuthRefreshTokenRepository;
import com.ireslab.sendx.service.ElectraAppService;
import com.ireslab.sendx.service.SignupService;
import com.ireslab.sendx.service.TransactionalApiService;
import com.ireslab.sendx.util.AppStatusCodes;
import com.ireslab.sendx.util.PropConstants;

@Service
public class ElectraAppServiceImpl implements ElectraAppService {

	private static final Logger LOG = LoggerFactory.getLogger(ElectraAppServiceImpl.class);

	@Autowired
	private TransactionalApiService transactionalApiService;

	@Autowired
	private AccountRepository accountRepo;

	@Autowired
	private OAuthAccessTokenRepository accessTokenRepo;

	@Autowired
	private OAuthRefreshTokenRepository refreshTokenRepo;

	@Autowired
	private MessageSource messageSource;

	

	@Autowired
	private SignupService signupService;

	@Override
	public CompanyCodeResponse generateCompanyCode() {

		// String uniqueCode = UUID.randomUUID().toString().toUpperCase();
		// String companyCode = uniqueCode.substring(0, 5);

		CompanyCodeResponse companyCodeResponse = transactionalApiService.invokeCompanyCodeAPI();
		return companyCodeResponse;
	}

	@Override
	public AccountVerificationResponse verifyAccountByMobileNo(Long mobileNumber, String countryDialCode,
			String companyCode) {
		AccountVerificationResponse accVerificationResponse = null;
		Account account = accountRepo.findByMobileNumberAndCountry_CountryDialCode(BigInteger.valueOf(mobileNumber),
				countryDialCode);

		boolean isCompanyCodeExist = false;
		ClientProfile clientByCompanyCode = transactionalApiService.invokeClientByCompanyCodeAPI(companyCode);
		if (clientByCompanyCode != null) {
			isCompanyCodeExist = true;
		}

		if (account != null) {

			LOG.debug("Account already exists for mobileNumber - " + countryDialCode + mobileNumber
					+ ", getting the account status from electra based on userCorrelationId - "
					+ account.getUserCorrelationId());

			UserProfile userProfile = transactionalApiService.invokeUserProfileAPI(account.getUserCorrelationId());
			LOG.debug("Account status from electra - " + userProfile.getAccountStatus());

			if (userProfile.getAccountStatus().equals(Status.TERMINATED)) {

				String username = countryDialCode + "_" + mobileNumber;

				LOG.debug("Account for mobileNumber - " + username
						+ " is Terminated and is available for re-registration");

				OAuthAccessToken authAccessToken = accessTokenRepo.findByUserName(username);

				if (authAccessToken != null) {
					OAuthRefreshToken authRefreshToken = refreshTokenRepo
							.findByTokenId(authAccessToken.getRefresh_token());

					if (authRefreshToken != null) {
						LOG.debug("Deleting refresh token based on refresh token - "
								+ authAccessToken.getRefresh_token());
						refreshTokenRepo.delete(authRefreshToken);
					}
					LOG.debug("Deleting access token for username - " + authAccessToken.getUserName());
					accessTokenRepo.delete(authAccessToken);
				}

			} else {
				LOG.debug("Account with Mobile Number : " + countryDialCode + mobileNumber + " already exists");
				return new AccountVerificationResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS,
						getMessage(PropConstants.ACCOUNT_EXISTS), true, isCompanyCodeExist);
			}
		}

		LOG.debug("Account with Mobile Number : " + countryDialCode + mobileNumber + " not exists and is available");
		accVerificationResponse = new AccountVerificationResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS,
				getMessage(PropConstants.ACCOUNT_NOT_EXISTS), false, isCompanyCodeExist);

		return accVerificationResponse;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SignupResponse registerClientUser(SignupRequest signupRequest) {

		SignupResponse signupResponse = new SignupResponse();

		// List<ClientProfile> accountByCompanyCode =
		// accountRepo.findByCompanyCode(signupRequest.getCompanyCode());
		ClientProfile clientByCompanyCode = transactionalApiService
				.invokeClientByCompanyCodeAPI(signupRequest.getCompanyCode());

		// Company Code exists - Register as user
		if (clientByCompanyCode != null) {

			LOG.debug("Company Code Exist, Registering as User ");
			
			
			
			ClientSubscriptionUpdateRequest clientSubscriptionUpdateRequest = new ClientSubscriptionUpdateRequest();
			clientSubscriptionUpdateRequest.setClientCorrelationId(clientByCompanyCode.getClientCorrelationId());
			clientSubscriptionUpdateRequest.setEmail(signupRequest.getEmailAddress());
			clientSubscriptionUpdateRequest.setMobileNo(signupRequest.getMobileNumber()+"");
			ClientSubscriptionUpdateResponse clientSubscriptionUpdateResponse = transactionalApiService.updateClientSubscriptionPlan(clientSubscriptionUpdateRequest);
			if(clientSubscriptionUpdateResponse.getErrors().size()>0) {
				
				signupResponse.setCode(101);
				signupResponse.setMessage("Email already exist !");
				signupResponse.setStatus(HttpStatus.OK.value());
			}
			else {
				
			
			signupResponse = signupService.registerAccount(signupRequest);

			String companyCode = clientByCompanyCode.getCompanyCode();
			String userCorrelationId = UUID.randomUUID().toString();
			//signupRequest.setCorrelationId(clientByCompanyCode.getClientCorrelationId());
			LOG.debug("Sendx Company Code  " + companyCode);
			signupRequest.setCompanyCode(companyCode);
			signupRequest.setUserCorrelationId(userCorrelationId);
			signupRequest.setIsKycConfigure(false);
			signupRequest.setClientCorrelationId(clientByCompanyCode.getClientCorrelationId());
			transactionalApiService.invokeUserClientEntryOnboardingApi(signupRequest); // client user table
			
			}
			
			

			// Company Code not exits - Register as Company/Client
		} else {

			LOG.debug("Company Code not Exist, Registering as Client ");

			*//**
			 * Registering as client
			 *//*
			ClientRegistrationRequest clientRegistrationRequest = new ClientRegistrationRequest();

			List<ClientProfile> clientProfileList = new ArrayList<>();
			ClientProfile clientProfile = new ClientProfile();

			String clientCorrelationId = UUID.randomUUID().toString();

			clientProfile.setClientCorrelationId(clientCorrelationId);
			clientProfile.setClientName(signupRequest.getCompanyName());
			clientProfile.setUserName(signupRequest.getFirstName() + " " + signupRequest.getLastName());
			clientProfile.setPassword(signupRequest.getPassword());
			clientProfile.setEmailAddress(signupRequest.getEmailAddress());
			clientProfile.setContactNumber1(signupRequest.getMobileNumber() + "");
			clientProfile.setDescription("Electra App");
			clientProfile.setClientStatus("ACTIVE");
			clientProfile.setAccount_type(0+"");
			clientProfile.setCompanyCode(signupRequest.getCompanyCode());

			clientProfileList.add(clientProfile);
			clientRegistrationRequest.setClientProfile(clientProfileList);

			ClientRegistrationResponse clientResponse = (ClientRegistrationResponse) transactionalApiService
					.invokeUserClientOnboardingApi(clientRegistrationRequest); // client table

			

			*//**
			 * Registering client as user to SGT
			 *//*
			signupResponse = signupService.registerAccount(signupRequest);
		}

		return signupResponse;
	}
	
	@Override
	@Transactional(rollbackFor = Exception.class)
	public ClientSubscriptionResponse clientSubscriptionPlan(ClientSubscriptionRequest clientSubscriptionRequest) {
		ClientSubscriptionResponse clientSubscriptionResponse = null;
		
		
		clientSubscriptionResponse = (ClientSubscriptionResponse) transactionalApiService.invokeSubscriptionPlanApi(clientSubscriptionRequest);
		
		return clientSubscriptionResponse;
	}

	@Override
	public SubscriptionPlanResponse getSubscriptionPlanList() {
		LOG.debug("Request recieved for subscription plan list in service ");
		
		SubscriptionPlanResponse subscriptionPlanResponse = transactionalApiService.invokeSubscriptionPlanListApi();
		return subscriptionPlanResponse;
	}
	
	@Override
	public ClientSubscriptionResponse getClientSubscriptionPlan(ClientSubscriptionRequest clientSubscriptionRequest) {
     LOG.debug("Request recieved for get client subscription plan list in service ");
		
     ClientSubscriptionResponse clientSubscriptionResponse = transactionalApiService.invokeClientSubscriptionPlanList(clientSubscriptionRequest);
		return clientSubscriptionResponse;
	}
	
	
	@Override
	public ClientSubscriptionResponse isClientORNot(ClientSubscriptionRequest clientSubscriptionRequest) {
		LOG.debug("Request recieved for check exist as client or not in service ");
		
	     ClientSubscriptionResponse clientSubscriptionResponse = transactionalApiService.isClientORNot(clientSubscriptionRequest);
			return clientSubscriptionResponse;
	}

	
	*//**
	 * @param key
	 * @return
	 *//*
	private String getMessage(String key) {
		return messageSource.getMessage(key, null, Locale.getDefault());
	}

	

	
	
	

}
*/