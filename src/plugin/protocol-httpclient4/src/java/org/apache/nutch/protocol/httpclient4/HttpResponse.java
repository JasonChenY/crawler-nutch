/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.protocol.httpclient4;

// JDK imports
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.avro.util.Utf8;
// HTTP Client imports
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
//import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.entity.GzipDecompressingEntity;
import org.apache.http.client.entity.DeflateDecompressingEntity;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
//import org.apache.http.params.BasicHttpParams;
import org.apache.http.entity.StringEntity;

// Nutch imports
import org.apache.http.client.methods.HttpPost;
import org.apache.nutch.companyschema.CompanyUtils;
import org.apache.nutch.companyschema.CompanySchema;
import org.apache.nutch.companyschema.CompanySchemaRepository;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.metadata.SpellCheckedMetadata;
import org.apache.nutch.net.protocols.HttpDateFormat;
import org.apache.nutch.net.protocols.Response;
import org.apache.nutch.protocol.ProtocolException;
import org.apache.nutch.protocol.http.api.HttpBase;
import org.apache.nutch.storage.WebPage;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.List;
import java.util.Date;
import org.apache.nutch.util.Bytes;

import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;



/**
 * An HTTP response.
 *
 * @author Susam Pal
 */
public class HttpResponse implements Response {

    private URL url;
    private byte[] content;
    private int code;
    private Metadata headers = new SpellCheckedMetadata();
    private static CompanySchemaRepository repo;
    //private CookieHandler cookieHandler;
    private CookieStore cookieStore = new BasicCookieStore();
    private HttpClientContext context = HttpClientContext.create();

    HttpResponse(Http http, URL url, WebPage page, boolean followRedirects)
            throws ProtocolException, IOException {
        CompanySchema schema = null;
        HttpRequestBase request;

        if ( repo == null ) repo = CompanySchemaRepository.getInstance(http.getConf().get("company.schema.dir", "./"));

        String name = CompanyUtils.getCompanyName(page);
        schema = repo.getCompanySchema(name);
        if ( schema == null ) {
            Http.LOG.warn(url.toString() + " schema not configured");
            throw new IOException(url + " schema not found , cant let it continue");
        } else {
            if (Http.LOG.isDebugEnabled())  Http.LOG.debug(url.toString() + " for company: " + name);
        }

        this.url = url;
        if ( schema != null ) {
            if ( CompanyUtils.isEntryLink(page) && schema.getL1_method().equalsIgnoreCase("POST") ) {
          /* for Microsoft case */
                String urlstring = url.toString();
                int indexSuffix = urlstring.indexOf("::");
                if (indexSuffix != -1) {
                    urlstring = urlstring.substring(0, indexSuffix);
                }
                request = new HttpPost(urlstring);

                if (page.getMetadata().containsKey(CompanyUtils.company_dyn_data)) {
              /* Normally there won't be any POST DATA for ENTRY PAGE,
               * this case is for site alike Microsoft, we are using the real 'NEXT' page button,
               * the newly generated WebPage will contain POST DATA calculating basing on previous page
               */
                    String data = Bytes.toString(page.getMetadata().get(CompanyUtils.company_dyn_data));
                    ((HttpPost) request).setEntity(new StringEntity(data));
                    request.addHeader("Content-Type", "application/x-www-form-urlencoded");
                } else if (!schema.getL1_postdata().isEmpty()) {
                    ((HttpPost) request).setEntity(new StringEntity(schema.getL1_postdata()));
                    request.addHeader("Content-Type", "application/x-www-form-urlencoded");
                    if (Http.LOG.isDebugEnabled()) Http.LOG.debug("post data: " + schema.getL1_postdata());
                }
                if (Http.LOG.isDebugEnabled()) Http.LOG.info("using POST for url:" + url.toString());
            }  else if ( CompanyUtils.isListLink(page) && schema.getL2_nextpage_method().equalsIgnoreCase("POST") ) {
           /* level 1 page list should not have any preconfigured POST data, but possible dynamic data
           * Alibaba case, use POST for page list, this will be finished later,
           * and we may get the method dynamiclly for some website
           * but not all website, e.g Alibaba, then we need preconfigure this.
           */
                String urlstring = url.toString();
                int indexSuffix = urlstring.indexOf("::");
                if (indexSuffix != -1) {
                    urlstring = urlstring.substring(0, indexSuffix);
                }
                request = new HttpPost(urlstring);
                if (page.getMetadata().containsKey(CompanyUtils.company_dyn_data)) {
                    String data = Bytes.toString(page.getMetadata().get(CompanyUtils.company_dyn_data));
                    ((HttpPost) request).setEntity(new StringEntity(data));
                    request.addHeader("Content-Type", "application/x-www-form-urlencoded");
                }
            } else {
                request = new HttpGet(url.toString());
            }
        } else {
       /* default case */
            request = new HttpGet(url.toString());
        }

        //request.addHeader("User-Agent", http.getUserAgent());
        request.addHeader("Accept-Language", "zh-CN,en-us,en-gb,en;q=0.7,*;q=0.3");
        request.addHeader("Accept-Charset", "utf-8,ISO-8859-1;q=0.7,*;q=0.7");
        request.addHeader("Accept", "application/json,text/html,application/xml;q=0.9,application/xhtml+xml,text/xml;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        request.addHeader("Accept-Encoding", "x-gzip, gzip, deflate");

        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:36.0) Gecko/20100101 Firefox/36.0");
        //request.addHeader("X-Requested-With", "XMLHttpRequest");
        //request.addHeader("X-MicrosoftAjax", "Delta=true");
        //request.addHeader("Referer", "https://careers.microsoft.com/search.aspx");
        request.addHeader("Cache-Control", "no-cache");

    /* This can be considered later, it will cause confusion sometimes
    if (page.getModifiedTime() > 0) {
       request.addHeader("If-Modified-Since", HttpDateFormat.toString(page.getModifiedTime()));
    }*/

        try {
        /* cookie handling */
            if ( !page.getMetadata().containsKey(CompanyUtils.company_cookie) ) {
                if ( CompanyUtils.isEntryLink(page) ) {
                    if ( !schema.getCookie().isEmpty() ) {
                     /* for site like microsoft, customer can configure cookie not expired?
                     * otherwise, send the request without cookie, server will return a new cookie
                     **/
                        request.addHeader("Cookie", schema.getCookie());
                        if (Http.LOG.isDebugEnabled()) Http.LOG.debug(url.toString() + " begin with schema cookie for ENTRY page");
                    } else {
                        if (Http.LOG.isDebugEnabled()) Http.LOG.debug(url.toString() + " begin without cookie for !ENTRY page");
                    }
                }
                context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
            } else {
                final byte[] raw = page.getMetadata().get(CompanyUtils.company_cookie).array();
                final ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
                final ObjectInputStream instream = new ObjectInputStream(inbuffer);
                try {
                    this.cookieStore = (BasicCookieStore) instream.readObject();
                } catch ( ClassNotFoundException ce ) {
                    Http.LOG.warn(url.toString() + " failed to restore cookie from db", ce);
                    throw new IOException("failed to restore cookie from db");
                }
                context.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);

            /* check whether cookie expired, dont need check cookie origin, borrow from httpclient */
                List<Cookie> cookies = cookieStore.getCookies();
                final Date now = new Date();
                boolean expired = false;
                for (final Cookie cookie : cookies) {
                    if (cookie.isExpired(now)) {
                        expired = true;
                        break;
                    }
                }

                if ( expired ) {
                    if ( CompanyUtils.isEntryLink(page) ) {
                        if ( !schema.getCookie().isEmpty() ) {
                        /* for site like microsoft, customer can configure cookie not expired?
                         * otherwise, send the request without cookie, server will return a new cookie
                         **/
                            request.addHeader("Cookie", schema.getCookie());
                            if (Http.LOG.isDebugEnabled()) {
                                Http.LOG.debug(url.toString() + " cookie expired, continue with schema cookie for ENTRY page");
                            }
                        } else {
                            if (Http.LOG.isDebugEnabled()) {
                                Http.LOG.debug(url.toString() + " cookie expired, continue without cookie for ENTRY page");
                            }
                        }
                    } else {
                        if (Http.LOG.isInfoEnabled()) Http.LOG.info(url.toString() + " Cookie expired, abort !ENTRY page request");
                        throw new ProtocolException("cookie expired for !ENTRY page, dont know how to continue");
                    }
                } else {
                /* cookie is ok, and client execute will take care of this
                final HttpRequestInterceptor interceptor = new RequestAddCookies();
                interceptor.process(request, context);
                */
                }
            }

            CloseableHttpResponse rsp = http.getClient().execute(request, context);
            code = rsp.getStatusLine().getStatusCode();
            HttpEntity entity = rsp.getEntity();

            Header[] heads = rsp.getAllHeaders();
            for (int i = 0; i < heads.length; i++) {
                headers.set(heads[i].getName(), heads[i].getValue());
            }

            String contentEncoding = headers.get(Response.CONTENT_ENCODING);
            if (contentEncoding != null && Http.LOG.isTraceEnabled())
                Http.LOG.info("; Content-Encoding: " + contentEncoding);
            if ("gzip".equals(contentEncoding) || "x-gzip".equals(contentEncoding)) {
                entity = new GzipDecompressingEntity(entity);
            } else if ("deflate".equals(contentEncoding)) {
                entity = new DeflateDecompressingEntity(entity);
            }

            content = EntityUtils.toByteArray(entity);

            // add headers in metadata to row
            if (page.getHeaders() != null) {
                page.getHeaders().clear();
            }

            for (String key : headers.names()) {
                page.getHeaders().put(new Utf8(key), new Utf8(headers.get(key)));
            }

      /* save the cookies into pages */
            final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
            final ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
            outstream.writeObject(this.cookieStore);
            outstream.close();
            page.getMetadata().put(CompanyUtils.company_cookie, ByteBuffer.wrap(outbuffer.toByteArray()));

            if ( Http.LOG.isDebugEnabled() ) {
                final List<Cookie> cookies = this.cookieStore.getCookies();
                Http.LOG.debug(url.toString() + " saved to WebPage: " + cookies.size() + " cookies");

                for (int i = 0; i < cookies.size(); i++) {
                    final Cookie cookie = cookies.get(i);
                    if ( cookie.getValue() != null ) {
                        Http.LOG.debug("version: " + cookie.getVersion());
                    }
                    Http.LOG.debug("name: " + cookie.getName());
                    Http.LOG.debug("value: " + cookie.getValue());
                    Http.LOG.debug("domain:" + cookie.getDomain());
                    Http.LOG.debug("path:" + cookie.getPath());
                    if ( cookie.getExpiryDate() != null ) {
                        Http.LOG.debug("expire:" + cookie.getExpiryDate().toString());
                    }
                }
            }

        } finally {
            request.releaseConnection();
        }
    }

  /*
   * ------------------------- * <implementation:Response> *
   * -------------------------
   */

    public URL getUrl() {
        return url;
    }

    public int getCode() {
        return code;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public Metadata getHeaders() {
        return headers;
    }

    public byte[] getContent() {
        return content;
    }

  /*
   * -------------------------- * </implementation:Response> *
   * --------------------------
   */
}
