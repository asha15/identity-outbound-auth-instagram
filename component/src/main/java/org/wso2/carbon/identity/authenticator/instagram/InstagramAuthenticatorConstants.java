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

public class InstagramAuthenticatorConstants {

    public static final String INSTAGRAM_OAUTH_ENDPOINT = "https://api.instagram.com/oauth/authorize/";
    public static final String INSTAGRAM_TOKEN_ENDPOINT = "https://api.instagram.com/oauth/access_token";
    public static final String INSTAGRAM_USERINFO_ENDPOINT = "https://graph.instagram.com/me";

    public static final String INSTAGRAM_CONNECTOR_FRIENDLY_NAME = "Instagram ";
    public static final String INSTAGRAM_CONNECTOR_NAME = "Instagram";

    public static final String HTTP_GET_METHOD = "GET";
    public static final String INSTAGRAM_USER = "user_id";
    public static final String INSTAGRAM_BASIC_SCOPE = "user_profile,user_media";
    public static final String CLAIM_DIALECT_URI = "http://wso2.org/instagram/claims";
}