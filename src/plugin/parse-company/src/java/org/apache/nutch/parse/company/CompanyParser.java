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

package org.apache.nutch.parse.company;

import java.io.*;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.Integer;
import java.lang.Object;
import java.lang.String;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;


import org.apache.avro.util.Utf8;
import org.apache.nutch.storage.Mark;
import org.apache.nutch.util.*;
import org.apache.nutch.util.Bytes;
import org.apache.nutch.util.EncodingDetector;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.TableUtil;
import org.apache.nutch.util.URLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.html.dom.HTMLDocumentImpl;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.ParseFilters;
import org.apache.nutch.parse.Outlink;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseStatusCodes;
import org.apache.nutch.parse.ParseStatusUtils;
import org.apache.nutch.parse.Parser;
import org.apache.nutch.storage.ParseStatus;
import org.apache.nutch.storage.WebPage;

import org.cyberneko.html.parsers.DOMParser;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/* xpath from jdk */
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import javax.xml.namespace.NamespaceContext;

import org.apache.nutch.companyschema.CompanyUtils;
import org.apache.nutch.companyschema.DateUtils;
import org.apache.nutch.companyschema.CompanySchema;
import org.apache.nutch.companyschema.CompanySchemaRepository;

import org.apache.nutch.indexer.solr.SolrUtils;

import java.util.Date;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.nutch.crawl.CrawlStatus;
import org.apache.nutch.crawl.DbUpdaterJob;
import org.apache.nutch.crawl.Signature;
import org.apache.nutch.crawl.SignatureFactory;

/* json-path */
import java.util.List;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;

/* perl5 pattern */
import org.apache.oro.text.regex.MatchResult;
//import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.PatternMatcherInput;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.apache.oro.text.perl.MalformedPerl5PatternException;
import org.apache.oro.text.perl.Perl5Util;

/* stuff for replacing postdata */
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import java.util.ArrayList;

/* for write debug POST data to file */

import org.apache.nutch.parse.company.PDF2HTML;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.w3c.dom.NamedNodeMap;

public class CompanyParser implements Parser {
  public static final Logger LOG = LoggerFactory
      .getLogger("org.apache.nutch.parse.company");

  // I used 1000 bytes at first, but found that some documents have
  // meta tag well past the first 1000 bytes.
  // (e.g. http://cn.promo.yahoo.com/customcare/music.html)
  private static final int CHUNK_SIZE = 2000;

  // NUTCH-1006 Meta equiv with single quotes not accepted
  private static Pattern metaPattern = Pattern.compile(
      "<meta\\s+([^>]*http-equiv=(\"|')?content-type(\"|')?[^>]*)>",
      Pattern.CASE_INSENSITIVE);
  private static Pattern charsetPattern = Pattern.compile(
      "charset=\\s*([a-z][_\\-0-9a-z]*)", Pattern.CASE_INSENSITIVE);
  private static Pattern charsetPatternHTML5 = Pattern.compile(
      "<meta\\s+charset\\s*=\\s*[\"']?([a-z][_\\-0-9a-z]*)[^>]*>",
      Pattern.CASE_INSENSITIVE);


  private CompanySchemaRepository repo;

  private boolean runtime_debug;

  private Signature sig;

  private boolean try_to_shortcut_l3page;
  private int defaultInterval;

  private static Collection<WebPage.Field> FIELDS = new HashSet<WebPage.Field>();

  static {
    FIELDS.add(WebPage.Field.BASE_URL);
  }

  private class SimpleNamespaceContext implements NamespaceContext {
        private final Map<String, String> PREF_MAP = new HashMap<String, String>();
        public SimpleNamespaceContext(final Map<String, String> prefMap) {
            PREF_MAP.putAll(prefMap);
        }
        public String getNamespaceURI(String prefix) {
            return PREF_MAP.get(prefix);
        }
        public String getPrefix(String uri) {
            throw new UnsupportedOperationException();
        }
        public Iterator getPrefixes(String uri) {
            throw new UnsupportedOperationException();
        }
    }

    /**
   * Given a <code>ByteBuffer</code> representing an html file of an
   * <em>unknown</em> encoding, read out 'charset' parameter in the meta tag
   * from the first <code>CHUNK_SIZE</code> bytes. If there's no meta tag for
   * Content-Type or no charset is specified, the content is checked for a
   * Unicode Byte Order Mark (BOM). This will also cover non-byte oriented
   * character encodings (UTF-16 only). If no character set can be determined,
   * <code>null</code> is returned. <br />
   * See also
   * http://www.w3.org/International/questions/qa-html-encoding-declarations,
   * http://www.w3.org/TR/2011/WD-html5-diff-20110405/#character-encoding, and
   * http://www.w3.org/TR/REC-xml/#sec-guessing <br />
   *
   * @param content
   *          <code>ByteBuffer</code> representation of an html file
   */

  private static String sniffCharacterEncoding(ByteBuffer content) {
    int length = Math.min(content.remaining(), CHUNK_SIZE);

    // We don't care about non-ASCII parts so that it's sufficient
    // to just inflate each byte to a 16-bit value by padding.
    // For instance, the sequence {0x41, 0x82, 0xb7} will be turned into
    // {U+0041, U+0082, U+00B7}.
    String str = "";
    try {
      str = new String(content.array(), content.arrayOffset()
          + content.position(), length, Charset.forName("ASCII").toString());
    } catch (UnsupportedEncodingException e) {
      // code should never come here, but just in case...
      return null;
    }

    Matcher metaMatcher = metaPattern.matcher(str);
    String encoding = null;
    if (metaMatcher.find()) {
      Matcher charsetMatcher = charsetPattern.matcher(metaMatcher.group(1));
      if (charsetMatcher.find())
        encoding = new String(charsetMatcher.group(1));
    }
    if (encoding == null) {
      // check for HTML5 meta charset
      metaMatcher = charsetPatternHTML5.matcher(str);
      if (metaMatcher.find()) {
        encoding = new String(metaMatcher.group(1));
      }
    }
    if (encoding == null) {
      // check for BOM
      if (length >= 3 && content.get(0) == (byte) 0xEF
          && content.get(1) == (byte) 0xBB && content.get(2) == (byte) 0xBF) {
        encoding = "UTF-8";
      } else if (length >= 2) {
        if (content.get(0) == (byte) 0xFF && content.get(1) == (byte) 0xFE) {
          encoding = "UTF-16LE";
        } else if (content.get(0) == (byte) 0xFE
            && content.get(1) == (byte) 0xFF) {
          encoding = "UTF-16BE";
        }
      }
    }

    return encoding;
  }

  private String defaultCharEncoding;

  private Configuration conf;

  class ByteArrayInOutStream extends ByteArrayOutputStream {
        public ByteArrayInOutStream() {
            super();
        }
        public ByteArrayInOutStream(int size) {
            super(size);
        }
        public ByteArrayInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(this.buf);
            this.buf = null;
            return in;
        }
    }

    public static void print(Node node, String indent) {
        System.out.print(indent+node.getClass().getName()+"->");
        if (node.getNodeName() != null) {
            System.out.print("   [" + node.getNodeName()+"]   ");
            NamedNodeMap attrs = node.getAttributes();
            if ( attrs != null ) {
                for (int i= 0; i < attrs.getLength(); i++ ) {
                    if ( attrs.item(i).getNodeName() != null )
                        System.out.print(attrs.item(i).getNodeName());
                    System.out.print("@");
                    if (attrs.item(i).getNodeValue() != null )
                        System.out.print(attrs.item(i).getNodeValue());
                    System.out.print("  ");
                }
            }
        }
        if (node.getNodeValue() != null){
            if("".equals(node.getNodeValue().trim())){
            } else {
                System.out.print(node.getNodeValue());
            }
        }
        System.out.println("");
        Node child = node.getFirstChild();
        while (child != null) {
            print(child, indent+"  ");
            child = child.getNextSibling();
        }
    }

  public Parse getParse(String url, WebPage page) {
      Parse parse = null;
      String name = CompanyUtils.getCompanyName(page);
      CompanySchema schema = repo.getCompanySchema(name);

      if (schema == null) {
          /* if return null, will try to reparse it again next time
           * if return an Empty, then ParseStatus will be FAILED, will try to reparse it again next time.
           * the best way is to Fake a SUCCESS code, won't take care of this uninterested page in future until the max interval comes.
           */
          LOG.warn(url + " company_key not found, setup PARSE_MARK, UPDATEDB_MARK, INDEX_MARK or just return null");
          return null;
      }

      LOG.info(url + " for : " + name + " link type: " + CompanyUtils.getLinkType(page));
      if ( CompanyUtils.getLinkType(page).equals("") ) {
          /* set the entry link type, avoid some config footprint during inject url */
          CompanyUtils.setEntryLink(page);
      } else if ( !CompanyUtils.isEntryLink(page) && !CompanyUtils.isListLink(page) && !CompanyUtils.isSummaryLink(page)) {
          LOG.warn(url + " invalid link type" + CompanyUtils.getLinkType(page));
          return null;
      }

    String baseUrl = TableUtil.toString(page.getBaseUrl());
    URL base;
    try {
      base = new URL(baseUrl);
    } catch (MalformedURLException e) {
      LOG.warn(url + " Failed to parse baseUrl");
      return ParseStatusUtils.getEmptyParse(e, getConf());
    }

    try {
      ByteBuffer contentInOctets = page.getContent();
      InputSource input = new InputSource(new ByteArrayInputStream(
          contentInOctets.array(), contentInOctets.arrayOffset()
              + contentInOctets.position(), contentInOctets.remaining()));

      EncodingDetector detector = new EncodingDetector(conf);
      detector.autoDetectClues(page, true);
      detector.addClue(sniffCharacterEncoding(contentInOctets), "sniffed");
      String encoding = detector.guessEncoding(page, defaultCharEncoding);

      page.getMetadata().put(new Utf8(Metadata.ORIGINAL_CHAR_ENCODING),
          ByteBuffer.wrap(Bytes.toBytes(encoding)));
      page.getMetadata().put(new Utf8(Metadata.CHAR_ENCODING_FOR_CONVERSION),
          ByteBuffer.wrap(Bytes.toBytes(encoding)));

      input.setEncoding(encoding);
      if (LOG.isTraceEnabled()) {
        LOG.trace("Parsing..." + encoding);
      }

      String content_type = "html";
      Utf8 content_type_key = new Utf8(org.apache.nutch.net.protocols.Response.CONTENT_TYPE);
      if ( page.getHeaders().containsKey(content_type_key) ) {
          java.lang.CharSequence content_type_utf8 = page.getHeaders().get(content_type_key);
          content_type = content_type_utf8.toString();
          LOG.debug(url + " : " + content_type);
      }

      if ( content_type.equals("application/pdf") ) {
          /* Danone case */
          try {
              ByteArrayInOutStream output = new ByteArrayInOutStream();
              PDF2HTML p2h = new PDF2HTML();
              p2h.process(input.getByteStream(), output);
              output.flush();
              /*
              if (LOG.isDebugEnabled()) {
                  LOG.debug(url + " After translate's outputstream : " + output.toString("UTF-8"));
              }*/
              input = new InputSource(output.getInputStream());
              input.setEncoding(encoding);
              /*
              Dangerous: this is only for extreme case debug, it will eat bytes
              if ( LOG.isDebugEnabled() ) {
                  String after = org.apache.commons.io.IOUtils.toString(input.getByteStream());
                  LOG.debug(url + " After translate's inputstream : " + after);
              }
              */
              LOG.debug(url + " translate pdf to html ");
              content_type = "text/html";
          } catch ( Exception e) {
              LOG.warn(url + " failed to translate pdf to html ");
              throw e;
          }
      }

      /* no better way, we have 3 kinds of pages, two kinds of content type,
       * which mean a matrix to parse, tried to use unify method,
       * but becasue of different data type, it make the code even unreadable,
       * Unforturnately we wont have more cases till now
       */
      if ( content_type.toLowerCase().contains("html") ||
              content_type.toLowerCase().contains("text/plain")   ) {
        /* parsing with NEKO DOM parser */
        DOMParser parser = new DOMParser();
        try {
          parser.setFeature("http://cyberneko.org/html/features/scanner/allow-selfclosing-iframe", true);
          parser.setFeature("http://cyberneko.org/html/features/augmentations", true);
          parser.setProperty("http://cyberneko.org/html/properties/default-encoding", encoding);
          parser.setFeature("http://cyberneko.org/html/features/scanner/ignore-specified-charset", true);
          parser.setFeature("http://cyberneko.org/html/features/balance-tags/ignore-outside-content", false);
          parser.setFeature("http://cyberneko.org/html/features/balance-tags/document-fragment", true);
          parser.setFeature("http://cyberneko.org/html/features/report-errors", LOG.isTraceEnabled());
          parser.parse(input);

          Document doc = parser.getDocument();
          /* Debug to check how file is parsed
          print(doc, "");
          */

          /* Create xpath */
          XPathFactory xpathFactory = XPathFactory.newInstance();
          XPath xpath = xpathFactory.newXPath();

          /* Setup xpath's namespace */
          //DocumentType doctype = doc.getDoctype();
          //if ( doctype != null && doctype.getName().equalsIgnoreCase("html") ) {}
            NodeList root = doc.getElementsByTagName("HTML");
            if (root != null) {
              Element head = (Element) root.item(0);
              if ( head != null ) {
                  final String ns = head.getNamespaceURI();
                  LOG.info("namespace: " + ns);
                  if (ns != null) {
                      HashMap<String, String> prefMap = new HashMap<String, String>() {{
                          put("main", ns);
                      }};
                      SimpleNamespaceContext namespaces = new SimpleNamespaceContext(prefMap);
                      xpath.setNamespaceContext(namespaces);
                  }
              } else {
                  LOG.warn(url + " Unbelievable, no HTML Element(Microsoft) ");
              }
            } else {
              LOG.warn(url + " dont have HTML element ");
            }

            if ( CompanyUtils.isEntryLink(page)) {
                parse = getParse_entry_html(url, page, schema, doc, xpath);
                parse = getParse_list_html(parse, url, page, schema, doc, xpath);
            } else if ( CompanyUtils.isListLink(page)) {
                parse = getParse_list_html(null, url, page, schema, doc, xpath);
            } else if ( CompanyUtils.isSummaryLink(page)) {
                parse = getParse_summary_html(url, page, schema, doc, xpath);
            }
        } catch (SAXException e) {
          LOG.warn("Failed to parse " + url + " with DOMParser " + e.getMessage());
        }       
      } else if ( content_type.toLowerCase().contains("json") ) {
          /* Create JsonReader Context, from that to parse */
          com.jayway.jsonpath.Configuration JSON_SMART_CONFIGURATION = com.jayway.jsonpath.Configuration.defaultConfiguration();
          DocumentContext doc = JsonPath.parse(input.getByteStream(), JSON_SMART_CONFIGURATION);
          if ( CompanyUtils.isEntryLink(page)) {
              parse = getParse_entry_json(url, page, schema, doc);
              parse = getParse_list_json(parse, url, page, schema, doc);
          } else if ( CompanyUtils.isListLink(page)) {
              parse = getParse_list_json(null, url, page, schema, doc);
          } else if ( CompanyUtils.isSummaryLink(page)) {
              /* Summary Page should not be in JSON format */
          }
      }
    } catch (MalformedURLException e) {
      LOG.error("Failed to generate target URL");
      return ParseStatusUtils.getEmptyParse(e, getConf());
    } catch (IOException e) {
      LOG.error("Failed with the following IOException: ", e);
      return ParseStatusUtils.getEmptyParse(e, getConf());
    } catch (DOMException e) {
      LOG.error("Failed with the following DOMException: ", e);
      return ParseStatusUtils.getEmptyParse(e, getConf());
    }  catch (XPathExpressionException e) {
      LOG.error("Failed to parse with schema ", e)  ;
      return ParseStatusUtils.getEmptyParse(e, getConf());
    } catch ( PathNotFoundException e ) {
      LOG.error("Failed to parse schema with json-path ", e)  ;
      return ParseStatusUtils.getEmptyParse(e, getConf());
    } catch (Exception e) {
      LOG.error("Failed with the following Exception: ", e);
      return ParseStatusUtils.getEmptyParse(e, getConf());
    }
    return parse;
  }
  private static String guess_URL(String to, String from, String config ) {
      URL target = null;
      try {
          target = new URL(to);
      } catch (MalformedURLException e) {
          URL orig = null;
          try {
              orig = new URL(from);
          } catch (MalformedURLException e1) {
              try {
                  orig = new URL(config);
              } catch (MalformedURLException e2) {
              }
          }
          if ( orig != null ) {
              try {
                  target = new URL(orig, to);
              } catch (MalformedURLException e3) { }
          }
      }
      if ( target != null )
          return target.toString();
      else
          return null;
  }

  private Parse getParse_entry_html(String url, WebPage page, CompanySchema schema, Document doc, XPath xpath)
      throws XPathExpressionException {
      XPathExpression expr;
      /* Next Page URL */
      String nextpage_url = schema.getL2_template_for_nextpage_url();
      if ( nextpage_url.isEmpty() ) {
          expr = xpath.compile(schema.getL2_schema_for_nextpage_url());
          nextpage_url = (String) expr.evaluate(doc, XPathConstants.STRING);
          LOG.debug(url + " (Normal case) Got nextpage url: " + nextpage_url);
      } else {
          LOG.debug(url + " (Abnormal case?) use l2_template_for_nextpage_url instead of l2_schema_for_nextpage_url");
      }
      nextpage_url = guess_URL(nextpage_url, url, schema.getL1_url());

      /* Last Page Number,
       * Note not all company will have the last page number to iterate,
       * e.g Microsoft, if so, we fallback to 'click' on the 'Next' button
       * */
      String last_page = schema.getL2_last_page();
      if ( !last_page.isEmpty() ) {
          expr = xpath.compile(last_page);
          last_page = (String) expr.evaluate(doc, XPathConstants.STRING);
          LOG.debug(url + " Got last page: " + last_page);
          int last = 0;
          try {
              last = Integer.parseInt(last_page);
          } catch (NumberFormatException e) {
              LOG.error(url + " failed to parse last page");
              return null;
          }

          return generate_next_pages(url, schema, nextpage_url, last);
      } else {
          /* fallback to 'click' 'Next' button
           * Following two meta data should appear to decide when to finish the iterate.
           * l2_nextpage_postdata_inherit_regex
           * l2_nextpage_endflag
           */
          return generate_next_page(url, page, nextpage_url, schema , doc, xpath);
      }
  }
  private Parse generate_next_page(String url, WebPage page, String nextpage_url, CompanySchema schema, Document doc, XPath xpath)
          throws XPathExpressionException{
      String regex = schema.getL2_nextpage_postdata_inherit_regex();
      String nextpage_endflag = schema.getL2_nextpage_endflag();
      XPathExpression expr = xpath.compile(nextpage_endflag);
      NodeList rows = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if ( rows == null || rows.getLength() == 0 ) {
          LOG.info(url + " reach last Page");
          return null;
      } else {
          LOG.debug(url + " generate_next_page's POST data");
          String origcontent = new String(page.getContent().array());
          /* For Microsoft, we should take care at least two params: __VIEWSTATE, __EVENTVALIDATION */
          List<NameValuePair> substitue_params = new ArrayList<NameValuePair>();
          try {
              PatternMatcherInput input = new PatternMatcherInput(origcontent);
              Perl5Util plUtil = new Perl5Util();
              while (plUtil.match(regex, input)) {
                  MatchResult result = plUtil.getMatch();
                  String key = result.group(1);
                  String value = result.group(2);
                  LOG.debug("Matcher found: " + key + " --> ");
                  substitue_params.add(new BasicNameValuePair(key, value));
              }
              /*
              final PatternCompiler cp = new Perl5Compiler();
              final org.apache.oro.text.regex.Pattern pattern = cp.compile(regex, Perl5Compiler.CASE_INSENSITIVE_MASK | Perl5Compiler.READ_ONLY_MASK | Perl5Compiler.MULTILINE_MASK);
              final PatternMatcher matcher = new Perl5Matcher();

              final PatternMatcherInput input = new PatternMatcherInput(origcontent);

              MatchResult result;

              // loop the matches
              while (matcher.contains(input, pattern)) {
                  result = matcher.getMatch();
                  String key = result.group(1);
                  String value = result.group(2);
                  LOG.debug("Matcher found: " + key + " --> ");
                  substitue_params.add(new BasicNameValuePair(key, value));
              }
              */
          } catch (Exception ex) {
              LOG.warn(url + " failed to get matcher" + ex.getMessage());
          }

          LOG.debug(url + " get " + substitue_params.size() + " matcher via regex " + regex);

          /* JDK pattern/match version
          Pattern pattern = null;
          try {
              pattern = Pattern.compile(regex);
              //pattern = Pattern.compile("\\d+\\|hiddenField\\|(__EVENTVALIDATION)\\|(.*)\\|\\d+\\|.*");
          } catch (PatternSyntaxException e) {
              System.out.println("Failed to compile nextpage postdata pattern: " + regex + " : " + e);
              return ;
          }

          Matcher matcher = pattern.matcher(origcontent);
          if ( matcher.find() ) {
              key = matcher.group(1);
              value = matcher.group(2);
              System.out.println("Matcher found: " + key + " " + value);
          }
          */
          String newpostdata = schema.getL2_template_for_nextpage_postdata();

          for ( int i=0; i < substitue_params.size(); i++ ) {
              String key = substitue_params.get(i).getName();
              String value = substitue_params.get(i).getValue();
              StringBuffer result = new StringBuffer();
              try {
                  Pattern pattern_for_postdata = Pattern.compile(key + "=([^&]*)");
                  Matcher matcher_for_postdata = pattern_for_postdata.matcher(newpostdata);

                  if (matcher_for_postdata.find()) {
                                /* new method to replace */
                                /* encode value firstly, for our case, "==" should be encoded */
                      try {
                          value = java.net.URLEncoder.encode(value, "UTF-8");
                      } catch (java.io.UnsupportedEncodingException ee) {};
                      matcher_for_postdata.appendReplacement(result,  key + "=" + value);
                      matcher_for_postdata.appendTail(result);
                  }
              } catch (PatternSyntaxException e) {
                  LOG.warn(url + " Failed to compile nextpage postdata pattern: " + regex + " : " + e);
              }
              newpostdata = result.toString();
          }

          if ( runtime_debug ) {
              try {
                  String date = DateUtils.getThreadLocalDateFormat().format(new Date());
                  LOG.debug(url + " write generated post data to file /tmp/" + date);
                  FileWriter fw = new FileWriter("/tmp/" + date + ".data", true);
                  BufferedWriter bw = new BufferedWriter(fw);
                  bw.write(newpostdata);
                  bw.flush();
                  bw.close();
                  fw.close();

                  fw = new FileWriter("/tmp/" + date + ".html", true);
                  bw = new BufferedWriter(fw);
                  bw.write(origcontent);
                  bw.flush();
                  bw.close();
                  fw.close();
              } catch (Exception ee) {
                  LOG.warn(url + " failed to generate post file " + ee.getMessage());
              };
          }

          ParseStatus status = ParseStatus.newBuilder().build();
          status.setMajorCode((int) ParseStatusCodes.SUCCESS);
          Parse parse = new Parse("next page", "next page", new Outlink[0], status);

          /* Now have data to generate next WebPage
           * Note, this will be deemed as ENTRY page as well,
           * and it will overwrite the original ENTRY page,
           * So now should first check the dynamic/post data in webpage it self,
           * then check the schema configuration for ENTRY page,
           * a little bit thing to be taken cared in httpclient4 plugin.
           * */
          WebPage newPage = WebPage.newBuilder().build();
          newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
          CompanyUtils.setCompanyName(newPage, schema.getName());
          CompanyUtils.setEntryLink(newPage);
          newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

          newPage.getMetadata().put(CompanyUtils.company_dyn_data, ByteBuffer.wrap(newpostdata.getBytes()));

          /* should change the new page url a bit, otherwise, ParseJob firstly save newPage to db
           * then again update something on the current page and write back to db
           * we wont go ahead, now the logic:
           * initial page :  xxxx
           * 2nd page     :  xxxx::
           * third page   :  xxxx
           * 4th page     :  xxxx::
           */
          if ( url.indexOf("::") == -1 ) {
              nextpage_url += "::";
          }
          parse.addPage(nextpage_url, newPage);
          return parse;
      }
  }
  private Parse generate_next_pages(String key, CompanySchema schema, String page_list_url, int last) {
      Perl5Util plUtil = new Perl5Util();

      /* Page number regex */
      String regex = schema.getL2_nextpage_regex();

      /* Page Interval, by default 1 */
      int incr = 1;
      if ( !schema.getL2_nextpage_increment().isEmpty() ) {
          try {
              incr = Integer.parseInt(schema.getL2_nextpage_increment());
          } catch (NumberFormatException e) {
              LOG.error(key + " failed to parse nextpage increment", e);
              return null;
          }
      }

      ParseStatus status = ParseStatus.newBuilder().build();
      status.setMajorCode((int) ParseStatusCodes.SUCCESS);
      Parse parse = new Parse("page list", "page list", new Outlink[0], status);

      try {
          if (schema.getL2_template_for_nextpage_url().isEmpty()) {
          /* normal case where next page is a href, can generate list of new urls basing on pattern */
              PatternMatcherInput matcherInput = new PatternMatcherInput(page_list_url);
              if (plUtil.match(regex, matcherInput)) {
              /* till now we will only have one matcher for url parameters, so use if instead of while-loop */
                  MatchResult result = plUtil.getMatch();
              /* In general we should define 3 group for regex, can be 2 if the patter is in end */

                  String prefix = result.group(1);

                  int start = Integer.parseInt(result.group(2));

                  String suffix = "";
                  if (result.groups() > 3) suffix = result.group(3);

                  for (int i = start; i <= last; i += incr) {
                      String newurl = prefix + Integer.toString(i) + suffix;

                      WebPage newPage = WebPage.newBuilder().build();
                      newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
                      CompanyUtils.setCompanyName(newPage, schema.getName());
                      CompanyUtils.setListLink(newPage);
                      newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

                  /* dont need to add post data here, we won't handle it,
                   * if there is any strange site do this way, will add it
                   **/
                      parse.addPage(newurl, newPage);
                      if (runtime_debug) break;
                  }
              } else {
                  LOG.error(key + " failed to find nextpage url via regex " + regex);
              }
          } else {
              /* normally this should be using method POST with some dynamic data, we should do pattern match/replace inside dynamic data */
              String l2_postdata = schema.getL2_template_for_nextpage_postdata();
              if (l2_postdata.isEmpty()) {
                  LOG.warn(key + " Dont know how to generate new pages without dynamic post data in schema");
                  return parse;
              }

              PatternMatcherInput matcherInput = new PatternMatcherInput(l2_postdata);
              if (plUtil.match(regex, matcherInput)) {
                  MatchResult result = plUtil.getMatch();
                  String prefix = result.group(1);
                  int start = Integer.parseInt(result.group(2));
                  String suffix = "";
                  if (result.groups() > 3) suffix = result.group(3);


                  for (int i = start; i <= last; i += incr) {
                      String newpostdata = prefix + Integer.toString(i) + suffix;
                      if (result.groups() > 4) {
                          /* A simple workaround here for Nokia case, need consider to extend the template to
                           * indicate how is each matched field to be replaced.
                           */
                          newpostdata += Integer.toString(i);
                      }
                  /* hbase use url as the key, but we will generate series of webpage with same key value,
                   * adding a trailing stuff, and remember to remove it in fetcher/protolcol-http4,
                   * This suffix should survive url normalizer and url filter.
                   * Should not be a problem for indexing, becausee l2 page won't be indexed.
                   * Any problem for other Job?
                   **/
                      String newurl = page_list_url + "::" + Integer.toString(i);

                      WebPage newPage = WebPage.newBuilder().build();
                      newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
                      CompanyUtils.setCompanyName(newPage, schema.getName());
                      CompanyUtils.setListLink(newPage);
                      newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

                      newPage.getMetadata().put(CompanyUtils.company_dyn_data, ByteBuffer.wrap(newpostdata.getBytes()));

                      parse.addPage(newurl, newPage);
                      if (runtime_debug) break;
                  }
              } else {
                  LOG.error(key + " failed to find nextpage pattern from postdata");
              }
          }
      } catch ( MalformedPerl5PatternException pe ) {
          LOG.error(key + " failed of nextpage regex " + regex, pe);
          return null;
      }
      return parse;
  }

  private Parse getParse_list_html(Parse parse, String url, WebPage page, CompanySchema schema, Document doc, XPath xpath)
      throws XPathExpressionException {
      XPathExpression expr = xpath.compile(schema.getL2_schema_for_jobs());
      NodeList jobs = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

      if ( jobs == null  || jobs.getLength() == 0 ) {
          LOG.info(url + " no jobs found ");
      } else {
          LOG.info(url + " Found " + jobs.getLength() + " jobs");
          if ( parse == null ) {
              ParseStatus status = ParseStatus.newBuilder().build();
              status.setMajorCode((int) ParseStatusCodes.SUCCESS);
              parse = new Parse("job list", "job list", new Outlink[0], status);
          }

          String prefix="";
          String suffix="";
          if ( !schema.getL2_template_for_joburl().isEmpty() ) {
              /* till now didn't see this case yet, just port Alibaba Json case to HTML */
              String l2_template_for_joburl = schema.getL2_template_for_joburl();
              LOG.warn(url + " (Abnormal case? ) joburl tempate: " + l2_template_for_joburl);

              String regex = schema.getL2_joburl_regex();
              if ( regex.isEmpty() ) {
                  LOG.warn(url + " template defined without regex: " + l2_template_for_joburl);
              } else {
                  try {
                      Perl5Util plUtil = new Perl5Util();
                      PatternMatcherInput matcherInput = new PatternMatcherInput(l2_template_for_joburl);
                      if (plUtil.match(regex, matcherInput)) {
                          MatchResult result = plUtil.getMatch();
                          prefix = result.group(1);
                          if (result.groups() > 3) suffix = result.group(3);
                      } else {
                          LOG.warn(url + " failed to match with regex " + regex);
                      }
                  } catch (MalformedPerl5PatternException pe) {
                      LOG.warn(url + " failed to compile regex " + regex, pe);
                  }
              }
          }

          for ( int i = 0; i < jobs.getLength(); i++ ) {
              Element job = (Element)jobs.item(i);

              expr = xpath.compile(schema.getL2_schema_for_joburl());
              String link = (String)((String) expr.evaluate(job, XPathConstants.STRING)).trim();
              if ( link.isEmpty() ) {
                  continue;
                  /* Danone case, the last page will have 7 job items, but some of them are empty */
              }

              link = prefix + link + suffix;

              LOG.debug(url + " contain link: " + link);
              String target = guess_URL(link, url, schema.getL1_url());

              String l2_joburl_repr = "";
              if ( !schema.getL2_schema_for_joburl_repr().isEmpty() ) {
                  expr = xpath.compile(schema.getL2_schema_for_joburl_repr());
                  l2_joburl_repr = (String) ((String) expr.evaluate(job, XPathConstants.STRING)).trim();
                  l2_joburl_repr = guess_URL(l2_joburl_repr, url, schema.getL1_url());
              }

              expr = xpath.compile(schema.getL2_job_title());
              String title = (String)((String) expr.evaluate(job, XPathConstants.STRING)).trim();
              title.replaceAll("\\s+", " ");
              /* here need to strip off the invalid char for ibm site */
              title = SolrUtils.stripNonCharCodepoints(title);

              expr = xpath.compile(schema.getL2_job_location());
              String location = (String)((String) expr.evaluate(job, XPathConstants.STRING)).trim();
              location = SolrUtils.stripNonCharCodepoints(location);

              String date = "";
              if ( !schema.getL2_job_date().isEmpty() ) {
                  expr = xpath.compile(schema.getL2_job_date());
                  date = (String) expr.evaluate(job, XPathConstants.STRING);
                  date = DateUtils.formatDate(date, schema.getL2_job_date_format());
              } else {
                  date = DateUtils.getCurrentDate();
              }

              WebPage  newPage = WebPage.newBuilder().build();
              newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
              CompanyUtils.setCompanyName(newPage, CompanyUtils.getCompanyName(page));
              CompanyUtils.setSummaryLink(newPage);

              newPage.getMetadata().put(CompanyUtils.company_job_title, ByteBuffer.wrap(title.getBytes()));
              newPage.getMetadata().put(CompanyUtils.company_job_location, ByteBuffer.wrap(location.getBytes()));
              newPage.getMetadata().put(CompanyUtils.company_job_date, ByteBuffer.wrap(date.getBytes()));

              newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

              if ( !l2_joburl_repr.isEmpty() ) {
                  newPage.setReprUrl(l2_joburl_repr);
                  LOG.debug(url + " with repr url: " + l2_joburl_repr);
              }

              /*if ( !schema.getL2_nextpage_postdata_inherit_regex().isEmpty() ) {
               Microsoft: Seems fetching summary page will distrub further 'NEXT' page,
               * might because server will checking VIEWSTATE/EVENTVALIDATION,
               * deply the summary page 10 minutes later,
               * this is decided basing whether need do some manual regex on post data.
               * if there are other sites, which using regex on post data, but dont need delay,
               * will introduce another configuration option in schema
               * Latest experiment show this doesn't matter
                  newPage.setPrevFetchTime(System.currentTimeMillis() + 10 * 60 * 1000L - defaultInterval * 1000L);
              }*/
              parse.addPage(target, newPage);

              if ( runtime_debug ) break;
          }
      }
      return parse;
  }
  private Parse getParse_summary_html(String url, WebPage page, CompanySchema schema, Document doc, XPath xpath)
      throws XPathExpressionException, MalformedURLException, PathNotFoundException {
      XPathExpression expr;
      String l3_title = "";
      /* l3 title is totally optional */
      if ( !schema.getL3_job_title().isEmpty() ) {
          expr = xpath.compile(schema.getL3_job_title());
          l3_title = (String) expr.evaluate(doc, XPathConstants.STRING);
          l3_title.replaceAll("\\s+", " ");
          l3_title = SolrUtils.stripNonCharCodepoints(l3_title);
      }

      String l3_date = "";
      if ( !schema.getL3_job_date().isEmpty() ) {
          expr = xpath.compile(schema.getL3_job_date());
          l3_date = (String) expr.evaluate(doc, XPathConstants.STRING);
          LOG.info(url + " Date: " + l3_date);
          l3_date = DateUtils.formatDate(l3_date, schema.getL3_job_date_format());
          /* Normally job date should be extracted from L2 page, but if configured which means use this */
          page.getMetadata().put(CompanyUtils.company_job_date, ByteBuffer.wrap(l3_date.getBytes()));
      }

      String l3_description = "";
      expr = xpath.compile(schema.getL3_job_description());
      NodeList nodes = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);
      for ( int i = 0; i < nodes.getLength(); i++ ) {
          Node node = nodes.item(i);
          l3_description += DOM2HTML.toString(node);
          l3_description += "<BR/>";
      }
      l3_description = SolrUtils.stripNonCharCodepoints(l3_description);

      LOG.info(url + " Title: " + l3_title +
              "\nDescription: " + ((l3_description.length()>200)?l3_description.substring(0, 200):l3_description));
      /* something to be done here,
       * we can select don't configure abstract & description in schema file,
       * then fallback to the default html parser implementation, html doc title and full page text.
       */
      ParseStatus status = ParseStatus.newBuilder().build();
      status.setMajorCode((int) ParseStatusCodes.SUCCESS);
      Parse parse = new Parse(l3_description, l3_title, new Outlink[0], status);
      return parse;
  }

  private Parse getParse_entry_json(String url, WebPage page, CompanySchema schema, DocumentContext doc)
      throws PathNotFoundException {
      /* Next Page URL */
      String nextpage_url = schema.getL2_template_for_nextpage_url();
      if ( nextpage_url.isEmpty() ) {
          nextpage_url = doc.read(schema.getL2_schema_for_nextpage_url());
          LOG.debug(url + " Got nextpage url: " + nextpage_url);
      } else {
          LOG.debug(url + " (Normal Case) use l2_prefix_for_nextpage_url instead of l2_schema_for_nextpage_url");
      }
      nextpage_url = guess_URL(nextpage_url, url, schema.getL1_url());

      /* Additional flag from alibaba, we don't use it.
      boolean result = doc.read("$.isSuccess");
      if ( result ) {
          LOG.warn("successful result");
      } else {
          LOG.warn("not successful result");
      } */

      /* Last Page Number */
      String last_page = schema.getL2_last_page();
      last_page = doc.read(last_page, String.class);
      LOG.debug(url + " Got last page: " + last_page);
      int last = 0;
      try {
          last = Integer.parseInt(last_page);
      } catch (NumberFormatException e) {
          LOG.error(url + " failed to parse last page");
          return null;
      }

      return generate_next_pages(url, schema, nextpage_url, last);
  }

  private Parse getParse_list_json(Parse parse, String url, WebPage page, CompanySchema schema, DocumentContext doc)
      throws PathNotFoundException {
      List<Object> jobs = doc.read(schema.getL2_schema_for_jobs());
      if ( jobs == null  || jobs.size() == 0 ) {
          LOG.info(url + " no jobs found");
          return parse;
      }

      LOG.info(url + " Found " + jobs.size() + " jobs");
      if ( parse == null ) {
          ParseStatus status = ParseStatus.newBuilder().build();
          status.setMajorCode((int) ParseStatusCodes.SUCCESS);
          parse = new Parse("job list", "job list", new Outlink[0], status);
      }
          /* Dont use this right now, throw out exception upper
          Configuration configuration = Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();
          JsonPath.parse(SIMPLE_MAP, configuration).read("$.not-found");
          */
      String prefix="";
      String suffix="";

      String l2_template_for_joburl = schema.getL2_template_for_joburl();
      if ( !l2_template_for_joburl.isEmpty() ) {
          LOG.debug(url + " l2_template_for_joburl: " + l2_template_for_joburl);

          String regex = schema.getL2_joburl_regex();
          try {
              Perl5Util plUtil = new Perl5Util();
              PatternMatcherInput matcherInput = new PatternMatcherInput(l2_template_for_joburl);
              if ( plUtil.match(regex, matcherInput)) {
                  MatchResult result = plUtil.getMatch();
                  prefix = result.group(1);
                  if (result.groups() > 3) suffix = result.group(3);
              } else {
                  LOG.warn(url + " failed to match with regex " + regex);
              }
          } catch ( MalformedPerl5PatternException pe ) {
              LOG.warn(url + " failed to compile regex " + regex);
          }
      } else {
          /* no such case */
      }

      for ( int i = 0; i < jobs.size(); i++ ) {
              /*
               * Dont use this old way, it will always try to cast to some type, which we not know in code
               * Object job = jobs.get(i);
               *
               * String link = JsonPath.read(job, schema.getL2_schema_for_joburl());
               */
          String pattern_prefix = schema.getL2_schema_for_jobs() + "[" + Integer.toString(i) + "]";

          String pattern_url = pattern_prefix + "." + schema.getL2_schema_for_joburl();
          String newurl = doc.read(pattern_url, String.class);
          if ( newurl.isEmpty() ) {
              continue;
          }

          /*Dont use format even though it is convenient,
          * because not sure any site carry invalid url for format,
          * aiming for a consistent regex handling as l2 pageurl/postdata
          * String link = String.format(l2_template_for_joburl, newurl);
          */
          String link = prefix + newurl + suffix;
          LOG.debug(url + " contain link: " + link);
          String target = guess_URL(link, url, schema.getL1_url());

          String pattern_title = pattern_prefix + "." + schema.getL2_job_title();
          String title = doc.read(pattern_title, String.class);
          title.replaceAll("\\s+", " ");
              /* here need to strip off the invalid char for ibm site */
          title = SolrUtils.stripNonCharCodepoints(title);

          String pattern_location = pattern_prefix + "." + schema.getL2_job_location();
          String location = doc.read(pattern_location, String.class);
          location = SolrUtils.stripNonCharCodepoints(location);

          String date = "";
          if ( !schema.getL2_job_date().isEmpty() ) {
              String pattern_date = pattern_prefix + "." + schema.getL2_job_date();
              date = doc.read(pattern_date, String.class);
              date = DateUtils.formatDate(date, schema.getL2_job_date_format());
          } else {
              date = DateUtils.getCurrentDate();
          }

          String newurl_repr = "";
          if ( !schema.getL2_schema_for_joburl_repr().isEmpty() ) {
              String pattern_url_repr = pattern_prefix + "." + schema.getL2_schema_for_joburl_repr();
              newurl_repr = doc.read(pattern_url_repr, String.class);
              newurl_repr = guess_URL(newurl_repr, url, schema.getL1_url());
          }

          WebPage  newPage = WebPage.newBuilder().build();
          newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
          CompanyUtils.setCompanyName(newPage, CompanyUtils.getCompanyName(page));
          CompanyUtils.setSummaryLink(newPage);

          newPage.getMetadata().put(CompanyUtils.company_job_title, ByteBuffer.wrap(title.getBytes()));
          newPage.getMetadata().put(CompanyUtils.company_job_location, ByteBuffer.wrap(location.getBytes()));
          newPage.getMetadata().put(CompanyUtils.company_job_date, ByteBuffer.wrap(date.getBytes()));

          newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

          if ( !newurl_repr.isEmpty() ) {
              newPage.setReprUrl(newurl_repr);
              LOG.debug(url + " with repr url: " + newurl_repr);
          }
          if ( try_to_shortcut_l3page ) {
                  /* Wait a moment, alibaba's JSON file contains the job detail already,
                  * which means We don't need fetch the 3rd level page which is generated via that.
                  * of course, it is better to have an option to enable/disable this feature.
                  */
              String pattern_l2_description = schema.getL2_job_description();
              if ( !pattern_l2_description.isEmpty()) {
                  pattern_l2_description = pattern_prefix + pattern_l2_description;

                  Map<String, String> descs = doc.read(pattern_l2_description, Map.class);
                  String l2_description = "";
                  for ( String value : descs.values() ) {
                      l2_description += value;
                      l2_description += "<BR/>";
                  }

                  newPage.setText(new Utf8(l2_description));
                  newPage.setTitle(new Utf8(title));
                  newPage.setContent(ByteBuffer.wrap(new byte[0]));

                  //final byte[] signature = sig.calculate(newPage);
                  final byte[] signature = org.apache.hadoop.io.MD5Hash.digest(l2_description.getBytes(), 0, l2_description.length()).getDigest();
                  newPage.setSignature(ByteBuffer.wrap(signature));

                  ParseStatus status = ParseStatus.newBuilder().build();
                  status.setMajorCode((int) ParseStatusCodes.SUCCESS);
                  newPage.setParseStatus(status);

                      /* using parents Mark */
                  Utf8 fetchMark = Mark.FETCH_MARK.checkMark(page);
                  if (fetchMark != null) {
                      Mark.GENERATE_MARK.putMark(newPage, fetchMark);
                      Mark.FETCH_MARK.putMark(newPage, fetchMark);
                      Mark.PARSE_MARK.putMark(newPage, fetchMark);
                      Mark.UPDATEDB_MARK.putMark(newPage, fetchMark);
                  }

                      /* should do some tricy here for schedule to make sure it will be scheduled later than parent?
                       * schedule.initializeSchedule(newurl, newPage);
                       * workaround, 999 mean this page is newly fetched,
                       * should be fetched again after configured INTERVAL
                       */
                  newPage.setPrevFetchTime(System.currentTimeMillis());
              }
          }
          parse.addPage(target, newPage);

          if ( runtime_debug ) break;
      }

      return parse;
  }

  public void setConf(Configuration conf) {
    this.conf = conf;
    this.defaultCharEncoding = getConf().get(
        "parser.character.encoding.default", "windows-1252");
    this.repo = CompanySchemaRepository.getInstance(conf.get("company.schema.dir", "./"));
    this.runtime_debug = conf.getBoolean("runtime.debug", false);
    this.sig = SignatureFactory.getSignature(conf);
    this.try_to_shortcut_l3page = conf.getBoolean("try.to.shortcut.l3page", true);
    this.defaultInterval = conf.getInt("db.fetch.interval.default", 0);
  }

  public Configuration getConf() {
    return this.conf;
  }

  @Override
  public Collection<WebPage.Field> getFields() {
    return FIELDS;
  }

  public static void main(String[] args) throws Exception {
    // LOG.setLevel(Level.FINE);
    String name = args[0];
    String url = "file:" + name;
    File file = new File(name);
    byte[] bytes = new byte[(int) file.length()];
    DataInputStream in = new DataInputStream(new FileInputStream(file));
    in.readFully(bytes);
    Configuration conf = NutchConfiguration.create();
    CompanyParser parser = new CompanyParser();
    parser.setConf(conf);
    WebPage page = WebPage.newBuilder().build();
    page.setBaseUrl(new Utf8(url));
    page.setContent(ByteBuffer.wrap(bytes));
    page.setContentType(new Utf8("text/html"));
    Parse parse = parser.getParse(url, page);
    System.out.println("title: " + parse.getTitle());
    System.out.println("text: " + parse.getText());
    System.out.println("outlinks: " + Arrays.toString(parse.getOutlinks()));

  }

}
