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
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;


import org.apache.avro.util.Utf8;
import org.apache.nutch.companyschema.*;
import org.apache.nutch.companyschema.CompanySchema;
import org.apache.nutch.companyschema.CompanySchemaRepository;
import org.apache.nutch.companyschema.CompanyUtils;
import org.apache.nutch.companyschema.DateUtils;
import org.apache.nutch.companyschema.LocationUtils;
import org.apache.nutch.companyschema.EncodeUtils;
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

/* for math expression evaluator */
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import java.lang.Math;

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

  private boolean debug_fetch_single_item;
  private boolean debug_save_page_content;
  private int fetch_first_n_pages;
  private int fetch_winthin_n_days_pages;

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
  public static void save_page_content(String url, String content_type,  WebPage page ) {
      String origcontent = new String(page.getContent().array());
      try {
          String date = DateUtils.getThreadLocalDateFormat().format(new Date());
          String suffix = ".html";
          if ( content_type.equals("application/pdf") ) suffix = ".pdf";
          String fname = "/tmp/" + date + suffix;
          FileWriter fw = new FileWriter(fname, true);
          BufferedWriter bw = new BufferedWriter(fw);
          bw.write(origcontent);
          bw.flush();
          bw.close();
          fw.close();
          LOG.debug(url + " content saved to " + fname);
      } catch (Exception ee) {
          LOG.warn(url + " failed to save content to file " + ee.getMessage());
      };
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

      String content_type = TableUtil.toString(page.getContentType());
      LOG.debug(url + " Parsing as " + content_type + " encoding: " + encoding);

      /*
      Utf8 content_type_key = new Utf8(org.apache.nutch.net.protocols.Response.CONTENT_TYPE);
      if ( page.getHeaders().containsKey(content_type_key) ) {
          java.lang.CharSequence content_type_utf8 = page.getHeaders().get(content_type_key);
          content_type = content_type_utf8.toString();
          LOG.debug(url + " : " + content_type);
      }*/

      if ( debug_save_page_content ) {
          save_page_content(url, content_type, page);
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
      if ( content_type.toLowerCase().contains("json") ) {
          /* Create JsonReader Context, from that to parse */
          com.jayway.jsonpath.Configuration JSON_SMART_CONFIGURATION = com.jayway.jsonpath.Configuration.defaultConfiguration();
          DocumentContext doc = JsonPath.parse(input.getByteStream(), JSON_SMART_CONFIGURATION);
          if ( CompanyUtils.isEntryLink(page)) {
              parse = getParse_entry_json(url, page, schema, doc);
              parse = getParse_list_json(parse, url, page, schema, doc);
          } else if ( CompanyUtils.isListLink(page)) {
              parse = getParse_list_json(null, url, page, schema, doc);
          } else if ( CompanyUtils.isSummaryLink(page)) {
              /* Summary Page in JSON format (Huawei) */
              parse = getParse_summary_json(url, page, schema, doc);
          }
      } else {
      //Default case
      //if ( content_type.toLowerCase().contains("html") ||
      //        content_type.toLowerCase().contains("text/plain")   ) {
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
          parser.setFeature("http://xml.org/sax/features/namespaces", false);
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
                if ( !schema.getL2_schema_for_jobs().isEmpty() ) {
                    parse = getParse_list_html(parse, url, page, schema, doc, xpath);
                } else if ( !schema.getL2_regex_matcher_for_jobs().isEmpty() ) {
                    /* Oracle Case, decode from raw content via regex */
                    parse = getParse_list_rawregex(parse, url, page, schema);
                }
            } else if ( CompanyUtils.isListLink(page)) {
                if (!schema.getL2_schema_for_jobs().isEmpty()) {
                    parse = getParse_list_html(null, url, page, schema, doc, xpath);
                } else if ( !schema.getL2_regex_matcher_for_jobs().isEmpty() ) {
                    /* Oracle Case, decode from raw content via regex */
                    parse = getParse_list_rawregex(null, url, page, schema);
                }
            } else if ( CompanyUtils.isSummaryLink(page)) {
                if ( !schema.getL3_regex_matcher_for_job().isEmpty() ) {
                    /* Oracle Case, decode from raw content via regex */
                    parse = getParse_summary_rawregex(url, page, schema);
                } else {
                    parse = getParse_summary_html(url, page, schema, doc, xpath);
                }
            }
        } catch (SAXException e) {
          LOG.warn("Failed to parse " + url + " with DOMParser " + e.getMessage());
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
          if ( !schema.getL2_schema_for_nextpage_url().isEmpty() ) {
              expr = xpath.compile(schema.getL2_schema_for_nextpage_url());
              nextpage_url = (String) expr.evaluate(doc, XPathConstants.STRING);
              LOG.debug(url + " (Normal case) Got nextpage url: " + nextpage_url);
          } else {
              LOG.debug(url + " (Goldmansachs?) Not template_for_nextpage_url and schema_for_nextpage_url");
              return null;
          }
      } else {
          LOG.debug(url + " (Normal case) use l2_template_for_nextpage_url instead of l2_schema_for_nextpage_url");
      }
      nextpage_url = guess_URL(nextpage_url, url, schema.getL1_url());

      /* Last Page Number,
       * Note not all company will have the last page number to iterate,
       * e.g Microsoft, if so, we fallback to 'click' on the 'Next' button
       * */
      if ( !schema.getL2_last_page_multiplier().isEmpty() ) {
          // Daimler case
          String last_page_multiplier = schema.getL2_last_page_multiplier();
          expr = xpath.compile(last_page_multiplier);
          last_page_multiplier = (String) expr.evaluate(doc, XPathConstants.STRING);
          LOG.debug(url + " Got last page multiplier: " + last_page_multiplier);
          int last = 0;
          try {
              last = Integer.parseInt(last_page_multiplier);
              int incr = Integer.parseInt(schema.getL2_nextpage_increment());
              last = last * incr;
          } catch (NumberFormatException e) {
              LOG.error(url + " failed to parse last page");
              return null;
          }
          return generate_next_pages(url, page, nextpage_url, schema, last, page.getScore() * 0.95f);
      } else if ( !schema.getL2_last_page().isEmpty() ) {
          String last_page = schema.getL2_last_page();
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

          return generate_next_pages(url, page, nextpage_url, schema, last, page.getScore() * 0.95f);
      } else if ( !schema.getL2_regex_matcher_for_jobnbr().isEmpty() ) {
          /* Oracle Case, can only get page number from total job numbers via regex, then math to get it */
          int last = 0;
          try {
              Perl5Util plUtil = new Perl5Util();
              String origcontent = new String(page.getContent().array());
              PatternMatcherInput matcherInput = new PatternMatcherInput(origcontent);
              if (plUtil.match(schema.getL2_regex_matcher_for_jobnbr(), matcherInput)) {
                  MatchResult result = plUtil.getMatch();
                  String jobnbr = result.group(1);

                  LOG.debug("total job number string:" + jobnbr);
                  /*ScriptEngineManager mgr = new ScriptEngineManager();
                   ScriptEngine engine = mgr.getEngineByName("JavaScript");
                   Double d = (Double)engine.eval(output);*/
                  double d = Integer.parseInt(jobnbr) / 10;
                  last = ((int) (Math.floor(d))) + 1;
                  if (LOG.isDebugEnabled()) LOG.debug(url + " last page number: " + last);
              } else {
                  LOG.warn(url + " failed to match total job nbr via regex " + schema.getL2_regex_matcher_for_jobnbr());
                  return null;
              }
          } catch ( MalformedPerl5PatternException me ) {
              LOG.warn(url + " failed to decode total job nbr via regex", me);
              return null;
          } catch ( NumberFormatException ne ) {
              LOG.warn(url + " failed to decode total job nbr via math expression", ne);
              return null;
          }
          /*catch ( javax.script.ScriptException se ) {
              LOG.warn(url + " failed to decode last page number via math expression", se);
              return null;
          }*/

          return generate_next_pages(url, page, nextpage_url, schema, last, page.getScore() * 0.95f);
      } else {
          /* fallback to 'click' 'Next' button
           * Following two meta data should appear to decide when to finish the iterate.
           * l2_nextpage_postdata_inherit_regex
           * l2_nextpage_endflag
           */
          return generate_next_page(url, page, nextpage_url, schema , doc, xpath, page.getScore() * 0.99f);
      }
  }
  private Parse generate_next_page(String url, WebPage page, String nextpage_url, CompanySchema schema, Document doc, XPath xpath, float score)
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

          if ( !schema.getL2_nextpage_regex().isEmpty() ) {
              /* shall we do the replacement again basing on schema?
              if so, how to get the current page number ? Stupid Honeywell.
               */
          }
          if ( debug_save_page_content ) {
              try {
                  String date = DateUtils.getThreadLocalDateFormat().format(new Date());
                  LOG.debug(url + " write generated post data to file /tmp/" + date);
                  FileWriter fw = new FileWriter("/tmp/" + date + ".data", true);
                  BufferedWriter bw = new BufferedWriter(fw);
                  bw.write(newpostdata);
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
          newPage.setScore(score);
          newPage.getMetadata().put(CompanyUtils.company_dyn_data, ByteBuffer.wrap(newpostdata.getBytes()));

          newPage.getMetadata().put(CompanyUtils.company_cookie, page.getMetadata().get(CompanyUtils.company_cookie));

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
  private String generate_regex_for_nextitem(String regex, String substitute) {
      /* this help function is for generating regex from schema file, e.g
      "l2_nextpage_regex" : "s/(.*SearchResult_)(\\d+)(.*__EVENTTARGET=ViewJob_)(\\2)(.*)/$1-deadbeaf-$3-deadbeaf-$5/g",
      this is to regplace "-deadbeaf-" to index nubmer,
      the generated regex:
         2nd page: "s/(.*SearchResult_)(\\d+)(.*__EVENTTARGET=ViewJob_)(\\2)(.*)/$1\\2$3\\2$5/g",
         3rd page: "s/(.*SearchResult_)(\\d+)(.*__EVENTTARGET=ViewJob_)(\\2)(.*)/$1\\3$3\\3$5/g",
      why using strange "-deadbeaf-" is to avoid any possible string in original regex.
       */
      String newregex = regex.replaceAll("-deadbeaf-", "\\\\"+substitute);
      /* this can be extended with again Perl5 Regex later if necessary for other usecases */
      return newregex;
  }
  private int get_startitem_from_regex(String regex, String input) throws MalformedPerl5PatternException {
      try {
          Perl5Util plUtil = new Perl5Util();
          //regex for the matcher part of base regex, e.g
          //"s/(.*SearchResult_)(\\d+)(.*__EVENTTARGET=ViewJob_)(\\2)(.*)/$1-deadbeaf-$3-deadbeaf-$5/g"
          // -> "/(.*SearchResult_)(\\d+)(.*__EVENTTARGET=ViewJob_)(\\2)(.*)/"
          // with this regex, we can get the start page number in group 2.
          // neee support Huawei's special case: "s/(.*page\\\\/10\\\\/)(\\d+)(.*)/$1-deadbeaf-$3/g"
          // schema designer need decide whether using "/" or "#" in regex
          String match_regex = "";
          if ( regex.startsWith("s/") ) {
              match_regex = plUtil.substitute("s/s(\\/[^\\/]+\\/).*/$1/g", regex);
          } else if ( regex.startsWith("s#") ) {
              match_regex = plUtil.substitute("s#s(\\#[^\\#]+\\#).*#$1#g", regex);
          } else throw new MalformedPerl5PatternException("invalid regex");

          LOG.debug("start page number regex " + match_regex + " from orig regex: " + regex);

          PatternMatcherInput matcherInput = new PatternMatcherInput(input);
          if (plUtil.match(match_regex, matcherInput)) {
              MatchResult result = plUtil.getMatch();
              /* we assume the initial page number will appear in the 2nd group
               * This can be extended later by checking the position of "-deadbeaf-" in replacer part */
              String start = result.group(2);
              return Integer.parseInt(start);
          } else {
              LOG.warn(input + " failed to get start page number from regex " + regex + " check schema!");
              throw new MalformedPerl5PatternException(regex);
          }
      } catch ( MalformedPerl5PatternException e ) {
          LOG.warn(input + " failed to get start page number from regex " + regex + " check schema!");
          throw e;
      }
  }
  private boolean shouldStop(String key, int current) {
      if (this.debug_fetch_single_item) {
          LOG.debug(key + " debug_fetch_single_item enabled, stop further");
          return true;
      } else if (current >= this.fetch_first_n_pages) {
          LOG.debug(key + " fetch_first_n_pages configured to " + fetch_first_n_pages + " stop further");
          return true;
      } else return false;
  }
  private Parse generate_next_pages(String key, WebPage page, String page_list_url, CompanySchema schema, int last, float score) {
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
          if ( schema.getL2_nextpage_method().equals("POST") ) {
              /* normally this should be using method POST with some dynamic data, we should do pattern match/replace inside dynamic data */
              String l2_postdata = schema.getL2_template_for_nextpage_postdata();
              if (l2_postdata.isEmpty()) {
                  LOG.warn(key + " Dont know how to generate new pages without dynamic post data in schema");
                  return parse;
              }

              int start = get_startitem_from_regex(regex, l2_postdata);

              int fetched = 0;
              for (int i = start; i <= last; i += incr) {
                  String newregex = generate_regex_for_nextitem(regex, Integer.toString(i));
                  String newpostdata = plUtil.substitute(newregex, l2_postdata);
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
                  newPage.setScore(score);
                  newPage.getMetadata().put(CompanyUtils.company_dyn_data, ByteBuffer.wrap(newpostdata.getBytes()));
                  newPage.getMetadata().put(CompanyUtils.company_cookie, page.getMetadata().get(CompanyUtils.company_cookie));
                  parse.addPage(newurl, newPage);

                  fetched++;
                  if (shouldStop(key, fetched)) break;
              }
          } else {
              /* normal case where next page is a href, can generate list of new urls basing on pattern */
              int start = get_startitem_from_regex(regex, page_list_url);
              int fetched = 0;
              for (int i = start; i <= last; i += incr) {
                  String newregex = generate_regex_for_nextitem(regex, Integer.toString(i));
                  String newurl = plUtil.substitute(newregex, page_list_url);
                  if ( LOG.isDebugEnabled() ) {
                      LOG.debug(newurl + " from regex " + newregex + " : " + regex);
                  }
                  WebPage newPage = WebPage.newBuilder().build();
                  newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
                  CompanyUtils.setCompanyName(newPage, schema.getName());
                  CompanyUtils.setListLink(newPage);
                  newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));
                  newPage.setScore(score);
                  newPage.getMetadata().put(CompanyUtils.company_cookie, page.getMetadata().get(CompanyUtils.company_cookie));
                  /* dont need to add post data here, we won't handle it,
                   * if there is any strange site do this way, will add it
                   **/
                  parse.addPage(newurl, newPage);

                  fetched++;
                  if ( shouldStop(key, fetched) ) break;
              }
          }
      } catch ( MalformedPerl5PatternException pe ) {
          LOG.error(key + " failed of nextpage regex " + regex, pe);
          return null;
      }
      return parse;
  }
    /* This function is used to generate a combined string basing on schema configuration:
     *  $1,$5
     *  $2:$6
     *  only replace $2 with stuff in previous Perl5 MatchResult, keep all the other thing impact.
     */
    public static String extract_matcher_groups(MatchResult src_matcher_result, String grps_regex) {
        StringBuffer result = new StringBuffer();
        try {
            java.util.regex.Pattern pattern_for_grpstr = java.util.regex.Pattern.compile("\\$(\\d+)");
            java.util.regex.Matcher matcher_for_grpstr = pattern_for_grpstr.matcher(grps_regex);

            while (matcher_for_grpstr.find()) {
                String grpstr = matcher_for_grpstr.group(1);
                int grpid = Integer.parseInt(grpstr);
                String matcher = src_matcher_result.group(grpid);
                if ( matcher != null ) {
                    if ( matcher.endsWith("!") ) {
                    /* trim off the trailing !
                    * in Ajax, it is using !|! as separator, but it is possible there will be ! inside the content,
                    * then we can use ! as the last valid character in regex, instead using ! as last valid char,
                    * which means in many case, there will be one trailing ! in matcher group,
                    * strip off it, this is dangerous/ugly, because some regex in schema use !as last valid char
                    **/
                        matcher = matcher.substring(0, matcher.length()-1);
                    }
                    matcher_for_grpstr.appendReplacement(result, matcher);
                }
                else
                    matcher_for_grpstr.appendReplacement(result,  "");
            }
            matcher_for_grpstr.appendTail(result);
        } catch (java.util.regex.PatternSyntaxException e) {
            LOG.error("Failed to extract groups from MatchResult via: " + grps_regex, e);
        }
        return result.toString();
    }
    private static String extract_via_regex(String input, String regex, int grp) {
        /* Only for extract one specific group id, items separated by ',' */
        StringBuffer result = new StringBuffer();
        Perl5Util plUtil = new Perl5Util();
        PatternMatcherInput matcherInput = new PatternMatcherInput(input);
        boolean firsttime = true;
        while (plUtil.match(regex, matcherInput)) {
            MatchResult matchresult = plUtil.getMatch();
            String item = matchresult.group(grp);
            if (item != null) {
                if ( !firsttime ) result.append(",");
                result.append(item);
                firsttime = false;
            }
        }
        return result.toString();
    }

    private Parse getParse_list_rawregex(Parse parse, String url, WebPage page, CompanySchema schema) {
        String origcontent = new String(page.getContent().array());

        int total = 0;
        if ( parse == null ) {
            ParseStatus status = ParseStatus.newBuilder().build();
            status.setMajorCode((int) ParseStatusCodes.SUCCESS);
            parse = new Parse("job list", "job list", new Outlink[0], status);
        }
        try {
            PatternMatcherInput matcherInput = new PatternMatcherInput(origcontent);
            String regex = schema.getL2_regex_matcher_for_jobs();
            Perl5Util plUtil = new Perl5Util();
            while (plUtil.match(regex, matcherInput)) {
                MatchResult result = plUtil.getMatch();

                String title = "";
                if (!schema.getL2_job_title().isEmpty()) {
                    title = extract_matcher_groups(result, schema.getL2_job_title());
                    title = EncodeUtils.rawdecode(title, "UTF-8"); /* some job has a '&' inside it */
                }

                String date = "";
                if (!schema.getL2_job_date().isEmpty()) {
                    date = extract_matcher_groups(result, schema.getL2_job_date());
                    date = DateUtils.formatDate(date, schema.getJob_date_format());

                    if ( DateUtils.nDaysAgo(date, fetch_winthin_n_days_pages) ) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Job posted " + fetch_winthin_n_days_pages + " days ago, ignore");
                        }
                        continue;
                    }
                } else {
                    date = DateUtils.getCurrentDate();
                }

                String location = "";
                if (!schema.getL2_job_location().isEmpty()) {
                    location = extract_matcher_groups(result, schema.getL2_job_location());
                    if ( !schema.getJob_regex_matcher_for_location().isEmpty() ) {
                        /* Oracle special case, difficult to handle with single match-replace statement,
                         * IN-IN,India-Hyderabad, SG-SG,Singapore-Singapore, CN-CN,China-Dalian, AU-AU,Australia-Sydney
                         * fallback to regex-matcher, then we assume only group 1 is needed,
                         * it is possible to specify job_location_format_regex further.
                         */
                        location = extract_via_regex(location, schema.getJob_regex_matcher_for_location(), 1);
                    }
                    location = LocationUtils.format(location, schema.getJob_location_format_regex());
                }

                String newurl = "";
                if (!schema.getL2_schema_for_joburl().isEmpty()) {
                    newurl = extract_matcher_groups(result, schema.getL2_schema_for_joburl());
                    newurl = URLDecoder.decode(newurl, "utf-8");
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug(total + "th job-> title: " + title + " date: " + date + " location: " + location + " url: " + url);
                }

                WebPage newPage = WebPage.newBuilder().build();
                newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
                CompanyUtils.setCompanyName(newPage, CompanyUtils.getCompanyName(page));
                CompanyUtils.setSummaryLink(newPage);

                newPage.getMetadata().put(CompanyUtils.company_job_title, ByteBuffer.wrap(title.getBytes()));
                newPage.getMetadata().put(CompanyUtils.company_job_location, ByteBuffer.wrap(location.getBytes()));
                newPage.getMetadata().put(CompanyUtils.company_job_date, ByteBuffer.wrap(date.getBytes()));

                newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

                newPage.setScore(page.getScore() / 10); /* lazy workaround */

                newPage.getMetadata().put(CompanyUtils.company_cookie, page.getMetadata().get(CompanyUtils.company_cookie));

                parse.addPage(newurl, newPage);

                total++;

                if (debug_fetch_single_item) {
                    LOG.debug(url + " debug_fetch_single_item enabled, stop further");
                    break;
                }
            }
        } catch ( Exception e ) {
            LOG.warn(url + " failed to decode jobs ", e);
        }
        if ( total == 0 ) {
            LOG.info(url + " no jobs found ");
        } else {
            LOG.info(url + " Total " + total + " jobs extraced");
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

          if ( !schema.getL2_template_for_joburl().isEmpty() ) {
              /* till now didn't see this case yet, just port Alibaba Json case to HTML */
              LOG.warn(url + " (Abnormal case? ) joburl tempate: " + schema.getL2_template_for_joburl());
              if (schema.getL2_joburl_regex().isEmpty()) {
                  LOG.warn(url + " template defined without regex");
              }
          }
          Perl5Util plUtil = new Perl5Util();
          for ( int i = 0; i < jobs.getLength(); i++ ) {
              Element job = (Element)jobs.item(i);

              expr = xpath.compile(schema.getL2_schema_for_joburl());
              String link = (String)((String) expr.evaluate(job, XPathConstants.STRING)).trim();
              if ( link.isEmpty() ) {
                  LOG.warn(url + " failed to get any link via  " + schema.getL2_schema_for_joburl());
                  continue;
                  /* Danone case, the last page will have 7 job items, but some of them are empty */
              }
              if ( !schema.getL2_template_for_joburl().isEmpty() && !schema.getL2_joburl_regex().isEmpty() ) {
                  String newregex = generate_regex_for_nextitem(schema.getL2_joburl_regex(), link);
                  link = plUtil.substitute(newregex, schema.getL2_template_for_joburl());
              }

              LOG.debug(url + " contain link: " + link);
              String target = guess_URL(link, url, schema.getL1_url());

              String l2_joburl_repr = "";
              if ( !schema.getL2_schema_for_joburl_repr().isEmpty() ) {
                  expr = xpath.compile(schema.getL2_schema_for_joburl_repr());
                  l2_joburl_repr = (String) ((String) expr.evaluate(job, XPathConstants.STRING)).trim();
                  l2_joburl_repr = guess_URL(l2_joburl_repr, url, schema.getL1_url());
                  if ( !schema.getL2_template_for_joburl_repr().isEmpty() && !schema.getL2_joburl_regex().isEmpty() ) {
                  /* Till now didn't see repr url need to be replaced with regex yet,
                   * only hit this for Huawei/Json case, will consider this if necessary later
                   * In general this should not happen, because repr urls are extraced from HTML href here,
                   * it is unreasonable to have a webpage with href again to be replaced.
                   */
                      String newregex = generate_regex_for_nextitem(schema.getL2_joburl_regex(), l2_joburl_repr);
                      l2_joburl_repr = plUtil.substitute(newregex, schema.getL2_template_for_joburl_repr());
                  }
                  l2_joburl_repr = guess_URL(l2_joburl_repr, url, schema.getL1_url());
              }

              expr = xpath.compile(schema.getL2_job_title());
              String title = (String)((String) expr.evaluate(job, XPathConstants.STRING)).trim();
              title.replaceAll("\\s+", " ");
              /* here need to strip off the invalid char for ibm site */
              title = SolrUtils.stripNonCharCodepoints(title);

              String location = "";
              if ( !schema.getL2_job_location().isEmpty() ) {
                  expr = xpath.compile(schema.getL2_job_location());
                  location = (String) expr.evaluate(job, XPathConstants.STRING);
                  location = LocationUtils.format(location, schema.getJob_location_format_regex());
              }

              String date = "";
              if ( !schema.getL2_job_date().isEmpty() ) {
                  expr = xpath.compile(schema.getL2_job_date());
                  date = (String) expr.evaluate(job, XPathConstants.STRING);
                  date = DateUtils.formatDate(date, schema.getJob_date_format());
                  if ( DateUtils.nDaysAgo(date, fetch_winthin_n_days_pages) ) {
                      if (LOG.isDebugEnabled()) {
                          LOG.debug("Job posted " + fetch_winthin_n_days_pages + " days ago, ignore");
                      }
                      continue;
                  }
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

              newPage.setScore(page.getScore()/jobs.getLength());

              newPage.getMetadata().put(CompanyUtils.company_cookie, page.getMetadata().get(CompanyUtils.company_cookie));

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

              if (debug_fetch_single_item) {
                  LOG.debug(url + " debug_fetch_single_item enabled, stop further");
                  break;
              }
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

      String company_subname = "";
      if ( !schema.getL3_company_subname().isEmpty() ) {
          //Cofco case
          expr = xpath.compile(schema.getL3_company_subname());
          company_subname = (String) expr.evaluate(doc, XPathConstants.STRING);
          if ( company_subname != null ) {
              page.getMetadata().put(CompanyUtils.company_subname, ByteBuffer.wrap(company_subname.getBytes()));
          }
      }

      String l3_date = "";
      if ( !schema.getL3_job_date().isEmpty() ) {
          expr = xpath.compile(schema.getL3_job_date());
          l3_date = (String) expr.evaluate(doc, XPathConstants.STRING);
          LOG.info(url + " Date: " + l3_date);
          l3_date = DateUtils.formatDate(l3_date, schema.getJob_date_format());
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

    private Parse getParse_summary_rawregex(String url, WebPage page, CompanySchema schema) {
        try {
            String origcontent = new String(page.getContent().array());
            String l3_title = "";
            String l3_description = "";

            PatternMatcherInput matcherInput = new PatternMatcherInput(origcontent);
            String regex = schema.getL3_regex_matcher_for_job();
            Perl5Util plUtil = new Perl5Util();

            if (plUtil.match(regex, matcherInput)) { /* only match once */
                MatchResult result = plUtil.getMatch();

                /* can be extented to include l3_title, and l3_date if necessary later, not such case now */
                if (!schema.getL3_job_description().isEmpty()) {
                    l3_description = extract_matcher_groups(result, schema.getL3_job_description());
                    //l3_description = URLDecoder.decode(l3_description, "utf-8");
                    l3_description = EncodeUtils.rawdecode(l3_description, "UTF-8");
                }

                /* Intel case, get job_location in summary page */
                if (!schema.getL3_job_location().isEmpty()) {
                    String l3_location = extract_matcher_groups(result, schema.getL3_job_location());
                    if ( !schema.getJob_regex_matcher_for_location().isEmpty() ) {
                        l3_location = extract_via_regex(l3_location, schema.getJob_regex_matcher_for_location(), 1);
                    }
                    l3_location = LocationUtils.format(l3_location, schema.getJob_location_format_regex());
                    page.getMetadata().put(CompanyUtils.company_job_location, ByteBuffer.wrap(l3_location.getBytes()));
                }
            } else {
                LOG.warn(url + " failed to match job description");
            }

            LOG.info(url + " Title: " + l3_title +
                    "\nDescription: " + ((l3_description.length() > 200) ? l3_description.substring(0, 200) : l3_description));

            ParseStatus status = ParseStatus.newBuilder().build();
            status.setMajorCode((int) ParseStatusCodes.SUCCESS);
            Parse parse = new Parse(l3_description, l3_title, new Outlink[0], status);
            return parse;

        } catch ( Exception e ) {
            LOG.error("failed to decode l3 description via regex ", e);
            return null;
        }
    }

  private Parse getParse_entry_json(String url, WebPage page, CompanySchema schema, DocumentContext doc)
      throws PathNotFoundException {
      /* Next Page URL */
      String nextpage_url = schema.getL2_template_for_nextpage_url();
      if ( nextpage_url.isEmpty() ) {
          if ( !schema.getL2_schema_for_nextpage_url().isEmpty() ) {
              nextpage_url = doc.read(schema.getL2_schema_for_nextpage_url());
              LOG.debug(url + " Got nextpage url: " + nextpage_url);
          } else {
              LOG.info(url + " No nextpage to fetch, return");
              return null;
          }
      } else {
          LOG.debug(url + " (Normal Case) use l2_template_for_nextpage_url instead of l2_schema_for_nextpage_url");
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

      return generate_next_pages(url, page, nextpage_url, schema, last, page.getScore() * 0.95f);
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

      if ( !schema.getL2_template_for_joburl().isEmpty() ) {
          LOG.debug(url + " l2_template_for_joburl: " + schema.getL2_template_for_joburl());
          if ( schema.getL2_joburl_regex().isEmpty() ) {
              LOG.warn(url + " l2_template_for_joburl defined without l2_joburl_regex");
          }
      } else {
          /* no such case */
          LOG.warn(url + " json page without l2_template_for_joburl configured ");
      }

      if ( !schema.getL2_template_for_joburl_repr().isEmpty() ) {
          /* HuaWei case, real job url should also be replaced */
          LOG.debug(url + " l2_template_for_joburl_repr: " + schema.getL2_template_for_joburl_repr());
          if ( schema.getL2_joburl_regex().isEmpty() ) {
              LOG.warn(url + " l2_template_for_joburl_repr defined without l2_joburl_regex");
          }
      }
      Perl5Util plUtil = new Perl5Util();
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

          try {
              //Dupont url in json file is encoded
              newurl = URLDecoder.decode(newurl, "utf-8");
          } catch ( Exception e) {
              LOG.warn("job url is incorrect, ignore " + newurl);
              continue;
          }

          if ( !schema.getL2_template_for_joburl().isEmpty() && !schema.getL2_joburl_regex().isEmpty() ) {
              String newregex = generate_regex_for_nextitem(schema.getL2_joburl_regex(), newurl);
              newurl = plUtil.substitute(newregex, schema.getL2_template_for_joburl());
          }

          /*Dont use format even though it is convenient,
          * because not sure any site carry invalid url for format,
          * aiming for a consistent regex handling as l2 pageurl/postdata
          * String link = String.format(l2_template_for_joburl, newurl);
          */
          LOG.debug(url + " contain link: " + newurl);
          String target = guess_URL(newurl, url, schema.getL1_url());

          String pattern_title = pattern_prefix + "." + schema.getL2_job_title();
          String title = doc.read(pattern_title, String.class);
          title.replaceAll("\\s+", " ");
              /* here need to strip off the invalid char for ibm site */
          title = SolrUtils.stripNonCharCodepoints(title);

          String location = "";
          if ( !schema.getL2_job_location().isEmpty() ) {
              /* Intel case, L2 job_location can be ["multiple location"], should get it from L3 */
              String pattern_location = pattern_prefix + "." + schema.getL2_job_location();
              try {
                  location = doc.read(pattern_location, String.class);
              } catch (com.jayway.jsonpath.PathNotFoundException e) {
                  LOG.warn("Failed to get L2 job location via " + pattern_location);
              }

              location = LocationUtils.format(location, schema.getJob_location_format_regex());
              if (schema.getJob_location_tokenize()) {
                  location = LocationUtils.tokenize(location);
              }
          }

          String date = "";
          if ( !schema.getL2_job_date().isEmpty() ) {
              String pattern_date = pattern_prefix + "." + schema.getL2_job_date();
              date = doc.read(pattern_date, String.class);
              date = DateUtils.formatDate(date, schema.getJob_date_format());
              if ( DateUtils.nDaysAgo(date, fetch_winthin_n_days_pages) ) {
                  if (LOG.isDebugEnabled()) {
                      LOG.debug("Job posted " + fetch_winthin_n_days_pages + " days ago, ignore");
                  }
                  continue;
              }
          } else {
              date = DateUtils.getCurrentDate();
          }

          String newurl_repr = "";
          if ( !schema.getL2_schema_for_joburl_repr().isEmpty() ) {
          /*HuaWei case, real job url should also be replaced basing on template with regex
          * Now we assume should be the same regex,
          * it is possible need to be changed later with another regex for other company
          * */
              String pattern_url_repr = pattern_prefix + "." + schema.getL2_schema_for_joburl_repr();
              newurl_repr = doc.read(pattern_url_repr, String.class);

              if ( !schema.getL2_template_for_joburl_repr().isEmpty() && !schema.getL2_joburl_regex().isEmpty() ) {
                  /* Till now didn't see repr url need to be replaced with regex yet,
                   * only hit this for Huawei/Json case, will consider this if necessary later
                   * In general this should not happen, because repr urls are extraced from HTML href here,
                   * it is unreasonable to have a webpage with href again to be replaced.
                   */
                  String newregex = generate_regex_for_nextitem(schema.getL2_joburl_regex(), newurl_repr);
                  newurl_repr = plUtil.substitute(newregex, schema.getL2_template_for_joburl_repr());
              }

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

          newPage.setScore(page.getScore()/jobs.size());

          newPage.getMetadata().put(CompanyUtils.company_cookie, page.getMetadata().get(CompanyUtils.company_cookie));

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
                  String l2_description = "";
                  if ( pattern_l2_description.startsWith("[") ) {
                      // list of fields, Alibaba
                      pattern_l2_description = pattern_prefix + pattern_l2_description;
                      Map<String, String> descs = doc.read(pattern_l2_description, Map.class);
                      l2_description = "";
                      for (String value : descs.values()) {
                          l2_description += value;
                          l2_description += "<BR/>";
                      }
                  } else {
                      // a single field, Shell
                      pattern_l2_description = pattern_prefix + "." + pattern_l2_description;
                      l2_description = doc.read(pattern_l2_description, String.class);
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

          if (debug_fetch_single_item) {
              LOG.debug(url + " debug_fetch_single_item enabled, stop further");
              break;
          }
      }

      return parse;
  }

  private Parse getParse_summary_json(String url, WebPage page, CompanySchema schema, DocumentContext doc)
      throws MalformedURLException, PathNotFoundException {

      String l3_title = "";
      /* l3 title is totally optional */
      if ( !schema.getL3_job_title().isEmpty() ) {
          l3_title = doc.read(schema.getL3_job_title(), String.class);
          l3_title.replaceAll("\\s+", " ");
          l3_title = SolrUtils.stripNonCharCodepoints(l3_title);
      }

      String l3_date = "";
      if ( !schema.getL3_job_date().isEmpty() ) {
          l3_date = doc.read(schema.getL3_job_date(), String.class);
          LOG.info(url + " Date: " + l3_date);
          l3_date = DateUtils.formatDate(l3_date, schema.getJob_date_format());
          /* Normally job date should be extracted from L2 page, but if configured which means use this */
          page.getMetadata().put(CompanyUtils.company_job_date, ByteBuffer.wrap(l3_date.getBytes()));
      }

      String l3_description = "";
      if ( !schema.getL3_job_description().isEmpty()) {
          Map<String, String> descs = doc.read(schema.getL3_job_description(), Map.class);
          for (String value : descs.values()) {
              l3_description += value.replaceAll("\n", "<BR/>");
              l3_description += "<BR/>";
          }
      }
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

  public void setConf(Configuration conf) {
    this.conf = conf;
    this.defaultCharEncoding = getConf().get(
        "parser.character.encoding.default", "windows-1252");
    this.repo = CompanySchemaRepository.getInstance(conf.get("company.schema.dir", "./"));
    this.debug_fetch_single_item = conf.getBoolean("debug.fetch.single.item", false);
    this.sig = SignatureFactory.getSignature(conf);
    this.try_to_shortcut_l3page = conf.getBoolean("try.to.shortcut.l3page", true);
    this.defaultInterval = conf.getInt("db.fetch.interval.default", 0);
    this.fetch_first_n_pages = conf.getInt("fetch.first.n.pages", 5);
    if ( this.fetch_first_n_pages < 0 ) this.fetch_first_n_pages = Integer.MAX_VALUE;
    this.debug_save_page_content = conf.getBoolean("debug.save.page.content", false);
    this.fetch_winthin_n_days_pages = conf.getInt("fetch.winthin.n.days.pages", 180);
    if ( this.fetch_first_n_pages < 0 ) this.fetch_first_n_pages = Integer.MAX_VALUE;
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
