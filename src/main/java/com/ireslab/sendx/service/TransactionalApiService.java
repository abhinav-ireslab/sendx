package com.ireslab.sendx.service;

import java.util.List;

import com.ireslab.sendx.electra.model.ClientInfoRequest;
import com.ireslab.sendx.electra.model.ClientInfoResponse;
import com.ireslab.sendx.electra.model.ClientProfile;
import com.ireslab.sendx.electra.model.ClientRegistrationRequest;
import com.ireslab.sendx.electra.model.ClientRegistrationResponse;
import com.ireslab.sendx.electra.model.ClientSubscriptionRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionResponse;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateRequest;
import com.ireslab.sendx.electra.model.ClientSubscriptionUpdateResponse;
import com.ireslab.sendx.electra.model.ExchangeRequest;
import com.ireslab.sendx.electra.model.ExchangeResponse;
import com.ireslab.sendx.electra.model.NotificationRequest;
import com.ireslab.sendx.electra.model.NotificationResponse;
import com.ireslab.sendx.electra.model.PaymentRequest;
import com.ireslab.sendx.electra.model.PaymentResponse;
import com.ireslab.sendx.electra.model.ProductAvailabilityRequest;
import com.ireslab.sendx.electra.model.ProductAvailabilityResponse;
import com.ireslab.sendx.electra.model.ProductRequest;
import com.ireslab.sendx.electra.model.ProductResponse;
import com.ireslab.sendx.electra.model.SendxElectraRequest;
import com.ireslab.sendx.electra.model.SendxElectraResponse;
import com.ireslab.sendx.electra.model.TokenTransferRequest;
import com.ireslab.sendx.electra.model.TokenTransferResponse;
import com.ireslab.sendx.electra.model.TransactionLimitResponse;
import com.ireslab.sendx.electra.model.TransactionPurposeResponse;
import com.ireslab.sendx.electra.model.UserRegistrationResponse;
import com.ireslab.sendx.model.AgentRequest;
import com.ireslab.sendx.model.AgentRequestBody;
import com.ireslab.sendx.model.AgentResponse;
import com.ireslab.sendx.model.CashOutRequest;
import com.ireslab.sendx.model.CompanyCodeResponse;
import com.ireslab.sendx.model.LoadTokensRequest;
import com.ireslab.sendx.model.SendTokensRequest;
import com.ireslab.sendx.model.SignupRequest;
import com.ireslab.sendx.model.SubscriptionPlanResponse;
import com.ireslab.sendx.model.UserProfile;


/**
 * @author Nitin
 *
 */
public interface TransactionalApiService {

	/**
	 * @param signupRequest
	 * @return
	 */
	public List<com.ireslab.sendx.electra.model.UserProfile> invokeUserOnboardingApi(SignupRequest signupRequest);

	/**
	 * @param loadTokensRequest
	 * @return
	 */
	public String invokeLoadTokensAPI(LoadTokensRequest loadTokensRequest);

	/**
	 * 
	 * @param userCorrelationId
	 * @return
	 */
	public UserProfile invokeUserProfileAPI(String userCorrelationId);

	/**
	 * @param sendTokensRequest
	 * @return
	 */
	public String invokeTransferTokensAPI(SendTokensRequest sendTokensRequest);

	/**
	 * @param cashOutTokensRequest
	 * @return
	 */
	public String invokeCashoutTokensAPI(CashOutRequest cashOutTokensRequest);

	/**
	 * @param userCorrelationId
	 * @return
	 */
	public ExchangeResponse getAllExchangeDetails(String userCorrelationId);

	/**
	 * @param agentRequest
	 * @param correlationId
	 * @return
	 */
	public AgentResponse invokeAgentOnboardingApi(AgentRequest agentRequest, String correlationId);

	/**
	 * @param agentRequestBody
	 * @return
	 */
	public AgentResponse invokeGetAgentAPI(AgentRequestBody agentRequestBody);

	public ClientRegistrationResponse invokeUserClientOnboardingApi(ClientRegistrationRequest clientRegistrationRequest);

	public void invokeUserClientEntryOnboardingApi(SignupRequest signupRequest);

	public String invokeTransferTokensAPIToClient(TokenTransferRequest tokenTransferRequest);

	public UserRegistrationResponse updateUser(UserProfile userProfile, String correlationId);


	public ClientSubscriptionResponse isClientORNot(ClientSubscriptionRequest clientSubscriptionRequest);

	public ClientSubscriptionUpdateResponse updateCheckmailRegistration(
			ClientSubscriptionUpdateRequest clientSubscriptionUpdateRequest);
	
	public ProductResponse getProductList(String url,ProductRequest productRequest);

	public PaymentResponse makePayment(String makePaymentEndPointUrl, PaymentRequest paymentRequest);

	public ClientInfoResponse clientInformation(ClientInfoRequest clientInfoRequest);

	public PaymentResponse savePurchasedProduct(PaymentRequest paymentRequest);

	public ProductAvailabilityResponse checkProductAvailability(ProductAvailabilityRequest productAvailabilityRequest);

	public TransactionPurposeResponse getAllTransactionPurpose(String userCorrelationId);

	public TokenTransferResponse transactionLimitsForAllowTransfer(TokenTransferRequest tokenTransferRequest);

	public String invokeTransferFeeToMaster(TokenTransferRequest tokenTransferRequest);

	public TransactionLimitResponse getTransactionLimit();

	public SendxElectraResponse updateDeviceSpecificParameter(SendxElectraRequest sendxElectraRequest);

	UserProfile searchUserProfileByUniqueCode(String uniqueCode);

	public NotificationResponse deleteNotificationData(NotificationRequest notificationRequest);

	public PaymentResponse makeOfflinePayment(TokenTransferRequest tokenTransferRequest);

	public NotificationResponse saveNotificationData(NotificationRequest notificationRequest);

	public ExchangeResponse getExchangeRate(ExchangeRequest exchangeRequest);

	public SendxElectraResponse getAllNotification(String correlationId);

	public SendxElectraResponse updateNotificationApi(SendxElectraRequest sendxElectraRequest);

	public UserProfile searchUserProfileByDialCodeAndMobile(String beneficiaryCountryDialCode,
			Long beneficiaryMobileNumber);

	public SendxElectraResponse getAllTransactionalDetails(SendxElectraRequest sendxElectraRequest);

	SendxElectraResponse getAllSettlementReports(String correllationId);

	
}
