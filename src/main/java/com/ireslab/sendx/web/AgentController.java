/**
 * 
 */
package com.ireslab.sendx.web;

import java.math.BigInteger;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ireslab.sendx.exception.BusinessException;
import com.ireslab.sendx.model.AgentRequest;
import com.ireslab.sendx.model.AgentRequestBody;
import com.ireslab.sendx.model.AgentResponse;
import com.ireslab.sendx.service.AgentService;
import com.ireslab.sendx.util.AppStatusCodes;
import com.ireslab.sendx.util.PropConstants;

@RestController
public class AgentController {

	@Autowired
	private ObjectWriter objectWriter;

	@Autowired
	private AgentService agentService;

	private static final Logger LOG = LoggerFactory.getLogger(AgentController.class);

	/**
	 * This function use to register user as a agent.
	 * 
	 * @param agentRequest
	 * @param request
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/agentRegister", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	public AgentResponse agentRegister(@RequestBody AgentRequest agentRequest, HttpServletRequest request)
			throws JsonProcessingException {

		LOG.info("Request received from Agent Registration - " + agentRequest.toString());

		AgentResponse agentResponse = agentService.registerAgent(agentRequest);
		LOG.info("Register agent response sent - " + objectWriter.writeValueAsString(agentResponse));

		return agentResponse;
	}

	/**
	 * This function use to get all agents list.
	 * 
	 * @param agentRequestBody
	 * @param request
	 * @return
	 * @throws JsonProcessingException
	 */
	@RequestMapping(value = "/getAgent", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public AgentResponse getAgent(@RequestBody AgentRequestBody agentRequestBody, HttpServletRequest request)
			throws JsonProcessingException {

		// Getting username details from Spring Security Context

		BigInteger mobileNumber = agentRequestBody.getMobileNumber();
		String countryDialCode = agentRequestBody.getCountryDialCode();

		AgentResponse agentResponse = null;
		LOG.info("Get agent request received - \n\t mobileNumber : " + mobileNumber + ",\n\t countryCode : "
				+ countryDialCode);

		if (mobileNumber == null || countryDialCode == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, AppStatusCodes.INVALID_REQUEST,
					PropConstants.INVALID_REQUEST);
		}

		agentResponse = agentService.getAgent(agentRequestBody);
		LOG.info("Get agent response sent " );

		return agentResponse;
	}

}
