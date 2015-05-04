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
import org.apache.nutch.protocol.http.api.HttpBase;
import org.apache.nutch.storage.WebPage;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.nutch.util.Bytes;
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

  /**
   * Fetches the given <code>url</code> and prepares HTTP response.
   * 
   * @param http
   *          An instance of the implementation class of this plugin
   * @param url
   *          URL to be fetched
   * @param page
   *          WebPage
   * @param followRedirects
   *          Whether to follow redirects; follows redirect if and only if this
   *          is true
   * @return HTTP response
   * @throws IOException
   *           When an error occurs
   */


  HttpResponse(Http http, URL url, WebPage page, boolean followRedirects)
      throws IOException {
    CompanySchema schema = null;
    HttpRequestBase request;

    if ( repo == null ) repo = CompanySchemaRepository.getInstance(http.getConf().get("company.schema.dir", "./"));

    String name = CompanyUtils.getCompanyName(page);
    schema = repo.getCompanySchema(name);
    if ( schema == null ) {
        Http.LOG.warn(url.toString() + " schema not configured");
    } else {
        Http.LOG.info("url for company: " + name);
    }

    this.url = url;
    if ( schema != null ) {
       if ( CompanyUtils.isEntryLink(page) && schema.method().equalsIgnoreCase("POST") ) {
          request = new HttpPost(url.toString());
          //List <NameValuePair> nvps = new ArrayList <NameValuePair>();
          //nvps.add(new BasicNameValuePair("username", "vip"));
          //nvps.add(new BasicNameValuePair("password", "secret"));
          //request.setEntity(new UrlEncodedFormEntity(nvps));

          /* Set HTTP parameters
           HttpParams params = new BasicHttpParams();
           params.setParameter("key1", "value1");
           params.setParameter("key2", "value2");
           params.setParameter("key3", "value3");
           request.setParams(params);
          */
          if (schema.data() != null) {
              ((HttpPost) request).setEntity(new StringEntity(schema.data()));
              request.addHeader("Content-Type", "application/x-www-form-urlencoded");
              Http.LOG.info("post data: " + schema.data());
          }
          Http.LOG.info("using POST for url:" + url.toString());
       } /* else if ( CompanyUtils.isListLink(page) && schema.pagelist_method().equalsIgnoreCase("POST") ) {
           page list should not have any preconfigured POST data, but possible dynamic data
           * Alibaba case, use POST for page list, this will be finished later,
           * and we may get the method dynamiclly for some website
           * but not all website, e.g Alibaba, then we need preconfigure this.
           *
          request = new HttpPost(url.toString());
          if (page.getMetadata().containsKey(CompanyUtils.company_dyn_data)) {
              String data = Bytes.toString(page.getMetadata().get(CompanyUtils.company_dyn_data));
              ((HttpPost) request).setEntity(new StringEntity(data));
              request.addHeader("Content-Type", "application/x-www-form-urlencoded");
          }

       } */ else {
          request = new HttpGet(url.toString());
       }
    } else {
       /* default case */
       request = new HttpGet(url.toString());
    }

    request.addHeader("User-Agent", http.getUserAgent());
    request.addHeader("Accept-Language", "en-us,en-gb,en;q=0.7,*;q=0.3");
    request.addHeader("Accept-Charset", "utf-8,ISO-8859-1;q=0.7,*;q=0.7");
    request.addHeader("Accept", "text/html,application/xml;q=0.9,application/xhtml+xml,text/xml;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
    request.addHeader("Accept-Encoding", "x-gzip, gzip, deflate");
    if (page.getModifiedTime() > 0) {
       request.addHeader("If-Modified-Since", HttpDateFormat.toString(page.getModifiedTime()));
    }

    try {
      CloseableHttpResponse rsp = http.getClient().execute(request);
      code = rsp.getStatusLine().getStatusCode();
      HttpEntity entity = rsp.getEntity();

      Header[] heads = rsp.getAllHeaders();
      for (int i = 0; i < heads.length; i++) {
        headers.set(heads[i].getName(), heads[i].getValue());
      }
      /*
      // Limit download size
      int contentLength = Integer.MAX_VALUE;
      String contentLengthString = headers.get(Response.CONTENT_LENGTH);
      if (contentLengthString != null) {
          try {
                contentLength = Integer.parseInt(contentLengthString.trim());
          } catch (NumberFormatException ex) {
                throw new IOException("bad content length: " + contentLengthString);
          }
      }
      if (http.getMaxContent() >= 0 && contentLength > http.getMaxContent()) {
          contentLength = http.getMaxContent();
      }

      // always read content. Sometimes content is useful to find a cause
      // for error.
      InputStream in = entity.getContent();
      try {
        byte[] buffer = new byte[HttpBase.BUFFER_SIZE];
        int bufferFilled = 0;
        int totalRead = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((bufferFilled = in.read(buffer, 0, buffer.length)) != -1
            && totalRead + bufferFilled <= contentLength) {
          totalRead += bufferFilled;
          out.write(buffer, 0, bufferFilled);
        }

        content = out.toByteArray();
      } catch (Exception e) {
        if (code == 200)
          throw new IOException(e.toString());
        // for codes other than 200 OK, we are fine with empty content
      } finally {
        if (in != null) {
          in.close();
        }
        request.abort();
      }

      StringBuilder fetchTrace = null;
      if (Http.LOG.isTraceEnabled()) {
          // Trace message
          fetchTrace = new StringBuilder("url: " + url + "; status code: " + code
                  + "; bytes received: " + content.length);
          if (getHeader(Response.CONTENT_LENGTH) != null)
              fetchTrace.append("; Content-Length: "
                      + getHeader(Response.CONTENT_LENGTH));
          if (getHeader(Response.LOCATION) != null)
              fetchTrace.append("; Location: " + getHeader(Response.LOCATION));
      }

      // Extract gzip, x-gzip and deflate content
      if (content != null) {
            // check if we have to uncompress it
          String contentEncoding = headers.get(Response.CONTENT_ENCODING);
          if (contentEncoding != null && Http.LOG.isTraceEnabled())
              fetchTrace.append("; Content-Encoding: " + contentEncoding);
          if ("gzip".equals(contentEncoding) || "x-gzip".equals(contentEncoding)) {
              content = http.processGzipEncoded(content, url);
              if (Http.LOG.isTraceEnabled())
                  fetchTrace.append("; extracted to " + content.length + " bytes");
          } else if ("deflate".equals(contentEncoding)) {
              content = http.processDeflateEncoded(content, url);
              if (Http.LOG.isTraceEnabled())
                  fetchTrace.append("; extracted to " + content.length + " bytes");
          }
      }
      */

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
