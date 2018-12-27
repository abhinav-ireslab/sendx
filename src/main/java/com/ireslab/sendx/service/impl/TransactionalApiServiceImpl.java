package com.ireslab.sendx.service.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ireslab.sendx.electra.ElectraApiConfig;
import com.ireslab.sendx.electra.dto.CashOutDto;
import com.ireslab.sendx.electra.model.ClientInfoRequest;
import com.ireslab.sendx.electra.model.ClientInfoResponse;
import com.ireslab.sendx.electra.model.ClientRegistrationRequest;
import com.ireslab.sendx.electra.model.ClientRegistrationResponse;
import com.ireslab.sendx.electra.model.ClientSubscriptionRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionResponse;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateResponse;
import com.ireslab.sendx.electra.model.Error;
import com.ireslab.sendx.electra.model.ExchangeRequest;
import com.ireslab.sendx.electra.model.ExchangeResponse;
import com.ireslab.sendx.electra.model.GenericRequest;
import com.ireslab.sendx.electra.model.GenericResponse;
import com.ireslab.sendx.electra.model.NotificationRequest;
import com.ireslab.sendx.electra.model.NotificationResponse;
import com.ireslab.sendx.electra.model.OAuth2Dto;
import com.ireslab.sendx.electra.model.PaymentRequest;
import com.ireslab.sendx.electra.model.PaymentResponse;
import com.ireslab.sendx.electra.model.ProductAvailabilityRequest;
import com.ireslab.sendx.electra.model.ProductAvailabilityResponse;
import com.ireslab.sendx.electra.model.ProductRequest;
import com.ireslab.sendx.electra.model.ProductResponse;
import com.ireslab.sendx.electra.model.SendxElectraRequest;
import com.ireslab.sendx.electra.model.SendxElectraResponse;
import com.ireslab.sendx.electra.model.TokenLifecycleManagementRequest;
import com.ireslab.sendx.electra.model.TokenLifecycleManagementResponse;
import com.ireslab.sendx.electra.model.TokenTransferRequest;
import com.ireslab.sendx.electra.model.TokenTransferResponse;
import com.ireslab.sendx.electra.model.TransactionLimitResponse;
import com.ireslab.sendx.electra.model.TransactionPurposeResponse;
import com.ireslab.sendx.electra.model.UserAgentRegistrationRequest;
import com.ireslab.sendx.electra.model.UserAgentRegistrationResponse;
import com.ireslab.sendx.electra.model.UserAgentRequest;
import com.ireslab.sendx.electra.model.UserProfile;
import com.ireslab.sendx.electra.model.UserProfileResponse;
import com.ireslab.sendx.electra.model.UserRegistrationRequest;
import com.ireslab.sendx.electra.model.UserRegistrationResponse;
import com.ireslab.sendx.entity.Account;
import com.ireslab.sendx.model.AgentRequest;
import com.ireslab.sendx.model.AgentRequestBody;
import com.ireslab.sendx.model.AgentResponse;
import com.ireslab.sendx.model.CashOutRequest;
import com.ireslab.sendx.model.LoadTokensRequest;
import com.ireslab.sendx.model.SendTokensRequest;
import com.ireslab.sendx.model.SignupRequest;
import com.ireslab.sendx.model.UserAgentResponse;
import com.ireslab.sendx.repository.AccountRepository;
import com.ireslab.sendx.service.TransactionalApiService;
import com.ireslab.sendx.util.AppStatusCodes;
import com.ireslab.sendx.util.Constants;

/**
 * @author iRESlab
 *
 */
@Service
public class TransactionalApiServiceImpl implements TransactionalApiService {

	private static Logger LOG = LoggerFactory.getLogger(TransactionalApiServiceImpl.class);

	private static final String BLANK = "";
	public static final String FORMAT_SPECIFIER = "%s";

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String ACCEPT_HEADER = "Accept";
	private static final String CONTENT_TYPE_HEADER = "Content-Type";
	private static final Integer ACCESS_TOKEN_RETRY_LIMIT = 3;

	private static String accessToken = null;
	private static boolean isAccessTokenExpired = false;
	private static int accessTokenRetryCount = 0;
	private static HttpHeaders httpHeaders = null;

	@Autowired
	public ElectraApiConfig electraApiConfig;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private ModelMapper modelMapper;

	@Autowired
	private ObjectWriter objectWriter;
	
	@Autowired
	private AccountRepository accountRepository;

	
	
	private String retrieveApiAccessToken() {

		if (accessToken == null || isAccessTokenExpired) {

			LOG.info("Requesting access token for Electra API authorization . . . .");
			String endpointUrl = String.format(electraApiConfig.getAuthTokenEndpointUrl(),
					electraApiConfig.getGrantType());

			OAuth2Dto auth2Dto = (OAuth2Dto) invokeApi(endpointUrl, HttpMethod.POST, OAuth2Dto.class,
					new GenericRequest(),false, true, false,false);

			if (!auth2Dto.getErrors().isEmpty()) {

				Error error = auth2Dto.getErrors().get(0);
				LOG.error("Error occurred while getting API Access Token from Electra - \n\tError : "
						+ error.getMessage() + "\n\tError Description : " + error.getDescription());
				return null;
			}

			isAccessTokenExpired = false;
			accessToken = auth2Dto.getAccess_token();
		}

		LOG.debug("Access token for accessing Electra APIs - " + accessToken);
		return accessToken;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ireslab.sendx.service.TransactionalApiService#createStellarAccount(
	 * com.ireslab.sendx.model.SignupRequest)
	 */
	@Override
	public List<com.ireslab.sendx.electra.model.UserProfile> invokeUserOnboardingApi(SignupRequest signupRequest) {

		accessTokenRetryCount = 0;

		List<com.ireslab.sendx.electra.model.UserProfile> userProfiles = new ArrayList<>();
		String userOnboardingEndpointUrl=null;
		if (signupRequest.getCorrelationId()!=null) {
			userOnboardingEndpointUrl = String.format(electraApiConfig.getClientUserOnboardingApiEndpointUrl(),
					signupRequest.getCorrelationId(),FORMAT_SPECIFIER);
		} else {
		userOnboardingEndpointUrl = String.format(electraApiConfig.getUserOnboardingApiEndpointUrl(),FORMAT_SPECIFIER);
		}
		
		

		/* Creating Electra User Onboarding JSON Request */
		List<UserProfile> users = new ArrayList<>();

		UserProfile profile = new UserProfile().setFirstName(signupRequest.getFirstName())
				.setLastName(signupRequest.getLastName())
				.setMobileNumber(signupRequest.getCountryDialCode() + signupRequest.getMobileNumber())
				.setEmailAddress(signupRequest.getEmailAddress())
				.setCountryDialCode(signupRequest.getCountryDialCode()).setClientCorrelationId(signupRequest.getClientCorrelationId())
				.setBusinessId(signupRequest.getBusinessId()).setPostalCode(signupRequest.getPostalCode()).setDob(signupRequest.getDob())
				.setGender(signupRequest.getGender()).setScanDocumentType(signupRequest.getScanDocumentType()).setScanDocumentId(signupRequest.getScanDocumentId())
				.setScanDocumentFrontPage(signupRequest.getScanDocumentFrontPage()).setScanDocumentBackPage(signupRequest.getScanDocumentBackPage())
				.setProfileImageValue(signupRequest.getProfileImageValue()).setResidentialAddress(signupRequest.getResidentialAddress()).setKycConfigure(signupRequest.getIsKycConfigure())
				.setBusinessLat(signupRequest.getBusinessLat()).setBusinessLong(signupRequest.getBusinessLong()).setIdproofImage(signupRequest.getIdProofImageValue()).setUniqueCode(signupRequest.getUniqueCode());
		
		users.add(profile);

		profile.setUserCorrelationId(signupRequest.getUserCorrelationId());
		UserRegistrationRequest electraUserRegistrationRequest = new UserRegistrationRequest().setUsers(users);

		
		
		boolean flag =false;
		
		if(signupRequest.getCorrelationId()!=null) {
			flag=true;
		}

		UserRegistrationResponse userRegistrationResponse = (UserRegistrationResponse) invokeApi(
				userOnboardingEndpointUrl, HttpMethod.POST, UserRegistrationResponse.class,
				electraUserRegistrationRequest,false, false, false,flag);

		// Account creation failed
		if (userRegistrationResponse == null
				|| userRegistrationResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (userRegistrationResponse != null && CollectionUtils.isNotEmpty(userRegistrationResponse.getErrors())) {
				List<Error> errors = userRegistrationResponse.getErrors();
				LOG.error("Account creation on Electra failed | Error Code - " + userRegistrationResponse.getCode()
						+ ", Error Message - " + userRegistrationResponse.getMessage() + ", Errors - " + errors);
			}
			return userProfiles;
		}

		
		
		UserProfile userProElectra = userRegistrationResponse.getUsers().get(0);

		UserProfile userPro = new UserProfile();
		userPro.setBusinessId(userProElectra.getBusinessId());
		userPro.setBusinessLat(userProElectra.getBusinessLat());
		userPro.setBusinessLong(userProElectra.getBusinessLong());
		userPro.setClientCorrelationId(userProElectra.getClientCorrelationId());
		userPro.setCompanyCode(userProElectra.getCompanyCode());
		userPro.setCompanyName(userProElectra.getCompanyName());
		userPro.setDob(userProElectra.getDob());
		userPro.setEkycEkybApproved(userProElectra.isEkycEkybApproved());
		userPro.setEmailAddress(userProElectra.getEmailAddress());
		userPro.setFirstName(userProElectra.getFirstName());
		userPro.setGender(userProElectra.getGender());
		userPro.setLastName(userProElectra.getLastName());
		userPro.setMobileNumber(userProElectra.getMobileNumber());
		userPro.setPostalCode(userProElectra.getPostalCode());
		userPro.setRegistered(userProElectra.isRegistered());
		userPro.setStatus(userProElectra.getStatus());
		userPro.setUserCorrelationId(userProElectra.getUserCorrelationId());
		userPro.setResidentialAddress(userProElectra.getResidentialAddress());

		userProfiles.add(userPro);
	
		return userProfiles;
	}
	
	
	
	@Override
	public UserRegistrationResponse updateUser(com.ireslab.sendx.model.UserProfile userProfile, String correlationId) {
         UserRegistrationRequest userRegistrationRequest = null;

		
		
		
		
		String endpointUrl = String.format(electraApiConfig.getUserUpdationApiEndpointUrl(),
				correlationId, FORMAT_SPECIFIER);

		List<UserProfile> users = new ArrayList<>();
		
		UserProfile profile = new UserProfile().setFirstName(userProfile.getFirstName())
				.setLastName(userProfile.getLastName());
				
		users.add(profile);
		
		
		userRegistrationRequest = new UserRegistrationRequest().setUsers(users);
		
		LOG.info("API EndpointUrl to update user :" + endpointUrl);

		UserRegistrationResponse clientRegistrationResponse = (UserRegistrationResponse) invokeApi(endpointUrl,
				HttpMethod.PUT, UserRegistrationResponse.class, userRegistrationRequest,false, false, false,false);
		return clientRegistrationResponse;
	}
	
	

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeAgentOnboardingApi(com.ireslab.sendx.model.AgentRequest, java.lang.String)
	 */
	@Override
	public com.ireslab.sendx.model.AgentResponse invokeAgentOnboardingApi(AgentRequest agentRequest, String correlationId) {

		accessTokenRetryCount = 0;

		String userAgentEndpointUrl = String.format(electraApiConfig.getUserAgentApiEndpointUrl(), FORMAT_SPECIFIER);

		/* Creating Electra User Onboarding JSON Request */
		List<AgentRequest> agents = new ArrayList<>();

		AgentRequest agent = new AgentRequest().setAgentMobNo(agentRequest.getAgentMobNo())
				.setBusinessAdd(agentRequest.getBusinessAdd()).setBusinessId(agentRequest.getBusinessId())
				.setBusinessLat(agentRequest.getBusinessLat()).setBusinessLong(agentRequest.getBusinessLong())
				.setCountryDialCode(agentRequest.getCountryDialCode()).setFiatCurrency(agentRequest.getFiatCurrency())
				.setIdProofImageValue(agentRequest.getIdProofImageValue())
				.setCryptoCurrency(agentRequest.getCryptoCurrency());
		agents.add(agent);
		
		java.lang.reflect.Type targetListType = new TypeToken<List<UserAgentRequest>>() {
		}.getType();

		List<UserAgentRequest> profileList = modelMapper.map(agents, targetListType);

		agent.setCorrelationId(correlationId);
		UserAgentRegistrationRequest electraAgentRegistrationRequest = new UserAgentRegistrationRequest()
				.setAgents(profileList);

		LOG.debug("Invoking Electra User Agent Onboarding API with Request - "
				+ electraAgentRegistrationRequest.toString());

		UserAgentRegistrationResponse userAgentRegistrationResponse = (UserAgentRegistrationResponse) invokeApi(
				userAgentEndpointUrl, HttpMethod.POST, UserAgentRegistrationResponse.class,
				electraAgentRegistrationRequest,false, false, false,false);

		// Account creation failed
		if (userAgentRegistrationResponse == null
				|| userAgentRegistrationResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (userAgentRegistrationResponse != null
					&& CollectionUtils.isNotEmpty(userAgentRegistrationResponse.getErrors())) {

				List<Error> errors = userAgentRegistrationResponse.getErrors();
				LOG.error("Account creation on Electra failed | Error Code - " + userAgentRegistrationResponse.getCode()
						+ ", Error Message - " + userAgentRegistrationResponse.getMessage() + ", Errors - " + errors);
			}
		}

		return new AgentResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS,
				"Your request to become agent is registered and sent for approval.");
	}

	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeLoadTokensAPI(com.ireslab.sendx.model.LoadTokensRequest)
	 */
	@Override
	public String invokeLoadTokensAPI(LoadTokensRequest loadTokensRequest) {

		accessTokenRetryCount = 0;

		String loadTokensEndpointUrl = String.format(electraApiConfig.getLoadTokensApiEndpointUrl(),
				loadTokensRequest.getUserCorrelationId(), FORMAT_SPECIFIER);

		TokenLifecycleManagementRequest tokenLifecycleManagementRequest = new TokenLifecycleManagementRequest();
		tokenLifecycleManagementRequest.setNoOfTokens(loadTokensRequest.getNoOfTokens());
		tokenLifecycleManagementRequest.setTokenCorrelationId(electraApiConfig.getTokenCorrelationId());

		try {
			LOG.debug("Invoking Electra Load Token API with Request - "
					+ objectWriter.writeValueAsString(tokenLifecycleManagementRequest));
		} catch (Exception exp) {
			
		}

		TokenLifecycleManagementResponse tokenLifecycleManagementResponse = (TokenLifecycleManagementResponse) invokeApi(
				loadTokensEndpointUrl, HttpMethod.POST, TokenLifecycleManagementResponse.class,
				tokenLifecycleManagementRequest,false, false, false,false);

		// Account creation failed
		if(tokenLifecycleManagementResponse != null ) {
			if(tokenLifecycleManagementResponse.getErrors().size()>0) {
				
				Error error = tokenLifecycleManagementResponse.getErrors().get(0);
				if(error.getCode() == 4000) {
					return "op_line_full";
				}
				else {
					return null;
				}
			}
		}
		
		
		if (tokenLifecycleManagementResponse == null
				|| tokenLifecycleManagementResponse.getStatus().intValue() != HttpStatus.OK.value()) {

			if (tokenLifecycleManagementResponse != null
					&& CollectionUtils.isNotEmpty(tokenLifecycleManagementResponse.getErrors())) {
				List<Error> errors = tokenLifecycleManagementResponse.getErrors();
				LOG.error("Load Tokens on Electra failed | Error Code - " + tokenLifecycleManagementResponse.getCode()
						+ ", Error Message - " + tokenLifecycleManagementResponse.getMessage() + ", Errors - "
						+ errors);
			}
			return null;
		}
		return tokenLifecycleManagementResponse.getAccountBalance();
	}

	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeTransferTokensAPI(com.ireslab.sendx.model.SendTokensRequest)
	 */
	@Override
	public String invokeTransferTokensAPI(SendTokensRequest sendTokensRequest) {

		accessTokenRetryCount = 0;

		String loadTokensEndpointUrl = String.format(electraApiConfig.getTransferTokensApiEndpointUrl(),
				sendTokensRequest.getSenderCorrelationId(), FORMAT_SPECIFIER);

		TokenLifecycleManagementRequest tokenLifecycleManagementRequest = new TokenLifecycleManagementRequest();
		tokenLifecycleManagementRequest.setNoOfTokens(sendTokensRequest.getNoOfTokens());
		tokenLifecycleManagementRequest.setTokenCorrelationId(electraApiConfig.getTokenCorrelationId());
		tokenLifecycleManagementRequest.setBeneficiaryCorrelationId(sendTokensRequest.getBeneficiaryCorrelationId());
		tokenLifecycleManagementRequest.setTransactionPurpose(sendTokensRequest.getTransactionPurpose());
		tokenLifecycleManagementRequest.setIsFee(false);

		try {
			LOG.debug("Invoking Electra Transfer Token API with Request - "
					+ objectWriter.writeValueAsString(tokenLifecycleManagementRequest));
		} catch (Exception exp) {
			
		}

		TokenLifecycleManagementResponse tokenLifecycleManagementResponse = (TokenLifecycleManagementResponse) invokeApi(
				loadTokensEndpointUrl, HttpMethod.POST, TokenLifecycleManagementResponse.class,
				tokenLifecycleManagementRequest,false, false, false,false);

		// Account creation failed
		if (tokenLifecycleManagementResponse == null
				|| tokenLifecycleManagementResponse.getStatus().intValue() != HttpStatus.OK.value()) {

			if (tokenLifecycleManagementResponse != null
					&& CollectionUtils.isNotEmpty(tokenLifecycleManagementResponse.getErrors())) {
				List<Error> errors = tokenLifecycleManagementResponse.getErrors();
				LOG.error("Transfer Tokens on Electra failed | Error Code - "
						+ tokenLifecycleManagementResponse.getCode() + ", Error Message - "
						+ tokenLifecycleManagementResponse.getMessage() + ", Errors - " + errors);
			}
			return null;
		}
		return tokenLifecycleManagementResponse.getAccountBalance();
	}

	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeCashoutTokensAPI(com.ireslab.sendx.model.CashOutRequest)
	 */
	@Override
	public String invokeCashoutTokensAPI(CashOutRequest cashOutTokensRequest) {

		accessTokenRetryCount = 0;

		String cashoutTokensEndpointUrl = String.format(electraApiConfig.getCashoutTokensApiEndpointUrl(),
				cashOutTokensRequest.getUserCorrelationId(), FORMAT_SPECIFIER);

		TokenLifecycleManagementRequest tokenLifecycleManagementRequest = new TokenLifecycleManagementRequest();
		tokenLifecycleManagementRequest.setNoOfTokens(cashOutTokensRequest.getNoOfTokens());
		tokenLifecycleManagementRequest.setTokenCorrelationId(electraApiConfig.getTokenCorrelationId());
		tokenLifecycleManagementRequest.setUserCorrelationId(cashOutTokensRequest.getUserCorrelationId());

		
		CashOutDto cashOutDto = new CashOutDto();
		cashOutDto.setUserCorrelationId(cashOutTokensRequest.getUserCorrelationId());
		cashOutDto.setNoOfTokens(cashOutTokensRequest.getNoOfTokens());
		cashOutDto.setFee(cashOutTokensRequest.getFee());
		cashOutDto.setInstitutionName(cashOutTokensRequest.getInstitutionName());
		cashOutDto.setInstitutionAccountNumber(cashOutTokensRequest.getInstitutionAccountNumber());
		tokenLifecycleManagementRequest.setCashOutDto(cashOutDto);
		
		
		try {
			LOG.debug("Invoking Electra Transfer Token API with Request - "
					+ objectWriter.writeValueAsString(tokenLifecycleManagementRequest));
		} catch (Exception exp) {
			
		}

		TokenLifecycleManagementResponse tokenLifecycleManagementResponse = (TokenLifecycleManagementResponse) invokeApi(
				cashoutTokensEndpointUrl, HttpMethod.POST, TokenLifecycleManagementResponse.class,
				tokenLifecycleManagementRequest,false, false, false,false);

		// Account creation failed
		if (tokenLifecycleManagementResponse == null
				|| tokenLifecycleManagementResponse.getStatus().intValue() != HttpStatus.OK.value()) {

			if (tokenLifecycleManagementResponse != null
					&& CollectionUtils.isNotEmpty(tokenLifecycleManagementResponse.getErrors())) {
				List<Error> errors = tokenLifecycleManagementResponse.getErrors();
				LOG.error("Transfer Tokens on Electra failed | Error Code - "
						+ tokenLifecycleManagementResponse.getCode() + ", Error Message - "
						+ tokenLifecycleManagementResponse.getMessage() + ", Errors - " + errors);
			}
			return null;
		}
		return tokenLifecycleManagementResponse.getAccountBalance();
	}

	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeUserProfileAPI(java.lang.String)
	 */
	@Override
	public com.ireslab.sendx.model.UserProfile invokeUserProfileAPI(String userCorrelationId) {
		Account account = accountRepository.findByUserCorrelationId(userCorrelationId);
		accessTokenRetryCount = 0;
		String userProfileEndpointUrl = String.format(electraApiConfig.getUserProfileApiEndpointUrl(),
				userCorrelationId, FORMAT_SPECIFIER);

		UserProfileResponse userProfileResponse = (UserProfileResponse) invokeApi(userProfileEndpointUrl,
				HttpMethod.GET, UserProfileResponse.class, null, false, false, false,false);

		if (userProfileResponse == null || userProfileResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (userProfileResponse != null && CollectionUtils.isNotEmpty(userProfileResponse.getErrors())) {
				List<Error> errors = userProfileResponse.getErrors();
				LOG.error("Get profile API request on Electra failed | Error Code - " + userProfileResponse.getCode()
						+ ", Error Message - " + userProfileResponse.getMessage() + ", Errors - " + errors);
			}
			return null;
		}

		UserProfile electraUserProfile = userProfileResponse.getUserProfile();
		com.ireslab.sendx.model.UserProfile userProfileModel = new com.ireslab.sendx.model.UserProfile();

		userProfileModel.setAccountStatus(electraUserProfile.getStatus());
		userProfileModel.setEkycEkybApproved(electraUserProfile.isEkycEkybApproved());
		userProfileModel.setCurrencySymbol(electraUserProfile.getCurrencySymbol());
		userProfileModel.setIso4217CurrencyAlphabeticCode(electraUserProfile.getIso4217CurrencyAlphabeticCode());
		userProfileModel.setCurrencySymbol(electraUserProfile.getCurrencySymbol());
		electraUserProfile.getAssetDetails().forEach(accountBalance -> {
			
			
			if (accountBalance.getAssetCode().equalsIgnoreCase(account.getProfile().getCountry().getIso4217CurrencyAlphabeticCode())) {
				userProfileModel.setAccountBalance(accountBalance.getAssetQuantity());
			}
		});

		if (userProfileModel.getAccountBalance() == null) {
			userProfileModel.setAccountBalance(Constants.ZERO_BALANCE);
		}
		return userProfileModel;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeGetAgentAPI(com.ireslab.sendx.model.AgentRequestBody)
	 */
	@Override
	public AgentResponse invokeGetAgentAPI(AgentRequestBody agentRequestBody) {
		accessTokenRetryCount = 0;
		String getAgentEndpointUrl = String.format(electraApiConfig.getGetAgentApiEndpointUrl(), FORMAT_SPECIFIER);

		List<UserAgentRequest> agents = new ArrayList<>();


		
		UserAgentRequest agent = new UserAgentRequest().setAgentMobNo(new Long(agentRequestBody.getMobileNumber() + ""))
		.setCountryDialCode(agentRequestBody.getCountryDialCode());
		agents.add(agent);

		UserAgentRegistrationRequest electraAgentRegistrationRequest = new UserAgentRegistrationRequest()
				.setAgents(agents);
		;

		try {
			LOG.debug("Invoking Electra get agent API with Request - "
					+ objectWriter.writeValueAsString(electraAgentRegistrationRequest));
		} catch (Exception exp) {
			
		}

		UserAgentResponse userAgentResponse = (UserAgentResponse) invokeApi(getAgentEndpointUrl, HttpMethod.POST,
				UserAgentResponse.class, electraAgentRegistrationRequest, false, false, false,false);

		if (userAgentResponse == null || userAgentResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (userAgentResponse != null && CollectionUtils.isNotEmpty(userAgentResponse.getErrors())) {
				List<Error> errors = userAgentResponse.getErrors();
				LOG.error("Get agent API request on Electra failed | Error Code - " + userAgentResponse.getCode()
						+ ", Error Message - " + userAgentResponse.getMessage() + ", Errors - " + errors);
			}
			return null;
		}

		AgentResponse agentResponse = userAgentResponse.getAgentResponse();
		

		return agentResponse;
	}

	/**
	 * @param endpointUrl
	 * @param httpMethod
	 * @param responseClass
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T invokeApi(String endpointUrl, HttpMethod httpMethod, Class<T> responseClass,
			GenericRequest genericRequest,boolean isCustomRequest, boolean isAuthRequest,  boolean isDirectRequest,boolean isClientUserRequest) {

		GenericResponse genericResponse = null;
		HttpEntity<?> apiResponse = null;
		String apiEndpointUrl = null;

		if (isAuthRequest) {
			apiEndpointUrl = String.format(electraApiConfig.getBaseUrl(), BLANK, BLANK) + endpointUrl;
		} else if (isDirectRequest) {
			apiEndpointUrl = String.format(electraApiConfig.getBaseUrl(), electraApiConfig.getApiVersion(), "")
					+ String.format(endpointUrl, retrieveApiAccessToken());
		}else if (isClientUserRequest) {
			
			apiEndpointUrl = String.format(electraApiConfig.getClientUserBaseUrl())
					+ String.format(endpointUrl, retrieveApiAccessToken());
		}
		else if (isCustomRequest) {
			apiEndpointUrl = String.format(endpointUrl, retrieveApiAccessToken());

		}else {
			apiEndpointUrl = String.format(electraApiConfig.getBaseUrl(), electraApiConfig.getApiVersion(),
					electraApiConfig.getClientCorrelationId()) + String.format(endpointUrl, retrieveApiAccessToken());
			
			
		}
		
		LOG.info("Client User Endpoint Onboarding Url - "+apiEndpointUrl);

		if (httpHeaders == null) {
			String plainCredentials = electraApiConfig.getClientId() + ":" + electraApiConfig.getClientSecret();
			String base64ClientCredentials = new String(Base64Utils.encodeToString(plainCredentials.getBytes()));

			httpHeaders = new HttpHeaders();
			httpHeaders.add(AUTHORIZATION_HEADER, electraApiConfig.getHeaderAuthorization() + base64ClientCredentials);
			httpHeaders.set(ACCEPT_HEADER, electraApiConfig.getHeaderAccept());
			httpHeaders.set(CONTENT_TYPE_HEADER, electraApiConfig.getHeaderContentType());
		}

		HttpEntity<?> httpEntity = null;

		if (genericRequest == null) {
			httpEntity = new HttpEntity<>(httpHeaders);
		} else {
			httpEntity = new HttpEntity<>(genericRequest, httpHeaders);
		}

		try {
			apiResponse = restTemplate.exchange(
					UriComponentsBuilder.fromHttpUrl(apiEndpointUrl).build().encode().toUri(), httpMethod, httpEntity,
					responseClass);

			genericResponse = (GenericResponse) apiResponse.getBody();

			if (isAuthRequest) {
				isAccessTokenExpired = false;
			}

		} catch (HttpClientErrorException clientErrorException) {

			LOG.error("Error occurred while calling Electra API - " + "\n\tHttpStatus : "
					+ clientErrorException.getStatusCode().value() + "\n\tResponseBody : "
					+ clientErrorException.getResponseBodyAsString());

			if (clientErrorException.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {

				// Unauthorized - Access Token invalid or expired
				LOG.info("Unauthorized request | Access Token invalid or expired");
				isAccessTokenExpired = true;

			} else {

				// Error occurred due to reason other than Access Token
				OAuth2Dto auth2Dto = null;
				try {
					auth2Dto = new ObjectMapper().readValue(clientErrorException.getResponseBodyAsString(),
							OAuth2Dto.class);

					genericResponse = (GenericResponse) Class.forName(responseClass.getName()).newInstance();
					genericResponse.setStatus(clientErrorException.getStatusCode().value());
					genericResponse.getErrors().add(new Error(auth2Dto.getError(), auth2Dto.getError_description()));

				} catch (Exception exp) {
					exp.printStackTrace();
				}
			}

			if (isAccessTokenExpired && ++accessTokenRetryCount < ACCESS_TOKEN_RETRY_LIMIT) {
				genericResponse = (GenericResponse)invokeApi(endpointUrl, httpMethod, responseClass, genericRequest,isCustomRequest, isAuthRequest, isDirectRequest,isClientUserRequest);
			}

		}
		return (T) genericResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getAllExchangeDetails(java.lang.String)
	 */
	@Override
	public ExchangeResponse getAllExchangeDetails(String userCorrelationId) {

		String exchangeEndPointUrl = String.format(electraApiConfig.getExchangeDetailsApiEndpointUrl(),
				userCorrelationId, FORMAT_SPECIFIER);

		
		
		ExchangeResponse exchangeResponse = (ExchangeResponse) invokeApi(exchangeEndPointUrl, HttpMethod.GET,
				ExchangeResponse.class, null, false, false, false,false);

		

		return exchangeResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeUserClientOnboardingApi(com.ireslab.sendx.electra.model.ClientRegistrationRequest)
	 */
	@Override
	public ClientRegistrationResponse invokeUserClientOnboardingApi(
			ClientRegistrationRequest clientRegistrationRequest) {
		ClientRegistrationResponse clientResponse = null;

		String userOnboardingEndpointUrl = String.format(electraApiConfig.getUserClientOnboardingApiEndpointUrl(),
				FORMAT_SPECIFIER);

		try {
			LOG.debug("Invoking Electra User Client Onboarding API with Request - "
					+ objectWriter.writeValueAsString(clientRegistrationRequest));
		} catch (Exception exp) {
			
		}

		clientResponse = (ClientRegistrationResponse) invokeApi(userOnboardingEndpointUrl, HttpMethod.POST,
				ClientRegistrationResponse.class, clientRegistrationRequest, false, false, true,false);

		// Account creation failed
		if (clientResponse == null || clientResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (clientResponse != null && CollectionUtils.isNotEmpty(clientResponse.getErrors())) {
				List<Error> errors = clientResponse.getErrors();
				LOG.error("Account creation on Electra failed | Error Code - " + clientResponse.getCode()
						+ ", Error Message - " + clientResponse.getMessage() + ", Errors - " + errors);
			}
			return clientResponse;
		}

		return clientResponse;
	}


	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeUserClientEntryOnboardingApi(com.ireslab.sendx.model.SignupRequest)
	 */
	@Override
	public void invokeUserClientEntryOnboardingApi(SignupRequest signupRequest) {
		accessTokenRetryCount = 0;

		

		String userOnboardingEndpointUrl = String.format(electraApiConfig.getUserClientEntryOnboardingApiEndpointUrl(),
				FORMAT_SPECIFIER);

		/* Creating Electra User Onboarding JSON Request */
		List<UserProfile> users = new ArrayList<>();

		UserProfile profile = new UserProfile().setFirstName(signupRequest.getFirstName())
				.setLastName(signupRequest.getLastName())
				.setMobileNumber(signupRequest.getCountryDialCode() + signupRequest.getMobileNumber())
				.setEmailAddress(signupRequest.getEmailAddress())
				.setUserCorrelationId(signupRequest.getUserCorrelationId())
				.setCompanyCode(signupRequest.getCompanyCode());
		;
		users.add(profile);

		profile.setUserCorrelationId(signupRequest.getUserCorrelationId());
		UserRegistrationRequest electraUserRegistrationRequest = new UserRegistrationRequest().setUsers(users);

		try {
			LOG.debug("Invoking Electra User Client Entry Onboarding API with Request - "
					+ objectWriter.writeValueAsString(electraUserRegistrationRequest));
		} catch (Exception exp) {
			// do nothing
		}

		UserRegistrationResponse userRegistrationResponse = (UserRegistrationResponse) invokeApi(
				userOnboardingEndpointUrl, HttpMethod.POST, UserRegistrationResponse.class,
				electraUserRegistrationRequest, false, false, false,false);

		// Account creation failed
		if (userRegistrationResponse == null
				|| userRegistrationResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (userRegistrationResponse != null && CollectionUtils.isNotEmpty(userRegistrationResponse.getErrors())) {
				List<Error> errors = userRegistrationResponse.getErrors();
				LOG.error("Account creation on Electra failed | Error Code - " + userRegistrationResponse.getCode()
						+ ", Error Message - " + userRegistrationResponse.getMessage() + ", Errors - " + errors);
			}
			
		}

		

	}

	
	
	
	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeTransferTokensAPIToClient(com.ireslab.sendx.electra.model.TokenTransferRequest)
	 */
	@Override
	public String invokeTransferTokensAPIToClient(TokenTransferRequest tokenTransferRequest) {
		String invokeTransferTokensAPIToClientUrl =String.format(electraApiConfig.getTransferTokensToClientApiEndpointUrl(),FORMAT_SPECIFIER);
		
		
		try {
			LOG.debug("Invoking Electra Transfer Token Onboarding API with Request - "
					+ objectWriter.writeValueAsString(tokenTransferRequest));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		TokenTransferResponse tokenLifecycleManagementResponse = (TokenTransferResponse) invokeApi(
				invokeTransferTokensAPIToClientUrl, HttpMethod.POST, TokenTransferResponse.class,
				tokenTransferRequest, false, false, false,false);
		
		return "";
	}

	
	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#isClientORNot(com.ireslab.sendx.electra.model.ClientSubscriptionRequest)
	 */
	@Override
	public ClientSubscriptionResponse isClientORNot(ClientSubscriptionRequest clientSubscriptionRequest) {
		String planOnboardingEndpointUrl = String.format(electraApiConfig.getIsClientApiEndpointUrl(),
				FORMAT_SPECIFIER);
		
		ClientSubscriptionResponse clientSubscriptionResponse = null;

		try {
			LOG.debug("Invoking Electra Subscription Plan Onboarding API with Request - "
					+ objectWriter.writeValueAsString(clientSubscriptionRequest));
		} catch (Exception exp) {
			// do nothing
		}

		clientSubscriptionResponse = (ClientSubscriptionResponse) invokeApi(planOnboardingEndpointUrl, HttpMethod.POST,
				ClientSubscriptionResponse.class, clientSubscriptionRequest, false, false, false,false);

		// Account creation failed
		if (clientSubscriptionResponse == null || clientSubscriptionResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (clientSubscriptionResponse != null && CollectionUtils.isNotEmpty(clientSubscriptionResponse.getErrors())) {
				List<Error> errors = clientSubscriptionResponse.getErrors();
				LOG.error("Subscription plan on Electra failed | Error Code - " + clientSubscriptionResponse.getCode()
						+ ", Error Message - " + clientSubscriptionResponse.getMessage() + ", Errors - " + errors);
			}
			return clientSubscriptionResponse;
		}

		return clientSubscriptionResponse;
	}


	
	
	
	

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#updateCheckmailRegistration(com.ireslab.sendx.electra.model.ClientSubscriptionUpdateRequest)
	 */
	@Override
	public ClientSubscriptionUpdateResponse updateCheckmailRegistration(
			ClientSubscriptionUpdateRequest clientSubscriptionUpdateRequest) {
            accessTokenRetryCount = 0;

		

		String userOnboardingEndpointUrl = String.format(electraApiConfig.getCheckMailRegistrationApiEndpointUrl(),
				FORMAT_SPECIFIER);

		try {
			LOG.debug("Invoking Electra Client Subscription Plan Updation Onboarding API with Request - "
					+ objectWriter.writeValueAsString(clientSubscriptionUpdateRequest));
		} catch (Exception exp) {
			// do nothing
		}

		ClientSubscriptionUpdateResponse clientSubscriptionUpdateResponse = (ClientSubscriptionUpdateResponse) invokeApi(
				userOnboardingEndpointUrl, HttpMethod.POST, ClientSubscriptionUpdateResponse.class,
				clientSubscriptionUpdateRequest, false, false, false,false);

		// Client Subscription plan Updation failed
		if (clientSubscriptionUpdateResponse == null
				|| clientSubscriptionUpdateResponse.getCode().intValue() != HttpStatus.OK.value()) {

			if (clientSubscriptionUpdateResponse != null && CollectionUtils.isNotEmpty(clientSubscriptionUpdateResponse.getErrors())) {
				List<Error> errors = clientSubscriptionUpdateResponse.getErrors();
				LOG.error("Client Subscription plan Updation on Electra failed | Error Code - " + clientSubscriptionUpdateResponse.getCode()
						+ ", Error Message - " + clientSubscriptionUpdateResponse.getMessage() + ", Errors - " + errors);
			}
			// return userProfiles;
		}

		

		return clientSubscriptionUpdateResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getProductList(java.lang.String, com.ireslab.sendx.electra.model.ProductRequest)
	 */
	@Override
	public ProductResponse getProductList(String url,ProductRequest productRequest) {
		
		ProductResponse productResponse = invokeApi(url, HttpMethod.POST, ProductResponse.class, productRequest, false, false, false, true);
	
		return productResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#makePayment(java.lang.String, com.ireslab.sendx.electra.model.PaymentRequest)
	 */
	@Override
	public PaymentResponse makePayment(String makePaymentEndPointUrl, PaymentRequest paymentRequest) {
		PaymentResponse paymentResponse = invokeApi(makePaymentEndPointUrl, HttpMethod.POST, PaymentResponse.class, paymentRequest, false, false, false, true);
		return paymentResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#clientInformation(com.ireslab.sendx.electra.model.ClientInfoRequest)
	 */
	@Override
	public ClientInfoResponse clientInformation(ClientInfoRequest clientInfoRequest) {
		String clientInformationUrl = String.format(electraApiConfig.getClientInformationApiEndpointUrl(),
				FORMAT_SPECIFIER);
		ClientInfoResponse clientInfoResponse = invokeApi(clientInformationUrl, HttpMethod.POST, ClientInfoResponse.class, clientInfoRequest, false, false, false, true);
		return clientInfoResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#savePurchasedProduct(com.ireslab.sendx.electra.model.PaymentRequest)
	 */
	@Override
	public PaymentResponse savePurchasedProduct(PaymentRequest paymentRequest) {
		String endpointUrl = String.format(electraApiConfig.getSavePurchasedProductApiEndpointUrl(),
				FORMAT_SPECIFIER);
		PaymentResponse paymentResponse = invokeApi(endpointUrl, HttpMethod.POST, PaymentResponse.class, paymentRequest, false, false, false, true);
		return paymentResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#checkProductAvailability(com.ireslab.sendx.electra.model.ProductAvailabilityRequest)
	 */
	@Override
	public ProductAvailabilityResponse checkProductAvailability(ProductAvailabilityRequest productAvailabilityRequest) {
		String endpointUrl = String.format(electraApiConfig.getCheckProductAvailabilityApiEndpointUrl(),
				FORMAT_SPECIFIER);
		
		ProductAvailabilityResponse productAvailabilityResponse = invokeApi(endpointUrl, HttpMethod.POST, ProductAvailabilityResponse.class, productAvailabilityRequest, false, false, false, true);
		return productAvailabilityResponse;
		
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getAllTransactionPurpose(java.lang.String)
	 */
	@Override
	public TransactionPurposeResponse getAllTransactionPurpose(String clientCorrelationId) {
		LOG.info("Request for transaction purpose, client correlation id -  "+clientCorrelationId);
		accessTokenRetryCount = 0;

		String endpointUrl = String.format(electraApiConfig.getBaseUrl(), electraApiConfig.getApiVersion(),
				clientCorrelationId ) + String.format(electraApiConfig.getAllTransactionPurposeApiEndpointUrl(), FORMAT_SPECIFIER);

		
		TransactionPurposeResponse transactionPurposeResponse = invokeApi(endpointUrl, HttpMethod.GET, TransactionPurposeResponse.class, null, true, false, false, false);
		
		return transactionPurposeResponse;
	}
	
	
	@Override
	public TokenTransferResponse transactionLimitsForAllowTransfer(TokenTransferRequest tokenTransferRequest) {
		try {
			LOG.info("Request for verify transaction allow or not -  "+objectWriter.writeValueAsString(tokenTransferRequest));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		String url = String.format(electraApiConfig.getAllowTransactionApiEndpointUrl(),
				 FORMAT_SPECIFIER);
		
		TokenTransferResponse tokenTransferResponse = invokeApi(url, HttpMethod.POST, TokenTransferResponse.class, tokenTransferRequest, false, false, false, false);
		
		return tokenTransferResponse;
	}

	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#invokeTransferFeeToMaster(com.ireslab.sendx.electra.model.TokenTransferRequest)
	 */
	@Override
	public String invokeTransferFeeToMaster(TokenTransferRequest tokenTransferRequest) {
		String invokeTransferTokensAPIToClientUrl = String
				.format(electraApiConfig.getTransferFeeToMasterApiEndPointUrl(), FORMAT_SPECIFIER);
		


		TokenTransferResponse tokenLifecycleManagementResponse = (TokenTransferResponse) invokeApi(
				invokeTransferTokensAPIToClientUrl, HttpMethod.POST, TokenTransferResponse.class, tokenTransferRequest, false,
				false, false, false);

		return "";
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getTransactionLimit()
	 */
	@Override
	public TransactionLimitResponse getTransactionLimit() {
		String url = String.format(electraApiConfig.getTransactionLimitApiEndpointUrl(),
				 FORMAT_SPECIFIER);
		
		TransactionLimitResponse transactionLimitResponse = invokeApi(url, HttpMethod.GET, TransactionLimitResponse.class,
				null, false, false, true, false);
		return transactionLimitResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#updateDeviceSpecificParameter(com.ireslab.sendx.electra.model.SendxElectraRequest)
	 */
	@Override
	public SendxElectraResponse updateDeviceSpecificParameter(SendxElectraRequest sendxElectraRequest) {
		String url = String.format(electraApiConfig.getUpdateDeviceSpecificParameterApiEndpointUrl(),
				 FORMAT_SPECIFIER);
		
		SendxElectraResponse sendxElectraResponse = invokeApi(url, HttpMethod.POST, SendxElectraResponse.class,
				sendxElectraRequest, false, false, false, true);
		return sendxElectraResponse;
	}
	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#searchUserProfileByUniqueCode(java.lang.String)
	 */
	@Override
	public com.ireslab.sendx.model.UserProfile searchUserProfileByUniqueCode(String uniqueCode) {
		UserProfileResponse userProfileResponse =null;
		String searchUserProfileApiEndpointUrl = String.format(electraApiConfig.getSearchUserProfileApiEndpointUrl(),uniqueCode,
				FORMAT_SPECIFIER);
		userProfileResponse = invokeApi(searchUserProfileApiEndpointUrl, HttpMethod.GET, UserProfileResponse.class,
				null, false, false, false, true);
		
		//System.out.println(searchUserProfileApiEndpointUrl);
		if(userProfileResponse==null) {
			throw new com.ireslab.sendx.exception.BusinessException(HttpStatus.OK, AppStatusCodes.INVALID_REQUEST, com.ireslab.sendx.util.PropConstants.INVALID_REQUEST);
		}
		
		UserProfile userProfile = userProfileResponse.getUserProfile();
		com.ireslab.sendx.model.UserProfile userProfileModel =new com.ireslab.sendx.model.UserProfile();
		userProfileModel.setFirstName(userProfile.getFirstName());
		userProfileModel.setLastName(userProfile.getLastName());
		userProfileModel.setMobileNumber(new BigInteger(userProfile.getMobileNumber()));
		userProfileModel.setEmailAddress(userProfile.getEmailAddress());
		userProfileModel.setCountryDialCode(userProfile.getCountryDialCode());
		userProfileModel.setCountryName(userProfile.getCountryName());
		userProfileModel.setUniqueCode(userProfile.getUniqueCode());
		userProfileModel.setUserCorrelationId(userProfile.getUserCorrelationId());
		userProfileModel.setAccountStatus(userProfile.getStatus());
		userProfileModel.setGcmRegisterKey(userProfile.getGcmRegisterKey());
		userProfileModel.setFirebaseServiceKey(userProfile.getFirebaseServiceKey());
		userProfileModel.setResidentialAddress(userProfile.getResidentialAddress());
		userProfileModel.setAccountStatus(userProfile.getStatus());
		userProfileModel.setIso4217CurrencyAlphabeticCode(userProfile.getIso4217CurrencyAlphabeticCode());
		return userProfileModel;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#deleteNotificationData(com.ireslab.sendx.electra.model.NotificationRequest)
	 */
	@Override
	public NotificationResponse deleteNotificationData(NotificationRequest notificationRequest) {
		String saveNotificationApiEndpointUrl = String.format(electraApiConfig.getDeleteNotificationApiEndpointUrl(),
				FORMAT_SPECIFIER);
		
		NotificationResponse notificationResponse = invokeApi(saveNotificationApiEndpointUrl, HttpMethod.POST, NotificationResponse.class,
				notificationRequest, false, false, true, false);
		return notificationResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#makeOfflinePayment(com.ireslab.sendx.electra.model.TokenTransferRequest)
	 */
	@Override
	public PaymentResponse makeOfflinePayment(TokenTransferRequest tokenTransferRequest) {
		String offlinePaymentEndPointUrl = String.format(electraApiConfig.getOfflinePaymentApiEndpointUrl(),
				FORMAT_SPECIFIER);
		PaymentResponse paymentResponse = invokeApi(offlinePaymentEndPointUrl, HttpMethod.POST, PaymentResponse.class,
				tokenTransferRequest, false, false, false, true);
		return paymentResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#saveNotificationData(com.ireslab.sendx.electra.model.NotificationRequest)
	 */
	@Override
	public NotificationResponse saveNotificationData(NotificationRequest notificationRequest) {
		String saveNotificationApiEndpointUrl = String.format(electraApiConfig.getSaveNotificationApiEndpointUrl(),
				FORMAT_SPECIFIER);
		
		NotificationResponse notificationResponse = invokeApi(saveNotificationApiEndpointUrl, HttpMethod.POST, NotificationResponse.class,
				notificationRequest, false, false, true, false);
		return notificationResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getExchangeRate(com.ireslab.sendx.electra.model.ExchangeRequest)
	 */
	@Override
	public ExchangeResponse getExchangeRate(ExchangeRequest exchangeRequest) {
		String exchangeRateEndPointUrl = String.format(electraApiConfig.getExchangeRateApiEndpointUrl(),
				FORMAT_SPECIFIER);
		ExchangeResponse exchangeResponse = invokeApi(exchangeRateEndPointUrl, HttpMethod.POST, ExchangeResponse.class,
				exchangeRequest, false, false, false, true);
		return exchangeResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getAllNotification(java.lang.String)
	 */
	@Override
	public SendxElectraResponse getAllNotification(String correlationId) {
		String updateNotificationApiEndpointUrl = String.format(electraApiConfig.getAllNotificationApiEndpointUrl(),correlationId,
				FORMAT_SPECIFIER);
		
		SendxElectraResponse notificationResponse = invokeApi(updateNotificationApiEndpointUrl, HttpMethod.GET, SendxElectraResponse.class,
				null, false, false, true, false);
		return notificationResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#updateNotificationApi(com.ireslab.sendx.electra.model.SendxElectraRequest)
	 */
	@Override
	public SendxElectraResponse updateNotificationApi(SendxElectraRequest sendxElectraRequest) {
		String updateNotificationApiEndpointUrl = String.format(electraApiConfig.getUpdateNotificationApiEndpointUrl(),
				FORMAT_SPECIFIER);
		
			SendxElectraResponse notificationResponse = invokeApi(updateNotificationApiEndpointUrl, HttpMethod.POST, SendxElectraResponse.class,
				sendxElectraRequest, false, false, true, false);
			return notificationResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#searchUserProfileByDialCodeAndMobile(java.lang.String, java.lang.Long)
	 */
	@Override
	public com.ireslab.sendx.model.UserProfile searchUserProfileByDialCodeAndMobile(String beneficiaryCountryDialCode,
			Long beneficiaryMobileNumber) {
		UserProfileResponse userProfileResponse =null;
		String searchUserProfileApiEndpointUrl = String.format(electraApiConfig.getSearchUserProfileByMobileApiEndpointUrl(),beneficiaryCountryDialCode,beneficiaryMobileNumber,
				FORMAT_SPECIFIER);
		userProfileResponse = invokeApi(searchUserProfileApiEndpointUrl, HttpMethod.GET, UserProfileResponse.class,
				null, false, false, false, true);
		
		
		com.ireslab.sendx.model.UserProfile userProfileModel = null;
		if(userProfileResponse!=null) {
			
			
		
		UserProfile userProfile = userProfileResponse.getUserProfile();
		userProfileModel =new com.ireslab.sendx.model.UserProfile();
		userProfileModel.setFirstName(userProfile.getFirstName());
		userProfileModel.setLastName(userProfile.getLastName());
		userProfileModel.setMobileNumber(new BigInteger(userProfile.getMobileNumber()));
		userProfileModel.setEmailAddress(userProfile.getEmailAddress());
		userProfileModel.setCountryDialCode(userProfile.getCountryDialCode());
		userProfileModel.setCountryName(userProfile.getCountryName());
		userProfileModel.setUniqueCode(userProfile.getUniqueCode());
		userProfileModel.setUserCorrelationId(userProfile.getUserCorrelationId());
		userProfileModel.setAccountStatus(userProfile.getStatus());
		userProfileModel.setGcmRegisterKey(userProfile.getGcmRegisterKey());
		userProfileModel.setFirebaseServiceKey(userProfile.getFirebaseServiceKey());
		userProfileModel.setResidentialAddress(userProfile.getResidentialAddress());
		userProfileModel.setAccountStatus(userProfile.getStatus());
		userProfileModel.setIsClient(userProfile.getIsClient());
		if(userProfile.getClientCorrelationId()!=null) {
			userProfileModel.setClientCorrelationId(userProfile.getClientCorrelationId());
		}
		}
		return userProfileModel;
	}
	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getAllTransactionalDetails(com.ireslab.sendx.electra.model.SendxElectraRequest)
	 */
	@Override
	public SendxElectraResponse getAllTransactionalDetails(SendxElectraRequest sendxElectraRequest) {

		String exchangeEndPointUrl = String.format(electraApiConfig.getTransactionalDetailsApiEndpointUrl(),
				sendxElectraRequest.getUserCorrelationId(), FORMAT_SPECIFIER);
        
		LOG.info("Endpoint url for get all transaction details  :"+exchangeEndPointUrl);

		SendxElectraResponse sendxElectraResponse =(SendxElectraResponse) invokeApi(exchangeEndPointUrl, HttpMethod.POST,
				SendxElectraResponse.class, sendxElectraRequest, false, false, false,false);

		
		return sendxElectraResponse;
	}
	
	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.TransactionalApiService#getAllSettlementReports(java.lang.String)
	 */
	@Override
	public SendxElectraResponse getAllSettlementReports(String correllationId) {
		
		
		String apiEndpointUrl = String.format(electraApiConfig.getSettlementReportListApiEndpointUrl(),correllationId,
				FORMAT_SPECIFIER);
		SendxElectraResponse sendxElectraResponse = invokeApi(apiEndpointUrl, HttpMethod.GET, SendxElectraResponse.class,
				null, false, false, false, true);
		
		return sendxElectraResponse;
	}

}
