package com.ireslab.sendx.service.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring4.SpringTemplateEngine;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.ireslab.sendx.electra.ElectraApiConfig;
import com.ireslab.sendx.electra.dto.EmailRequest;
import com.ireslab.sendx.electra.dto.PurchaserDto;
import com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto;
import com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto.BillToAddress;
import com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto.CompanyDetails;
import com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto.Detailtable;
import com.ireslab.sendx.electra.model.ClientInfoRequest;
import com.ireslab.sendx.electra.model.ClientInfoResponse;
import com.ireslab.sendx.electra.model.ExchangeRequest;
import com.ireslab.sendx.electra.model.ExchangeResponse;
import com.ireslab.sendx.electra.model.NotificationRequest;
import com.ireslab.sendx.electra.model.NotificationResponse;
import com.ireslab.sendx.electra.model.PaymentRequest;
import com.ireslab.sendx.electra.model.PaymentResponse;
import com.ireslab.sendx.electra.model.ProductAvailabilityRequest;
import com.ireslab.sendx.electra.model.ProductRequest;
import com.ireslab.sendx.electra.model.ProductResponse;
import com.ireslab.sendx.electra.model.SendxElectraRequest;
import com.ireslab.sendx.electra.model.SendxElectraResponse;
import com.ireslab.sendx.electra.model.TokenTransferRequest;
import com.ireslab.sendx.entity.Account;
import com.ireslab.sendx.entity.CheckoutBankDetail;
import com.ireslab.sendx.entity.Country;
import com.ireslab.sendx.entity.Notification;
import com.ireslab.sendx.entity.Profile;
import com.ireslab.sendx.exception.BusinessException;
import com.ireslab.sendx.model.CheckoutBankDetailsDto;
import com.ireslab.sendx.model.CheckoutBanksResponse;
import com.ireslab.sendx.model.ContactDetails;
import com.ireslab.sendx.model.ContactListVerificationRequest;
import com.ireslab.sendx.model.ContactListVerificationResponse;
import com.ireslab.sendx.model.CountryDetails;
import com.ireslab.sendx.model.CountryListResponse;
import com.ireslab.sendx.model.MiscConfigDetailsResponse;
import com.ireslab.sendx.model.UserProfile;
import com.ireslab.sendx.notification.AndroidPushNotificationRequest;
import com.ireslab.sendx.notification.AndroidPushNotificationsService;
import com.ireslab.sendx.notification.CommonMethods;
import com.ireslab.sendx.notification.MailMessage;
import com.ireslab.sendx.notification.MailService;
import com.ireslab.sendx.notification.SendxConfig;
import com.ireslab.sendx.repository.AccountRepository;
import com.ireslab.sendx.repository.CheckoutBankDetailsRepository;
import com.ireslab.sendx.repository.CountryRepository;
import com.ireslab.sendx.repository.NotificationRepository;
import com.ireslab.sendx.service.CommonService;
import com.ireslab.sendx.service.TransactionalApiService;
import com.ireslab.sendx.util.AppStatusCodes;
import com.ireslab.sendx.util.CommonUtils;
import com.ireslab.sendx.util.PhoneNumberValidationUtils;
import com.ireslab.sendx.util.PropConstants;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerFontProvider;
import com.itextpdf.tool.xml.XMLWorkerHelper;

/**
 * @author iRESlab
 *
 */
@Service
public class CommonServiceImpl  implements CommonService {

	private static final Logger LOG = LoggerFactory.getLogger(CommonServiceImpl.class);
	private String FORMAT_SPECIFIER="%s";
	private static final String COUNTRY_CODE_PREFIX = "[+]";

	private static Map<String, CountryDetails> countryDetailsMap = new HashMap<>();

	@Autowired
	private CheckoutBankDetailsRepository checkoutBankDetailsRepo;

	@Autowired
	private CountryRepository countryRepo;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private ModelMapper modelMapper;

	@Autowired
	private SendxConfig sendxConfig;
	
	@Autowired
	private ElectraApiConfig electraApiConfig;
	
	@Autowired
	private TransactionalApiService transactionalApiService;
	
	@Autowired
	private MailService mailService;
	
	@Autowired
	private AndroidPushNotificationsService pushNotificationService;
	
	@Autowired
	private NotificationRepository notificationRepo;
	
	@Autowired
    private SpringTemplateEngine templateEngine;
	

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ireslab.sendx.service.CommonService#getCountryDetails(java.lang.
	 * String)
	 */
	@Override
	public CountryDetails getCountryDetails(String countryCode) {

		CountryDetails countryDetails = null;
		LOG.info("Getting country details based on country code - " + countryCode);

		if (countryDetailsMap.get(countryCode) == null) {
			LOG.info("Country details not found in memory map for country code - " + countryCode
					+ " | Getting details from database");

			Iterator<Country> countryIterator = countryRepo.findAll().iterator();
			while (countryIterator.hasNext()) {

				Country country = countryIterator.next();
				countryDetails = new CountryDetails();
				modelMapper.map(country, countryDetails);
				countryDetailsMap.put(country.getCountryDialCode(), countryDetails);
			}
		}

		countryDetails = countryDetailsMap.get(countryCode);
		LOG.debug("Country list sent ");

		return countryDetails;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ireslab.sendx.service.CommonService#getAllCountryDetails()
	 */
	@Override
	public CountryListResponse getAllCountryDetails() {

		List<CountryDetails> supportedCountryDetails = null;

		LOG.info("Getting supported countries details from database . . .");

		List<Country> countries = new ArrayList<>();
		countryRepo.findAll().forEach(countries::add);

		Iterator<Country> countryIterator = countries.listIterator();
		while (countryIterator.hasNext()) {

			Country country = countryIterator.next();
			CountryDetails countryDetails = new CountryDetails();
			modelMapper.map(country, countryDetails);
			countryDetailsMap.put(country.getCountryDialCode(), countryDetails);
		}

		java.lang.reflect.Type countryDetailsListType = new TypeToken<List<CountryDetails>>() {
		}.getType();

		supportedCountryDetails = modelMapper.map(countries, countryDetailsListType);

		return new CountryListResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS, PropConstants.SUCCESS,
				supportedCountryDetails, sendxConfig.appVersionCode, sendxConfig.appPlaystoreUrl,
				sendxConfig.appForceUpgradeMessage);
	}

	/**
	 * use to validate contacts.
	 * 
	 * @param contactListVerificationRequest
	 * @return
	 */
	@Override
	public ContactListVerificationResponse validateContacts(
			ContactListVerificationRequest contactListVerificationRequest) {

		List<ContactDetails> contactDetailsList = contactListVerificationRequest.getContactList();
		LOG.debug("No of contacts received : " + contactDetailsList.size());

		String region = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(Integer
				.parseInt(contactListVerificationRequest.getCountryDialCode().replaceAll(COUNTRY_CODE_PREFIX, "")));

		LOG.debug("Default region for contacts verfication - " + region);

		contactDetailsList.forEach((contactDetails) -> {

			String mobileNumber = contactDetails.getMobileNumber();
			mobileNumber = StringUtils.trim(mobileNumber);
			mobileNumber = mobileNumber.replaceAll("[^0-9]", "");

			String[] phoneNumber = PhoneNumberValidationUtils
					.validateMobNoWithCountryAbbv(contactDetails.getMobileNumber(), region);

			if (phoneNumber != null && phoneNumber.length > 1 && countryDetailsMap.get(phoneNumber[0]) != null) {
				contactDetails.setIsNumberValid(true);
				contactDetails.setCountryDialCode(phoneNumber[0]);
				contactDetails.setMobileNumber(phoneNumber[1]);
			}
		});

		/*
		 * removing invalid contacts
		 */
		contactDetailsList.removeIf((eachDetail) -> (eachDetail.isNumberValid() == false));
		
		/*
		 * removing contacts with different country dial code
		 */
		if(!sendxConfig.isCrossBorderTransactionsEnabled()) {
		contactDetailsList.removeIf((eachDetail) -> (!eachDetail.getCountryDialCode().equals(contactListVerificationRequest.getCountryDialCode())));
		}
		// LOG.debug("No of valid contacts : " + contactDetailsList.size());

		// validating registered mobile & getting name
		contactDetailsList.forEach((validContactDetails) -> {
			String mobileNumber = validContactDetails.getCountryDialCode() + validContactDetails.getMobileNumber();
			String contactName = accountRepository.findNameByMobileNumberWithCountryCode(mobileNumber);
			
			if (contactName != null && !contactName.isEmpty()) {
				Account account =accountRepository.findBymobileNumber(new BigInteger(validContactDetails.getMobileNumber()));
				if(account!=null) {
					
					validContactDetails.setContactName(contactName);
					validContactDetails.setIsRegistered(true);
					
					validContactDetails.setProfileImageUrl(account.getProfileImageUrl());
			UserProfile		profile = transactionalApiService.invokeUserProfileAPI(account.getUserCorrelationId());
				
			if(profile.getAccountStatus().equals(com.ireslab.sendx.electra.utils.Status.TERMINATED)) {
				validContactDetails.setIsRegistered(false);
			}
				}
				
			}
			Country country = countryRepo.findCountryByCountryDialCode(validContactDetails.getCountryDialCode());
			validContactDetails.setIso4217CurrencyAlphabeticCode(country.getIso4217CurrencyAlphabeticCode());
		});

		

		LOG.info("No of valid contacts : " + contactDetailsList.size());

		ContactListVerificationResponse response = new ContactListVerificationResponse();
		response.setCode(AppStatusCodes.SUCCESS);
		response.setStatus(HttpStatus.OK.value());
		response.setContactList(contactDetailsList);
		return response;
	}

	/**
	 * @param countryDialCode
	 * @return
	 */
	@Override
	public CheckoutBanksResponse getCheckoutBanksDetails(String countryDialCode) {

		CheckoutBanksResponse checkoutBanksResponse = null;
		List<CheckoutBankDetailsDto> checkoutBankDetailsDtos = null;

		List<CheckoutBankDetail> checkoutBankDetailsList = checkoutBankDetailsRepo
				.findByCountry_CountryDialCode(countryDialCode);

		if (checkoutBankDetailsList == null) {
			LOG.error("No Banks supported for checkout for countryCode - " + countryDialCode);
			throw new BusinessException(HttpStatus.OK, AppStatusCodes.INVALID_REQUEST, PropConstants.INVALID_REQUEST);
		}

		java.lang.reflect.Type checkoutBankDetailsDtoType = new TypeToken<List<CheckoutBankDetailsDto>>() {
		}.getType();

		checkoutBankDetailsDtos = modelMapper.map(checkoutBankDetailsList, checkoutBankDetailsDtoType);

		checkoutBanksResponse = new CheckoutBanksResponse(HttpStatus.OK.value(), AppStatusCodes.SUCCESS,
				PropConstants.SUCCESS, checkoutBankDetailsDtos);

		return checkoutBanksResponse;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ireslab.sendx.service.CommonService#getMiscConfigDetails(java.math.
	 * BigInteger, java.lang.String)
	 */
	@Override
	public MiscConfigDetailsResponse getMiscConfigDetails(BigInteger mobileNumber, String countryDialCode) {

		MiscConfigDetailsResponse configDetailsResponse = new MiscConfigDetailsResponse(HttpStatus.OK.value(),
				AppStatusCodes.SUCCESS, PropConstants.SUCCESS);

		/* Commented - Only applicable in Livenet */
		// configDetailsResponse.setPayFundBankTransferMessage(
		// String.format(sendxConfig.payFundBankTransferMessage, new
		// String((countryDialCode + mobileNumber))));

		return configDetailsResponse;
	}

	@Override
	public ProductResponse getProductList(ProductRequest productRequest) {
		ProductResponse productList = new ProductResponse();
		
		Account account = accountRepository.findBymobileNumber(new BigInteger(productRequest.getMobileNumber()));
		Profile profile =account.getProfile();
		
		ClientInfoRequest clientInfoRequest = new ClientInfoRequest();
		
		clientInfoRequest.setEmail(profile.getEmailAddress());
		
		ClientInfoResponse clientInformation = transactionalApiService.clientInformation(clientInfoRequest);
		
		String productEndPointUrl;
		
		if (clientInformation.getClientCorrelationId()!=null && clientInformation.getCode()!=101) {
			productEndPointUrl = String.format(electraApiConfig.getProductDetailsApiEndpointUrl(),
					clientInformation.getClientCorrelationId(), FORMAT_SPECIFIER);
			productList = transactionalApiService.getProductList(productEndPointUrl, productRequest);
			
			//MerchantDto merchantDto = productList.getMerchantDetails();
			com.ireslab.sendx.electra.dto.MerchantDto merchantDto = productList.getMerchantDetails();
			
			merchantDto.setFirstName(profile.getFirstName());
			merchantDto.setLastName(profile.getLastName());
			merchantDto.setCountryDialCode(profile.getCountry().getCountryDialCode());
			productList.setMerchantDetails(merchantDto);
			productList.setCurrencyCode(profile.getCountry().getIso4217CurrencyAlphabeticCode());
			
			return productList;
		}
	
		productList.setCode(101);
		productList.setMessage("Please enter correct client QR code ");
		return productList;
	}

	@Override
	 public PaymentResponse makePayment(PaymentRequest paymentRequest) {
	  PaymentResponse paymentResponse  = new PaymentResponse();
	  String makePaymentEndPointUrl =String.format(electraApiConfig.getMakePaymentApiEndpointUrl(),FORMAT_SPECIFIER);
	     
	  	  
	  TokenTransferRequest tokenTransferRequest =new TokenTransferRequest();
	  
	// Get seller account details from electra server based on unique code.
	UserProfile merchantProfile=transactionalApiService.searchUserProfileByUniqueCode(paymentRequest.getMerchantDetails().getUniqueCode());
	 
	  // Get purchaser account details from sendx server.
	   Account userAccount = accountRepository.findBymobileNumber(new BigInteger(paymentRequest.getPurchaserDetails().getMobileNumber()));
	  
	   //Initializing product list
	   List<com.ireslab.sendx.electra.dto.ProductDto> productList = paymentRequest.getProductList();
		   
		   ExchangeRequest exchangeRequest = new ExchangeRequest();
	    	exchangeRequest.setExchangeToken(countryRepo.findCountryByCountryDialCode(merchantProfile.getCountryDialCode()).getIso4217CurrencyAlphabeticCode());
	    	exchangeRequest.setNativeCurrency(userAccount.getCountry().getIso4217CurrencyAlphabeticCode());
	    	
	    	//Get exchange rate from electra server.
	    	ExchangeResponse exchangeResponse = transactionalApiService.getExchangeRate(exchangeRequest);
	    	com.ireslab.sendx.electra.dto.ExchangeDto exchangeDto = exchangeResponse.getExchangeList().get(0);
	    	double totalAmount=0.0;
	    	
	    if (productList.size()>0 && !productList.isEmpty()) { 
	    	boolean isgsnt = false;
			boolean isgstAppliend = false;
			
			if (paymentRequest.getMerchantDetails().getGstn() != null && !StringUtils.isEmpty(paymentRequest.getMerchantDetails().getGstn()) && !paymentRequest.getMerchantDetails().getGstn().equalsIgnoreCase("null")) {
				
				isgsnt = true;
				
				
				for(com.ireslab.sendx.electra.dto.ProductDto productDto : productList) {
					if(Double.parseDouble(productDto.getSgstUtgst()) != 0.00 && Double.parseDouble(productDto.getIgst())  != 0.00 && Double.parseDouble(productDto.getCgst())  != 0.00 ) {
						isgstAppliend = true;
						
					}
						
				}
				
				
			}
	    	for (com.ireslab.sendx.electra.dto.ProductDto productDto : productList) { //for loop for iterate product list and product based calculations.
	    		
	    		ProductAvailabilityRequest productAvailabilityRequest = new ProductAvailabilityRequest();
	    		productAvailabilityRequest.setOrderQuantity(productDto.getPurchasedQty());
	    		productAvailabilityRequest.setProductCode(productDto.getProductCode());
	    		
                double itemTotal = 0.00;
				
				if(isgsnt && isgstAppliend) {
					itemTotal = Double.parseDouble(productDto.getTotal())
							* Double.parseDouble(productDto.getPurchasedQty());
				}else {
					itemTotal = Double.parseDouble(productDto.getTotalItemPrice()) - (Double.parseDouble(productDto.getDiscount())*Double.parseDouble(productDto.getPurchasedQty()));
				}
				
				
				totalAmount = totalAmount + (itemTotal/* + gstAmount*/);
			}
			
			tokenTransferRequest.setClientId(merchantProfile.getUserCorrelationId());
			tokenTransferRequest.setNoOfToken(String.valueOf(Math.round(totalAmount * 100.0) / 100.0)); // Calculate and set total no. of token to be transfer.
			
			tokenTransferRequest.setReceiverCorrelationId(merchantProfile.getUserCorrelationId());
			
			tokenTransferRequest.setSenderCorrelationId(userAccount.getUserCorrelationId());
			paymentRequest.setTokenTransferRequest(tokenTransferRequest);
		}
	   
	 
	    com.ireslab.sendx.electra.dto.PurchaserDto purchaserDto = paymentRequest.getPurchaserDetails();
	    	purchaserDto.setClient(false);
	    	paymentRequest.setPurchaserDetails(purchaserDto);
	    	
	    	//Generate Invoice number and receipt number.
	    	String invoiceAndReceiptNumber = org.apache.commons.lang3.StringUtils
					.leftPad(String.valueOf(paymentRequest.getNotificationId()), 6, "0");
			invoiceAndReceiptNumber = "RXC-" + invoiceAndReceiptNumber;
			paymentRequest.setInvoiceNumber(invoiceAndReceiptNumber);
	    	savePurchasedProduct(paymentRequest); 
	    	
	    	
	    	
	        
	    	 if(!paymentRequest.isOffline()) {
	    		 
	    		 //transfer amount to client account and update ledger..
	    		 tokenTransferRequest
	             .setNoOfToken(String.valueOf(Math.round(totalAmount * Double.parseDouble(exchangeDto.getExchangeRate()) * 100.0) / 100.0));
	    		 paymentResponse = transactionalApiService.makePayment(makePaymentEndPointUrl, paymentRequest);
	    		 paymentRequest.setTransactionId(paymentResponse.getTransactionDto().getTransactionSequenceNo());
	    		 generateReceiptInvoice(paymentRequest);
	     }
	    	 else {
	    		//Delete notification for successful payment
	    		 if(paymentRequest.getNotificationId()!=null) {
	    			 NotificationRequest deleteNotificationRequest = new NotificationRequest();
		    	      com.ireslab.sendx.electra.dto.NotificationDto deleteNotificationDto = new com.ireslab.sendx.electra.dto.NotificationDto();
		    	      deleteNotificationDto.setNotificationId(Integer.parseInt(paymentRequest.getNotificationId()));
		    	      deleteNotificationRequest.setNotificationDto(deleteNotificationDto);
		    	      deleteNotificationData(deleteNotificationRequest);
	 	    		
	 	    	}
	    		 //update offline ledger.
	    		 paymentResponse = transactionalApiService.makeOfflinePayment(tokenTransferRequest);
	    		 AndroidPushNotificationRequest androidPushNotificationRequestForUser = new AndroidPushNotificationRequest();
	    		 JSONObject bodyForUser = new JSONObject();
	    		 JSONObject notificationForUser = new JSONObject();
	    	     JSONObject dataForUser = new JSONObject();
	    	     
	    	     
	    	   //Sending push notification to seller.
	    	      try {
	    			bodyForUser.put("to", /*"/topics/" +*/ merchantProfile.getGcmRegisterKey());
	    			  bodyForUser.put("priority", "high");
	    			  	notificationForUser.put("title", "Electra Notification");
	    			  	
	    			  	dataForUser.put("message", "Amount Paid!");
	    			  	dataForUser.put("code", "103");
	    			  bodyForUser.put("notification", notificationForUser);
	    			  bodyForUser.put("data", dataForUser);
	    			
	    			  List<JSONObject> productArray = new ArrayList<>(); 
	    			  
	    			  for (com.ireslab.sendx.electra.dto.ProductDto object : paymentRequest.getProductList()) {
	    				  JSONObject product = new JSONObject();
	    				 
	    				product.put("productCode", object.getProductCode());
	  					product.put("itemNameOrDesc", object.getItemNameOrDesc());
	  					product.put("itemPrice", object.getItemPrice());
	  					product.put("invoiceType", object.getInvoiceType());
	  					product.put("discountPercentage", object.getDiscountPercentage());
	  					product.put("discount", object.getDiscount());
	  					product.put("subTotal", object.getSubTotal());
	  					product.put("purchasedQty", object.getPurchasedQty());
	  					product.put("totalItemPrice", object.getTotalItemPrice());
	  					product.put("totalTaxInclusive", object.getTotalTaxInclusive());
	  					product.put("cgst", object.getCgst());
	  					product.put("sgstUtgst", object.getSgstUtgst());
	  					product.put("igst", object.getIgst());
	  					product.put("total", object.getTotal());
	  					product.put("interState", object.getInterState());
	  					product.put("itemCode", object.getItemCode());
	  					product.put("paymentTerms", object.getPaymentTerms());
						product.put("customerNotes", object.getCustomerNotes());
						product.put("termsAndConditions", object.getTermsAndConditions());
	    				  productArray.add(product);
	    			}
	    			  
	    			  bodyForUser.put("productDetails", productArray);
	    			  
	    			  JSONObject merchantDetails = new JSONObject();
	    			
	    			  com.ireslab.sendx.electra.dto.MerchantDto merchantData = paymentRequest.getMerchantDetails();
	    			  merchantDetails.put("countryDialCode",merchantData.getCountryDialCode() );
	    			  merchantDetails.put("mobileNumber", paymentRequest.getMerchantDetails().getMobileNumber());
	    			  merchantDetails.put("firstName", merchantProfile.getFirstName());
	    			  merchantDetails.put("lastName", merchantProfile.getLastName());
	    			  merchantDetails.put("emailAdd", merchantProfile.getEmailAddress());
	    			  merchantDetails.put("companyCode", merchantData.getCompanyCode());
	    			  merchantDetails.put("uniqueCode", paymentRequest.getMerchantDetails().getUniqueCode());
	    			  merchantDetails.put("gstn", merchantData.getGstn());
	    			  bodyForUser.put("merchantDetails", merchantDetails);
	    			  
	    			  JSONObject purchaserDetailsJson = new JSONObject();
	    			  purchaserDetailsJson.put("countryDialCode",userAccount.getCountry().getCountryDialCode() );
	    			  purchaserDetailsJson.put("mobileNumber", ""+userAccount.getMobileNumber());
	    			  purchaserDetailsJson.put("firstName", userAccount.getProfile().getFirstName());
	    			  purchaserDetailsJson.put("lastName", userAccount.getProfile().getLastName());
	    			  purchaserDetailsJson.put("emailAdd", userAccount.getProfile().getEmailAddress());
	    			  purchaserDetailsJson.put("uniqueCode", userAccount.getUniqueCode());
	    			  bodyForUser.put("purchaserDetails", purchaserDetailsJson);
	    			  
	    			  bodyForUser.put("invoiceNumber", paymentRequest.getInvoiceNumber());
	    			  bodyForUser.put("transactionId", paymentResponse.getTransactionDto().getTransactionSequenceNo());
	    			  
	    			 		} catch (JSONException e) {
	    			paymentResponse.setCode(101);
	    		      paymentResponse.setMessage("Failed to issued invoice.");
	    		      
	    			e.printStackTrace();
	    			return paymentResponse;
	    		}
	    	     
	    	      androidPushNotificationRequestForUser.setBody(bodyForUser);
	    	      androidPushNotificationRequestForUser.setFirebaseServiceKey(merchantProfile.getFirebaseServiceKey());
	    	      
	    	      pushNotification(androidPushNotificationRequestForUser);
	    	     
	    	      
	    	      //save notification data
	    	      NotificationRequest notificationRequest = new NotificationRequest();
	    	      com.ireslab.sendx.electra.dto.NotificationDto notificationDto = new com.ireslab.sendx.electra.dto.NotificationDto();
	    	      notificationDto.setCorrelationId(merchantProfile.getUserCorrelationId());
	    	      notificationDto.setEmailAddress(merchantProfile.getEmailAddress());
	    	      notificationDto.setMobileNumber(""+merchantProfile.getMobileNumber());
	    	      notificationDto.setNotificationData(bodyForUser.toString());
	    	      notificationDto.setStatus(false);
	    	      notificationDto.setInvoice(true);
	    	      notificationDto.setIsOffline(paymentRequest.isOffline());
	    	      notificationDto.setIsPaymentConfirm(true);
	    	      notificationRequest.setNotificationDto(notificationDto);
	    	      NotificationResponse notificationResponse =  saveNotificationData(notificationRequest);
	    	      
	    	      
	    	      
	    	      paymentResponse.setCode(100);
	    	      paymentResponse.setMessage("Payment has been completed successfully.");
	    	 }
	    	 
	    	
	           
	            return paymentResponse;
	 }
	
	
	/**
	 * use to save notification data in database.
	 * 
	 * @param notificationRequest
	 * @return
	 */
	private NotificationResponse saveNotificationData(NotificationRequest notificationRequest) {
		NotificationResponse notificationResponse = transactionalApiService.saveNotificationData(notificationRequest);
		return notificationResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#generateReceiptInvoice(com.ireslab.sendx.electra.model.PaymentRequest)
	 */
	@Override
	public PaymentResponse generateReceiptInvoice(PaymentRequest paymentRequest) {
		
		//Get merchant account details form electra server based on unique code.
		UserProfile merchantProfile=transactionalApiService.searchUserProfileByUniqueCode(paymentRequest.getMerchantDetails().getUniqueCode());
		
		   ClientInfoRequest clientInfoRequest = new ClientInfoRequest();
		   clientInfoRequest.setEmail(merchantProfile.getEmailAddress());
		
		  // Get purchaser account details from electra server based on unique code
		   UserProfile purchaserProfile=transactionalApiService.searchUserProfileByUniqueCode(paymentRequest.getPurchaserDetails().getUniqueCode());
		  
		   ClientInfoResponse clientInformation = transactionalApiService.clientInformation(clientInfoRequest);
		   
		   //sending push notification to merchant
		   AndroidPushNotificationRequest androidPushNotificationRequestForMerchant = new AndroidPushNotificationRequest();
	        AndroidPushNotificationRequest androidPushNotificationRequestForUser = new AndroidPushNotificationRequest();
	        JSONObject bodyForMerchant = new JSONObject();
	        JSONObject bodyForUser = new JSONObject();
	        JSONObject notificationForMerchant = new JSONObject();
	     JSONObject notificationForUser = new JSONObject();
	     JSONObject dataForMerchant = new JSONObject();
	     JSONObject dataForUser = new JSONObject();
	     LOG.debug("\n Merchant GCM Key : "+merchantProfile.getGcmRegisterKey()+"\n User GCM Key : "+purchaserProfile.getGcmRegisterKey());
	     try {
	     
	      bodyForMerchant.put("to",  merchantProfile.getGcmRegisterKey());
	      bodyForMerchant.put("priority", "high");
	         notificationForMerchant.put("title", "Electra Notification");
	         notificationForMerchant.put("body", "Receipt has been sent out!");
	         dataForMerchant.put("message", "Receipt has been sent out!");
	         dataForMerchant.put("code", "102");
	    bodyForMerchant.put("notification", notificationForMerchant);
	    bodyForMerchant.put("data", dataForMerchant);
	    bodyForMerchant.put("Message","Electra Notification for Merchant" );
	    androidPushNotificationRequestForMerchant.setBody(bodyForMerchant);
	    androidPushNotificationRequestForMerchant.setFirebaseServiceKey(merchantProfile.getFirebaseServiceKey());
	      
	  //sending push notification to purchaser
	      bodyForUser.put("to", purchaserProfile.getGcmRegisterKey());
	      bodyForUser.put("priority", "high");
	      	notificationForUser.put("title", "Electra Notification");
	      	notificationForUser.put("body", "Receipt has been received!");
	      	dataForUser.put("message", "Receipt has been received!");
	      	dataForUser.put("code", "102");
	      bodyForUser.put("notification", notificationForUser);
	      bodyForUser.put("data", dataForUser);
	      
	      androidPushNotificationRequestForUser.setBody(bodyForUser);
	      androidPushNotificationRequestForUser.setFirebaseServiceKey(purchaserProfile.getFirebaseServiceKey());
	      
	     
	      pushNotification(androidPushNotificationRequestForUser);
	      pushNotification(androidPushNotificationRequestForMerchant);
	      
	      
	     
	      

	     } catch (JSONException e) {
	      
	      e.printStackTrace();
	     }
		   
	   //Delete notification for successful payment
	     if(paymentRequest.getNotificationId()!=null) {
	    	 
	    	 NotificationRequest deleteNotificationRequest = new NotificationRequest();
   	         com.ireslab.sendx.electra.dto.NotificationDto deleteNotificationDto = new com.ireslab.sendx.electra.dto.NotificationDto();
   	         deleteNotificationDto.setNotificationId(Integer.parseInt(paymentRequest.getNotificationId()));
   	         deleteNotificationRequest.setNotificationDto(deleteNotificationDto);
   	         deleteNotificationData(deleteNotificationRequest);
	    		
	    	}
		   
		
		
		String invoice=generateInvoiceOrReceipt(paymentRequest, merchantProfile, purchaserProfile, clientInformation, "INVOICE");
    	String receipt=generateInvoiceOrReceipt(paymentRequest, merchantProfile, purchaserProfile, clientInformation, "RECEIPT");
    	
    	
    	
    	
    	 File invoiceFile=new File("./invoice.pdf");
		 File receiptFile=new File("./receipt.pdf");
		 
		 String catalinaHome = System.getenv("CATALINA_HOME");
			catalinaHome = (catalinaHome == null) ? System.getProperty("catalina.home") : catalinaHome;

			StringBuilder directory = new StringBuilder();
			directory.append(catalinaHome);
			directory.append(File.separator);
			directory.append("webapps");
			directory.append(File.separator);
			directory.append("pdf");

			String sFontDir = directory.toString();
			

			XMLWorkerFontProvider fontImp = new XMLWorkerFontProvider(sFontDir, null);
			FontFactory.setFontImp(fontImp);
		 
         Document documentForInvoice = new Document(PageSize.LETTER);
         Document documentForReceipt = new Document(PageSize.LETTER);
         PdfWriter pdfWriterForInvoice = null;
         PdfWriter pdfWriterForReceipt = null;
         try {
        	 pdfWriterForInvoice = PdfWriter.getInstance(documentForInvoice, new FileOutputStream(invoiceFile));
        	 pdfWriterForReceipt = PdfWriter.getInstance(documentForReceipt, new FileOutputStream(receiptFile));
		} catch (FileNotFoundException e) {
			
			e.printStackTrace();
		} catch (DocumentException e) {
			
			e.printStackTrace();
		}
		
         documentForReceipt.open();
         documentForInvoice.open();
         XMLWorkerHelper workerForInvoice = XMLWorkerHelper.getInstance();
         XMLWorkerHelper workerForReceipt = XMLWorkerHelper.getInstance();
         
         try {
 			
        	 
        	 workerForReceipt.parseXHtml(pdfWriterForReceipt, documentForReceipt,
 					new ByteArrayInputStream(receipt.getBytes()), null, null, fontImp);

 			

        	 workerForInvoice.parseXHtml(pdfWriterForInvoice, documentForInvoice,
 					new ByteArrayInputStream(invoice.getBytes()), null, null, fontImp);
 		} catch (IOException e) {
 			
 			e.printStackTrace();
 		}
         documentForReceipt.close();
         documentForInvoice.close();
    	
    	
    	
    	MailMessage mailMessage = new MailMessage();
		mailMessage.setToEmailAddresses(new String[] { purchaserProfile.getEmailAddress() });
        mailMessage.setSubject("Electra Invoice & Receipt Dated "+CommonUtils.formatDate(new Date(), "dd/MM/yyyy"));

		
		//"Please find the attachment"
		
		EmailRequest emailRequestForPurchaser = new EmailRequest();
		PurchaserDto purchaserDto = new PurchaserDto();
		purchaserDto.setFirstName(purchaserProfile.getFirstName());
		purchaserDto.setTransactionId(paymentRequest.getTransactionId());
		emailRequestForPurchaser.setPurchaserDetails(purchaserDto);
		emailRequestForPurchaser.setMerchanDetails(null);
			
		
		mailMessage.setMessageBody(CommonMethods.formateEmailBodyForReceiptAndInvoice(emailRequestForPurchaser));
		mailService.sendEmailInvoiceAndReceipt(mailMessage,new File("./invoice.pdf"), new File("./receipt.pdf"));
		LOG.info("mail sent to seller ! ");
		
		String invoiceClient=generateInvoiceOrReceiptForClient(paymentRequest, merchantProfile, purchaserProfile, clientInformation, "INVOICE");
    	String receiptClient=generateInvoiceOrReceiptForClient(paymentRequest, merchantProfile, purchaserProfile, clientInformation, "RECEIPT");
		
		File invoiceFileClient=new File("./invoiceClient.pdf");
		File receiptFileClient=new File("./receiptClient.pdf");
        Document documentForInvoiceClient = new Document(PageSize.LETTER);
        Document documentForReceiptClient = new Document(PageSize.LETTER);
        PdfWriter pdfWriterForInvoiceClient = null;
        PdfWriter pdfWriterForReceiptClient = null;
        try {
        	pdfWriterForInvoiceClient = PdfWriter.getInstance(documentForInvoiceClient, new FileOutputStream(invoiceFileClient));
        	pdfWriterForReceiptClient = PdfWriter.getInstance(documentForReceiptClient, new FileOutputStream(receiptFileClient));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DocumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
        documentForReceiptClient.open();
        documentForInvoiceClient.open();
        XMLWorkerHelper workerForInvoiceClient = XMLWorkerHelper.getInstance();
        XMLWorkerHelper workerForReceiptClient = XMLWorkerHelper.getInstance();
        
        try {
        	
        	
        	workerForReceiptClient.parseXHtml(pdfWriterForReceiptClient, documentForReceiptClient,
					new ByteArrayInputStream(receiptClient.getBytes()), null, null, fontImp);

			

			workerForInvoiceClient.parseXHtml(pdfWriterForInvoiceClient, documentForInvoiceClient,
					new ByteArrayInputStream(invoiceClient.getBytes()), null, null, fontImp);
        	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        documentForReceiptClient.close();
        documentForInvoiceClient.close();
		
		
		MailMessage userMailMessage = new MailMessage();
		userMailMessage.setToEmailAddresses(new String[] { merchantProfile.getEmailAddress() });
		EmailRequest emailRequestForMerchant = new EmailRequest();
		com.ireslab.sendx.electra.dto.MerchantDto merchantDto = new com.ireslab.sendx.electra.dto.MerchantDto();
		merchantDto.setFirstName(merchantProfile.getFirstName());
		merchantDto.setTransactionId(paymentRequest.getTransactionId());
		emailRequestForMerchant.setMerchanDetails(merchantDto);
		emailRequestForMerchant.setPurchaserDetails(null);
		
		
		userMailMessage.setSubject("Electra Invoice & Receipt Dated "+CommonUtils.formatDate(new Date(), "dd/MM/yyyy"));

		userMailMessage.setMessageBody(CommonMethods.formateEmailBodyForReceiptAndInvoice(emailRequestForMerchant));
		mailService.sendEmailInvoiceAndReceipt(userMailMessage,new File("./invoiceClient.pdf"), new File("./receiptClient.pdf"));
		LOG.debug("mail sent to purchaser ! ");
		
		PaymentResponse paymentResponse = new PaymentResponse();
		paymentResponse.setCode(100);
		paymentResponse.setStatus(HttpStatus.OK.value());
		paymentResponse.setMessage("Payment has been done successfully.");
		
		
		return paymentResponse;
	}	
	
	
	/**
	 * use to send push notification.
	 * 
	 * @param androidPushNotificationRequest
	 * @return
	 */
	private String pushNotification(AndroidPushNotificationRequest androidPushNotificationRequest) {
		  return pushNotificationService.sendPushNotification(androidPushNotificationRequest);
		 }
	
	/**
	 * This function use to generate invoice and receipt.
	 * 
	 * @param paymentRequest
	 * @param clientAccount
	 * @param purchaserProfile
	 * @param clientInformation
	 * @param type
	 * @return
	 */
	private String generateInvoiceOrReceipt(PaymentRequest paymentRequest, UserProfile clientAccount, UserProfile purchaserProfile,ClientInfoResponse clientInformation, String type) {
		String outPutString=null;
		
		String merchantName;
    	if(clientInformation.getClientName() != null) {
    		merchantName = clientInformation.getClientName();
    	}else {
    		merchantName = paymentRequest.getMerchantDetails().getFirstName()+" "+paymentRequest.getMerchantDetails().getLastName();
    	}
		
		
    	
    	Country country = countryRepo.findCountryByCountryDialCode(purchaserProfile.getCountryDialCode());
    	Country merchantCountry = countryRepo.findCountryByCountryDialCode(clientAccount.getCountryDialCode());
    	
		
		StringBuilder tabledata = new StringBuilder();
		
		Double tolal = 0.0;
		Double grandTotal = 0.0;
		Double gstTotal = 0.0;
		Double discount = 0.0;
		DecimalFormat decimalFormat = new DecimalFormat("0.00");
		String invoiceType = "Invoice";
		String paymentTerm = "";
		StringBuilder customerNotes = new StringBuilder();
		StringBuilder termsAndCondition = new StringBuilder();
		List<String> customerNotesList = new ArrayList<String>();
		List<String> termsAndConditionList = new ArrayList<String>();
		boolean isgsnt = false;
		boolean isgstAppliend = false;
		
		ExchangeRequest exchangeRequest = new ExchangeRequest();
    	exchangeRequest.setExchangeToken(merchantCountry.getIso4217CurrencyAlphabeticCode());
    	exchangeRequest.setNativeCurrency(country.getIso4217CurrencyAlphabeticCode());
    	
    	ExchangeResponse exchangeResponse = transactionalApiService.getExchangeRate(exchangeRequest);
    	com.ireslab.sendx.electra.dto.ExchangeDto exchangeDto = exchangeResponse.getExchangeList().get(0);
		
		
		
    	List<com.ireslab.sendx.electra.dto.ProductDto> productList = paymentRequest.getProductList();
    	String gstNo = "";
		if (paymentRequest.getMerchantDetails().getGstn() != null && !StringUtils.isEmpty(paymentRequest.getMerchantDetails().getGstn()) && !paymentRequest.getMerchantDetails().getGstn().equalsIgnoreCase("null")) {
			
			isgsnt = true;
			
			
			for(com.ireslab.sendx.electra.dto.ProductDto productDto : productList) {
				if(Double.parseDouble(productDto.getSgstUtgst()) != 0.00 && Double.parseDouble(productDto.getIgst())  != 0.00 && Double.parseDouble(productDto.getCgst())  != 0.00 ) {
					isgstAppliend = true;
					gstNo = "GSTIN: "+paymentRequest.getMerchantDetails().getGstn();
					invoiceType = "TAX-INVOICE";
				}
					
			}
			
			
		}
		
		int count = 0;
			if(type.equals("INVOICE")) {
				int paymentTermId = 0;
				if(isgsnt && isgstAppliend) {
					
				for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) { //for loop for product based calculation for invoice.
														
					
					String productCost = decimalFormat.format(Double.parseDouble(productDto.getItemPrice())
							* Double.parseDouble(exchangeDto.getExchangeRate()));
					
					double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getDiscount())*Double.parseDouble(exchangeDto.getExchangeRate()));
					
					double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice())*Double.parseDouble(exchangeDto.getExchangeRate()));
					
					double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getTotalTaxInclusive())*Double.parseDouble(exchangeDto.getExchangeRate()));
					double netTotal = gstAmount + (itemTotalClac - itemDiscount);
					//String itemTotal = decimalFormat.format(itemTotalClac);
					
					
					tabledata.append("<tr><td >");
					tabledata.append(++count);
					tabledata.append("</td><td class=\"text-left\" >");
					tabledata.append(productDto.getItemNameOrDesc());
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(productDto.getItemCode());
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(productCost);
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
					tabledata.append("</td><td class=\"text-right\" >");
					
					if(productDto.getInterState()) {
						if(productDto.getIgst() == null || new Double(productDto.getIgst()) ==0.00) {
							tabledata.append("NIL");
						}else {
							tabledata.append("IGST@ "+String.format("%.2f", new Double(productDto.getIgst()))+"");
						}
						
						
					}else {
						if((productDto.getCgst() == null || new Double(productDto.getCgst()) ==0.00) || (productDto.getSgstUtgst() == null || new Double(productDto.getSgstUtgst()) ==0.00)) {
							tabledata.append("NIL");
						}else {
							tabledata.append("CGST@ "+String.format("%.2f", new Double(productDto.getCgst()))+"<br/>SGST@ "+String.format("%.2f", new Double(productDto.getSgstUtgst()))+"");
						}
						
					}
					
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(decimalFormat.format(netTotal));
					tabledata.append("</td></tr>");
			
			tolal = tolal + itemTotalClac;
			
			grandTotal = grandTotal + netTotal;
			
			gstTotal = gstTotal+ gstAmount;
			
			discount = discount + itemDiscount;
			
			if(productDto.getInvoiceType().equalsIgnoreCase("tax-invoice")) {
				//invoiceType = "TAX-INVOICE";
			}
			if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
				paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
			}
			
			
			if(productDto.getCustomerNotes() != null && !StringUtils.isAllEmpty(productDto.getCustomerNotes())) {						
				customerNotesList.add(productDto.getCustomerNotes());
			}
			
			if(productDto.getTermsAndConditions() != null && !StringUtils.isAllEmpty(productDto.getTermsAndConditions())) {
			
			termsAndConditionList.add(productDto.getTermsAndConditions());
			}
				}//end of for loop
				
				}
				else {
					
					for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) { //for loop for product based calculation for invoice
						
												
						
						String productCost = decimalFormat.format(Double.parseDouble(productDto.getItemPrice())
								* Double.parseDouble(exchangeDto.getExchangeRate()));
						
						double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
								* Double.parseDouble(productDto.getDiscount())*Double.parseDouble(exchangeDto.getExchangeRate()));
						
						double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice())*Double.parseDouble(exchangeDto.getExchangeRate()));
						
						double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
								* Double.parseDouble(productDto.getTotalTaxInclusive())*Double.parseDouble(exchangeDto.getExchangeRate()));
						double netTotal = (itemTotalClac - itemDiscount);
						//String itemTotal = decimalFormat.format(itemTotalClac);
						
						
						tabledata.append("<tr><td >");
						tabledata.append(++count);
						tabledata.append("</td><td class=\"text-left\" >");
						tabledata.append(productDto.getItemNameOrDesc());
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
						tabledata.append("</td><td class=\"text-right\" >");
						
						tabledata.append(productCost);
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
						
						
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(decimalFormat.format(netTotal));
						tabledata.append("</td></tr>");
				
				tolal = tolal + itemTotalClac;
				
				grandTotal = grandTotal + netTotal;
				
				gstTotal = gstTotal+ gstAmount;
				
				discount = discount + itemDiscount;
				
				if(productDto.getInvoiceType().equalsIgnoreCase("tax-invoice")) {
					//invoiceType = "TAX-INVOICE";
				}
				if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
					paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
				}
				
				
				if(productDto.getCustomerNotes() != null && !StringUtils.isAllEmpty(productDto.getCustomerNotes())) {						
					customerNotesList.add(productDto.getCustomerNotes());
				}
				
				if(productDto.getTermsAndConditions() != null && !StringUtils.isAllEmpty(productDto.getTermsAndConditions())) {
				
				termsAndConditionList.add(productDto.getTermsAndConditions());
				}
					}//end of for loop
					
				}
				
				if(paymentTermId == 0) {
					paymentTerm = "Pay on Delivery";
				}
				else {
					paymentTerm = paymentTermId+" Days";
				}
			}
			
			if(customerNotesList.size()<1) {
				customerNotes.append("<br/>");
				customerNotes.append("<br/>");
				customerNotes.append("<br/>");
				customerNotes.append("<br/>");
			}
			else if(customerNotesList.size() == 1) {
				customerNotes.append(customerNotesList.get(0));
			}else {
				customerNotes.append("<ol style=\"padding-left:15px;\">");
				for(String customerNote:customerNotesList) {
					customerNotes.append("<li>");
					customerNotes.append(customerNote);
					customerNotes.append("</li>");
				}
				customerNotes.append("</ol>");
			}
			
			
			
			if(termsAndConditionList.size()<1) {
				termsAndCondition.append("<br/>");
				termsAndCondition.append("<br/>");
				termsAndCondition.append("<br/>");
				termsAndCondition.append("<br/>");
			}
			else if(termsAndConditionList.size() == 1) {
				termsAndCondition.append(termsAndConditionList.get(0));
			}else {
				termsAndCondition.append("<ol style=\"padding-left:15px;\">");
				for(String termAndCondition:termsAndConditionList) {
					termsAndCondition.append("<li>");
					termsAndCondition.append(termAndCondition);
					termsAndCondition.append("</li>");
				}
				termsAndCondition.append("</ol>");
			}
			
			
			
			
			if(type.equals("RECEIPT")) {
				int paymentTermId = 0;
				
				if(isgsnt && isgstAppliend) {
				
				for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) {// for loop for product based calculation for receipt.
					
					
					double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getDiscount())* Double.parseDouble(exchangeDto.getExchangeRate()));
					
					double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice())* Double.parseDouble(exchangeDto.getExchangeRate()));
					
					double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getTotalTaxInclusive())* Double.parseDouble(exchangeDto.getExchangeRate()));
					double netTotal = gstAmount + (itemTotalClac - itemDiscount);
					//String itemTotal = decimalFormat.format(itemTotalClac);
					
					
					tabledata.append("<tr><td>");
					tabledata.append(++count);
					
					tabledata.append("</td><td>");
					tabledata.append(productDto.getItemNameOrDesc());
					tabledata.append("</td><td class=\"text-right\">");
					tabledata.append(productDto.getItemCode());
					tabledata.append("</td><td class=\"text-right\">");
					tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
					tabledata.append("</td><td class=\"text-right\">");
					tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
					tabledata.append("</td><td class=\"text-right\">");
					
					if(productDto.getInterState()) {
						if(productDto.getIgst() == null || new Double(productDto.getIgst()) ==0.00) {
							tabledata.append("NIL");
						}else {
							tabledata.append("IGST@ "+String.format("%.2f", new Double(productDto.getIgst()))+"");
						}
						
						
					}else {
						if((productDto.getCgst() == null || new Double(productDto.getCgst()) ==0.00) || (productDto.getSgstUtgst() == null || new Double(productDto.getSgstUtgst()) ==0.00)) {
							tabledata.append("NIL");
						}else {
							tabledata.append("CGST@ "+String.format("%.2f", new Double(productDto.getCgst()))+"<br/>SGST@ "+String.format("%.2f", new Double(productDto.getSgstUtgst()))+"");
						}
						
					}
					
					tabledata.append("</td><td class=\"text-right\">");
					tabledata.append(decimalFormat.format(new Double(netTotal)));
					tabledata.append("</td></tr>");
				
				tolal = tolal + itemTotalClac;
				
                grandTotal = grandTotal + netTotal;
                
                discount = discount + itemDiscount;
				
				gstTotal = gstTotal+ gstAmount;
				
				if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
					paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
				}
				}
			}
		else {
			for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) {// for loop for product based calculation for receipt.
				
				
				double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
						* Double.parseDouble(productDto.getDiscount())* Double.parseDouble(exchangeDto.getExchangeRate()));
				
				double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice())* Double.parseDouble(exchangeDto.getExchangeRate()));
				
				double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
						* Double.parseDouble(productDto.getTotalTaxInclusive())* Double.parseDouble(exchangeDto.getExchangeRate()));
				double netTotal = (itemTotalClac - itemDiscount);
			//	String itemTotal = decimalFormat.format(itemTotalClac);
				
				
				tabledata.append("<tr><td>");
				tabledata.append(++count);
				
				tabledata.append("</td><td>");
				tabledata.append(productDto.getItemNameOrDesc());
				
				tabledata.append("</td><td class=\"text-right\">");
				tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
				tabledata.append("</td><td class=\"text-right\">");
				tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
				tabledata.append("</td><td class=\"text-right\">");
				
				
				tabledata.append(decimalFormat.format(new Double(netTotal)));
				tabledata.append("</td></tr>");
			
			tolal = tolal + itemTotalClac;
			
            grandTotal = grandTotal + netTotal;
            
            discount = discount + itemDiscount;
			
			gstTotal = gstTotal+ gstAmount;
			if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
				paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
			}
			}	
				}
				
				if(paymentTermId == 0) {
					paymentTerm = "Pay on Delivery";
				}
				else {
					paymentTerm = paymentTermId+" Days";
				}
		}
			
			
	    	
	    	
			
			String invoiceAndReceiptNumber = paymentRequest.getInvoiceNumber();
			
			SimpleDateFormat dateFormateForReceipt = new SimpleDateFormat ("EEEE dd/MM/yyyy hh:mm a");
			dateFormateForReceipt.setTimeZone(TimeZone.getTimeZone(country.getCountryTimeZone()));
			String receiptDate =  dateFormateForReceipt.format(new Date());
		
			String invoiceAndReceiptGeneratedDate = receiptDate;
		String totalString = decimalFormat.format(tolal);
		
		String grandTotalString = decimalFormat.format(grandTotal);
		String discountString = decimalFormat.format(discount);
		
		
	String gstTotalString = decimalFormat.format(gstTotal);

		String recieptTableHeader = "";
		String invoiceTableHeader = "";
		String subtotalHeader = "";
		String totalHeader = "";
		int receiptSpan = 6;
		
		int invDisSpan = 0;
		int tdSpan = 6;
		String gstRow = "";
		
		if (type.equals("INVOICE")) {
			totalHeader = "";
			subtotalHeader= "";
			if(isgsnt && isgstAppliend) {
				invoiceTableHeader = "        <tr>r\n\" + \r\n" + 
										"          <th class=\"text-left\" style=\"width:8%\">Sr No.</th> \r\n" + 
										"          <th class=\"text-left\" style=\"width:21%\">Items</th>\r\n" + 
										"          <th class=\"text-right\" style=\"width:10%\">HSN/SAC</th> \r\n" + 
										"          <th class=\"text-right\" style=\"width:10%\">Qty</th>\\r\\n\" + \r\n" + 
									    "          <th class=\"text-right\" style=\"width:15%\">Unit Price(" + country.getCurrencySymbol() + ")</th> \r\n" + 
									    "          <th class=\"text-right\" style=\"width:9%\">Disc%</th> \r\n" + 
										"          <th class=\"text-right\" style=\"width:12%\">GST%</th> \r\n" + 
										"          <th class=\"text-right\" style=\"width:15%\">Amount (" + country.getCurrencySymbol() + ")</th>\r\n" + 
										"        </tr>\\r\\n\" + ";
			
				subtotalHeader = "<td  colspan=\"2\" class=\"text-right ft\" style=\"padding:5px;\"><b>Total without Tax:</b></td>";
				totalHeader= "<td  colspan=\"2\" class=\"text-right ft bt\" style=\"padding:5px;\"><b>Total with Tax:</b></td>";
				
				gstRow = "<tr>\r\n" + 
						"				\r\n" + 
						"		<td colspan=\"5\" class=\"br\"></td>	<td  colspan=\"2\" class=\"text-right ft\" style=\"padding:5px;\">GST:</td>\r\n" + 
						"				<td class=\"text-right\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;"+gstTotalString+"</td> \r\n" + 
						"			</tr> ";
				
				receiptSpan = 5;
				tdSpan = 8;
				invDisSpan = 2;
			
			
			}
			else {
				invoiceTableHeader = "        <tr>r\n\"" + 
						"          <th class=\"text-left\" style=\"width:10%\">Sr No.</th> \r\n" + 
						"          <th class=\"text-left\" style=\"width:26%\">Items</th>\r\n" + 
						
						"          <th class=\"text-right\" style=\"width:12%\">Qty</th>\r\n" + 
					    "          <th class=\"text-right\" style=\"width:20%\">Unit Price(" + country.getCurrencySymbol() + ")</th>\r\n" + 
					    "          <th class=\"text-right\" style=\"width:12%\">Disc%</th> \r\n" + 
						
						"          <th class=\"text-right\" style=\"width:20%\">Amount (" + country.getCurrencySymbol() + ")</th> \r\n" + 
						"        </tr> ";

               subtotalHeader = "<td  colspan=\"0\" class=\"text-right ft\" style=\"padding:5px;\"><b>Sub Total:</b></td>";
               totalHeader= "<td  colspan=\"0\" class=\"text-right ft bt\" style=\"padding:5px;\"><b>Total:</b></td>";
				
				receiptSpan = 4;
				tdSpan = 6;
			
			}
		
		}
		
		if (type.equals("RECEIPT")) {

			if(isgsnt && isgstAppliend) {
				recieptTableHeader = "<tr>\r\n" + 
						"				<th class=\"text-left\" style=\"width:7%\">S.No.</th>\r\n" + 
						"				\r\n" + 
						"			<th class=\"text-left\" style=\"width:28%\">Items</th>\r\n" + 
						"				<th class=\"text-right\" style=\"width:12%\">HSN/SAC</th>\r\n" + 
						"				 <th class=\"text-right\" style=\"width:12%\">Qty</th>\r\n" + 
						"				<th class=\"text-right\" style=\"width:10%\">Disc%</th>\r\n" + 
						"				 <th class=\"text-right\"style=\"width:16%\">GST%</th>\"\r\n" + 
						"				 <th class=\"text-right\" style=\"width:15%\">Amount (" + country.getCurrencySymbol() + ")</th></tr>";
			
				subtotalHeader = "<td colspan=\"5\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">Total without Tax:</td>";
				totalHeader= "<td colspan=\"5\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;border-top: 2px solid #cdcdcd;padding:5px;\">Total with Tax:</td>";
				
				gstRow = "<tr class=\"text-right\">\r\n\r\n" + 
						"				\r\n" + 
						"		<td colspan=\"5\"></td>	<td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">GST:</td>\r\n" + 
						"				<td class=\"text-right\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;"+gstTotalString+"</td> \r\n" + 
						"			</tr> ";
				
				 receiptSpan = 6;
			
			
			}
			else {
				recieptTableHeader = "<tr>\r\n" + 
						"				<th class=\"text-left\" style=\"width:10%\">S.No.</th>\r\n" + 
						"				\r\n" + 
						"			<th class=\"text-left\" style=\"width:39%\">Items</th>\r\n" + 
						
						"				 <th class=\"text-right\" style=\"width:15%\">Qty</th>\r\n" + 
						"				<th class=\"text-right\" style=\"width:16%\">Disc%</th>\r\n" + 
						
						"				 <th class=\"text-right\" style=\"width:20%\">Amount (" + country.getCurrencySymbol() + ")</th></tr>";
				
				subtotalHeader = "<td colspan=\"3\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\" >Sub Total:</td>";
				totalHeader= "<td colspan=\"3\"></td><td  style=\"font-size:9pt;border-top: 2px solid #cdcdcd;border-left: 2px solid #cdcdcd;padding:5px;\">Total:</td>";
				receiptSpan = 4;
			
			}
		
		}	
			
		String invoice = "<!DOCTYPE html>\r\n" + 
				"<html>\r\n" + 
				"\r\n" + 
				"<head>\r\n" + 
				"  <meta charset=\"utf-8 \" />\r\n" + 
				"  <meta http-equiv=\"X-UA-Compatible \" content=\"IE=edge \" />\r\n" + 
				"  <title>Invoice</title>\r\n" + 
				"  <meta name=\"viewport \" content=\"width=device-width, initial-scale=1 \" />\r\n" + 
				"  <style>\r\n"
				+ "@font-face { font-family: Playfair Display;src: url(https://fonts.googleapis.com/css?family=Playfair+Display);}" + 
				"    .container {\r\n" + 
				"      font-family: Arial;\r\n" + 
				"      line-height: 1.3;\r\n" + 
				"      font-size: 9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .float-left {\r\n" + 
				"      float: left;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .float-right {\r\n" + 
				"      float: right;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .pending {\r\n" + 
				"      background-color: #e87f09;\r\n" + 
				"      color: #fff;\r\n" + 
				"      padding: 5px;\r\n" + 
				"      border-radius: 5px;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .text-center {\r\n" + 
				"      text-align: center;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .text-right {\r\n" + 
				"      text-align: right;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .text-left {\r\n" + 
				"      text-align: left;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable {\r\n" + 
				"      border: 2px solid #cdcdcd;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable th {\r\n" + 
				"      text-align: left;\r\n" + 
				"      background-color: #e0e0e0;\r\n" + 
				"      padding: 0px 10px;\r\n" + 
				"      font-size:9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable td {\r\n" + 
				"      text-align: right;\r\n" + 
				"      padding: 0px 10px;\r\n" + 
				"      font-size:8pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable tr {\r\n" + 
				"      height: 25px;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable {\r\n" + 
				"      width: 100%;\r\n" + 
				"      border: 2px solid #cdcdcd;\r\n" + 
				"      table-layout: fixed;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable th {\r\n" + 
				"      background-color: #e0e0e0;\r\n" + 
				"      border-bottom: 2px solid #cdcdcd;\r\n" + 
				"      font-size: 9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				
				"\r\n" + 
				"    .text-right.ft {\r\n" + 
				"      font-size: 9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable th,\r\n" + 
				"    .billTable td {\r\n" + 
				"      padding: 5px;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable td {\r\n" + 
				"      font-size: 8pt;"
				+ "padding: 8px 5px;\r\n" + 
				"    }\r\n" + 
				"    .address {\r\n" + 
				"      width: 300px;\r\n" + 
				"      word-wrap: break-word;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				
				"    .termConditionInner {" + 
				"      width: 100%;" + 
				"      position: relative;" + 
				"      min-height: 100px;" + 
				"      border-radius: 25px;" + 
				"      border: 2px solid #cdcdcd ;" + 
				"    }" + 
				"\r\n" + 
				"\r\n" + 
				"    .row {\r\n" + 
				"      min-width: 100%;\r\n" + 
				"      margin-bottom: 20px\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .row:before,\r\n" + 
				"    .row:after {\r\n" + 
				"      display: table;\r\n" + 
				"      content: \" \";\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .row:after {\r\n" + 
				"      clear: both;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .br {\r\n" + 
				"      border-right: 2px solid #cdcdcd;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .bt {\r\n" + 
				"      border-top: 2px solid #cdcdcd;\r\n" + 
				"    }\r\n" + 
				"    .termCondition {\r\n" + 
				"      border: 2px solid #cdcdcd;"
				+ "border-radius: 20%;"
				+ "padding: 15px 10px;"
				+ "font-size:8pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"  </style>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"  <div class=\"container\">\r\n" + 
				"    <div class=\"row\">\r\n" + 
				"      <div class=\"pending text-center\">"+invoiceType+"</div><br/>\r\n" + 
				"    </div>\r\n" + 
				"    <div class=\"row\" style=\"margin-bottom:0px\">\r\n" + 
				"      <div class=\"companyDetails float-left\">\r\n" + 
				"        <h2 style='margin:0px'>"+merchantName+"</h2>\r\n" + 
				"        " + clientAccount.getEmailAddress() + "<br/> " + merchantCountry.getCountryDialCode() + " - " + clientAccount.getMobileNumber()+"<br /> " + clientAccount.getResidentialAddress() + "<br/>"+gstNo+"\r\n" + 
				"      </div><br/>\r\n" + 
				"      <div class=\"bookingDetails float-right\">\r\n" + 
				"        <table class=\"detailtable\" cellspacing=\"0 \">\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Invoice No</th>\r\n" + 
				"            <td> <b>" + invoiceAndReceiptNumber + "</b>\r\n" + 
				"            </td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Created</th>\r\n" + 
				"            <td>" + invoiceAndReceiptGeneratedDate + "</td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Invoice Date</th>\r\n" + 
				"            <td>" + invoiceAndReceiptGeneratedDate + "</td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Total (" + country.getCurrencySymbol() + ")</th>\r\n" + 
				"            <td>" + country.getCurrencySymbol() + "&nbsp;" + grandTotalString+"</td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Payment Term</th>\r\n" + 
				"            <td>"+paymentTerm+"</td>\r\n" + 
				"          </tr>\r\n" + 
				"        </table>\r\n" + 
				"      </div>\r\n" + 
				"    </div>\r\n" + 
				"\r\n" + 
				"    <div class=\"row address\">\r\n" + 
				"      <b>Billed To: <br/>" + purchaserProfile.getFirstName() + ""+ purchaserProfile.getLastName() + "</b> <br/>"+ purchaserProfile.getEmailAddress() + " <br />"+ country.getCountryDialCode() + " - " + purchaserProfile.getMobileNumber() + " <br />\r\n" + 
				"      <div class=\"address \">" + purchaserProfile.getResidentialAddress() + " </div>\r\n" + 
				"    </div><br/>\r\n" + 
				"\r\n" + 
				"    <div class=\"row\">\r\n" + 
				"      <table class=\"billTable \" cellspacing=\"0\">\r\n" + 
				invoiceTableHeader+ 
				tabledata.toString() + "  "+
				
				"\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+tdSpan+" style=\"background-color:#cdcdcd;padding:1px\"></td>\r\n" + 
				"        </tr>\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				""+subtotalHeader+
				"          <td class=\"text-right\" style=\"padding:5px;\">\r\n" + 
				"           "+ country.getCurrencySymbol() + "&nbsp;" + totalString + "\r\n" + 
				"          </td>\r\n" + 
				"        </tr>\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				"          <td colspan="+invDisSpan+" class=\"text-right ft\" style=\"padding:5px;\">\r\n" + 
				"            Discount : </td>\r\n" + 
				"          <td class=\"text-right \" style=\"padding:5px;\">-"+ country.getCurrencySymbol() + "&nbsp;"+discountString+" </td>\r\n" + 
				"        </tr>\r\n" + 
				""+gstRow+
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				
				""+totalHeader+
				"          <td class=\"text-right bt\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;" + grandTotalString + " </td>\r\n" + 
				"        </tr>\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				"          <td colspan="+invDisSpan+" class=\"text-right ft\" style=\"padding:5px;\"> <b>Amount due :</b>\r\n" + 
				"          </td>\r\n" + 
				"          <td class=\"text-right\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;" + grandTotalString + "</td>\r\n" + 
				"        </tr>\r\n" + 
				"      </table>\r\n" + 
				"    </div><br/>\r\n" + 
				"<table style=\"width:100%;font-size:9pt\">\r\n" + "        <tr>\r\n"
				+ "          <th width=\"45%\" class=\"text-left\">Customer Notes</th>\r\n"
				+ "          <th width=\"10%\"></th>\r\n"
				+ "          <th width=\"45%\" class=\"text-left\">Terms & Conditions</th>\r\n" + "        </tr>\r\n "
				+ "  <tr>\r\n" + "          <td width=\"45%\" class=\"termCondition\"> " + customerNotes + " </td>\r\n"
				+ "          <td width=\"10%\"></td>\r\n" + "          <td width=\"45%\" class=\"termCondition\"> "
				+ termsAndCondition + " </td>\r\n" + "        </tr>" +

				"      </table>" +
				"  </div>\r\n" + 
				"  <!--main container-->\r\n" + 
				"</body>\r\n" + 
				"\r\n" + 
				"</html>";
		
		
		String receipt = "<!DOCTYPE html>\r\n" + "<html>\r\n" + "\r\n" + "<head>\r\n"
				+ "    <meta charset=\"utf-8\" />\r\n"
				+ "    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\r\n"
				+ "    <title>Receipt</title>\r\n"
				+ "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\r\n"
				+ "    <style>\r\n" + "        .container {\r\n" + "font-size:9pt;font-family: Arial, Helvetica, sans-serif;line-height: 1.3;\r\n"
				+ "        }\r\n" + "\r\n" + "        .header {\r\n" + "            width: 100%;\r\n"
				+ "            float: left;\r\n" + "            margin-bottom: 30px;\r\n" + "        }\r\n" + "\r\n"
				+ "        .headerDetails {\r\n" + "            width: 100%;\r\n" + "            clear: both;\r\n"
				+ "        }\r\n" + "\r\n" + "        .billSummary {\r\n" + "            margin-top: 170px;\r\n"
				+ "            width: 100%;\r\n" + "        }\r\n" + "\r\n" + "        .float-left {\r\n"
				+ "            float: left;\r\n" + "        }\r\n" + "\r\n" + "        .float-right {\r\n"
				+ "            float: right;\r\n" + "        }\r\n" + "\r\n" + "        .pending {\r\n"
				+ "            background-color: #e87f09;\r\n" + "            color: #fff;\r\n"
				+ "            padding: 5px;\r\n" + "            border-radius: 5px\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-center {\r\n" + "            text-align: center;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-right {\r\n" + "            text-align: right;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-left {\r\n" + "            text-align: left;\r\n" + "        }\r\n" + "\r\n"
				+ "        .detailtable {\r\n" + "            border: 2px solid #cdcdcd;\r\n" + "        }\r\n" + "\r\n"
				+ "        .detailtable th {\r\n" + "            text-align: left;font-size:9pt;\r\n"
				+ "            background-color: #e0e0e0;\r\n" + "            padding: 0px 10px;\r\n" + "        }\r\n"
				+ "\r\n" + "        .detailtable td {\r\n" + "            text-align: right;font-size:8pt;\r\n"
				+ "            padding: 0px 10px;\r\n" + "        }\r\n" + "\r\n" + "        .detailtable tr {\r\n"
				+ "            height: 25px;\r\n" + "        }\r\n" + "\r\n" + "        .billTable {\r\n"
				+ "            width: 100%;\r\n" + "            border: 2px solid #cdcdcd;\r\n"
				+ "            table-layout: fixed;\r\n" + "        }\r\n" + "\r\n" + "        .billTable th {\r\n"
				+ "            background-color: #e0e0e0;font-size:9pt;\r\n" + "        }\r\n" + "\r\n" + "        .billTable th,\r\n"
				+ "        .billTable td {\r\n" + "            padding: 5px;\r\n" + "        }\r\n" + "\r\n"
				+ "        .billTable td {\r\n" + "            padding: 5px;font-size:8pt;\r\n" + "        }\r\n" + "\r\n"
				+ "        .termCondition {\r\n" + "            clear: both;\r\n" + "        }\r\n" + "\r\n"
				+ "        .container {\r\n" + "            margin: 50px 100px;\r\n" + "        }\r\n" + "\r\n"
				+ "        .header {\r\n" + "            width: 100%;\r\n" + "        }\r\n" + "\r\n"
				+ "        /* .billSummary {\r\n" + "            margin-top: 50px;\r\n" + "        } */\r\n" + "\r\n"
				+ "        .text-center {\r\n" + "            text-align: center;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-right {\r\n" + "            text-align: right;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-left {\r\n" + "            text-align: left;\r\n" + "        }\r\n" + "\r\n"
				+ "        .billTable {\r\n" + "            width: 100%;\r\n" + "        }\r\n" + "\r\n"
				+ "        .billTable th,\r\n" + "        .billTable td {\r\n" + "            padding: 8px 5px;\r\n"
				+ "        }\r\n" + "\r\n" + "        .noMargin {\r\n" + "            margin: 0px;\r\n"
				+ "        }\r\n" + "\r\n" + "        .address {\r\n" + "            width: 300px;\r\n"
				+ "            word-wrap: break-word;\r\n" + "        }\r\n" + "    </style>\r\n" + "</head>\r\n"
				+ "\r\n" + "<body>\r\n" + "    <div class=\"container\">\r\n" + "        <div class=\"header\">\r\n"
				+ "            <div class=\"text-center\">\r\n"
				+ "                <h1 class=\"noMargin\" style=\"margin-bottom: 2px;\">Receipt</h1>\r\n"
				+ "                <h5 class=\"noMargin\">" + receiptDate + "</h5>\r\n" +
				
				"            </div>\r\n" + "        </div><br/>\r\n" + "        <div class=\"headerDetails\">\r\n"
				+ "            <div class=\"addressDetails float-left\">\r\n" + "                <b>\r\n"
				+ "                    Billed To:\r\n" + "                </b>\r\n" + "                \r\n"
				+ "                <b>" + purchaserProfile.getFirstName() + " " + purchaserProfile.getLastName()
				+ "</b>\r\n" + "                <br/>" + purchaserProfile.getEmailAddress() + "\r\n"
				+ "                <br/>" + country.getCountryDialCode() + " - "
				+ purchaserProfile.getMobileNumber() + "\r\n" + "                <br/>\r\n"
				+ "                <div class=\"address\">\r\n" + "                    "
				+ purchaserProfile.getResidentialAddress() + "\r\n" + "                </div>\r\n" +
				
				"            </div>\r\n" + "            <div class=\"bookingDetails float-right\">\r\n"
				+ "                <table class=\"detailtable\" cellspacing=\"0\">\r\n" + "                    <tr>\r\n"
				+ "                        <th>Receipt No</th>\r\n" + "                        <td>\r\n"
				+ "                            <b>" + invoiceAndReceiptNumber + "-01</b>\r\n"
				+ "                        </td>\r\n" + "                    </tr>\r\n" + "                    <tr>\r\n"
				+ "                        <th>Payment Date</th>\r\n" + "                        <td>"
				+ invoiceAndReceiptGeneratedDate + "</td>\r\n" + "                    </tr>\r\n"
				+ "<tr><th>Payment Term</th><td>"+paymentTerm+"</td></tr>"
				
				+ "                </table>\r\n" + "            </div>\r\n" + "        </div><br/>\r\n" + "\r\n"
				+ "        <div class=\"billSummary\" >\r\n" + "            <table class=\"billTable\" >\r\n" + recieptTableHeader+""
				+

				tabledata.toString() + "                <tr>\r\n"
				+ "                    <td colspan=\""+receiptSpan+1+"\" style=\"background-color: #cdcdcd;padding: 1px\"></td>\r\n"
				+ "                </tr>\r\n" + "                <tr class=\"text-right\">\r\n"   +subtotalHeader+" " 
				
				
				+ "                    <td class=\"text-right\" style=\"padding:5px;\">"+country.getCurrencySymbol()+"&nbsp;" + totalString + "</td>\r\n"
				+ "                </tr>\r\n" 
				+ " <tr class=\"text-right\">\r\n" 
				
				+ "<td colspan=\""+(receiptSpan-1)+"\"></td> <td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">Discount:</td>\r\n" 
				+ " <td class=\"text-right\" style=\"padding:5px;\">-"+country.getCurrencySymbol()+"&nbsp;"+discountString+"</td>\r\n" 
				+ " </tr>\r\n" +gstRow+""
				
				+  "                <tr class=\"text-right\" style=\"padding:5px;\">\r\n"   +totalHeader+" "               
				
				+ "                    <td class=\"text-right\" style=\"font-size:8pt;border-top: 2px solid #cdcdcd;padding:5px;\">"+country.getCurrencySymbol()+"&nbsp;" + grandTotalString + "</td>\r\n"
				+ "                </tr>\r\n"
				+ "<tr class=\"text-right\"><td colspan=\""+(receiptSpan-1)+"\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">Amount Due:</td><td style=\"padding:5px;\">"+country.getCurrencySymbol()+"&nbsp;" + grandTotalString + "</td></tr>"
				+ "            </table>\r\n" + "        </div>\r\n"
				+ "        <div class=\"text-center\">\r\n" + "            <h5>Thanks for shopping with us</h5>\r\n"
				+ "        </div>\r\n" + "    </div>\r\n" + "    <!--main container-->\r\n" + "\r\n" + "</body>\r\n"
				+ "\r\n" + "</html>";
		
		
		
		if (type.equals("INVOICE")) {
			
			outPutString = invoice;

			
			String thymeleafTabledata ="<table class=\"billTable \" cellspacing=\"0\">"
					+ invoiceTableHeader + tabledata.toString() + " " +
					
					"" + "        <tr>\r\n" + "          <td colspan=" + tdSpan
					+ " style=\"background-color:#cdcdcd;padding:1px\"></td>\r\n" + "        </tr>\r\n" + "        <tr>\r\n"
					+ "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n" + "" + subtotalHeader
					+ "          <td class=\"text-right\" style=\"padding:5px;\">\r\n" + "           "
					+ country.getCurrencySymbol() + " " + totalString + "\r\n" + "          </td>\r\n" + "        </tr>\r\n"
					+ "        <tr>\r\n" + "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n"
					+ "          <td colspan=" + invDisSpan + " class=\"text-right ft\" style=\"padding:5px;\">\r\n"
					+ "            Discount : </td>\r\n" + "          <td class=\"text-right \" style=\"padding:5px;\">-"
					+ country.getCurrencySymbol() + " " + discountString + " </td>\r\n" + "        </tr>\r\n" + "" + gstRow
					+ "        <tr>\r\n" + "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n" +

					"" + totalHeader + "          <td class=\"text-right bt\" style=\"padding:5px;\">"
					+ country.getCurrencySymbol() + " " + grandTotalString + " </td>\r\n" + "        </tr>\r\n"
					+ "        <tr>\r\n" + "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n"
					+ "          <td colspan=" + invDisSpan
					+ " class=\"text-right ft\" style=\"padding:5px;\"> <b>Amount due :</b>\r\n" + "          </td>\r\n"
					+ "          <td class=\"text-right\" style=\"padding:5px;\">" + country.getCurrencySymbol() + " "
					+ grandTotalString + "</td>\r\n" + "        </tr>\r\n" + "      </table>";
			
			


			//------------- Thymeleaf ----------
					
							com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto dto = new com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto();
							dto.setPdfType("invoice");
							//----------
						    CompanyDetails companyDetails = new CompanyDetails();
							companyDetails.setMerchantName(merchantName);
							companyDetails.setEmailAddress(clientAccount.getEmailAddress());
							companyDetails.setCountryDialCode(country.getCountryDialCode());
							companyDetails.setMobileNumber(""+clientAccount.getMobileNumber());
							companyDetails.setResidentialAddress(clientAccount.getResidentialAddress());
							companyDetails.setGstNo(gstNo);
							//----------
							
							Detailtable detailTable = new Detailtable();
							detailTable.setInvoiceAndReceiptNumber(invoiceAndReceiptNumber);
							detailTable.setInvoiceAndReceiptGeneratedDate(invoiceAndReceiptGeneratedDate);
							detailTable.setCurrencySymbol(country.getCurrencySymbol());
							detailTable.setGrandTotalString(grandTotalString);
							detailTable.setPaymentTerm(paymentTerm);
							
							BillToAddress billToAddress = new BillToAddress();
							
							//billToAddress.setTableDynamicHeaderAndRows(invoiceTableHeader+tabledata);
							billToAddress.setThymeleafTabledata(thymeleafTabledata);
							billToAddress.setInvoiceType(invoiceType);
							billToAddress.setEmailAddress(purchaserProfile.getEmailAddress());
							billToAddress.setMobileNumber(country.getCountryDialCode() + " - " + clientAccount.getMobileNumber());
							billToAddress.setPurchaserName(purchaserProfile.getFirstName() + "" + purchaserProfile.getLastName());
							billToAddress.setResidentialAddress(clientAccount.getResidentialAddress());
							billToAddress.setSubtotalHeader(subtotalHeader);
							billToAddress.setTotalString(totalString);
							billToAddress.setDiscountString(discountString);
							billToAddress.setTotalHeader(totalHeader);
							billToAddress.setGstTotalString(gstTotalString);
							billToAddress.setGrandTotalString(grandTotalString);
							billToAddress.setCustomerNotes(""+customerNotes);
							billToAddress.setTermsAndCondition(""+termsAndCondition);
							
							
							//PaymentRequest paymentRequest = new PaymentRequest();
							//ProductDto product1 =new ProductDto();
							//ProductDto product2 =new ProductDto();
							
							//List<ProductDto> plist=new ArrayList<>();
							//plist.add(product1);
							//plist.add(product2);
							paymentRequest.setProductList(paymentRequest.getProductList());
							
							dto.setCompanyDetails(companyDetails);
							dto.setDetailTable(detailTable);
							dto.setBillToAddress(billToAddress);
							dto.setPaymentRequest(paymentRequest);
							dto.setReceiptSpan(receiptSpan);
							dto.setInvDisSpan(invDisSpan);
							dto.setTdSpan(tdSpan);
							dto.setIsgsnt(isgsnt);
							dto.setIsgstAppliend(isgstAppliend);
							
							
							// call method to generate HTML string using thymeleaf
						String invoiceByThymeleaf =	generateInvoiceAndPdfString(dto);
							
			
			
			
			
		} else if (type.equals("RECEIPT")) {
			
			outPutString = receipt;
			
			String thymeleafTabledataForReceipt ="<table class=\"billTable\" >\r\n"
			+ recieptTableHeader + "" +

			tabledata.toString() + "                <tr>\r\n" + "                    <td colspan=\"" + receiptSpan
			+ 1 + "\" style=\"background-color: #cdcdcd;padding: 1px\"></td>\r\n" + "                </tr>\r\n"
			+ "                <tr class=\"text-right\">\r\n" + subtotalHeader + " "

			+ "                    <td class=\"text-right\" >" + country.getCurrencySymbol() + " " + totalString
			+ "</td>\r\n" + "                </tr>\r\n" + " <tr class=\"text-right\">\r\n"

			+ "<td colspan=\"" + (receiptSpan - 1)
			+ "\"></td> <td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;\">Discount:</td>\r\n"
			+ " <td class=\"text-right\" >-" + country.getCurrencySymbol() + " " + discountString + "</td>\r\n"
			+ " </tr>\r\n" + gstRow + ""

			+ "                <tr class=\"text-right\">\r\n" + totalHeader + " "

			+ "                    <td class=\"text-right\" style=\"font-size:8pt;border-top: 2px solid #cdcdcd;\">"
			+ country.getCurrencySymbol() + " " + grandTotalString + "</td>\r\n" + "                </tr>\r\n"
			+ "<tr class=\"text-right\"><td colspan=\"" + (receiptSpan - 1)
			+ "\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;\">Amount Due:</td><td >"
			+ country.getCurrencySymbol() + " " + grandTotalString + "</td></tr>" + "</table>";
			
			
			
			//------------- Thymeleaf ----------
			
			com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto dto = new com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto();
			dto.setPdfType("receipt");
			//----------
		    CompanyDetails companyDetails = new CompanyDetails();
			companyDetails.setMerchantName(merchantName);
			companyDetails.setEmailAddress(clientAccount.getEmailAddress());
			companyDetails.setCountryDialCode(country.getCountryDialCode());
			companyDetails.setMobileNumber(""+clientAccount.getMobileNumber());
			companyDetails.setResidentialAddress(clientAccount.getResidentialAddress());
			companyDetails.setGstNo(gstNo);
			//----------
			
			Detailtable detailTable = new Detailtable();
			detailTable.setInvoiceAndReceiptNumber(invoiceAndReceiptNumber);
			detailTable.setInvoiceAndReceiptGeneratedDate(invoiceAndReceiptGeneratedDate);
			detailTable.setCurrencySymbol(country.getCurrencySymbol());
			detailTable.setGrandTotalString(grandTotalString);
			detailTable.setPaymentTerm(paymentTerm);
			
			BillToAddress billToAddress = new BillToAddress();
			
			//billToAddress.setTableDynamicHeaderAndRows(invoiceTableHeader+tabledata);
			billToAddress.setThymeleafTabledata(thymeleafTabledataForReceipt);
			billToAddress.setInvoiceType(invoiceType);
			billToAddress.setEmailAddress(purchaserProfile.getEmailAddress());
			billToAddress.setMobileNumber(country.getCountryDialCode() + " - " + clientAccount.getMobileNumber());
			billToAddress.setPurchaserName(purchaserProfile.getFirstName() + "" + purchaserProfile.getLastName());
			billToAddress.setResidentialAddress(clientAccount.getResidentialAddress());
			billToAddress.setSubtotalHeader(subtotalHeader);
			billToAddress.setTotalString(totalString);
			billToAddress.setDiscountString(discountString);
			billToAddress.setTotalHeader(totalHeader);
			billToAddress.setGstTotalString(gstTotalString);
			billToAddress.setGrandTotalString(grandTotalString);
			billToAddress.setCustomerNotes(""+customerNotes);
			billToAddress.setTermsAndCondition(""+termsAndCondition);
			
			
			//PaymentRequest paymentRequest = new PaymentRequest();
			//ProductDto product1 =new ProductDto();
			//ProductDto product2 =new ProductDto();
			
			//List<ProductDto> plist=new ArrayList<>();
			//plist.add(product1);
			//plist.add(product2);
			paymentRequest.setProductList(paymentRequest.getProductList());
			
			dto.setCompanyDetails(companyDetails);
			dto.setDetailTable(detailTable);
			dto.setBillToAddress(billToAddress);
			dto.setPaymentRequest(paymentRequest);
			dto.setReceiptSpan(receiptSpan);
			dto.setInvDisSpan(invDisSpan);
			dto.setTdSpan(tdSpan);
			dto.setIsgsnt(isgsnt);
			dto.setIsgstAppliend(isgstAppliend);
			
			
			// call method to generate HTML string using thymeleaf
		String invoiceByThymeleaf =	generateInvoiceAndPdfString(dto);
		
			
		}
		
		return outPutString;
		}
	
	
	/**
	 * use to generate invoice and receipt for client.
	 * 
	 * @param paymentRequest
	 * @param merchantProfile
	 * @param purchaserProfile
	 * @param clientInformation
	 * @param type
	 * @return
	 */
	private String generateInvoiceOrReceiptForClient(PaymentRequest paymentRequest, UserProfile merchantProfile, UserProfile purchaserProfile,ClientInfoResponse clientInformation, String type) {
		String outPutString=null;
		
		String merchantName;
    	if(clientInformation.getClientName() != null) {
    		merchantName = clientInformation.getClientName();
    	}else {
    		merchantName = paymentRequest.getMerchantDetails().getFirstName()+" "+paymentRequest.getMerchantDetails().getLastName();
    	}
		
		
		
		StringBuilder tabledata = new StringBuilder();
		
		Double tolal = 0.0;
		Double grandTotal = 0.0;
		Double gstTotal = 0.0;
		Double discount = 0.0;
		DecimalFormat decimalFormat = new DecimalFormat("0.00");
		String invoiceType = "Invoice";
		String paymentTerm = "";
		StringBuilder customerNotes = new StringBuilder();
		StringBuilder termsAndCondition = new StringBuilder();
		boolean isgsnt = false;
		boolean isgstAppliend = false;
		List<String> customerNotesList = new ArrayList<String>();
		List<String> termsAndConditionList = new ArrayList<String>();
		
		Country purchaseCountry = countryRepo.findCountryByCountryDialCode(purchaserProfile.getCountryDialCode());
		
		Country country = countryRepo.findCountryByCountryDialCode(merchantProfile.getCountryDialCode());
		
		
		List<com.ireslab.sendx.electra.dto.ProductDto> productList = paymentRequest.getProductList();
		
		String gstNo = "";
		if (paymentRequest.getMerchantDetails().getGstn() != null && !StringUtils.isEmpty(paymentRequest.getMerchantDetails().getGstn()) && !paymentRequest.getMerchantDetails().getGstn().equalsIgnoreCase("null")) {
			
			isgsnt = true;
			
			
			for(com.ireslab.sendx.electra.dto.ProductDto productDto : productList) {
				if(Double.parseDouble(productDto.getSgstUtgst()) != 0.00 && Double.parseDouble(productDto.getIgst())  != 0.00 && Double.parseDouble(productDto.getCgst())  != 0.00 ) {
					isgstAppliend = true;
					gstNo = "GSTIN: "+paymentRequest.getMerchantDetails().getGstn();
					invoiceType = "TAX-INVOICE";
				}
					
			}
			
			
		}
		int count = 0;
		
			if(type.equals("INVOICE")) {
				int paymentTermId = 0;
				if(isgsnt && isgstAppliend) {
					
					for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) {// for loop for product based calculation for invoice.
						//String purchasedQty = decimalFormat.format(new Double(productDto.getPurchasedQty()));
						String productCost = decimalFormat.format(new Double(productDto.getItemPrice()));
						
						
						double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
								* Double.parseDouble(productDto.getDiscount()));
						
						double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice()));
						
						double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
								* Double.parseDouble(productDto.getTotalTaxInclusive()));
						double netTotal = gstAmount + (itemTotalClac - itemDiscount);
						//String itemTotal = decimalFormat.format(itemTotalClac);
						
						tabledata.append("<tr><td >");
						tabledata.append(++count);
						tabledata.append("</td><td class=\"text-left\" >");
						tabledata.append(productDto.getItemNameOrDesc());
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(productDto.getItemCode());
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(productCost);
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
						tabledata.append("</td><td class=\"text-right\" >");
						
						if(productDto.getInterState()) {
							if(productDto.getIgst() == null || new Double(productDto.getIgst()) ==0.00) {
								tabledata.append("NIL");
							}else {
								tabledata.append("IGST@ "+String.format("%.2f", new Double(productDto.getIgst()))+"");
							}
							
							
						}else {
							if((productDto.getCgst() == null || new Double(productDto.getCgst()) ==0.00) || (productDto.getSgstUtgst() == null || new Double(productDto.getSgstUtgst()) ==0.00)) {
								tabledata.append("NIL");
							}else {
								tabledata.append("CGST@ "+String.format("%.2f", new Double(productDto.getCgst()))+"<br/>SGST@ "+String.format("%.2f", new Double(productDto.getSgstUtgst()))+"");
							}
							
						}
						
						tabledata.append("</td><td class=\"text-right\" >");
						tabledata.append(decimalFormat.format(netTotal));
						tabledata.append("</td></tr>");
				
				    tolal = tolal + itemTotalClac;
				    grandTotal = grandTotal + netTotal;
					
					gstTotal = gstTotal+ gstAmount;
	                discount = discount + itemDiscount;
					
	                if(productDto.getInvoiceType().equalsIgnoreCase("tax-invoice")) {
						//invoiceType = "TAX-INVOICE";
					}
					if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
						paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
					}
					
					
					if(productDto.getCustomerNotes() != null && !StringUtils.isAllEmpty(productDto.getCustomerNotes())) {						
						customerNotesList.add(productDto.getCustomerNotes());
					}
					
					if(productDto.getTermsAndConditions() != null && !StringUtils.isAllEmpty(productDto.getTermsAndConditions())) {
					
					termsAndConditionList.add(productDto.getTermsAndConditions());
					}
					}
					
				}else {
					
				for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) {// for loop for product based calculation for invoice.
					//String purchasedQty = decimalFormat.format(new Double(productDto.getPurchasedQty()));
					String productCost = decimalFormat.format(new Double(productDto.getItemPrice()));
					
					
					double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getDiscount()));
					
					double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice()));
					
					double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getTotalTaxInclusive()));
					double netTotal = (itemTotalClac - itemDiscount);
					//String itemTotal = decimalFormat.format(itemTotalClac);
					
					tabledata.append("<tr><td >");
					tabledata.append(++count);
					tabledata.append("</td><td class=\"text-left\" >");
					tabledata.append(productDto.getItemNameOrDesc());
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
					tabledata.append("</td><td class=\"text-right\" >");
					
					tabledata.append(productCost);
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
					
					
					tabledata.append("</td><td class=\"text-right\" >");
					tabledata.append(decimalFormat.format(netTotal));
					tabledata.append("</td></tr>");
			
			    tolal = tolal + itemTotalClac;
			    grandTotal = grandTotal + netTotal;
				
				gstTotal = gstTotal+ gstAmount;
                discount = discount + itemDiscount;
				
                if(productDto.getInvoiceType().equalsIgnoreCase("tax-invoice")) {
					//invoiceType = "TAX-INVOICE";
				}
				if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
					paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
				}
				
				
				if(productDto.getCustomerNotes() != null && !StringUtils.isAllEmpty(productDto.getCustomerNotes())) {						
					customerNotesList.add(productDto.getCustomerNotes());
				}
				
				if(productDto.getTermsAndConditions() != null && !StringUtils.isAllEmpty(productDto.getTermsAndConditions())) {
				
				termsAndConditionList.add(productDto.getTermsAndConditions());
				}
				}
				
			}
				if(paymentTermId == 0) {
					paymentTerm = "Pay on Delivery";
				}
				else {
					paymentTerm = paymentTermId+" Days";
				}
			}
			
			
			if(customerNotesList.size()<1) {
				customerNotes.append("<br/>");
				customerNotes.append("<br/>");
				customerNotes.append("<br/>");
				customerNotes.append("<br/>");
			}
			else if(customerNotesList.size() == 1) {
				customerNotes.append(customerNotesList.get(0));
			}else {
				customerNotes.append("<ol style=\"padding-left:15px;\">");
				for(String customerNote:customerNotesList) {
					customerNotes.append("<li>");
					customerNotes.append(customerNote);
					customerNotes.append("</li>");
				}
				customerNotes.append("</ol>");
			}
			
			
			
			if(termsAndConditionList.size()<1) {
				termsAndCondition.append("<br/>");
				termsAndCondition.append("<br/>");
				termsAndCondition.append("<br/>");
				termsAndCondition.append("<br/>");
			}
			else if(termsAndConditionList.size() == 1) {
				termsAndCondition.append(termsAndConditionList.get(0));
			}else {
				termsAndCondition.append("<ol style=\"padding-left:15px;\">");
				for(String termAndCondition:termsAndConditionList) {
					termsAndCondition.append("<li>");
					termsAndCondition.append(termAndCondition);
					termsAndCondition.append("</li>");
				}
				termsAndCondition.append("</ol>");
			}
			
			
			
			if(type.equals("RECEIPT")) {
				
				int paymentTermId = 0;
				//paymentTerm
				if(isgsnt && isgstAppliend) {
					for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) {// for loop for product based calculation for receipt.
						
						
						double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
								* Double.parseDouble(productDto.getDiscount()));
						
						double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice()));
						
						double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
								* Double.parseDouble(productDto.getTotalTaxInclusive()));
						double netTotal = gstAmount + (itemTotalClac - itemDiscount);
						//String itemTotal = decimalFormat.format(itemTotalClac);
						
						tabledata.append("<tr><td>");
						tabledata.append(++count);
						
						tabledata.append("</td><td>");
						tabledata.append(productDto.getItemNameOrDesc());
						tabledata.append("</td><td class=\"text-right\">");
						tabledata.append(productDto.getItemCode());
						tabledata.append("</td><td class=\"text-right\">");
						tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
						tabledata.append("</td><td class=\"text-right\">");
						tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
						tabledata.append("</td><td class=\"text-right\">");
						
						if(productDto.getInterState()) {
							if(productDto.getIgst() == null || new Double(productDto.getIgst()) ==0.00) {
								tabledata.append("NIL");
							}else {
								tabledata.append("IGST@ "+String.format("%.2f", new Double(productDto.getIgst()))+"");
							}
							
							
						}else {
							if((productDto.getCgst() == null || new Double(productDto.getCgst()) ==0.00) || (productDto.getSgstUtgst() == null || new Double(productDto.getSgstUtgst()) ==0.00)) {
								tabledata.append("NIL");
							}else {
								tabledata.append("CGST@ "+String.format("%.2f", new Double(productDto.getCgst()))+"<br/>SGST@ "+String.format("%.2f", new Double(productDto.getSgstUtgst()))+"");
							}
							
						}
						
						tabledata.append("</td><td class=\"text-right\">");
						tabledata.append(decimalFormat.format(new Double(netTotal)));
						tabledata.append("</td></tr>");
					tolal = tolal + itemTotalClac;
					
	                grandTotal = grandTotal + netTotal;
	                
	                discount = discount + itemDiscount;
					
					gstTotal = gstTotal+ gstAmount;
					if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
						paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
					}
				}
				}else {
				for(com.ireslab.sendx.electra.dto.ProductDto productDto:productList) {// for loop for product based calculation for receipt.
					
					
					double itemDiscount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getDiscount()));
					
					double itemTotalClac =(Double.parseDouble(productDto.getTotalItemPrice()));
					
					double gstAmount = (Double.parseDouble(productDto.getPurchasedQty())
							* Double.parseDouble(productDto.getTotalTaxInclusive()));
					
					double netTotal = itemTotalClac - itemDiscount;
					//String itemTotal = decimalFormat.format(itemTotalClac);
					
					tabledata.append("<tr><td>");
					tabledata.append(++count);
					
					tabledata.append("</td><td>");
					tabledata.append(productDto.getItemNameOrDesc());
					
					tabledata.append("</td><td class=\"text-right\">");
					tabledata.append(String.format("%d", new Integer(productDto.getPurchasedQty())));
					tabledata.append("</td><td class=\"text-right\">");
					tabledata.append(String.format("%.2f", new Double(productDto.getDiscountPercentage()))+"");
					tabledata.append("</td><td class=\"text-right\">");
					
					
					tabledata.append(decimalFormat.format(new Double(netTotal)));
					tabledata.append("</td></tr>");
				tolal = tolal + itemTotalClac;
				
                grandTotal = grandTotal + netTotal;
                
                discount = discount + itemDiscount;
				
				gstTotal = gstTotal+ gstAmount;
				if(Integer.parseInt(productDto.getPaymentTerms())>paymentTermId) {
					paymentTermId = Integer.parseInt(productDto.getPaymentTerms());
				}
			}
				}
				if(paymentTermId == 0) {
					paymentTerm = "Pay on Delivery";
				}
				else {
					paymentTerm = paymentTermId+" Days";
				}
		}
			
			
			String invoiceAndReceiptNumber = paymentRequest.getInvoiceNumber();
			
			SimpleDateFormat dateFormateForReceipt = new SimpleDateFormat ("EEEE dd/MM/yyyy hh:mm a");
			dateFormateForReceipt.setTimeZone(TimeZone.getTimeZone(country.getCountryTimeZone()));
			String receiptDate =  dateFormateForReceipt.format(new Date());
		
			String invoiceAndReceiptGeneratedDate = receiptDate;
		String totalString = decimalFormat.format(tolal);
		
		String grandTotalString = decimalFormat.format(grandTotal);
		String discountString = decimalFormat.format(discount);
		
		String gstTotalString = decimalFormat.format(gstTotal);
		String recieptTableHeader = "";
		String invoiceTableHeader = "";
		String subtotalHeader = "";
		String totalHeader = "";
		int receiptSpan = 6;
		
		int invDisSpan = 0;
		int tdSpan = 6;
		String gstRow = "";
		
		if (type.equals("RECEIPT")) {

			if(isgsnt && isgstAppliend) {
				recieptTableHeader = "<tr>" + 
						"				<th class=\"text-left\" style=\"width:7%\">S.No.</th>" + 
						"				" + 
						"			<th class=\"text-left\" style=\"width:28%\">Items</th>" + 
						"				<th class=\"text-right\" style=\"width:12%\">HSN/SAC</th>" + 
						"				 <th class=\"text-right\" style=\"width:12%\">Qty</th>" + 
						"				<th class=\"text-right\" style=\"width:10%\">Disc%</th>" + 
						"				 <th class=\"text-right\"style=\"width:16%\">GST%</th>" + 
						"				 <th class=\"text-right\" style=\"width:15%\">Amount (" + country.getCurrencySymbol() + ")</th></tr>";
			
				subtotalHeader = "<td colspan=\"5\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">Total without Tax:</td>";
				totalHeader= "<td colspan=\"5\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;border-top: 2px solid #cdcdcd;padding:5px;\">Total with Tax:</td>";
				
				gstRow = "<tr class=\"text-right\">" + 
						"				" + 
						"		<td colspan=\"5\"></td>	<td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">GST:</td>" + 
						"				<td class=\"text-right\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;"+gstTotalString+"</td> " + 
						"			</tr> ";
				
				 receiptSpan = 6;
				 
				 
			
			
			}
			else {
				recieptTableHeader = "<tr>" + 
						"				<th class=\"text-left\" style=\"width:10%\">S.No.</th>" + 
						"				\r\n" + 
						"			<th class=\"text-left\" style=\"width:39%\">Items</th>" + 
						
						"				 <th class=\"text-right\" style=\"width:15%\">Qty</th>" + 
						"				<th class=\"text-right\" style=\"width:16%\">Disc%</th>" + 
						
						"				 <th class=\"text-right\" style=\"width:20%\">Amount (" + country.getCurrencySymbol() + ")</th></tr>";
				
				subtotalHeader = "<td colspan=\"3\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\" >Sub Total:</td>";
				totalHeader= "<td colspan=\"3\"></td><td  style=\"font-size:9pt;border-top: 2px solid #cdcdcd;border-left: 2px solid #cdcdcd;padding:5px;\">Total:</td>";
				receiptSpan = 4;
				
				
			
			}
		
		}
		
		if (type.equals("INVOICE")) {
			totalHeader = "";
			subtotalHeader= "";
			if(isgsnt && isgstAppliend) {
				invoiceTableHeader = "        <tr>" + 
										"          <th class=\"text-left\" style=\"width:8%\">Sr No.</th>" + 
										"          <th class=\"text-left\" style=\"width:21%\">Items</th>" + 
										"          <th class=\"text-right\" style=\"width:10%\">HSN/SAC</th>" + 
										"          <th class=\"text-right\" style=\"width:10%\">Qty</th>" + 
									    "          <th class=\"text-right\" style=\"width:15%\">Unit Price(" + country.getCurrencySymbol() + ")</th>" + 
									    "          <th class=\"text-right\" style=\"width:9%\">Disc%</th>" + 
										"          <th class=\"text-right\" style=\"width:12%\">GST%</th>" + 
										"          <th class=\"text-right\" style=\"width:15%\">Amount (" + country.getCurrencySymbol() + ")</th>" + 
										"        </tr>";
			
				subtotalHeader = "<td  colspan=\"2\" class=\"text-right ft\" style=\"padding:5px;\"><b>Total without Tax:</b></td>";
				totalHeader= "<td  colspan=\"2\" class=\"text-right ft bt\" style=\"padding:5px;\"><b>Total with Tax:</b></td>";
				
				gstRow = "<tr>"  + 
						"		<td colspan=\"5\" class=\"br\"></td>	<td  colspan=\"2\" class=\"text-right ft\" style=\"padding:5px;\">GST:</td>" + 
						"				<td class=\"text-right\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;"+gstTotalString+"</td>" + 
						"			</tr> ";
				
				receiptSpan = 5;
				tdSpan = 8;
				invDisSpan = 2;
			
			
			}
			else {
				invoiceTableHeader = "        <tr>" + 
						"          <th class=\"text-left\" style=\"width:10%\">Sr No.</th> " + 
						"          <th class=\"text-left\" style=\"width:26%\">Items</th>" + 
						
						"          <th class=\"text-right\" style=\"width:12%\">Qty</th>" + 
					    "          <th class=\"text-right\" style=\"width:20%\">Unit Price(" + country.getCurrencySymbol() + ")</th>" + 
					    "          <th class=\"text-right\" style=\"width:12%\">Disc%</th> " + 
						
						"          <th class=\"text-right\" style=\"width:20%\">Amount (" + country.getCurrencySymbol() + ")</th> " + 
						"        </tr> ";

               subtotalHeader = "<td  colspan=\"0\" class=\"text-right ft\" style=\"padding:5px;\"><b>Sub Total:</b></td>";
               totalHeader= "<td  colspan=\"0\" class=\"text-right ft bt\" style=\"padding:5px;\"><b>Total:</b></td>";
				
				receiptSpan = 4;
				tdSpan = 6;
			
			}
		
		}
			
		String invoice = "<!DOCTYPE html>\r\n" + 
				"<html>\r\n" + 
				"\r\n" + 
				"<head>\r\n" + 
				"  <meta charset=\"utf-8 \" />\r\n" + 
				"  <meta http-equiv=\"X-UA-Compatible \" content=\"IE=edge \" />\r\n" + 
				"  <title>Invoice</title>\r\n" + 
				"  <meta name=\"viewport \" content=\"width=device-width, initial-scale=1 \" />\r\n" + 
				"  <style>\r\n"
				+ "@font-face { font-family: Playfair Display;src: url(https://fonts.googleapis.com/css?family=Playfair+Display);}" + 
				"    .container {\r\n" + 
				"      font-family: Arial;\r\n" + 
				"      line-height: 1.3;\r\n" + 
				"      font-size: 9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .float-left {\r\n" + 
				"      float: left;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .float-right {\r\n" + 
				"      float: right;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .pending {\r\n" + 
				"      background-color: #e87f09;\r\n" + 
				"      color: #fff;\r\n" + 
				"      padding: 5px;\r\n" + 
				"      border-radius: 5px;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .text-center {\r\n" + 
				"      text-align: center;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .text-right {\r\n" + 
				"      text-align: right;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .text-left {\r\n" + 
				"      text-align: left;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable {\r\n" + 
				"      border: 2px solid #cdcdcd;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable th {\r\n" + 
				"      text-align: left;\r\n" + 
				"      background-color: #e0e0e0;\r\n" + 
				"      padding: 0px 10px;\r\n" + 
				"      font-size:9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable td {\r\n" + 
				"      text-align: right;\r\n" + 
				"      padding: 0px 10px;\r\n" + 
				"      font-size:8pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .detailtable tr {\r\n" + 
				"      height: 25px;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable {\r\n" + 
				"      width: 100%;\r\n" + 
				"      border: 2px solid #cdcdcd;\r\n" + 
				"      table-layout: fixed;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable th {\r\n" + 
				"      background-color: #e0e0e0;\r\n" + 
				"      border-bottom: 2px solid #cdcdcd;\r\n" + 
				"      font-size: 9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				
				"\r\n" + 
				"    .text-right.ft {\r\n" + 
				"      font-size: 9pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable th,\r\n" + 
				"    .billTable td {\r\n" + 
				"      padding: 5px;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .billTable td {\r\n" + 
				"      font-size: 8pt;"
				+ "padding: 8px 5px;\r\n" + 
				"    }\r\n" + 
				"    .address {\r\n" + 
				"      width: 300px;\r\n" + 
				"      word-wrap: break-word;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				
				"\r\n" + 
				"    .termConditionInner {" + 
				"      width: 100%;" + 
				"      position: relative;" + 
				"      min-height: 100px;" + 
				"      border-radius: 25px;" + 
				"      border: 2px solid #cdcdcd ;" + 
				"    }" + 
				"\r\n" + 
				"    .termCondition {\r\n" + 
				"      border: 2px solid #cdcdcd;"
				+ "border-radius: 20%;"
				+ "padding: 15px 10px;"
				+ "font-size:8pt;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .row {\r\n" + 
				"      min-width: 100%;\r\n" + 
				"      margin-bottom: 20px\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .row:before,\r\n" + 
				"    .row:after {\r\n" + 
				"      display: table;\r\n" + 
				"      content: \" \";\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .row:after {\r\n" + 
				"      clear: both;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .br {\r\n" + 
				"      border-right: 2px solid #cdcdcd;\r\n" + 
				"    }\r\n" + 
				"\r\n" + 
				"    .bt {\r\n" + 
				"      border-top: 2px solid #cdcdcd;\r\n" + 
				"    }\r\n" + 
				"  </style>\r\n" + 
				"</head>\r\n" + 
				"\r\n" + 
				"<body>\r\n" + 
				"  <div class=\"container\">\r\n" + 
				"    <div class=\"row\">\r\n" + 
				"      <div class=\"pending text-center\">"+invoiceType+"</div><br/>\r\n" + 
				"    </div>\r\n" + 
				"    <div class=\"row\" style=\"margin-bottom:0px\">\r\n" + 
				"      <div class=\"companyDetails float-left\">\r\n" + 
				"        <h2 style='margin:0px'>"+merchantName+"</h2>\r\n" + 
				"        " + merchantProfile.getEmailAddress() + "<br/> " + country.getCountryDialCode() + " - " + merchantProfile.getMobileNumber()+"<br /> " + merchantProfile.getResidentialAddress() + "<br/>"+gstNo+"\r\n" + 
				"      </div><br/>\r\n" + 
				"      <div class=\"bookingDetails float-right\">\r\n" + 
				"        <table class=\"detailtable\" cellspacing=\"0 \">\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Invoice No</th>\r\n" + 
				"            <td> <b>" + invoiceAndReceiptNumber + "</b>\r\n" + 
				"            </td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Created</th>\r\n" + 
				"            <td>" + invoiceAndReceiptGeneratedDate + "</td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Invoice Date</th>\r\n" + 
				"            <td>" + invoiceAndReceiptGeneratedDate + "</td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Total (" + country.getCurrencySymbol() + ")</th>\r\n" + 
				"            <td>" + country.getCurrencySymbol() + "&nbsp;" + grandTotalString+"</td>\r\n" + 
				"          </tr>\r\n" + 
				"          <tr>\r\n" + 
				"            <th>Payment Term</th>\r\n" + 
				"            <td>"+paymentTerm+"</td>\r\n" + 
				"          </tr>\r\n" + 
				"        </table>\r\n" + 
				"      </div>\r\n" + 
				"    </div>\r\n" + 
				"\r\n" + 
				"    <div class=\"row address\">\r\n" + 
				"      <b>Billed To: <br/>" + purchaserProfile.getFirstName() + ""+ purchaserProfile.getLastName() + "</b> <br/>"+ purchaserProfile.getEmailAddress() + " <br />"+ purchaseCountry.getCountryDialCode() + " - " + purchaserProfile.getMobileNumber() + " <br />\r\n" + 
				"      <div class=\"address \">" + purchaserProfile.getResidentialAddress() + " </div>\r\n" + 
				"    </div><br/>\r\n" + 
				"\r\n" + 
				"    <div class=\"row\">\r\n" + 
				"      <table class=\"billTable \" cellspacing=\"0\">\r\n" + 
				invoiceTableHeader+ 
				tabledata.toString() + "  "+
				
				"\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+tdSpan+" style=\"background-color:#cdcdcd;padding:1px\"></td>\r\n" + 
				"        </tr>\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				""+subtotalHeader+
				"          <td class=\"text-right\" style=\"padding:5px;\">\r\n" + 
				"           "+ country.getCurrencySymbol() + "&nbsp;" + totalString + "\r\n" + 
				"          </td>\r\n" + 
				"        </tr>\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				"          <td colspan="+invDisSpan+" class=\"text-right ft\" style=\"padding:5px;\">\r\n" + 
				"            Discount : </td>\r\n" + 
				"          <td class=\"text-right \" style=\"padding:5px;\">-"+ country.getCurrencySymbol() + "&nbsp;"+discountString+" </td>\r\n" + 
				"        </tr>\r\n" + 
				""+gstRow+
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				
				""+totalHeader+
				"          <td class=\"text-right bt\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;" + grandTotalString + " </td>\r\n" + 
				"        </tr>\r\n" + 
				"        <tr>\r\n" + 
				"          <td colspan="+receiptSpan+" class=\"br\"></td>\r\n" + 
				"          <td colspan="+invDisSpan+" class=\"text-right ft\" style=\"padding:5px;\"> <b>Amount due :</b>\r\n" + 
				"          </td>\r\n" + 
				"          <td class=\"text-right\" style=\"padding:5px;\">"+ country.getCurrencySymbol() + "&nbsp;" + grandTotalString + "</td>\r\n" + 
				"        </tr>\r\n" + 
				"      </table>\r\n" + 
				"    </div><br/>\r\n" + 
			    "<table style=\"width:100%;font-size:9pt\">\r\n"
				+ "        <tr>\r\n" + "          <th width=\"45%\" class=\"text-left\">Customer Notes</th>\r\n"
				+ "          <th width=\"10%\"></th>\r\n"
				+ "          <th width=\"45%\" class=\"text-left\">Terms & Conditions</th>\r\n" + "        </tr>\r\n "
				+ "  <tr>\r\n" + "          <td width=\"45%\" class=\"termCondition\"> " + customerNotes + " </td>\r\n"
				+ "          <td width=\"10%\"></td>\r\n" + "          <td width=\"45%\" class=\"termCondition\"> "
				+ termsAndCondition + " </td>\r\n" + "        </tr>" +

				"      </table>" + "\r\n" + 
				"\r\n" + 
				"  </div>\r\n" + 
				"  <!--main container-->\r\n" + 
				"</body>\r\n" + 
				"\r\n" + 
				"</html>";
		
		
		
		String receipt = "<!DOCTYPE html>\r\n" + "<html>\r\n" + "\r\n" + "<head>\r\n"
				+ "    <meta charset=\"utf-8\" />\r\n"
				+ "    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\r\n"
				+ "    <title>Receipt</title>\r\n"
				+ "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\r\n"
				+ "    <style>\r\n" + "        .container {\r\n" + "font-size:9pt;font-family: Arial, Helvetica, sans-serif;line-height: 1.3;\r\n"
				+ "        }\r\n" + "\r\n" + "        .header {\r\n" + "            width: 100%;\r\n"
				+ "            float: left;\r\n" + "            margin-bottom: 30px;\r\n" + "        }\r\n" + "\r\n"
				+ "        .headerDetails {\r\n" + "            width: 100%;\r\n" + "            clear: both;\r\n"
				+ "        }\r\n" + "\r\n" + "        .billSummary {\r\n" + "            margin-top: 170px;\r\n"
				+ "            width: 100%;\r\n" + "        }\r\n" + "\r\n" + "        .float-left {\r\n"
				+ "            float: left;\r\n" + "        }\r\n" + "\r\n" + "        .float-right {\r\n"
				+ "            float: right;\r\n" + "        }\r\n" + "\r\n" + "        .pending {\r\n"
				+ "            background-color: #e87f09;\r\n" + "            color: #fff;\r\n"
				+ "            padding: 5px;\r\n" + "            border-radius: 5px\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-center {\r\n" + "            text-align: center;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-right {\r\n" + "            text-align: right;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-left {\r\n" + "            text-align: left;\r\n" + "        }\r\n" + "\r\n"
				+ "        .detailtable {\r\n" + "            border: 2px solid #cdcdcd;\r\n" + "        }\r\n" + "\r\n"
				+ "        .detailtable th {\r\n" + "            text-align: left;font-size:9pt;\r\n"
				+ "            background-color: #e0e0e0;\r\n" + "            padding: 0px 10px;\r\n" + "        }\r\n"
				+ "\r\n" + "        .detailtable td {\r\n" + "            text-align: right;font-size:8pt;\r\n"
				+ "            padding: 0px 10px;\r\n" + "        }\r\n" + "\r\n" + "        .detailtable tr {\r\n"
				+ "            height: 25px;\r\n" + "        }\r\n" + "\r\n" + "        .billTable {\r\n"
				+ "            width: 100%;\r\n" + "            border: 2px solid #cdcdcd;\r\n"
				+ "            table-layout: fixed;\r\n" + "        }\r\n" + "\r\n" + "        .billTable th {\r\n"
				+ "            background-color: #e0e0e0;font-size:9pt;\r\n" + "        }\r\n" + "\r\n" + "        .billTable th,\r\n"
				+ "        .billTable td {\r\n" + "            padding: 5px;\r\n" + "        }\r\n" + "\r\n"
				+ "        .billTable td {\r\n" + "            padding: 5px;font-size:8pt;\r\n" + "        }\r\n" + "\r\n"
				+ "        .termCondition {\r\n" + "            clear: both;\r\n" + "        }\r\n" + "\r\n"
				+ "        .container {\r\n" + "            margin: 50px 100px;\r\n" + "        }\r\n" + "\r\n"
				+ "        .header {\r\n" + "            width: 100%;\r\n" + "        }\r\n" + "\r\n"
				+ "        /* .billSummary {\r\n" + "            margin-top: 50px;\r\n" + "        } */\r\n" + "\r\n"
				+ "        .text-center {\r\n" + "            text-align: center;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-right {\r\n" + "            text-align: right;\r\n" + "        }\r\n" + "\r\n"
				+ "        .text-left {\r\n" + "            text-align: left;\r\n" + "        }\r\n" + "\r\n"
				+ "        .billTable {\r\n" + "            width: 100%;\r\n" + "        }\r\n" + "\r\n"
				+ "        .billTable th,\r\n" + "        .billTable td {\r\n" + "            padding: 8px 5px;\r\n"
				+ "        }\r\n" + "\r\n" + "        .noMargin {\r\n" + "            margin: 0px;\r\n"
				+ "        }\r\n" + "\r\n" + "        .address {\r\n" + "            width: 300px;\r\n"
				+ "            word-wrap: break-word;\r\n" + "        }\r\n" + "    </style>\r\n" + "</head>\r\n"
				+ "\r\n" + "<body>\r\n" + "    <div class=\"container\">\r\n" + "        <div class=\"header\">\r\n"
				+ "            <div class=\"text-center\">\r\n"
				+ "                <h1 class=\"noMargin\" style=\"margin-bottom: 2px;\">Receipt</h1>\r\n"
				+ "                <h5 class=\"noMargin\">" + receiptDate + "</h5>\r\n" +
				
				"            </div>\r\n" + "        </div><br/>\r\n" + "        <div class=\"headerDetails\">\r\n"
				+ "            <div class=\"addressDetails float-left\">\r\n" + "                <b>\r\n"
				+ "                    Billed To:\r\n" + "                </b>\r\n" + "                \r\n"
				+ "                <b>" + purchaserProfile.getFirstName() + " " + purchaserProfile.getLastName()
				+ "</b>\r\n" + "                <br/>" + purchaserProfile.getEmailAddress() + "\r\n"
				+ "                <br/>" + purchaseCountry.getCountryDialCode() + " - "
				+ purchaserProfile.getMobileNumber() + "\r\n" + "                <br/>\r\n"
				+ "                <div class=\"address\">\r\n" + "                    "
				+ purchaserProfile.getResidentialAddress() + "\r\n" + "                </div>\r\n" +
				
				"            </div>\r\n" + "            <div class=\"bookingDetails float-right\">\r\n"
				+ "                <table class=\"detailtable\" cellspacing=\"0\">\r\n" + "                    <tr>\r\n"
				+ "                        <th>Receipt No</th>\r\n" + "                        <td>\r\n"
				+ "                            <b>" + invoiceAndReceiptNumber + "-01</b>\r\n"
				+ "                        </td>\r\n" + "                    </tr>\r\n" + "                    <tr>\r\n"
				+ "                        <th>Payment Date</th>\r\n" + "                        <td>"
				+ invoiceAndReceiptGeneratedDate + "</td>\r\n" + "                    </tr>\r\n"
				+ "<tr><th>Payment Term</th><td>"+paymentTerm+"</td></tr>"
				
				+ "                </table>\r\n" + "            </div>\r\n" + "        </div><br/>\r\n" + "\r\n"
				+ "        <div class=\"billSummary\" >\r\n" + "            <table class=\"billTable\" >\r\n" + recieptTableHeader+""
				+

				tabledata.toString() + "                <tr>\r\n"
				+ "                    <td colspan=\""+receiptSpan+1+"\" style=\"background-color: #cdcdcd;padding: 1px\"></td>\r\n"
				+ "                </tr>\r\n" + "                <tr class=\"text-right\">\r\n"   +subtotalHeader+" " 
				
				
				+ "                    <td class=\"text-right\" style=\"padding:5px;\">"+country.getCurrencySymbol()+"&nbsp;" + totalString + "</td>\r\n"
				+ "                </tr>\r\n" 
				+ " <tr class=\"text-right\">\r\n" 
				
				+ "<td colspan=\""+(receiptSpan-1)+"\"></td> <td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">Discount:</td>\r\n" 
				+ " <td class=\"text-right\" style=\"padding:5px;\">-"+ country.getCurrencySymbol() + "&nbsp;"+discountString+"</td>\r\n" 
				+ " </tr>\r\n" +gstRow+""
				
				+  "                <tr class=\"text-right\">\r\n"   +totalHeader+" "               
				
				+ "                    <td class=\"text-right\" style=\"font-size:8pt;border-top: 2px solid #cdcdcd;padding:5px;\">"+country.getCurrencySymbol()+"&nbsp;" + grandTotalString + "</td>\r\n"
				+ "                </tr>\r\n"
				+ "<tr class=\"text-right\"><td colspan=\""+(receiptSpan-1)+"\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;padding:5px;\">Amount Due:</td><td style=\"padding:5px;\">"+country.getCurrencySymbol()+"&nbsp;" + grandTotalString + "</td></tr>"
				+ "            </table>\r\n" + "        </div>\r\n"
				+ "        <div class=\"text-center\">\r\n" + "            <h5>Thanks for shopping with us</h5>\r\n"
				+ "        </div>\r\n" + "    </div>\r\n" + "    <!--main container-->\r\n" + "\r\n" + "</body>\r\n"
				+ "\r\n" + "</html>";
		
		
		if (type.equals("INVOICE")) {
			
			outPutString = invoice;

			
			String thymeleafTabledata ="<table class=\"billTable \" cellspacing=\"0\">"
					+ invoiceTableHeader + tabledata.toString() + " " +
					
					"" + "        <tr>\r\n" + "          <td colspan=" + tdSpan
					+ " style=\"background-color:#cdcdcd;padding:1px\"></td>\r\n" + "        </tr>\r\n" + "        <tr>\r\n"
					+ "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n" + "" + subtotalHeader
					+ "          <td class=\"text-right\" style=\"padding:5px;\">\r\n" + "           "
					+ country.getCurrencySymbol() + " " + totalString + "\r\n" + "          </td>\r\n" + "        </tr>\r\n"
					+ "        <tr>\r\n" + "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n"
					+ "          <td colspan=" + invDisSpan + " class=\"text-right ft\" style=\"padding:5px;\">\r\n"
					+ "            Discount : </td>\r\n" + "          <td class=\"text-right \" style=\"padding:5px;\">-"
					+ country.getCurrencySymbol() + " " + discountString + " </td>\r\n" + "        </tr>\r\n" + "" + gstRow
					+ "        <tr>\r\n" + "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n" +

					"" + totalHeader + "          <td class=\"text-right bt\" style=\"padding:5px;\">"
					+ country.getCurrencySymbol() + " " + grandTotalString + " </td>\r\n" + "        </tr>\r\n"
					+ "        <tr>\r\n" + "          <td colspan=" + receiptSpan + " class=\"br\"></td>\r\n"
					+ "          <td colspan=" + invDisSpan
					+ " class=\"text-right ft\" style=\"padding:5px;\"> <b>Amount due :</b>\r\n" + "          </td>\r\n"
					+ "          <td class=\"text-right\" style=\"padding:5px;\">" + country.getCurrencySymbol() + " "
					+ grandTotalString + "</td>\r\n" + "        </tr>\r\n" + "      </table>";
			
			


			//------------- Thymeleaf ----------
					
							com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto dto = new com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto();
							dto.setPdfType("invoice");
							//----------
						    CompanyDetails companyDetails = new CompanyDetails();
							companyDetails.setMerchantName(merchantName);
							companyDetails.setEmailAddress(merchantProfile.getEmailAddress());
							companyDetails.setCountryDialCode(country.getCountryDialCode());
							companyDetails.setMobileNumber(""+merchantProfile.getMobileNumber());
							companyDetails.setResidentialAddress(merchantProfile.getResidentialAddress());
							companyDetails.setGstNo(gstNo);
							//----------
							
							Detailtable detailTable = new Detailtable();
							detailTable.setInvoiceAndReceiptNumber(invoiceAndReceiptNumber);
							detailTable.setInvoiceAndReceiptGeneratedDate(invoiceAndReceiptGeneratedDate);
							detailTable.setCurrencySymbol(country.getCurrencySymbol());
							detailTable.setGrandTotalString(grandTotalString);
							detailTable.setPaymentTerm(paymentTerm);
							
							BillToAddress billToAddress = new BillToAddress();
							
							//billToAddress.setTableDynamicHeaderAndRows(invoiceTableHeader+tabledata);
							billToAddress.setThymeleafTabledata(thymeleafTabledata);
							billToAddress.setInvoiceType(invoiceType);
							billToAddress.setEmailAddress(purchaserProfile.getEmailAddress());
							billToAddress.setMobileNumber(country.getCountryDialCode() + " - " + merchantProfile.getMobileNumber());
							billToAddress.setPurchaserName(purchaserProfile.getFirstName() + "" + purchaserProfile.getLastName());
							billToAddress.setResidentialAddress(purchaserProfile.getResidentialAddress());
							billToAddress.setSubtotalHeader(subtotalHeader);
							billToAddress.setTotalString(totalString);
							billToAddress.setDiscountString(discountString);
							billToAddress.setTotalHeader(totalHeader);
							billToAddress.setGstTotalString(gstTotalString);
							billToAddress.setGrandTotalString(grandTotalString);
							billToAddress.setCustomerNotes(""+customerNotes);
							billToAddress.setTermsAndCondition(""+termsAndCondition);
							
							
							//PaymentRequest paymentRequest = new PaymentRequest();
							//ProductDto product1 =new ProductDto();
							//ProductDto product2 =new ProductDto();
							
							//List<ProductDto> plist=new ArrayList<>();
							//plist.add(product1);
							//plist.add(product2);
							paymentRequest.setProductList(paymentRequest.getProductList());
							
							dto.setCompanyDetails(companyDetails);
							dto.setDetailTable(detailTable);
							dto.setBillToAddress(billToAddress);
							dto.setPaymentRequest(paymentRequest);
							dto.setReceiptSpan(receiptSpan);
							dto.setInvDisSpan(invDisSpan);
							dto.setTdSpan(tdSpan);
							dto.setIsgsnt(isgsnt);
							dto.setIsgstAppliend(isgstAppliend);
							
							
							// call method to generate HTML string using thymeleaf
						String invoiceByThymeleaf =	generateInvoiceAndPdfString(dto);
							
			
							//----------------------------------
			
			
			
		} else if (type.equals("RECEIPT")) {
			
			outPutString = receipt;
			
			String thymeleafTabledataForReceipt ="<table class=\"billTable\" >\r\n"
			+ recieptTableHeader + "" +

			tabledata.toString() + "                <tr>\r\n" + "                    <td colspan=\"" + receiptSpan
			+ 1 + "\" style=\"background-color: #cdcdcd;padding: 1px\"></td>\r\n" + "                </tr>\r\n"
			+ "                <tr class=\"text-right\">\r\n" + subtotalHeader + " "

			+ "                    <td class=\"text-right\" >" + country.getCurrencySymbol() + " " + totalString
			+ "</td>\r\n" + "                </tr>\r\n" + " <tr class=\"text-right\">\r\n"

			+ "<td colspan=\"" + (receiptSpan - 1)
			+ "\"></td> <td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;\">Discount:</td>\r\n"
			+ " <td class=\"text-right\" >-" + country.getCurrencySymbol() + " " + discountString + "</td>\r\n"
			+ " </tr>\r\n" + gstRow + ""

			+ "                <tr class=\"text-right\">\r\n" + totalHeader + " "

			+ "                    <td class=\"text-right\" style=\"font-size:8pt;border-top: 2px solid #cdcdcd;\">"
			+ country.getCurrencySymbol() + " " + grandTotalString + "</td>\r\n" + "                </tr>\r\n"
			+ "<tr class=\"text-right\"><td colspan=\"" + (receiptSpan - 1)
			+ "\"></td><td  style=\"font-size:9pt;border-left: 2px solid #cdcdcd;\">Amount Due:</td><td >"
			+ country.getCurrencySymbol() + " " + grandTotalString + "</td></tr>" + "</table>";
			
			
			
			//------------- Thymeleaf ----------
			
			com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto dto = new com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto();
			dto.setPdfType("receipt");
			//----------
		    CompanyDetails companyDetails = new CompanyDetails();
		    companyDetails.setMerchantName(merchantName);
			companyDetails.setEmailAddress(merchantProfile.getEmailAddress());
			companyDetails.setCountryDialCode(country.getCountryDialCode());
			companyDetails.setMobileNumber(""+merchantProfile.getMobileNumber());
			companyDetails.setResidentialAddress(merchantProfile.getResidentialAddress());
			companyDetails.setGstNo(gstNo);
			//----------
			
			Detailtable detailTable = new Detailtable();
			detailTable.setInvoiceAndReceiptNumber(invoiceAndReceiptNumber);
			detailTable.setInvoiceAndReceiptGeneratedDate(invoiceAndReceiptGeneratedDate);
			detailTable.setCurrencySymbol(country.getCurrencySymbol());
			detailTable.setGrandTotalString(grandTotalString);
			detailTable.setPaymentTerm(paymentTerm);
			
			BillToAddress billToAddress = new BillToAddress();
			
			//billToAddress.setTableDynamicHeaderAndRows(invoiceTableHeader+tabledata);
			billToAddress.setThymeleafTabledata(thymeleafTabledataForReceipt);
			billToAddress.setInvoiceType(invoiceType);
			billToAddress.setEmailAddress(purchaserProfile.getEmailAddress());
			billToAddress.setMobileNumber(country.getCountryDialCode() + " - " + merchantProfile.getMobileNumber());
			billToAddress.setPurchaserName(purchaserProfile.getFirstName() + "" + purchaserProfile.getLastName());
			billToAddress.setResidentialAddress(purchaserProfile.getResidentialAddress());
			billToAddress.setSubtotalHeader(subtotalHeader);
			billToAddress.setTotalString(totalString);
			billToAddress.setDiscountString(discountString);
			billToAddress.setTotalHeader(totalHeader);
			billToAddress.setGstTotalString(gstTotalString);
			billToAddress.setGrandTotalString(grandTotalString);
			billToAddress.setCustomerNotes(""+customerNotes);
			billToAddress.setTermsAndCondition(""+termsAndCondition);
			
			
			//PaymentRequest paymentRequest = new PaymentRequest();
			//ProductDto product1 =new ProductDto();
			//ProductDto product2 =new ProductDto();
			
			//List<ProductDto> plist=new ArrayList<>();
			//plist.add(product1);
			//plist.add(product2);
			paymentRequest.setProductList(paymentRequest.getProductList());
			
			dto.setCompanyDetails(companyDetails);
			dto.setDetailTable(detailTable);
			dto.setBillToAddress(billToAddress);
			dto.setPaymentRequest(paymentRequest);
			dto.setReceiptSpan(receiptSpan);
			dto.setInvDisSpan(invDisSpan);
			dto.setTdSpan(tdSpan);
			dto.setIsgsnt(isgsnt);
			dto.setIsgstAppliend(isgstAppliend);
			
			
			// call method to generate HTML string using thymeleaf
		String invoiceByThymeleaf =	generateInvoiceAndPdfString(dto);
			

			
			
			
		}
		
		return outPutString;
		}
	
	
	/**
	 * use to save purchased product in database.
	 * 
	 * @param paymentRequest
	 * @return
	 */
	public PaymentResponse savePurchasedProduct(PaymentRequest paymentRequest) {
		
		
	com.ireslab.sendx.electra.dto.PurchaserDto purchaserDto = paymentRequest.getPurchaserDetails();
		
		ClientInfoRequest clientInfoRequest = new ClientInfoRequest();
		clientInfoRequest.setEmail(purchaserDto.getEmailAddress());
		ClientInfoResponse clientInformation = transactionalApiService.clientInformation(clientInfoRequest);
		if(clientInformation.getCode() != 101) {
			LOG.debug("Purchaser is a client ! ");
			purchaserDto.setCorrelationId(clientInformation.getClientCorrelationId());
			purchaserDto.setClient(true);
			
		}
		else {
			LOG.debug("Purchaser not a client ! ");
			Account account = accountRepository.findByMobileNumberAndCountry_CountryDialCode(new BigInteger(purchaserDto.getMobileNumber()), purchaserDto.getCountryDialCode());
			purchaserDto.setCorrelationId(account.getUserCorrelationId());
			purchaserDto.setClient(false);
		}
		paymentRequest.setPurchaserDetails(purchaserDto);
		PaymentResponse paymentResponse = transactionalApiService.savePurchasedProduct(paymentRequest);
		return paymentResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#updateDeviceSpecificParameter(com.ireslab.sendx.electra.model.SendxElectraRequest)
	 */
	@Override
	 public SendxElectraResponse updateDeviceSpecificParameter(SendxElectraRequest sendxElectraRequest) {
	  SendxElectraResponse sendxElectraResponse = new SendxElectraResponse();
	  
	  Account account = accountRepository.findBymobileNumber(new BigInteger(sendxElectraRequest.getMobileNumber()));
	  
	  if(account!=null) {
	   account.setGcmRegisterKey(sendxElectraRequest.getGcmRegisterKey());
	   
		sendxElectraRequest.setUserCorrelationId(account.getUserCorrelationId());
	  
	   
	   SendxElectraResponse sendxElectraRes =  transactionalApiService.updateDeviceSpecificParameter(sendxElectraRequest);
	   
	   accountRepository.save(account);
	   sendxElectraResponse.setCode(100);
	   sendxElectraResponse.setMessage("SUCCESS");
	   
	  }else {
	   
	   sendxElectraResponse.setCode(101);
	   sendxElectraResponse.setMessage("GCM Register key not saved..!!");
	  }
	  
	  
	  return sendxElectraResponse;
	 }

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#sendInvoicePayload(com.ireslab.sendx.electra.model.PaymentRequest)
	 */
	@Override
	public PaymentResponse sendInvoicePayload(PaymentRequest paymentRequest) {
		PaymentResponse paymentResponse = new PaymentResponse();
		
		
				Account account = accountRepository.findBymobileNumber(new BigInteger(paymentRequest.getMerchantDetails().getMobileNumber()));
				Profile profile =account.getProfile();
				
				ClientInfoRequest clientInfoRequest = new ClientInfoRequest();
				
				clientInfoRequest.setEmail(profile.getEmailAddress());
				
				ClientInfoResponse clientInformation = transactionalApiService.clientInformation(clientInfoRequest);
				
		
		
		Account userAccount = accountRepository.findBymobileNumber(new BigInteger(paymentRequest.getPurchaserDetails().getMobileNumber()));
		AndroidPushNotificationRequest androidPushNotificationRequestForUser = new AndroidPushNotificationRequest();
		 JSONObject bodyForUser = new JSONObject();
		 JSONObject notificationForUser = new JSONObject();
	     JSONObject dataForUser = new JSONObject();
	     
	     
	  
	      try {
			bodyForUser.put("to",  userAccount.getGcmRegisterKey());
			  bodyForUser.put("priority", "high");
			  	notificationForUser.put("title", "Electra Notification");
			  	notificationForUser.put("body", "Invoice for user "+userAccount.getMobileNumber());
			  	dataForUser.put("message", "Invoice has been received!");
			  	dataForUser.put("code", "101");
			  bodyForUser.put("notification", notificationForUser);
			  bodyForUser.put("data", dataForUser);
			
			  List<JSONObject> productArray = new ArrayList<>(); 
			  
			  for (com.ireslab.sendx.electra.dto.ProductDto object : paymentRequest.getProductList()) {
				  JSONObject product = new JSONObject();
				 
				    product.put("productCode", object.getProductCode());
					product.put("itemNameOrDesc", object.getItemNameOrDesc());
					product.put("itemPrice", object.getItemPrice());
					product.put("invoiceType", object.getInvoiceType());
					product.put("discountPercentage", object.getDiscountPercentage());
					product.put("discount", object.getDiscount());
					product.put("subTotal", object.getSubTotal());
					product.put("purchasedQty", object.getPurchasedQty());
					product.put("totalItemPrice", object.getTotalItemPrice());
					product.put("totalTaxInclusive", object.getTotalTaxInclusive());
					product.put("cgst", object.getCgst());
					product.put("sgstUtgst", object.getSgstUtgst());
					product.put("igst", object.getIgst());
					product.put("total", object.getTotal());
					product.put("interState", object.getInterState());
					product.put("itemCode", object.getItemCode());
					product.put("paymentTerms", object.getPaymentTerms());
					product.put("customerNotes", object.getCustomerNotes());
					product.put("termsAndConditions", object.getTermsAndConditions());
				  productArray.add(product);
			}
			  
			  bodyForUser.put("productDetails", productArray);
			  JSONObject merchantDetails = new JSONObject();
			 
			  com.ireslab.sendx.electra.dto.MerchantDto merchantData = paymentRequest.getMerchantDetails();
			  merchantDetails.put("countryDialCode",merchantData.getCountryDialCode() );
			  merchantDetails.put("mobileNumber", ""+profile.getMobileNumber());
			  merchantDetails.put("firstName", profile.getFirstName());
			  merchantDetails.put("lastName", profile.getLastName());
			  merchantDetails.put("emailAdd", profile.getEmailAddress());
			  merchantDetails.put("companyCode", clientInformation.getCompanyCode());
			  merchantDetails.put("gstn", merchantData.getGstn());
			  bodyForUser.put("merchantDetails", merchantDetails);
			 		} catch (JSONException e) {
			paymentResponse.setCode(101);
		      paymentResponse.setMessage("Failed to issued invoice.");
		      
			e.printStackTrace();
			return paymentResponse;
		}
	     
	      androidPushNotificationRequestForUser.setBody(bodyForUser);
	      //androidPushNotificationRequestForUser.setFirebaseServiceKey(merchantData.getFirebaseServiceKey());
	      
	      //--send push notification
	      pushNotification(androidPushNotificationRequestForUser);
	     
	      //--save push notification data
	      Profile Userprofile = userAccount.getProfile();
	      Notification notification = new Notification();
	      notification.setCreatedDate(new Date());
	      notification.setEmailAddress(Userprofile.getEmailAddress());
	      notification.setMobileNumber(""+Userprofile.getMobileNumber());
	     
	      notification.setNotificationData(bodyForUser.toString());
	      notification.setStatus(false);
	      notification.setInvoice(true);
	     // notification.set
	      saveNotifidationData(paymentRequest, notification);
	     
	      paymentResponse.setCode(100);
	      paymentResponse.setMessage("Invoice issued successfully.");
	      
		return paymentResponse;
	}

	

	/**
	 * use to save notification data in database.
	 * 
	 * @param paymentRequest
	 * @param notification
	 */
	private void saveNotifidationData(PaymentRequest paymentRequest,Notification notification) {
		
		notificationRepo.save(notification);
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#updateNotification(com.ireslab.sendx.electra.model.SendxElectraRequest)
	 */
	@Override
	public SendxElectraResponse updateNotification(SendxElectraRequest sendxElectraRequest) {
			
		SendxElectraResponse sendxElectraResponse  = transactionalApiService.updateNotificationApi(sendxElectraRequest);
		
			
			return sendxElectraResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#getAllNotification(java.lang.String)
	 */
	@Override
	public SendxElectraResponse getAllNotification(String mobileNumber) {
		
		SendxElectraResponse sendxElectraResponse =new SendxElectraResponse();
				
		Account account = accountRepository.findBymobileNumber(new BigInteger(mobileNumber));
				
		sendxElectraResponse = transactionalApiService.getAllNotification(account.getUserCorrelationId());
		 List<com.ireslab.sendx.electra.dto.Notification> notificationList = sendxElectraResponse.getNotificationList();
		if(notificationList.size()>0 && !notificationList.isEmpty()) {
			
			sendxElectraResponse.setNotificationList(notificationList);
			sendxElectraResponse.setCode(100);
			sendxElectraResponse.setMessage("SUCCESS");
			sendxElectraResponse.setStatus(200);
			return sendxElectraResponse;
		}
		sendxElectraResponse.setCode(101);
		sendxElectraResponse.setMessage("Notification List is Empty");
		sendxElectraResponse.setStatus(200);
		return sendxElectraResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#searchUserByuniqueCodeInElectra(java.lang.String)
	 */
	@Override
	public UserProfile searchUserByuniqueCodeInElectra(String uniqueCode) {
		LOG.info("Searching user with uniqueCode :"+uniqueCode);
		UserProfile searchUserProfileByUniqueCode = transactionalApiService.searchUserProfileByUniqueCode(uniqueCode);
		return searchUserProfileByUniqueCode;
	}
	
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#deleteNotificationData(com.ireslab.sendx.electra.model.NotificationRequest)
	 */
	@Override
	public NotificationResponse deleteNotificationData(NotificationRequest notificationRequest) {
		LOG.info("Request for delete notification");
		NotificationResponse notificationResponse = transactionalApiService.deleteNotificationData(notificationRequest);
		return notificationResponse;
	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#searchUserByDialCodeAndMobileInElectra(java.lang.String, java.lang.Long)
	 */
	@Override
	public UserProfile searchUserByDialCodeAndMobileInElectra(String beneficiaryCountryDialCode,
			Long beneficiaryMobileNumber) {
		LOG.info("Searching user with DialCode And MobileNumber :"+beneficiaryCountryDialCode+beneficiaryMobileNumber);
		UserProfile searchUserProfileByUniqueCode = transactionalApiService.searchUserProfileByDialCodeAndMobile(beneficiaryCountryDialCode,beneficiaryMobileNumber);
		return searchUserProfileByUniqueCode;

	}

	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#downloadInvoicePdf(com.ireslab.sendx.electra.model.PaymentRequest, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void downloadInvoicePdf(PaymentRequest paymentRequest, HttpServletRequest request,
			HttpServletResponse response) {
		ClientInfoRequest clientInfoRequest = new ClientInfoRequest();
		clientInfoRequest.setEmail(paymentRequest.getMerchantDetails().getEmailAddress());

		//Get purchaser account from electra server based on unique code.
		UserProfile purchaserProfile = transactionalApiService
				.searchUserProfileByUniqueCode(paymentRequest.getPurchaserDetails().getUniqueCode());
		ClientInfoResponse clientInformation = transactionalApiService.clientInformation(clientInfoRequest);

		UserProfile merchantProfile=transactionalApiService.searchUserProfileByUniqueCode(paymentRequest.getMerchantDetails().getUniqueCode());
		
		String invoiceAndReceiptNumber = org.apache.commons.lang3.StringUtils
				.leftPad(String.valueOf(paymentRequest.getNotificationId()), 6, "0");
		invoiceAndReceiptNumber = "RXC-" + invoiceAndReceiptNumber;
		paymentRequest.setInvoiceNumber(invoiceAndReceiptNumber);
		
		
		
		String invoice=generateInvoiceOrReceipt(paymentRequest, merchantProfile, purchaserProfile, clientInformation, "INVOICE");

		
		response.setContentType("application/pdf");
		
		
		String catalinaHome = System.getenv("CATALINA_HOME");
		catalinaHome = (catalinaHome == null) ? System.getProperty("catalina.home") : catalinaHome;

		StringBuilder directory = new StringBuilder();
		directory.append(catalinaHome);
		directory.append(File.separator);
		directory.append("webapps");
		directory.append(File.separator);
		directory.append("pdf");

		String sFontDir = directory.toString();
		

		XMLWorkerFontProvider fontImp = new XMLWorkerFontProvider(sFontDir, null);
		FontFactory.setFontImp(fontImp);
		
		Document documentForInvoice = new Document(PageSize.LETTER);

		PdfWriter pdfWriterForInvoice = null;

		try {
			pdfWriterForInvoice = PdfWriter.getInstance(documentForInvoice, response.getOutputStream());

		} catch (FileNotFoundException e) {
			
			e.printStackTrace();
		} catch (DocumentException e) {
			
			e.printStackTrace();
		} catch (IOException e) {
			
			e.printStackTrace();
		}

		documentForInvoice.open();
		XMLWorkerHelper workerForInvoice = XMLWorkerHelper.getInstance();

		try {

			
			
			workerForInvoice.parseXHtml(pdfWriterForInvoice, documentForInvoice,
					new ByteArrayInputStream(invoice.getBytes()), null, null, fontImp);

		} catch (IOException e) {
			
			e.printStackTrace();
		}

		documentForInvoice.close();

	}
	/* (non-Javadoc)
	 * @see com.ireslab.sendx.service.CommonService#generateInvoiceAndPdfString(com.ireslab.sendx.electra.dto.ReceiptAndInvoiceUtilDto)
	 */
	@Override
	public String generateInvoiceAndPdfString(ReceiptAndInvoiceUtilDto dto) {
		Context context = new Context();
		context.setVariable("receiptAndInvoiceUtilDto", dto);
		String html ="";
		if(dto.getPdfType().equals("invoice")) {
			html= templateEngine.process("invoice", context);
		}
		
		if(dto.getPdfType().equals("receipt")) {
			html= templateEngine.process("receipt", context);	
		}
		return html;
	}

	
}