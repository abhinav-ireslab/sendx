package com.ireslab.sendx.web;

import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ireslab.sendx.electra.model.ExchangeResponse;
import com.ireslab.sendx.exception.BusinessException;
import com.ireslab.sendx.model.CheckoutBanksResponse;
import com.ireslab.sendx.model.ContactListVerificationRequest;
import com.ireslab.sendx.model.ContactListVerificationResponse;
import com.ireslab.sendx.model.CountryListResponse;
import com.ireslab.sendx.model.MiscConfigDetailsResponse;
import com.ireslab.sendx.service.CommonService;
import com.ireslab.sendx.service.TransactionalApiService;
import com.ireslab.sendx.util.AppStatusCodes;
import com.ireslab.sendx.util.PropConstants;

/**
 * @author iRESlab
 *
 */
@RestController
public class GenericController {

	private static final Logger LOG = LoggerFactory.getLogger(GenericController.class);

	

	@Autowired
	private CommonService commonService;

	@Autowired
	private TransactionalApiService transactionService;

	
	/**
	 * use to get country list.
	 * 
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/allCountryDetails", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public CountryListResponse getAllCountryDetails() throws JsonProcessingException {

		CountryListResponse countryListResponse = null;
		LOG.info("Getting Supported countries details request received . . . ");

		countryListResponse = commonService.getAllCountryDetails();
		LOG.info("Getting Supported countries details response sent . . . ");
		/* + objectWriter.writeValueAsString(countryListResponse)); */

		return countryListResponse;
	}

	/**
	 * use to get exchange rate of currencies.
	 * 
	 * @param userCorrelationId
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/getExchangeDetails", method = RequestMethod.GET)
	public ResponseEntity<ExchangeResponse> getExchangeDetails(
			@RequestParam(value = "userCorrelationId", required = false) String userCorrelationId)
			throws JsonProcessingException {

		LOG.info("Exchange details  request received  : ");
		ExchangeResponse exchangeDetailsResponse = transactionService.getAllExchangeDetails(userCorrelationId);
		LOG.info("Response sent for exchange details ");
		return new ResponseEntity<>(exchangeDetailsResponse, HttpStatus.OK);
	}

	/**
	 * use to verify contact based on country dial code.
	 * 
	 * @param contactListVerificationRequest
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/verifyContactList", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ContactListVerificationResponse validateContacts(
			@RequestBody ContactListVerificationRequest contactListVerificationRequest) throws JsonProcessingException {

		ContactListVerificationResponse contactListVerificationResponse = null;
		LOG.info("Contact List Verification request received . . . "
				+ contactListVerificationRequest.getCountryDialCode());

		contactListVerificationResponse = commonService.validateContacts(contactListVerificationRequest);
		LOG.info("Contact List Verification response sent . . . "
																	 /*+objectWriter.writeValueAsString(
																	 contactListVerificationResponse)*/
																	 );

		return contactListVerificationResponse;
	}

	/**
	 * use to get bank list based on country.
	 * 
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/allCheckoutBankDetails", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public CheckoutBanksResponse getCheckoutBanks(
			@RequestParam(value = "countryDialCode", required = true) String countryDialCode)
			throws JsonProcessingException {

		CheckoutBanksResponse checkoutBanksResponse = null;
		LOG.info("Getting supported checkout bank details based on countryCode : " + countryDialCode);

		if (countryDialCode == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, AppStatusCodes.INVALID_REQUEST,
					PropConstants.INVALID_REQUEST);
		}

		checkoutBanksResponse = commonService.getCheckoutBanksDetails(countryDialCode);
		LOG.info("Getting supported checkout bank details response sent ");

		return checkoutBanksResponse;
	}

	/**
	 * @param countryDialCode
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/miscConfigDetails", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public MiscConfigDetailsResponse getSendxConfigDetails(
			@RequestParam(value = "mobileNumber", required = false) Long mobileNumber,
			@RequestParam(value = "countryDialCode", required = false) String countryDialCode)
			throws JsonProcessingException {

		return commonService.getMiscConfigDetails(BigInteger.valueOf(mobileNumber), countryDialCode);
	}

	

	
}
