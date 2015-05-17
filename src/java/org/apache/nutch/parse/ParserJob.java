/*******************************************************************************
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
 ******************************************************************************/
package org.apache.nutch.parse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import org.apache.avro.util.Utf8;
import org.apache.gora.filter.MapFieldValueFilter;
import org.apache.nutch.companyschema.CompanyUtils;
import org.apache.nutch.companyschema.CompanySchema;
import org.apache.nutch.crawl.*;
import org.apache.nutch.protocol.ProtocolStatusCodes;
import org.apache.nutch.scoring.ScoreDatum;
import org.apache.nutch.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.metadata.HttpHeaders;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.storage.Mark;
import org.apache.nutch.storage.ParseStatus;
import org.apache.nutch.storage.StorageUtils;
import org.apache.nutch.storage.WebPage;
import org.apache.gora.filter.FilterOp;
import org.apache.gora.filter.SingleFieldValueFilter;
import org.apache.gora.mapreduce.GoraMapper;

public class ParserJob extends NutchTool implements Tool {

  public static final Logger LOG = LoggerFactory.getLogger(ParserJob.class);

  private static final String RESUME_KEY = "parse.job.resume";
  private static final String FORCE_KEY = "parse.job.force";

  public static final String SKIP_TRUNCATED = "parser.skip.truncated";

  private static final Utf8 REPARSE = new Utf8("-reparse");

  private static final Collection<WebPage.Field> FIELDS = new HashSet<WebPage.Field>();

  private Configuration conf;

  static {
    FIELDS.add(WebPage.Field.STATUS);
    FIELDS.add(WebPage.Field.CONTENT);
    FIELDS.add(WebPage.Field.CONTENT_TYPE);
    FIELDS.add(WebPage.Field.SIGNATURE);
    FIELDS.add(WebPage.Field.MARKERS);
    FIELDS.add(WebPage.Field.PARSE_STATUS);
    FIELDS.add(WebPage.Field.OUTLINKS);
    FIELDS.add(WebPage.Field.METADATA);
    FIELDS.add(WebPage.Field.HEADERS);
    FIELDS.add(WebPage.Field.SCORE);
  }

  public static class ParserMapper extends
      GoraMapper<String, WebPage, String, WebPage> {
    private ParseUtil parseUtil;

    private boolean shouldResume;

    private boolean force;

    private Utf8 batchId;

    private boolean skipTruncated;

    private FetchSchedule schedule;

    private boolean removeContent;

    @Override
    public void setup(Context context) throws IOException {
      Configuration conf = context.getConfiguration();
      parseUtil = new ParseUtil(conf);
      shouldResume = conf.getBoolean(RESUME_KEY, false);
      force = conf.getBoolean(FORCE_KEY, false);
      batchId = new Utf8(
          conf.get(GeneratorJob.BATCH_ID, Nutch.ALL_BATCH_ID_STR));
      skipTruncated = conf.getBoolean(SKIP_TRUNCATED, true);
      schedule = FetchScheduleFactory.getFetchSchedule(conf);
      removeContent = conf.getBoolean("parser.remove.content", true);
    }

    @Override
    public void map(String key, WebPage page, Context context)
        throws IOException, InterruptedException {
      String unreverseKey = TableUtil.unreverseUrl(key);
      if (batchId.equals(REPARSE)) {
        LOG.debug("Reparsing " + unreverseKey);
      } else {
        if (Mark.FETCH_MARK.checkMark(page) == null) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Skipping " + TableUtil.unreverseUrl(key)
                + "; not fetched yet");
          }
          return;
        }
        if (shouldResume && Mark.PARSE_MARK.checkMark(page) != null) {
          if (force) {
            LOG.info("Forced parsing " + unreverseKey + "; already parsed");
          } else {
            LOG.info("Skipping " + unreverseKey + "; already parsed");
            return;
          }
        } else {
          LOG.info("Parsing " + unreverseKey);
        }
      }

      if (skipTruncated && isTruncated(unreverseKey, page)) {
          if ( CompanyUtils.isEntryLink(page) || CompanyUtils.isListLink(page) ) {
              //if ( page.getProtocolStatus().getCode() == ProtocolStatusCodes.EXCEPTION ) {
                  LOG.warn(unreverseKey + " ENTRY or LIST page previous fetch failed, refetch soon");
                  page.setStatus((int) CrawlStatus.STATUS_UNFETCHED);
                  schedule.initializeSchedule(unreverseKey, page);
                  context.write(key, page);
              //}
          }
        return;
      }

      Parse parse = parseUtil.process(key, page);

      ParseStatus pstatus = page.getParseStatus();
      if (pstatus != null) {
          context.getCounter("ParserStatus",
                  ParseStatusCodes.majorCodes[pstatus.getMajorCode()]).increment(1);
      }

      if ( parse != null ) {
          GenerateWebPage(page, parse, context);
      }

      if (removeContent) {
         /* we have get all the useful information, can remove the page to save disk space */
         page.setContent(ByteBuffer.wrap(new byte[0]));
      }

      context.write(key, page);
    }

    /* Private Function to generate WebPage into Context directly basing on information from Parse
     * So the main functionality is done insiding parse plugin.
     */
      private void GenerateWebPage(WebPage page, Parse parse, Context context) {
          if ( CompanyUtils.getCompanyName(page).equals("")) {
              LOG.warn("no company key url enter ParseJob");
              return;
          }
          Map<String, WebPage> newPages = parse.getPages();
          if ( newPages == null || newPages.size() == 0 ) {
              return;
          }
          LOG.info("GenerateWebPage for company: " + CompanyUtils.getCompanyName(page));

          for (Map.Entry<String, WebPage> e : newPages.entrySet()) {
              String newurl = e.getKey();
              WebPage newPage = e.getValue();
              if ( newPage.getPrevFetchTime() != 0 ) {
                  /* Just a flag to make sure that this page won't be fetched before his parent
                   * we are aiming for a faked Fetched/Parsed WebPage.
                   *
                   * if prevFetchTime is set to NOW, which means nearly don't hope to fetch this page,
                   *     this is used for the case of Alibaba's summary page, we already have enough info for indexing
                   *     so the newPage is only used for triggering indexing.
                   * if prevFetchTime is set to NOW-20min, which means we hopen this page to be fetched 20min later
                   *     this is used for Microsoft case, seems fetching summary page will disturb the level 2 Next page,
                   *     maybe server will do some VIEWSTATE/EVENTVALIDATION.
                   */

                  //long cur = System.currentTimeMillis();
                  long prevFetchTime = newPage.getPrevFetchTime();
                  schedule.initializeSchedule(newurl, newPage);
                  schedule.setFetchSchedule(newurl, newPage, 0, 0, prevFetchTime, prevFetchTime, 0);
              } else {
                  schedule.initializeSchedule(newurl, newPage);
              }
              try {
                  String newreverseurl = TableUtil.reverseUrl(newurl);
                  context.write(newreverseurl, newPage);
                  LOG.info("Generating: " + newurl);
              } catch (Exception ee) {
                  LOG.warn("Failed to write new page to db" + newurl);
              }
          }
      }
  }

  public ParserJob() {

  }

  public ParserJob(Configuration conf) {
    setConf(conf);
  }

  /**
   * Checks if the page's content is truncated.
   * 
   * @param url
   * @param page
   * @return If the page is truncated <code>true</code>. When it is not, or when
   *         it could be determined, <code>false</code>.
   */
  public static boolean isTruncated(String url, WebPage page) {
    ByteBuffer content = page.getContent();
    if (content == null) {
      return false;
    }
    CharSequence lengthUtf8 = page.getHeaders().get(
        new Utf8(HttpHeaders.CONTENT_LENGTH));
    if (lengthUtf8 == null) {
      return false;
    }
    String lengthStr = lengthUtf8.toString().trim();
    if (StringUtil.isEmpty(lengthStr)) {
      return false;
    }
    int inHeaderSize;
    try {
      inHeaderSize = Integer.parseInt(lengthStr);
    } catch (NumberFormatException e) {
      LOG.warn("Wrong contentlength format for " + url, e);
      return false;
    }
    int actualSize = content.limit();
    if (inHeaderSize > actualSize) {
      LOG.warn(url + " skipped. Content of size " + inHeaderSize
          + " was truncated to " + actualSize);
      return true;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug(url + " actualSize=" + actualSize + " inHeaderSize="
          + inHeaderSize);
    }
    return false;
  }

  public Collection<WebPage.Field> getFields(Job job) {
    Configuration conf = job.getConfiguration();
    Collection<WebPage.Field> fields = new HashSet<WebPage.Field>(FIELDS);
    ParserFactory parserFactory = new ParserFactory(conf);
    ParseFilters parseFilters = new ParseFilters(conf);

    Collection<WebPage.Field> parsePluginFields = parserFactory.getFields();
    Collection<WebPage.Field> signaturePluginFields = SignatureFactory
        .getFields(conf);
    Collection<WebPage.Field> htmlParsePluginFields = parseFilters.getFields();

    if (parsePluginFields != null) {
      fields.addAll(parsePluginFields);
    }
    if (signaturePluginFields != null) {
      fields.addAll(signaturePluginFields);
    }
    if (htmlParsePluginFields != null) {
      fields.addAll(htmlParsePluginFields);
    }

    return fields;
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  @Override
  public Map<String, Object> run(Map<String, Object> args) throws Exception {
    String batchId = (String) args.get(Nutch.ARG_BATCH);
    Boolean shouldResume = (Boolean) args.get(Nutch.ARG_RESUME);
    Boolean force = (Boolean) args.get(Nutch.ARG_FORCE);

    if (batchId != null) {
      getConf().set(GeneratorJob.BATCH_ID, batchId);
    }
    if (shouldResume != null) {
      getConf().setBoolean(RESUME_KEY, shouldResume);
    }
    if (force != null) {
      getConf().setBoolean(FORCE_KEY, force);
    }
    LOG.info("ParserJob: resuming:\t" + getConf().getBoolean(RESUME_KEY, false));
    LOG.info("ParserJob: forced reparse:\t"
        + getConf().getBoolean(FORCE_KEY, false));
    if (batchId == null || batchId.equals(Nutch.ALL_BATCH_ID_STR)) {
      LOG.info("ParserJob: parsing all");
    } else {
      LOG.info("ParserJob: batchId:\t" + batchId);
    }
    currentJob = new NutchJob(getConf(), "parse");

    Collection<WebPage.Field> fields = getFields(currentJob);
    MapFieldValueFilter<String, WebPage> batchIdFilter = getBatchIdFilter(batchId);
    StorageUtils.initMapperJob(currentJob, fields, String.class, WebPage.class,
        ParserMapper.class, batchIdFilter);
    StorageUtils.initReducerJob(currentJob, IdentityPageReducer.class);
    currentJob.setNumReduceTasks(0);

    currentJob.waitForCompletion(true);
    ToolUtil.recordJobStatus(null, currentJob, results);
    return results;
  }

  private MapFieldValueFilter<String, WebPage> getBatchIdFilter(String batchId) {
    if (batchId.equals(REPARSE.toString())
        || batchId.equals(Nutch.ALL_CRAWL_ID.toString())) {
      return null;
    }
    MapFieldValueFilter<String, WebPage> filter = new MapFieldValueFilter<String, WebPage>();
    filter.setFieldName(WebPage.Field.MARKERS.toString());
    filter.setFilterOp(FilterOp.EQUALS);
    filter.setFilterIfMissing(true);
    filter.setMapKey(Mark.FETCH_MARK.getName());
    filter.getOperands().add(new Utf8(batchId));
    return filter;
  }

  public int parse(String batchId, boolean shouldResume, boolean force)
      throws Exception {

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    long start = System.currentTimeMillis();
    LOG.info("ParserJob: starting at " + sdf.format(start));

    run(ToolUtil.toArgMap(Nutch.ARG_BATCH, batchId, Nutch.ARG_RESUME,
        shouldResume, Nutch.ARG_FORCE, force));
    LOG.info("ParserJob: success");

    long finish = System.currentTimeMillis();
    LOG.info("ParserJob: finished at " + sdf.format(finish)
        + ", time elapsed: " + TimingUtil.elapsedTime(start, finish));
    return 0;
  }

  public int run(String[] args) throws Exception {
    boolean shouldResume = false;
    boolean force = false;
    String batchId = null;

    if (args.length < 1) {
      System.err
          .println("Usage: ParserJob (<batchId> | -all) [-crawlId <id>] [-resume] [-force]");
      System.err
          .println("    <batchId>     - symbolic batch ID created by Generator");
      System.err
          .println("    -crawlId <id> - the id to prefix the schemas to operate on, \n \t \t    (default: storage.crawl.id)");
      System.err
          .println("    -all          - consider pages from all crawl jobs");
      System.err
          .println("    -resume       - resume a previous incomplete job");
      System.err
          .println("    -force        - force re-parsing even if a page is already parsed");
      return -1;
    }
    for (int i = 0; i < args.length; i++) {
      if ("-resume".equals(args[i])) {
        shouldResume = true;
      } else if ("-force".equals(args[i])) {
        force = true;
      } else if ("-crawlId".equals(args[i])) {
        getConf().set(Nutch.CRAWL_ID_KEY, args[++i]);
      } else if ("-all".equals(args[i])) {
        batchId = args[i];
      } else {
        if (batchId != null) {
          System.err.println("BatchId already set to '" + batchId + "'!");
          return -1;
        }
        batchId = args[i];
      }
    }
    if (batchId == null) {
      System.err.println("BatchId not set (or -all/-reparse not specified)!");
      return -1;
    }
    return parse(batchId, shouldResume, force);
  }

  public static void main(String[] args) throws Exception {
    final int res = ToolRunner.run(NutchConfiguration.create(),
        new ParserJob(), args);
    System.exit(res);
  }

}
