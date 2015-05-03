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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.String;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.apache.nutch.companyschema.CompanySchema;
import org.apache.nutch.companyschema.CompanySchemaRepository;

import org.apache.nutch.indexer.solr.SolrUtils;

import java.util.Date;
import org.apache.solr.common.util.DateUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.nutch.crawl.CrawlStatus;
import org.apache.nutch.crawl.DbUpdaterJob;

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

  public Parse getParse(String url, WebPage page) {
      Parse parse = null;
      String name = CompanyUtils.getCompanyName(page);
      CompanySchema schema = repo.getCompanySchema(name);

      if (schema == null) {
          /* if return null, will try to reparse it again next time
           * if return an Empty, then ParseStatus will be FAILED, will try to reparse it again next time.
           * the best way is to Fake a SUCCESS code, won't take care of this uninterested page in future until the max interval comes.
           */
          LOG.warn(url.toString() + "company_key not found, setup PARSE_MARK, UPDATEDB_MARK, INDEX_MARK or just return null");
          return null;
      }

      LOG.info("url for company: " + name + "link type: " + CompanyUtils.getLinkType(page));
      if ( CompanyUtils.getLinkType(page).equals("") ) {
          /* set the entry link type, avoid some config footprint during inject url */
          CompanyUtils.setEntryLink(page);
      } else if ( !CompanyUtils.isEntryLink(page) && !CompanyUtils.isListLink(page) && !CompanyUtils.isSummaryLink(page)) {
          LOG.warn(url.toString() + " invalid link type" + CompanyUtils.getLinkType(page));
          return null;
      }

    String baseUrl = TableUtil.toString(page.getBaseUrl());
    URL base;
    try {
      base = new URL(baseUrl);
    } catch (MalformedURLException e) {
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
        LOG.trace("Parsing...");
      }

      /* parsing with NEKO DOM parser */
      DOMParser parser = new DOMParser();
      try {
          parser.setFeature("http://cyberneko.org/html/features/scanner/allow-selfclosing-iframe", true);
          parser.setFeature("http://cyberneko.org/html/features/augmentations", true);
          parser.setProperty("http://cyberneko.org/html/properties/default-encoding", defaultCharEncoding);
          parser.setFeature("http://cyberneko.org/html/features/scanner/ignore-specified-charset", true);
          parser.setFeature("http://cyberneko.org/html/features/balance-tags/ignore-outside-content", false);
          parser.setFeature("http://cyberneko.org/html/features/balance-tags/document-fragment", true);
          parser.setFeature("http://cyberneko.org/html/features/report-errors", LOG.isTraceEnabled());
      } catch (SAXException e) {
      }
      parser.parse(input);

      /* Create xpath */
      XPathFactory xpathFactory = XPathFactory.newInstance();
      XPath xpath = xpathFactory.newXPath();

      /* Setup xpath's namespace */
      Document doc = parser.getDocument();
      DocumentType doctype = doc.getDoctype();
      if ( doctype != null && doctype.getName().equalsIgnoreCase("html") ) {
          NodeList root = doc.getElementsByTagName("HTML");
          if (root != null) {
              Element head = (Element) root.item(0);
              final String ns = head.getNamespaceURI();
              LOG.info("namespace: " + ns);
              HashMap<String, String> prefMap = new HashMap<String, String>() {{
                  put("main", ns);
              }};
              SimpleNamespaceContext namespaces = new SimpleNamespaceContext(prefMap);
              xpath.setNamespaceContext(namespaces);
          }
      }

      /* main logic to get expected info with xpath */
      if ( CompanyUtils.isEntryLink(page)) {
          parse = getParse_entry(url, page, schema, doc, xpath);
          parse = getParse_list(parse, url, page, schema, doc, xpath);
      } else if ( CompanyUtils.isListLink(page)) {
          parse = getParse_list(null, url, page, schema, doc, xpath);
      } else if ( CompanyUtils.isSummaryLink(page)) {
          parse = getParse_summary(url, page, schema, doc, xpath);
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
    } catch (SAXException e) {
      LOG.error("Failed with the following SAXException: ", e);
      return ParseStatusUtils.getEmptyParse(e, getConf());
    }  catch (XPathExpressionException e) {
      LOG.error("Failed to parse with schema")  ;
      return ParseStatusUtils.getEmptyParse(e, getConf());
    } catch (Exception e) {
      LOG.error("Failed with the following Exception: ", e);
      return ParseStatusUtils.getEmptyParse(e, getConf());
    }
    return parse;
  }
  private Parse getParse_entry(String url, WebPage page, CompanySchema schema, Document doc, XPath xpath)
      throws XPathExpressionException, MalformedURLException {
      /* Page List URL */
      XPathExpression expr = xpath.compile(schema.page_list_schema());
      String page_list = (String) expr.evaluate(doc, XPathConstants.STRING);
      LOG.info("page_list schema: " + schema.page_list_schema() + " Got url: " + page_list);

      URL target;
      try {
          target = new URL(page_list);
      } catch (MalformedURLException e) {
          URL orig;
          try {
              orig = new URL(url);
          } catch (MalformedURLException e2) {
              orig = new URL(schema.url());
          }
          target = new URL(orig, page_list);
      }
      String page_list_url = target.toString();

      /* Last Page Number */
      expr = xpath.compile(schema.page_list_last());
      String page_last = (String) expr.evaluate(doc, XPathConstants.STRING);
      LOG.info("page_list Got last page: " + page_last);
      int last = 0;
      try {
          last = Integer.parseInt(page_last);
      } catch (NumberFormatException e) {
          LOG.error("failed to parse page increment or page last");
          return null;
      }

      /* Page number Pattern */
      String patternValue = schema.page_list_pattern();
      patternValue = "(" + patternValue + "=)(\\d*)";
      Pattern pattern = null;
      try {
          pattern = Pattern.compile(patternValue);
      } catch (PatternSyntaxException e) {
          LOG.warn("Failed to compile pattern: " + patternValue + " : " + e);
          return null;
      }

      /* Page Interval */
      int incr = 0;
      try {
          incr = Integer.parseInt(schema.page_list_increment());
      } catch (NumberFormatException e) {
          LOG.error("failed to parse page increment or page last");
          return null;
      }

      ParseStatus status = ParseStatus.newBuilder().build();
      status.setMajorCode((int) ParseStatusCodes.SUCCESS);
      Parse parse = new Parse("page list", "page list", new Outlink[0], status);

      Matcher matcher = pattern.matcher(page_list_url);
      if ( matcher.find() ) {
          int start = Integer.parseInt(matcher.group(2));
          String prefix = matcher.group(1);
          for (int i = start; i <= last; i += incr ) {
              String subsitute = prefix + Integer.toString(i);
              String newurl = matcher.replaceAll(subsitute);

              WebPage  newPage = WebPage.newBuilder().build();
              newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
              CompanyUtils.setCompanyName(newPage, CompanyUtils.getCompanyName(page));
              CompanyUtils.setListLink(newPage);
              newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

              parse.addPage(newurl, newPage);
              if ( runtime_debug ) break;
          }
      } else {
          LOG.error("failed to find page list pattern");
      }

      return parse;
  }
  private Parse getParse_list(Parse parse, String url, WebPage page, CompanySchema schema, Document doc, XPath xpath)
      throws XPathExpressionException, MalformedURLException {
      XPathExpression expr = xpath.compile(schema.job_list_schema());
      NodeList rows = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

      if ( rows == null  || rows.getLength() == 0 ) {
          LOG.info(" no jobs in url : " + url);
      } else {
          LOG.info("Found " + rows.getLength() + "jobs");
          if ( parse == null ) {
              ParseStatus status = ParseStatus.newBuilder().build();
              status.setMajorCode((int) ParseStatusCodes.SUCCESS);
              parse = new Parse("job list", "job list", new Outlink[0], status);
          }
          for ( int i = 0; i < rows.getLength(); i++ ) {
              Element row = (Element)rows.item(i);
              expr = xpath.compile(schema.job_link());
              String link = (String)((String) expr.evaluate(row, XPathConstants.STRING)).trim();
              LOG.info("link:" + link);

              URL target;
              try {
                  target = new URL(link);
              } catch (MalformedURLException e) {
                  URL orig;
                  try {
                      orig = new URL(url);
                  } catch (MalformedURLException e2) {
                      orig = new URL(schema.url());
                  }
                  target = new URL(orig, link);
              }

              expr = xpath.compile(schema.job_title());
              String title = (String)((String) expr.evaluate(row, XPathConstants.STRING)).trim();
              title.replaceAll("\\s+", " ");
              /* here need to strip off the invalid char for ibm site */
              title = SolrUtils.stripNonCharCodepoints(title);

              expr = xpath.compile(schema.job_location());
              String location = (String)((String) expr.evaluate(row, XPathConstants.STRING)).trim();
              location = SolrUtils.stripNonCharCodepoints(location);

              expr = xpath.compile(schema.job_date());
              String date = (String)((String) expr.evaluate(row, XPathConstants.STRING)).trim();
              //date = SolrUtils.stripNonCharCodepoints(date);
              try {
                  /* we need use facet.fields on job post date, which need specific date format
                   * And different company might use different format, need to unify them.
                   * Fortunately there is already common function in DateUtil, hope can cope with all.
                   * If not, we might extend the schema to add our own format.
                   */
                  Date d = DateUtil.parseDate(date);
                  date = DateUtil.getThreadLocalDateFormat().format(d);
              } catch (java.text.ParseException pe) {
                  LOG.warn(schema.name() + " invalid date format(need extend our schema): " + date);
                  /* let it continue with current system time */
                  date = DateUtil.getThreadLocalDateFormat().format(new Date());
              }

              WebPage  newPage = WebPage.newBuilder().build();
              newPage.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
              CompanyUtils.setCompanyName(newPage, CompanyUtils.getCompanyName(page));
              CompanyUtils.setSummaryLink(newPage);

              newPage.getMetadata().put(CompanyUtils.company_job_title, ByteBuffer.wrap(title.getBytes()));
              newPage.getMetadata().put(CompanyUtils.company_job_location, ByteBuffer.wrap(location.getBytes()));
              newPage.getMetadata().put(CompanyUtils.company_job_date, ByteBuffer.wrap(date.getBytes()));

              newPage.getMarkers().put(DbUpdaterJob.DISTANCE, new Utf8(Integer.toString(0)));

              parse.addPage(target.toString(), newPage);

              if ( runtime_debug ) break;
          }
      }
      return parse;
  }
  private Parse getParse_summary(String url, WebPage page, CompanySchema schema, Document doc, XPath xpath)
      throws XPathExpressionException, MalformedURLException {
      XPathExpression expr = xpath.compile(schema.job_abstract());
      String title = (String) expr.evaluate(doc, XPathConstants.STRING);
      title.replaceAll("\\s+", " ");
      title = SolrUtils.stripNonCharCodepoints(title);

      expr = xpath.compile(schema.job_description());
      String text = "";
      NodeList nodes = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);
      for ( int i = 0; i < nodes.getLength(); i++ ) {
          Node node = nodes.item(i);
          text += DOM2HTML.toString(node);
      }
      text = SolrUtils.stripNonCharCodepoints(text);

      LOG.info("Title: " + title + " Description: " + text);
      /* something to be done here,
       * we can select don't configure abstract & description in schema file,
       * then fallback to the default html parser implementation, html doc title and full page text.
       */
      ParseStatus status = ParseStatus.newBuilder().build();
      status.setMajorCode((int) ParseStatusCodes.SUCCESS);
      Parse parse = new Parse(text, title, new Outlink[0], status);
      return parse;
  }

  public void setConf(Configuration conf) {
    this.conf = conf;
    this.defaultCharEncoding = getConf().get(
        "parser.character.encoding.default", "windows-1252");
    this.repo = CompanySchemaRepository.getInstance(conf);
    this.runtime_debug = conf.getBoolean("runtime.debug", false);
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
