package com.ireslab.sendx.service.impl;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.auth0.jwt.internal.org.apache.commons.lang3.exception.ExceptionUtils;
import com.ireslab.sendx.entity.ScheduledTransaction;
import com.ireslab.sendx.model.SendTokensRequest;
import com.ireslab.sendx.repository.ScheduledTransactionRepository;
import com.ireslab.sendx.service.TransactionService;

/**
 * @author iRESlab
 *
 */
@Component
public class ScheduledTransactionExecutor {

	private static final Logger LOG = LoggerFactory.getLogger(ScheduledTransactionExecutor.class);

	@Autowired
	private ScheduledTransactionRepository scheduledTxnRepo;

	@Autowired
	private TransactionService txnService;

	
	/**
	 * use to perform schedule transaction.
	 * 
	 * @param mobileNumber
	 * @param countryDialCode
	 * @param clientCorrelationId
	 */
	@Async
	public void executeScheduledTransactions(Long mobileNumber, String countryDialCode, String clientCorrelationId) {

		LOG.info("Executing scheduled transactions for user, mobile no - "+mobileNumber+ "\n country dial code - "+countryDialCode+"\n client correlationId - "+clientCorrelationId);

		List<ScheduledTransaction> scheduledTransactions = scheduledTxnRepo
				.findByBeneficiaryMobileNumberAndBeneficiaryCountry_CountryDialCode(BigInteger.valueOf(mobileNumber),
						countryDialCode);

		scheduledTransactions.forEach((scheduledTransaction) -> {

			// Only for P2P transfers, not for bank or merchant transfers
			if (!scheduledTransaction.isCashOut()) {
				try {
					SendTokensRequest sendTokensRequest = new SendTokensRequest();
					sendTokensRequest.setSenderMobileNumber(
							scheduledTransaction.getSenderAccount().getMobileNumber().longValue());
					sendTokensRequest.setSenderCountryDialCode(
							scheduledTransaction.getSenderAccount().getCountry().getCountryDialCode());
					sendTokensRequest.setBeneficiaryMobileNumber(mobileNumber);
					sendTokensRequest.setBeneficiaryCountryDialCode(countryDialCode);
					sendTokensRequest.setNoOfTokens(scheduledTransaction.getNoOfTokens());
					sendTokensRequest.setClientCorrelationId(clientCorrelationId);

					
					
					txnService.handleTokensTransfer(sendTokensRequest);
					scheduledTxnRepo.delete(scheduledTransaction);

				} catch (Exception exp) {
					LOG.error("Error executing token transfer transaction " + ExceptionUtils.getStackTrace(exp));
				}
			}
		});
	}

	
}
