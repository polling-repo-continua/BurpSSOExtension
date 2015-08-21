/**
 * EsPReSSO - Extension for Processing and Recognition of Single Sign-On Protocols.
 * Copyright (C) 2015/ Tim Guenther and Christian Mainka
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package de.rub.nds.burp.espresso;

import burp.IBurpExtenderCallbacks;
import burp.IExtensionHelpers;
import burp.IHttpListener;
import burp.IHttpRequestResponse;
import burp.IParameter;
import burp.IRequestInfo;
import burp.IResponseInfo;
import de.rub.nds.burp.espresso.gui.UIOptions;
import static de.rub.nds.burp.utilities.ParameterUtilities.getFirstParameterByName;
import static de.rub.nds.burp.utilities.ParameterUtilities.parameterListContainsParameterName;
import de.rub.nds.burp.utilities.protocols.OpenID;
import de.rub.nds.burp.utilities.protocols.SAML;
import de.rub.nds.burp.utilities.protocols.SSOProtocol;
import de.rub.nds.burp.utilities.table.Table;
import de.rub.nds.burp.utilities.table.TableDB;
import de.rub.nds.burp.utilities.table.TableEntry;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highlight request in the proxy history.
 * The protocols OpenID, OpenID Connect, OAuth, BrowserID and SAML are highlighted.
 * @author Christian Mainka, Tim Guenther
 * @version 1.1
 */

public class HttpMarker implements IHttpListener {
    
        private static int counter = 1;

	private String[] OPENID_TOKEN_PARAMETER = {"openid.return_to"};

	private static final Set<String> IN_REQUEST_OPENID2_TOKEN_PARAMETER = new HashSet<String>(Arrays.asList(
            new String[]{"openid.claimed_id", "openid.op_endpoint"}
	));

	private static final Set<String> IN_REQUEST_OAUTH_TOKEN_PARAMETER = new HashSet<String>(Arrays.asList(
            new String[]{"redirect_uri", "scope", "client_id"}
	));

	private static final Set<String> IN_REQUEST_SAML_TOKEN_PARAMETER = new HashSet<String>(Arrays.asList(
            new String[]{"SAMLResponse"}
	));

	private static final Set<String> IN_REQUEST_SAML_REQUEST_PARAMETER = new HashSet<String>(Arrays.asList(
            new String[]{"SAMLRequest"}
	));

	private static final Set<String> IN_REQUEST_BROWSERID_PARAMETER = new HashSet<String>(Arrays.asList(
            new String[]{"browserid_state", "assertion"}
	));

	private static final String HIGHLIGHT_COLOR = "yellow";
	private static final String MIMETYPE_HTML = "HTML";
	private static final int STATUS_OK = 200;

	private IBurpExtenderCallbacks callbacks;
	private IExtensionHelpers helpers;
        private PrintWriter stdout;
        private PrintWriter stderr;
        
        /**
         * Create a new HttpMarker.
         * @param callbacks IPC for the Burp Suite api.
         */
	public HttpMarker(IBurpExtenderCallbacks callbacks) {
            this.callbacks = callbacks;
            this.helpers = callbacks.getHelpers();
            this.stderr = new PrintWriter(callbacks.getStderr(), true);
            this.stdout = new PrintWriter(callbacks.getStdout(), true);
	}
        
        /**
         * Implementation of the IHttpListener interface.
         * Is called every time a request/response is processed by Burp Suite.
         * @param toolFlag A numeric identifier for the Burp Suite tool that calls. 
         * @param isRequest True for a request, false for a response.
         * @param httpRequestResponse The request/response that should processed.
         */
	@Override
	public void processHttpMessage(int toolFlag, boolean isRequest, IHttpRequestResponse httpRequestResponse) {
            // only flag messages sent/received by the proxy
            if (toolFlag == IBurpExtenderCallbacks.TOOL_PROXY && !isRequest) {
                    TableEntry entry = processHttpRequest(httpRequestResponse);
                    if(entry != null){
                        updateTables(entry);
                    }
                    
                    processHttpResponse(httpRequestResponse);
            }
	}

	private void processHttpResponse(IHttpRequestResponse httpRequestResponse) {
            final byte[] responseBytes = httpRequestResponse.getResponse();
            IResponseInfo responseInfo = helpers.analyzeResponse(responseBytes);
            checkRequestForOpenIdLoginMetadata(responseInfo, httpRequestResponse);
	}

	private TableEntry processHttpRequest(IHttpRequestResponse httpRequestResponse) {
            IRequestInfo requestInfo = helpers.analyzeRequest(httpRequestResponse);
            if(UIOptions.openIDActive){
                SSOProtocol protocol = checkRequestForOpenId(requestInfo, httpRequestResponse);
                if(protocol != null){
                    protocol.setCounter(counter++);
                    return protocol.toTableEntry();
                }
            }
            if(UIOptions.oAuthv1Active || UIOptions.oAuthv2Active){
                checkRequestHasOAuthParameters(requestInfo, httpRequestResponse);
            }
            if(UIOptions.samlActive){
                SSOProtocol protocol = checkRequestForSaml(requestInfo, httpRequestResponse);
                if(protocol != null){
                    protocol.setCounter(counter++);
                    return protocol.toTableEntry();
                }
            }
            if(UIOptions.browserIDActive){
                checkRequestForBrowserId(requestInfo, httpRequestResponse);
            }
            if(UIOptions.openIDConnectActive){
                //TODO OpenID Connect
            }
            return null;
	}
        
        private void updateTables(TableEntry entry){
            //Full history
            TableDB.getTable(0).getTableHelper().addRow(entry);
            //Add content to additional tables
            for(int i = 1; i<TableDB.size(); i++){
                Table t = TableDB.getTable(i);
                t.update();
            }
        }

	private SSOProtocol checkRequestForOpenId(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
            final List<IParameter> parameterList = requestInfo.getParameters();
            IParameter openidMode = getFirstParameterByName(parameterList, "openid.mode");
            String protocol = "OpenID";
            if (openidMode != null) {
                if (openidMode.getValue().equals("checkid_setup")) {
                    markRequestResponse(httpRequestResponse, "OpenID Request", HIGHLIGHT_COLOR);
                } else if (openidMode.getValue().equals("id_res")) {

                    if (parameterListContainsParameterName(parameterList, IN_REQUEST_OPENID2_TOKEN_PARAMETER)) {
                            markRequestResponse(httpRequestResponse, "OpenID 2.0 Token", HIGHLIGHT_COLOR);
                            protocol += " v2.0";
                    } else {
                            markRequestResponse(httpRequestResponse, "OpenID 1.0 Token", HIGHLIGHT_COLOR);
                            protocol += " v1.0";
                    }
                } else if(openidMode.getValue().equals("associate")){
                    markRequestResponse(httpRequestResponse, "OpenID Association", HIGHLIGHT_COLOR);
                }
                
                return new OpenID(httpRequestResponse, protocol, callbacks);
            }
            return null;
	}

	private void checkRequestHasOAuthParameters(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
            if (parameterListContainsParameterName(requestInfo.getParameters(), IN_REQUEST_OAUTH_TOKEN_PARAMETER)) {
                markRequestResponse(httpRequestResponse, "OAuth", HIGHLIGHT_COLOR);
            }
	}

	private SSOProtocol checkRequestForSaml(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
            final List<IParameter> parameterList = requestInfo.getParameters();
            if (parameterListContainsParameterName(parameterList, IN_REQUEST_SAML_REQUEST_PARAMETER)) {
                markRequestResponse(httpRequestResponse, "SAML Authentication Request", HIGHLIGHT_COLOR);
                return new SAML(httpRequestResponse, "SAML", callbacks, getFirstParameterByName(parameterList, "SAMLRequest"));
            }

            if (parameterListContainsParameterName(parameterList, IN_REQUEST_SAML_TOKEN_PARAMETER)) {
                markRequestResponse(httpRequestResponse, "SAML Token", HIGHLIGHT_COLOR);
                return new SAML(httpRequestResponse, "SAML", callbacks, getFirstParameterByName(parameterList, "SAMLResponse"));
            }
            return null;
	}

	private boolean checkRequestForOpenIdLoginMetadata(IResponseInfo responseInfo, IHttpRequestResponse httpRequestResponse) {
            if (responseInfo.getStatusCode() == STATUS_OK && MIMETYPE_HTML.equals(responseInfo.getStatedMimeType())) {
                final byte[] responseBytes = httpRequestResponse.getResponse();
                final int bodyOffset = responseInfo.getBodyOffset();
		final String responseBody = (new String(responseBytes)).substring(bodyOffset);
                final String response = helpers.bytesToString(responseBytes);
                
                Pattern p = Pattern.compile("=[\"'][^\"']*openid[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(responseBody);
                if (m.find()) {
                    markRequestResponse(httpRequestResponse, "OpenID Login Possibility", "green");
                    IRequestInfo iri = helpers.analyzeRequest(httpRequestResponse);
                    callbacks.issueAlert("OpenID Login on: "+iri.getUrl().toString()); 
                    return true;
                }
                p = Pattern.compile("rel=\"openid(.server|.delegate|2.provider|2.local_id)\"", Pattern.CASE_INSENSITIVE);
                m = p.matcher(responseBody);
                if (m.find()) {
                    markRequestResponse(httpRequestResponse, "OpenID Metadata", "green");
                    IRequestInfo iri = helpers.analyzeRequest(httpRequestResponse);
                    callbacks.issueAlert("OpenID Login on: "+iri.getUrl().toString());
                    return true;
                }
                p = Pattern.compile("X-XRDS-Location:\\s(https?:\\/\\/)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})", Pattern.CASE_INSENSITIVE);
                m = p.matcher(response);
                if (m.find()) {
                    markRequestResponse(httpRequestResponse, "OpenID Metadata", "green");
                    IRequestInfo iri = helpers.analyzeRequest(httpRequestResponse);
                    callbacks.issueAlert("OpenID Login on: "+iri.getUrl().toString()); 
                    return true;
                }
                p = Pattern.compile("xmlns:xrds=\"xri://$xrds\" xmlns=\"xri://$xrd*($v*2.0)\"", Pattern.CASE_INSENSITIVE);
                m = p.matcher(response);
                if (m.find()) {
                    markRequestResponse(httpRequestResponse, "OpenID Metadata", "green");
                    IRequestInfo iri = helpers.analyzeRequest(httpRequestResponse);
                    callbacks.issueAlert("OpenID Login on: "+iri.getUrl().toString()); 
                    return true;
                }
            }
            return false;
	}
        
	private void checkRequestForBrowserId(IRequestInfo requestInfo, IHttpRequestResponse httpRequestResponse) {
            final List<IParameter> parameterList = requestInfo.getParameters();
            if (parameterListContainsParameterName(parameterList, IN_REQUEST_BROWSERID_PARAMETER)) {
                markRequestResponse(httpRequestResponse, "BrowserID", HIGHLIGHT_COLOR);
            }
	}

        private void markRequestResponse(IHttpRequestResponse httpRequestResponse, String message, String colour) {
            if(UIOptions.highlightBool){
                httpRequestResponse.setHighlight(colour);
            }
            final String oldComment = httpRequestResponse.getComment();
            if (oldComment != null && !oldComment.isEmpty()) {
                    httpRequestResponse.setComment(String.format("%s, %s", oldComment, message));
            } else {
                    httpRequestResponse.setComment(message);
            }

	}
}
