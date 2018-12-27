package com.ireslab.sendx.service.impl;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.transaction.Transactional;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ireslab.sendx.electra.ElectraApiConfig;
import com.ireslab.sendx.electra.dto.CashOutDto;
import com.ireslab.sendx.electra.model.SendxElectraRequest;
import com.ireslab.sendx.electra.model.SendxElectraResponse;
import com.ireslab.sendx.electra.model.TokenTransferRequest;
import com.ireslab.sendx.electra.model.TokenTransferResponse;
import com.ireslab.sendx.electra.model.TransactionLimitResponse;
import com.ireslab.sendx.electra.model.TransactionPurposeResponse;
import com.ireslab.sendx.entity.Account;
import com.ireslab.sendx.entity.Profile;
import com.ireslab.sendx.entity.ScheduledTransaction;
import com.ireslab.sendx.entity.TopupTransaction;
import com.ireslab.sendx.entity.TransactionDetail;
import com.ireslab.sendx.exception.BusinessException;
import com.ireslab.sendx.model.CashOutRequest;
import com.ireslab.sendx.model.CashOutResponse;
import com.ireslab.sendx.model.GenericResponse;
import com.ireslab.sendx.model.LoadTokensRequest;
import com.ireslab.sendx.model.LoadTokensResponse;
import com.ireslab.sendx.model.SendTokensRequest;
import com.ireslab.sendx.model.SendTokensResponse;
import com.ireslab.sendx.model.TransactionHistoryRequest;
import com.ireslab.sendx.model.TransactionHistoryResponse;
import com.ireslab.sendx.model.UserProfile;
import com.ireslab.sendx.model.UserTransactionDetails;
import com.ireslab.sendx.notification.SMSService;
import com.ireslab.sendx.repository.AccountRepository;
import com.ireslab.sendx.repository.CountryRepository;
import com.ireslab.sendx.repository.ScheduledTransactionRepository;
import com.ireslab.sendx.repository.TopupTransactionRepository;
import com.ireslab.sendx.service.CommonService;
import com.ireslab.sendx.service.TransactionService;
import com.ireslab.sendx.service.TransactionalApiService;
import com.ireslab.sendx.springsecurity.SpringSecurityUtil;
import com.ireslab.sendx.util.AppStatusCodes;
import com.ireslab.sendx.util.CommonUtils;
import com.ireslab.sendx.util.Constants;
import com.ireslab.sendx.util.PropConstants;

/**
 * @author iRESlab
 *
 */
@Service
public class TransactionServiceImpl implements TransactionService {

	private static final Logger LOG = LoggerFactory.getLogger(TransactionServiceImpl.class);

	@Autowired
	private SMSService smsSender;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private AccountRepository accountRepo;

	@Autowired
	private CountryRepository countryRepo;
	
	@Autowired
	private CommonService commonService;
	
	@Autowired
	private TopupTransactionRepository topupTransactionRepository;

	@Autowired
	private ScheduledTransactionRepository scheduledTxnRepo;

	@Autowired
	private TransactionalApiService transactionalApiService;
	
	@Autowired
	private ElectraApiConfig ElectraApiConfig;
	
	@Autowired
	private TestServiceImpl serviceImpl;
	
	@Autowired
	private ObjectWriter objectWriter;
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ireslab.sendx.service.TransactionService#handleTokensTransfer(com.
	 * ireslab.sendx.model.SendTokensRequest)
	 */
	@Override
	@Transactional
	public SendTokensResponse handleTokensTransfer(SendTokensRequest sendTokensRequest) {
		SendTokensResponse sendTokensResponse = null;
		
		try {
			LOG.debug("Transfer Token Request Recieved in Service :- "+ objectWriter.writeValueAsString(sendTokensRequest));
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		/*if(!sendTokensRequest.getSenderCountryDialCode().equals(sendTokensRequest.getBeneficiaryCountryDialCode())) {
			sendTokensResponse = new SendTokensResponse();
            sendTokensResponse.setStatus(HttpStatus.OK.value());
            sendTokensResponse.setCode(101);
           sendTokensResponse.setMessage("You are not allowed to transfer amount to this user.");
           return sendTokensResponse;
		}*/
		
		
		TransactionLimitResponse transactionLimitResponse = transactionalApiService.getTransactionLimit();
		
		
        double numberOfToken = Double.parseDouble(sendTokensRequest.getNoOfTokens());
		
		if(numberOfToken>Double.parseDouble(transactionLimitResponse.getDailyLimit()) && numberOfToken<= Double.parseDouble(transactionLimitResponse.getMonthlyLimit())) {
			
			 sendTokensResponse = new SendTokensResponse();
	            sendTokensResponse.setStatus(HttpStatus.OK.value());
	            sendTokensResponse.setCode(101);
	           sendTokensResponse.setMessage("You are exceeding your daily transaction limit.");
	           return sendTokensResponse;
		}
		
		if(numberOfToken> Double.parseDouble(transactionLimitResponse.getMonthlyLimit())) {
			
			 sendTokensResponse = new SendTokensResponse();
	            sendTokensResponse.setStatus(HttpStatus.OK.value());
	            sendTokensResponse.setCode(101);
	           sendTokensResponse.setMessage("You are exceeding your monthly transaction limit.");
	           return sendTokensResponse;
		}

		BigInteger senderMobileNumber = BigInteger.valueOf(sendTokensRequest.getSenderMobileNumber());
		String senderCountryDialCode = sendTokensRequest.getSenderCountryDialCode();

		// Get account details of sender (from spring context if null)
		if (senderMobileNumber == null) {
			String[] usernameToken = SpringSecurityUtil.usernameFromSecurityContext();
			senderMobileNumber = new BigInteger(usernameToken[1]);
			senderCountryDialCode = usernameToken[0];
		}

		LOG.info("Getting Sender account details from database. . . ");

		// Get account details of beneficiary
		Account senderAccountDetails = accountRepo.findByMobileNumberAndCountry_CountryDialCode(senderMobileNumber,
				senderCountryDialCode);

		if (senderAccountDetails == null) {
			LOG.error("Sender Account doesn't exists");
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.INTERNAL_SERVER_ERROR,
					PropConstants.ACCOUNT_NOT_EXISTS);
		}
		TokenTransferRequest tokenTransferRequestForcheck =new TokenTransferRequest();
		tokenTransferRequestForcheck.setNoOfToken(sendTokensRequest.getNoOfTokens());
		tokenTransferRequestForcheck.setSenderCorrelationId(senderAccountDetails.getUserCorrelationId());

		TokenTransferResponse tokenTransferResponse = transactionalApiService.transactionLimitsForAllowTransfer(tokenTransferRequestForcheck);
		if(tokenTransferResponse.getCode().intValue()==AppStatusCodes.DAILY_TRANSACTION_LIMIT_REACHED.intValue()) {
			
            sendTokensResponse = new SendTokensResponse();
            sendTokensResponse.setStatus(HttpStatus.OK.value());//HttpStatus.OK.value(), AppStatusCodes.SUCCESS, successMessage
            sendTokensResponse.setCode(101);
           // messageSource.getMessage(PropConstants., null, Locale.getDefault())
           sendTokensResponse.setMessage("You have reached your allowed transaction daily limit.");
            
			
		}
		else if(tokenTransferResponse.getCode().intValue()==AppStatusCodes.MONTHLY_TRANSACTION_LIMIT_REACHED.intValue()) {
			
            sendTokensResponse = new SendTokensResponse();
            sendTokensResponse.setStatus(HttpStatus.OK.value());//HttpStatus.OK.value(), AppStatusCodes.SUCCESS, successMessage
            sendTokensResponse.setCode(101);
           // messageSource.getMessage(PropConstants., null, Locale.getDefault())
           sendTokensResponse.setMessage("You have reached your allowed transaction monthly limit.");
            
			
		}
		else {

		Profile senderProfile = senderAccountDetails.getProfile();
		LOG.info("Sender Details: \n\tMobile Number - " + senderCountryDialCode + senderMobileNumber + ",\n\tName - "
				+ senderProfile.getFirstName() + " " + senderProfile.getLastName() + ",\n\tEmail Address - "
				+ senderProfile.getEmailAddress());

		BigInteger beneficiaryMobileNumber = BigInteger.valueOf(sendTokensRequest.getBeneficiaryMobileNumber());
		String beneficiaryCountryDialCode = sendTokensRequest.getBeneficiaryCountryDialCode();

		LOG.info("Getting Beneficiary account details from database. . . ");

		// Get Beneficiary Account details
		/*Account beneficiaryAccountDetails = accountRepo
				.findByMobileNumberAndCountry_CountryDialCode(beneficiaryMobileNumber, beneficiaryCountryDialCode);*/
		
		
		
		
		UserProfile userProfileModel = null;
		
		if(sendTokensRequest.getBeneficiaryUniqueCode() != null && sendTokensRequest.getBeneficiaryUniqueCode().length()>0) {
			userProfileModel = commonService.searchUserByuniqueCodeInElectra(sendTokensRequest.getBeneficiaryUniqueCode());
		}else {
			
			
			
			Account beneficiaryAccountDetails = accountRepo
					.findByMobileNumberAndCountry_CountryDialCode(beneficiaryMobileNumber, beneficiaryCountryDialCode);
			
			
			if(beneficiaryAccountDetails  != null) {
				Profile profile = beneficiaryAccountDetails.getProfile();
				userProfileModel = new UserProfile();
				userProfileModel.setAccountStatus(Enum.valueOf(com.ireslab.sendx.electra.utils.Status.class, beneficiaryAccountDetails.getStatus()));
				userProfileModel.setFirstName(profile.getFirstName());
				userProfileModel.setLastName(profile.getLastName());
				userProfileModel.setEmailAddress(profile.getEmailAddress());
				userProfileModel.setUserCorrelationId(beneficiaryAccountDetails.getUserCorrelationId());
			}else {
				
				userProfileModel = commonService.searchUserByDialCodeAndMobileInElectra(sendTokensRequest.getBeneficiaryCountryDialCode(),sendTokensRequest.getBeneficiaryMobileNumber());
				
				
				
			}
			
			
			
		}
		
		
		
		
		if(userProfileModel != null) {
			
		
			if(userProfileModel.getAccountStatus().equals(com.ireslab.sendx.electra.utils.Status.SUSPENDED)) {
				 sendTokensResponse = new SendTokensResponse();
		            sendTokensResponse.setStatus(HttpStatus.OK.value());
		            sendTokensResponse.setCode(101);
		           
		           sendTokensResponse.setMessage("This account has been suspended.");
		           return sendTokensResponse;
			}
			
		}
		// check if the beneficiary user is registered user
		if (userProfileModel == null || userProfileModel.getAccountStatus().equals(com.ireslab.sendx.electra.utils.Status.TERMINATED)) {

			LOG.debug("Beneficiary - " + beneficiaryCountryDialCode + beneficiaryMobileNumber
					+ " is not registered. Scheduling payment and sending SMS message. . . .");

			ScheduledTransaction scheduledTransaction = new ScheduledTransaction();
			scheduledTransaction.setSenderAccount(senderAccountDetails);
			scheduledTransaction.setBeneficiaryMobileNumber(beneficiaryMobileNumber);
			scheduledTransaction.setBeneficiaryCountry(
					countryRepo.findOne(commonService.getCountryDetails(beneficiaryCountryDialCode).getCountryId()));
			scheduledTransaction.setNoOfTokens(sendTokensRequest.getNoOfTokens());
			scheduledTransaction.setCreatedDate(new Date());
			scheduledTransaction.setModifiedDate(new Date());

			handleUnregisteredBeneficiaryTransfer(scheduledTransaction);
			
			String successMessage = String.format(
					messageSource.getMessage(PropConstants.PAYMENT_TRANSACTION_REQUEST_SCHEDULED, null,
							Locale.getDefault()),
					sendTokensRequest.getNoOfTokens(),senderProfile.getCountry().getIso4217CurrencyAlphabeticCode(), (beneficiaryCountryDialCode + beneficiaryMobileNumber));

			sendTokensResponse = new SendTokensResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS, successMessage);

		} else {
			String transactionXDR = null;

			/* Commented - Only applicable in Livenet */
			// Double beneficiaryAccountCurrentBalance =
			// Double.parseDouble(stellarTxnManager
			// .getAccountBalance(beneficiaryAccountDetails.getStellarAccount().getPublicKey(),
			// false));
			//
			// Double maxAllowedAccountBalance =
			// Double.parseDouble(sendxConfig.maxAllowedAccountBalance);
			// Double tokensToTransfer =
			// Double.parseDouble(sendTokensRequest.getNoOfTokens());
			//
			// if (beneficiaryAccountCurrentBalance >= maxAllowedAccountBalance)
			// {
			//
			// LOG.error(
			// "Tokens cannot be transferred to Beneficiary Account as account
			// balance limit reached already");
			// throw new BusinessException(HttpStatus.OK,
			// AppStatusCodes.ACCOUNT_BALANCE_LIMIT_REACHED,
			// PropConstants.ACCOUNT_BALANCE_LIMIT_REACHED);
			//
			// } else if ((beneficiaryAccountCurrentBalance + tokensToTransfer)
			// > maxAllowedAccountBalance) {
			//
			// LOG.error(
			// "Tokens cannot be transferred to Beneficiary Account as surpasses
			// maximum account balance limit");
			// throw new BusinessException(HttpStatus.OK,
			// AppStatusCodes.ACCOUNT_BALANCE_LIMIT_REACHED,
			// PropConstants.ACCOUNT_BALANCE_LIMIT_SURPASSES);
			// }

			// if(Integer.parseInt(beneficiaryAccountBalance) ==
			// Integer.parseInt(sendxConfig.maxAllowedAccountBalance);

			//Profile beneficiaryProfile = beneficiaryAccountDetails.getProfile();
			String beneficiaryName = userProfileModel.getFirstName() + " " + userProfileModel.getLastName();

			LOG.info("Beneficiary Details: \n\tMobile Number - " + beneficiaryCountryDialCode + beneficiaryMobileNumber
					+ ",\n\tName - " + beneficiaryName + ",\n\tEmail Address - "
					+ userProfileModel.getEmailAddress());

			LOG.info("Initiating transfer of '" + sendTokensRequest.getNoOfTokens() + "' tokens");

			// Save transaction details in database
			TransactionDetail transactionDetail = new TransactionDetail();
			transactionDetail.setNoOfTokens(sendTokensRequest.getNoOfTokens());
			transactionDetail.setSenderAccountId(senderAccountDetails);
			//transactionDetail.setReceiverAccountId(beneficiaryAccountDetails); // This line is commented becouse we are getting detais from electra.
			transactionDetail.setModifiedDate(new Date());
			transactionDetail.setTransactionDate(new Date());

			String accountBalance = null;

			// transfer tokens
			try {
				sendTokensRequest.setSenderCorrelationId(senderAccountDetails.getUserCorrelationId());
				sendTokensRequest.setBeneficiaryCorrelationId(userProfileModel.getUserCorrelationId());
				
				TokenTransferRequest tokenTransferRequest =new TokenTransferRequest();
				
				tokenTransferRequest.setClientId(ElectraApiConfig.getClientCorrelationId());
				
				tokenTransferRequest.setNoOfToken(sendTokensRequest.getFee());
				tokenTransferRequest.setReceiverCorrelationId(ElectraApiConfig.getClientCorrelationId());
				
				tokenTransferRequest.setSenderCorrelationId(sendTokensRequest.getSenderCorrelationId());
				transactionalApiService.invokeTransferFeeToMaster(tokenTransferRequest);
				
				if ((accountBalance = transactionalApiService.invokeTransferTokensAPI(sendTokensRequest)) == null) {
					throw new BusinessException(HttpStatus.OK, AppStatusCodes.INTERNAL_SERVER_ERROR,
							PropConstants.INTERNAL_SERVER_ERROR);
				}
				
				

			} catch (BusinessException exp) {
				transactionDetail.setTransactionStatus((short) 2);
				transactionDetail.setTransactionStatusMessage("Failed");

				
				throw new BusinessException(exp);

			} catch (Exception exp) {

				transactionDetail.setTransactionStatus((short) 2);
				transactionDetail.setTransactionStatusMessage("Failed");

				

				LOG.error("Token transfer operation failed due to error - " + ExceptionUtils.getStackTrace(exp));
				throw new BusinessException(HttpStatus.OK, AppStatusCodes.STELLAR_PAYMENT_OPERATION_FAILED,
						PropConstants.PAYMENT_TRANSACTION_FAILURE, exp);
			}

			LOG.info("Tokens transferred successfully");

			transactionDetail.setTransactionStatus((short) 1);
			transactionDetail.setTransactionXdr(transactionXDR);
			transactionDetail.setTransactionStatusMessage("Success");

		//	txnDetailRepo.save(transactionDetail);

			String successMessage = String.format(
					messageSource.getMessage(PropConstants.PAYMENT_TRANSACTION_SUCCESS, null, Locale.getDefault()),
					sendTokensRequest.getNoOfTokens(),senderProfile.getCountry().getIso4217CurrencyAlphabeticCode(), beneficiaryName);

			sendTokensResponse = new SendTokensResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS, successMessage);
			sendTokensResponse.setAccountBalance(accountBalance);
		}
		}
		return sendTokensResponse;
		}

	/**
	 * @param scheduledTransaction
	 */
	@Transactional(rollbackOn = Exception.class)
	private void handleUnregisteredBeneficiaryTransfer(ScheduledTransaction scheduledTransaction) {

		String userMobileNumber = scheduledTransaction.getBeneficiaryCountry().getCountryDialCode().replace("+", "")
				+ String.valueOf(scheduledTransaction.getBeneficiaryMobileNumber());

		try {
			LOG.info("Scheduling transaction in database . . . ");
			scheduledTxnRepo.save(scheduledTransaction);

		} catch (Exception exp) {
			LOG.error("Error occurred while scheduling transaction in database. . . . ");
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.STELLAR_PAYMENT_OPERATION_FAILED,
					PropConstants.PAYMENT_TRANSACTION_FAILURE);
		}

		String downloadAppMessage = String.format(smsSender.getDownloadAppMessage(), "");

		if (!smsSender.sendMessage(userMobileNumber, downloadAppMessage, scheduledTransaction.getBeneficiaryCountry()
				.getCountryDialCode().equalsIgnoreCase(Constants.US_COUNTRY_DIAL_CODE))) {
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.STELLAR_PAYMENT_OPERATION_FAILED,
					PropConstants.PAYMENT_TRANSACTION_FAILURE);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ireslab.sendx.service.TransactionService#handleCashOutTokens(com.
	 * ireslab.sendx.model.CashOutRequest)
	 */
	@Override
	public CashOutResponse handleCashOutTokens(CashOutRequest cashOutTokensRequest) {

		CashOutResponse cashOutResponse = new CashOutResponse();

		if (cashOutTokensRequest.getMobileNumber() == null) {
			String[] usernameToken = SpringSecurityUtil.usernameFromSecurityContext();
			cashOutTokensRequest.setMobileNumber(Long.valueOf(usernameToken[1]));
			cashOutTokensRequest.setCountryDialCode(usernameToken[0]);
		}

		Account account = accountRepo.findByMobileNumberAndCountry_CountryDialCode(
				BigInteger.valueOf(cashOutTokensRequest.getMobileNumber()), cashOutTokensRequest.getCountryDialCode());

		if (account == null) {
			LOG.error("Account not exists !!");
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.ACCOUNT_NOT_EXISTS,
					PropConstants.ACCOUNT_NOT_EXISTS);
		}
		TokenTransferRequest tokenTransferRequestForcheck =new TokenTransferRequest();
		tokenTransferRequestForcheck.setNoOfToken(cashOutTokensRequest.getNoOfTokens());
		tokenTransferRequestForcheck.setSenderCorrelationId(account.getUserCorrelationId());
		
		

		cashOutTokensRequest.setUserCorrelationId(account.getUserCorrelationId());

		LOG.info("Calling Electra Cashout tokens API");
		String accountBalance = transactionalApiService.invokeCashoutTokensAPI(cashOutTokensRequest);

		if (accountBalance == null) {

			LOG.error("Electra API failed");
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.STELLAR_PAYMENT_OPERATION_FAILED,
					PropConstants.PAYMENT_TRANSACTION_FAILURE);
		}

		LOG.info("Saving cash out transaction as scheduled transaction in database . . . ");

		ScheduledTransaction scheduledTransaction = new ScheduledTransaction();
		scheduledTransaction.setSenderAccount(account);
		scheduledTransaction.setNoOfTokens(cashOutTokensRequest.getNoOfTokens());
		scheduledTransaction.setCashOut(true);
		scheduledTransaction.setInstitutionName(cashOutTokensRequest.getInstitutionName());
		scheduledTransaction.setInstitutionAccountNumber(cashOutTokensRequest.getInstitutionAccountNumber());
		scheduledTransaction.setAdditionalInstitutionInfo(cashOutTokensRequest.getAddtionalInstitutionInfo());
		scheduledTransaction.setCreatedDate(new Date());
		scheduledTransaction.setModifiedDate(new Date());
		scheduledTxnRepo.save(scheduledTransaction);

		

		String successMessage = String.format(
				messageSource.getMessage(PropConstants.CASHOUT_TRANSACTION_SUCCESS, null, Locale.getDefault()),
				cashOutTokensRequest.getNoOfTokens(),account.getProfile().getCountry().getIso4217CurrencyAlphabeticCode(),cashOutTokensRequest.getInstitutionAccountNumber());

		cashOutResponse = new CashOutResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS, successMessage,
				accountBalance);
		//}
		return cashOutResponse;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ireslab.sendx.service.TransactionService#handleTransactionHistory(com
	 * .ireslab.sendx.model.TransactionHistoryRequest)
	 */
	@Override
	public TransactionHistoryResponse handleTransactionHistory(TransactionHistoryRequest txnHistoryRequest) {

		TransactionHistoryResponse txnHistoryResponse = null;

		String[] usernameToken = SpringSecurityUtil.usernameFromSecurityContext();
		BigInteger mobileNumber = new BigInteger(usernameToken[1]);
		String countryDialCode = usernameToken[0];

		Account account = accountRepo.findByMobileNumberAndCountry_CountryDialCode(mobileNumber, countryDialCode);
		if (account == null) {
			LOG.error("Account not exists !!");
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.ACCOUNT_NOT_EXISTS,
					PropConstants.ACCOUNT_NOT_EXISTS);
		}

		List<UserTransactionDetails> userTransactionDetailsList = new ArrayList<>();
		
		
		
		//-- code to fetch data from electra server
		SendxElectraRequest requestTransactionDetailList =new SendxElectraRequest();
		requestTransactionDetailList.setUserCorrelationId(account.getUserCorrelationId());
		requestTransactionDetailList.setAllLedger(txnHistoryRequest.getAllLedger());
		requestTransactionDetailList.setOfflineLedger(txnHistoryRequest.getOfflineLedger());
		List<com.ireslab.sendx.electra.dto.TransactionDetailsDto> transactionDetailsDtos = serviceImpl.getAllTransactionalDetails(requestTransactionDetailList).getTransactionDetailsDtos();
			
		
		for (com.ireslab.sendx.electra.dto.TransactionDetailsDto userTransactionDetails : transactionDetailsDtos) {
			UserTransactionDetails userTxnDetails = new UserTransactionDetails();
			userTxnDetails.setUserMobileNumber(account.getMobileNumber().longValue());
			userTxnDetails.setUserCountryDialCode(account.getCountry().getCountryDialCode());
			
			//----
			
			userTxnDetails.setIsSendingTransaction(userTransactionDetails.isSendingTransaction());
			
			if(userTransactionDetails.isSendingTransaction()) {
				userTxnDetails.setTransactionUserName(userTransactionDetails.getRecieverFirstName());
			}else {
				userTxnDetails.setTransactionUserName(userTransactionDetails.getSenderFirstName());
			}
			
			
			userTxnDetails.setTransactionDate(userTransactionDetails.getTransactionDate());
			
			
			userTxnDetails.setTransactionTime(CommonUtils.transactionTime(userTransactionDetails.getTxnDate()));
			
		
			
			userTxnDetails.setTxnDate(userTransactionDetails.getTxnDate());
			userTxnDetails.setTransactionMessage("message");
			userTxnDetails.setTransactionStatus(userTransactionDetails.getTransactionStatus());
			userTxnDetails.setNoOfTokens(userTransactionDetails.getNoOfTokens());
			
			userTxnDetails.setOffline(userTransactionDetails.getOffline());
			
			userTransactionDetailsList.add(userTxnDetails);
			
			
		}
		
		
		
		//------------------------------settlement report----------------
		
		
		SendxElectraResponse allSettlementReports = transactionalApiService.getAllSettlementReports(account.getUserCorrelationId());
		
		
		if(allSettlementReports.getSettlementReportList()!=null && !allSettlementReports.getSettlementReportList().isEmpty()) {
			
			
			
			List<CashOutDto> settlementReportList = allSettlementReports.getSettlementReportList();
			
			settlementReportList.forEach((cashOutDto) -> {

				UserTransactionDetails userTxnDetails = new UserTransactionDetails();

					userTxnDetails.setTransactionUserName(cashOutDto.getInstitutionName());
					userTxnDetails.setIsCashOutTransaction(true);
					userTxnDetails.setIsSendingTransaction(true);
					userTxnDetails.setNoOfTokens(cashOutDto.getNoOfTokens());
					if(cashOutDto.getStatus().equals("NEW")) {
						userTxnDetails.setTransactionStatus((short) 0);
					}else if(cashOutDto.getStatus().equals("TRANSFERRED")) {
						userTxnDetails.setTransactionStatus((short) 1);
					}else if(cashOutDto.getStatus().equals("REJECTED")) {
						userTxnDetails.setTransactionStatus((short) 3);
					}
					
					SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					
					userTxnDetails.setTransactionDate(cashOutDto.getCreatedDate());
					try {
						userTxnDetails.setTransactionDate(CommonUtils.transactionDate(new Date(sdf.parse(cashOutDto.getCreatedDate()).getTime())));
						userTxnDetails.setTransactionTime(CommonUtils.transactionTime(new Date(sdf.parse(cashOutDto.getCreatedDate()).getTime())));
					
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					userTransactionDetailsList.add(userTxnDetails);
				
			});
		
		
		
		
			}

		

		txnHistoryResponse = new TransactionHistoryResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS,
				PropConstants.SUCCESS, userTransactionDetailsList);
		
		

		return txnHistoryResponse;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ireslab.sendx.service.TransactionService#handleLoadTokens(com.ireslab
	 * .sendx.model.LoadTokensRequest)
	 */
	@Override
	@Transactional(rollbackOn = Exception.class)
	public LoadTokensResponse handleLoadTokens(LoadTokensRequest loadtokensRequest) {

		LoadTokensResponse loadTokensResponse = null;
		Date currentDate = new Date();

		Account account = accountRepo.findByMobileNumberAndCountry_CountryDialCode(
				BigInteger.valueOf(loadtokensRequest.getMobileNumber()), loadtokensRequest.getCountryDialCode());

		if (account == null) {
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.INTERNAL_SERVER_ERROR,
					PropConstants.ACCOUNT_NOT_EXISTS);
		}

		loadtokensRequest.setUserCorrelationId(account.getUserCorrelationId());

		// Invoke Electra Load Tokens API
		String accountBalance = transactionalApiService.invokeLoadTokensAPI(loadtokensRequest);
		if (accountBalance == null) {

			// TODO: throw proper exception
			LOG.error("Error occurred while invoking Electra load tokens API");
			throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, AppStatusCodes.INTERNAL_SERVER_ERROR,
					PropConstants.INTERNAL_SERVER_ERROR);
		}
		
		if (accountBalance.equalsIgnoreCase("op_line_full")) {

			// TODO: throw proper exception
			LOG.error("Error occurred while invoking Electra load tokens API");
			throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, AppStatusCodes.LOAD_TOKEN_FAILED,
					PropConstants.LOAD_TOKEN_FAILED);
		}

		TopupTransaction topupTransaction = new TopupTransaction();
		topupTransaction.setBeneficiaryMobileNumber(BigInteger.valueOf(loadtokensRequest.getMobileNumber()));
		topupTransaction.setBeneficiaryCountryDialCode(loadtokensRequest.getCountryDialCode());
		topupTransaction.setNoOfTokens(loadtokensRequest.getNoOfTokens());
		topupTransaction.setPaymentReferenceNumber(loadtokensRequest.getPaymentReferenceNumber());
		topupTransaction.setPaymentPlatform(loadtokensRequest.getPaymentPlatform());
		topupTransaction.setPaymentType(loadtokensRequest.getPaymentType());
		topupTransaction.setTransactionDate(currentDate);
		topupTransaction.setModifiedDate(currentDate);
		topupTransactionRepository.save(topupTransaction);

		loadTokensResponse = new LoadTokensResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS,
				"Amount loaded successfully", accountBalance);
		return loadTokensResponse;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ireslab.sendx.service.TransactionService#validateUserTopUp(java.lang.
	 * String, java.lang.String)
	 */
	@Override
	public GenericResponse validateUserTopUp(BigInteger mobileNumber, String countryDialCode) {

		GenericResponse validateUserTopUpResponse = null;

		/* Commented - Only applicable in Livenet */

		// List<TopupTransaction> topupTransaction = topupTransactionRepository
		// .findByBeneficiaryMobileNumberAndBeneficiaryCountryDialCodeByDay(mobileNumber,
		// countryDialCode);
		// if (topupTransaction != null && (topupTransaction.size() >=
		// sendxConfig.maxAllowedAccountTopup)) {
		//
		// LOG.error("Maximum topup limit for a day has been reached.");
		// throw new BusinessException(HttpStatus.OK,
		// AppStatusCodes.ACCOUNT_TOPUP_LIMIT_REACHED,
		// PropConstants.TOPUP_DAYLIMIT_REACHED);
		// }

		Account userAccount = accountRepo.findByMobileNumberAndCountry_CountryDialCode(mobileNumber, countryDialCode);
		if (userAccount != null) {

			/* Commented - Only applicable in Livenet */

			// Double accountBalance = Double.valueOf(
			// stellarTxnManager.getAccountBalance(userAccount.getStellarAccount().getPublicKey(),
			// false));
			//
			// if (accountBalance >=
			// Double.valueOf(sendxConfig.maxAllowedAccountBalance)) {
			//
			// LOG.error("Account balance limit reached.");
			// throw new BusinessException(HttpStatus.OK,
			// AppStatusCodes.ACCOUNT_BALANCE_LIMIT_REACHED,
			// PropConstants.TOPUP_FAILED_ACCOUNT_BALANCE_LIMIT_REACHED);
			// }
		}

		validateUserTopUpResponse = new GenericResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS,
				PropConstants.SUCCESS);

		return validateUserTopUpResponse;
	}

	@Override
	public TransactionPurposeResponse getAllTransactionPurpose(String clientCorrelationId) {
		
		
		 
		TransactionPurposeResponse transactionPurposeResponse = transactionalApiService.getAllTransactionPurpose(clientCorrelationId);
		
		return transactionPurposeResponse;
	}
}
