/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.identity.authenticator.instagram;

import org.apache.commons.lang.StringUtils;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthClientResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.utils.JSONUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.wso2.carbon.identity.application.authentication.framework.AuthenticatorFlowStatus;
import org.wso2.carbon.identity.application.authentication.framework.FederatedApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.exception.LogoutFailedException;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authenticator.oidc.OIDCAuthenticatorConstants;
import org.wso2.carbon.identity.application.authenticator.oidc.OpenIDConnectAuthenticator;
import org.wso2.carbon.identity.application.common.model.ClaimMapping;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.core.util.IdentityUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Authenticator for Instagram
 */
public class InstagramAuthenticator extends OpenIDConnectAuthenticator implements FederatedApplicationAuthenticator {

    private static final long serialVersionUID = -1179165995021182755L;
    private static final Log log = LogFactory.getLog(InstagramAuthenticator.class);

    /**
     * Get Instagram authorization endpoint
     */
    @Override
    protected String getAuthorizationServerEndpoint(Map<String, String> authenticatorProperties) {
        return InstagramAuthenticatorConstants.INSTAGRAM_OAUTH_ENDPOINT;
    }

    /**
     * Get Instagram access token endpoint
     */
    @Override
    protected String getTokenEndpoint(Map<String, String> authenticatorProperties) {
        return InstagramAuthenticatorConstants.INSTAGRAM_TOKEN_ENDPOINT;
    }

    /**
     * Get Instagram user info endpoint
     */
    @Override
    protected String getUserInfoEndpoint(OAuthClientResponse token, Map<String, String> authenticatorProperties) {
        return InstagramAuthenticatorConstants.INSTAGRAM_USERINFO_ENDPOINT;
    }

    /**
     * Always return false as there is no ID token in Instagram OAuth.
     *
     * @param authenticatorProperties Authenticator properties.
     * @return False
     */
    @Override
    protected boolean requiredIDToken(Map<String, String> authenticatorProperties) {
        return false;
    }

    /**
     * Get friendly name of the Authenticator
     */
    @Override
    public String getFriendlyName() {
        return InstagramAuthenticatorConstants.INSTAGRAM_CONNECTOR_FRIENDLY_NAME;
    }

    /**
     * Get name of the Authenticator
     */
    @Override
    public String getName() {
        return InstagramAuthenticatorConstants.INSTAGRAM_CONNECTOR_NAME;
    }

    @Override
    public AuthenticatorFlowStatus process(HttpServletRequest request, HttpServletResponse response, AuthenticationContext context) throws AuthenticationFailedException, LogoutFailedException {
        if (context.isLogoutRequest()) {
            try {
                if (!this.canHandle(request)) {
                    context.setCurrentAuthenticator(this.getName());
                    initiateLogoutRequest(request, response, context);
                    return AuthenticatorFlowStatus.INCOMPLETE;
                } else {
                    processLogoutResponse(request, response, context);
                    return AuthenticatorFlowStatus.SUCCESS_COMPLETED;
                }
            } catch (UnsupportedOperationException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Ignoring UnsupportedOperationException.", e);
                }
                return AuthenticatorFlowStatus.SUCCESS_COMPLETED;
            }
        } else {
            return super.process(request, response, context);
        }
    }

    /**
     * Get OAuth2 Scope
     *
     * @param scope                   Scope
     * @param authenticatorProperties Authentication properties.
     * @return OAuth2 Scope
     */
    @Override
    protected String getScope(String scope, Map<String, String> authenticatorProperties) {

        return InstagramAuthenticatorConstants.INSTAGRAM_BASIC_SCOPE;
    }

    /**
     * Process the response of first call
     */
    @Override
    protected void processAuthenticationResponse(HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 AuthenticationContext context)
            throws AuthenticationFailedException {
        try {
            Map<String, String> authenticatorProperties = context.getAuthenticatorProperties();
            String clientId = authenticatorProperties.get(OIDCAuthenticatorConstants.CLIENT_ID);
            String clientSecret = authenticatorProperties.get(OIDCAuthenticatorConstants.CLIENT_SECRET);
            String tokenEndPoint = getTokenEndpoint(authenticatorProperties);
            String callbackUrl = getCallbackUrl(authenticatorProperties);

            OAuthAuthzResponse authzResponse = OAuthAuthzResponse.oauthCodeAuthzResponse(request);
            String code = authzResponse.getCode();

            OAuthClientRequest accessRequest =
                    getAccessRequest(tokenEndPoint, clientId, code, clientSecret, callbackUrl);
            OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
            OAuthClientResponse oAuthResponse = getOauthResponse(oAuthClient, accessRequest);
            String accessToken = oAuthResponse.getParam(OIDCAuthenticatorConstants.ACCESS_TOKEN);
            String userObj = oAuthResponse.getParam(InstagramAuthenticatorConstants.INSTAGRAM_USER);
            if (StringUtils.isBlank(accessToken)) {
                throw new AuthenticationFailedException("Access token is empty or null");
            }
            context.setProperty(OIDCAuthenticatorConstants.ACCESS_TOKEN, accessToken);
            AuthenticatedUser authenticatedUserObj = AuthenticatedUser
                    .createFederateAuthenticatedUserFromSubjectIdentifier(JSONUtils.parseJSON(userObj)
                            .get(InstagramAuthenticatorConstants.INSTAGRAM_USERNAME).toString());
            authenticatedUserObj.setAuthenticatedSubjectIdentifier(JSONUtils.parseJSON(userObj)
                    .get(InstagramAuthenticatorConstants.INSTAGRAM_USERNAME).toString());
            Map<ClaimMapping, String> claims = getSubjectAttributes(oAuthResponse, authenticatorProperties);
            authenticatedUserObj.setUserAttributes(claims);
            context.setSubject(authenticatedUserObj);
        } catch (OAuthProblemException e) {
            throw new AuthenticationFailedException("Authentication process failed", e);
        }
    }

    /**
     * Get subject attributes.
     *
     * @param userObj                 OAuthClientResponse
     * @param authenticatorProperties Map<String, String>
     * @return Map<ClaimMapping, String> Claim mappings.
     */
    @Override
    protected Map<ClaimMapping, String> getSubjectAttributes(OAuthClientResponse userObj,
                                                             Map<String, String> authenticatorProperties) {
        Map<ClaimMapping, String> claims = new HashMap<>();
        try {
            String accessToken = userObj.getParam(OIDCAuthenticatorConstants.ACCESS_TOKEN);
            String url = getUserInfoEndpoint(userObj, authenticatorProperties);
            String json = sendRequest(url, accessToken);
            JSONObject obj = new JSONObject(json);
            String userData = obj.getJSONObject("data").toString();
            if (StringUtils.isBlank(json)) {
                if (log.isDebugEnabled()) {
                    log.debug("Unable to fetch user claims. Proceeding without user claims");
                }
                return claims;
            }
            Map<String, Object> jsonObject = JSONUtils.parseJSON(userData);
            for (Map.Entry<String, Object> data : jsonObject.entrySet()) {
                String key = data.getKey();
                claims.put(ClaimMapping.build(InstagramAuthenticatorConstants.CLAIM_DIALECT_URI + "/" + key,
                        InstagramAuthenticatorConstants.CLAIM_DIALECT_URI + "/" + key, null, false),
                        jsonObject.get(key).toString());
                if (log.isDebugEnabled()) {
                    log.debug("Adding claims from end-point data mapping : " + key + " - " +
                            jsonObject.get(key).toString());
                }
            }
        } catch (Exception e) {
            log.error("Error occurred while accessing user info endpoint", e);
        }
        return claims;
    }

    private OAuthClientRequest getAccessRequest(String tokenEndPoint, String clientId, String code, String clientSecret,
                                                String callbackurl)
            throws AuthenticationFailedException {
        OAuthClientRequest accessRequest;
        try {
            accessRequest = OAuthClientRequest.tokenLocation(tokenEndPoint)
                    .setGrantType(GrantType.AUTHORIZATION_CODE).setClientId(clientId)
                    .setClientSecret(clientSecret).setRedirectURI(callbackurl).setCode(code)
                    .buildBodyMessage();
        } catch (OAuthSystemException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        }
        return accessRequest;
    }

    private OAuthClientResponse getOauthResponse(OAuthClient oAuthClient, OAuthClientRequest accessRequest)
            throws AuthenticationFailedException {
        OAuthClientResponse oAuthResponse = null;
        try {
            oAuthResponse = oAuthClient.accessToken(accessRequest);
        } catch (OAuthSystemException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        } catch (OAuthProblemException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
        }
        return oAuthResponse;
    }

    @Override
    public String sendRequest(String url, String accessToken) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Claim URL: " + url);
        }
        if (StringUtils.isEmpty(url)) {
            return "";
        } else {
            URL obj = new URL(url + "?" + OIDCAuthenticatorConstants.ACCESS_TOKEN +
                    "=" + accessToken);
            HttpURLConnection urlConnection = (HttpURLConnection) obj.openConnection();

            urlConnection.setRequestMethod(InstagramAuthenticatorConstants.HTTP_GET_METHOD);
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String inputLine = in.readLine();
            while (!StringUtils.isEmpty(inputLine)) {
                builder.append(inputLine).append("\n");
                inputLine = in.readLine();
            }
            in.close();

            if (log.isDebugEnabled()) {
                log.debug("response: " + builder.toString());
            }
            return builder.toString();
        }
    }

    /**
     * Get the Instagram specific claim dialect URI.
     *
     * @return Claim dialect URI.
     */
    @Override
    public String getClaimDialectURI() {
        return InstagramAuthenticatorConstants.CLAIM_DIALECT_URI;
    }

    /**
     * Get the configuration properties of UI
     */
    @Override
    public List<Property> getConfigurationProperties() {
        List<Property> configProperties = new ArrayList<Property>();

        Property clientId = new Property();
        clientId.setName(OIDCAuthenticatorConstants.CLIENT_ID);
        clientId.setDisplayName("Client Id");
        clientId.setRequired(true);
        clientId.setDescription("Enter Instagram client identifier value");
        clientId.setDisplayOrder(0);
        configProperties.add(clientId);

        Property clientSecret = new Property();
        clientSecret.setName(OIDCAuthenticatorConstants.CLIENT_SECRET);
        clientSecret.setDisplayName("Client Secret");
        clientSecret.setRequired(true);
        clientSecret.setConfidential(true);
        clientSecret.setDescription("Enter Instagram client secret value");
        clientSecret.setDisplayOrder(1);
        configProperties.add(clientSecret);

        Property callbackUrl = new Property();
        callbackUrl.setDisplayName("Callback URL");
        callbackUrl.setName(IdentityApplicationConstants.OAuth2.CALLBACK_URL);
        callbackUrl.setDescription("Enter the callback url");
        callbackUrl.setDisplayOrder(2);
        configProperties.add(callbackUrl);

        return configProperties;
    }
}
