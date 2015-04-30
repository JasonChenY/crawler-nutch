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

package org.apache.nutch.indexer.company;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.indexer.IndexingException;
import org.apache.nutch.indexer.IndexingFilter;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.util.Bytes;
import org.apache.nutch.util.TableUtil;
import org.apache.solr.common.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;

import org.apache.nutch.companyschema.CompanyUtils;

public class CompanyIndexingFilter implements IndexingFilter {
  public static final Logger LOG = LoggerFactory.getLogger(CompanyIndexingFilter.class);

  private int MAX_TITLE_LENGTH;
  private Configuration conf;

  private static final Collection<WebPage.Field> FIELDS = new HashSet<WebPage.Field>();

  static {
    FIELDS.add(WebPage.Field.TITLE);
    FIELDS.add(WebPage.Field.TEXT);
    FIELDS.add(WebPage.Field.FETCH_TIME);
    FIELDS.add(WebPage.Field.METADATA);
  }

  public NutchDocument filter(NutchDocument doc, String url, WebPage page)
      throws IndexingException {

    if (CompanyUtils.isEntryLink(page) || CompanyUtils.isListLink(page)) {
        LOG.info(url + "don't need to be index, type: " + (CompanyUtils.isEntryLink(page)?"ENTRY":"LIST"));
        return null;
    }

    String reprUrl = null;
    // if (page.isReadable(WebPage.Field.REPR_URL.getIndex())) {
    reprUrl = TableUtil.toString(page.getReprUrl());
    // }

    String host = null;
    try {
      URL u;
      if (reprUrl != null) {
        u = new URL(reprUrl);
      } else {
        u = new URL(url);
      }
      host = u.getHost();
    } catch (MalformedURLException e) {
      throw new IndexingException(e);
    }

    if (host != null) {
      // add host as un-stored, indexed and tokenized
      // Shall I use host for Company Name?
      doc.add("host", host);
    }

    // url is both stored and indexed, so it's both searchable and returned
    doc.add("url", reprUrl == null ? url : reprUrl);

    // content(Job Description) is indexed, so that it's searchable, but not stored in index
    doc.add("content", TableUtil.toString(page.getText()));

    // title(Job Abstract)
    String title = TableUtil.toString(page.getTitle());
    if (MAX_TITLE_LENGTH > -1 && title.length() > MAX_TITLE_LENGTH) { // truncate
                                                                      // title
                                                                      // if
                                                                      // needed
      title = title.substring(0, MAX_TITLE_LENGTH);
    }
    if (title.length() > 0) {
      // NUTCH-1004 Do not index empty values for title field
      doc.add("title", title);
    }

    /* Other Job information
     * Note here job_title is mostly same as the Page Title
     * Job title is extracted from Job List Page
     * Page Title is extracted from Job Summary Page
     * Leave it here for future expansion, alike area type.
     **/
    String job_company = Bytes.toString(page.getMetadata().get(CompanyUtils.company_key));
    String job_title = Bytes.toString(page.getMetadata().get(CompanyUtils.company_job_title));
    String job_location = Bytes.toString(page.getMetadata().get(CompanyUtils.company_job_location));
    String job_date = Bytes.toString(page.getMetadata().get(CompanyUtils.company_job_date));
    doc.add("job_company", job_company);
    doc.add("job_title", job_title);
    doc.add("job_location", job_location);
    doc.add("job_date", job_date);

    // add cached content/summary display policy, if available
    ByteBuffer cachingRaw = page.getMetadata().get(
        Nutch.CACHING_FORBIDDEN_KEY_UTF8);
    String caching = Bytes.toString(cachingRaw);
    if (caching != null && !caching.equals(Nutch.CACHING_FORBIDDEN_NONE)) {
      doc.add("cache", caching);
    }

    // add timestamp when fetched, for deduplication
    String tstamp = DateUtil.getThreadLocalDateFormat().format(
        new Date(page.getFetchTime()));
    doc.add("tstamp", tstamp);

    return doc;
  }

  public void addIndexBackendOptions(Configuration conf) {
  }

  /**
   * Set the {@link Configuration} object
   */
  public void setConf(Configuration conf) {
    this.conf = conf;
    this.MAX_TITLE_LENGTH = conf.getInt("indexer.max.title.length", 100);
    LOG.info("Maximum title length for indexing set to: "
        + this.MAX_TITLE_LENGTH);
  }

  /**
   * Get the {@link Configuration} object
   */
  public Configuration getConf() {
    return this.conf;
  }

  /**
   * Gets all the fields for a given {@link WebPage} Many datastores need to
   * setup the mapreduce job by specifying the fields needed. All extensions
   * that work on WebPage are able to specify what fields they need.
   */
  @Override
  public Collection<WebPage.Field> getFields() {
    return FIELDS;
  }

}
