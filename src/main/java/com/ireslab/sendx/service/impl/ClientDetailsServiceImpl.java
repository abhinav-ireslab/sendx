package com.ireslab.sendx.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.ClientRegistrationException;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.stereotype.Service;

import com.ireslab.sendx.entity.ClientCredential;
import com.ireslab.sendx.repository.ClientCredentialRepository;

/**
 * @author iRESlab
 *
 */
@Primary
@Service(value = "clientDetailsService")
public class ClientDetailsServiceImpl implements ClientDetailsService {

	
	
	public static final Logger LOG = LoggerFactory.getLogger(ClientDetailsServiceImpl.class);

	@Autowired
	private ClientCredentialRepository clientCredentialRepo;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.security.oauth2.provider.ClientDetailsService#
	 * loadClientByClientId(java.lang.String)
	 */
	@Override
	public ClientDetails loadClientByClientId(String clientId) throws ClientRegistrationException {
		/*
		 * ClientOauthDetails clientOauthDetails =
		 * clientOauthDetailsDao.find(clientId);
		 * 
		 * if (clientOauthDetails == null) { throw new
		 * ClientRegistrationException("Client Not Found"); }
		 * 
		 * BaseClientDetails clientDetails = new
		 * BaseClientDetails(clientOauthDetails.getClientId(),
		 * clientOauthDetails.getResourceIds(), clientOauthDetails.getScope(),
		 * clientOauthDetails.getAuthorizedGrantTypes(),
		 * clientOauthDetails.getAuthorities());
		 */
		
		
		
/*SCRU-176 [Multi-tenancy of database]*/
		/*BaseClientDetails clientDetails = new BaseClientDetails(sendxConfig.clientId, sendxConfig.resourceIds,
				sendxConfig.scopes, sendxConfig.grantTypes, sendxConfig.authorities);
		clientDetails.setClientSecret(sendxConfig.clientSecret);*/
		
		LOG.info("Validating access credentials for App authorization. . . ");
		ClientCredential clientCredentials = clientCredentialRepo.findByClientId(clientId);
		BaseClientDetails clientDetails = new BaseClientDetails(clientCredentials.getClientId(), clientCredentials.getResourceIds(),
				clientCredentials.getScopes(), clientCredentials.getGrantTypes(), clientCredentials.getAuthorities());
		clientDetails.setClientSecret(clientCredentials.getClientSecret());

		// clientDetails.setAccessTokenValiditySeconds(clientOauthDetails.getAccessTokenValidity());
		// clientDetails.setRefreshTokenValiditySeconds(clientOauthDetails.getRefreshTokenValidity());
		return clientDetails;
	}
}
