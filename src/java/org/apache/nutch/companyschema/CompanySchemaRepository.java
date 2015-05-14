/*
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
package org.apache.nutch.companyschema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
public class CompanySchemaRepository {

  private boolean auto;

  private HashMap<String, CompanySchema> companies = new HashMap<String, CompanySchema>();;

  public static final Logger LOG = LoggerFactory.getLogger(CompanySchemaRepository.class);

  private JSONParser parser = new JSONParser();;

  private String schemaDir;
  /* Note the repository and HashMap itself is not multithread safe,
   * but don't care about it, we won't remove any entries
   * the lock condition is just added on schema file
   */
  private final Lock lock = new ReentrantLock();

  private static CompanySchemaRepository _instance;
  public static CompanySchemaRepository getInstance(String dir) throws RuntimeException {
      if ( _instance == null ) {
          _instance = new CompanySchemaRepository(dir);
      }
      return _instance;
  }
  private CompanySchemaRepository(String dir) throws RuntimeException {
    //this.auto = conf.getBoolean("company.schema.auto-load", false);
    schemaDir = dir;
  }

  public CompanySchema getCompanySchema(String name) {
    /* remove this? shortcum is that other thread will wait for A schema
     * while another thread is parsing  B schema
     * Doesn't matter acutally because we won't have millions schema to be parsed, usually will be quick.
     */
    if (companies.containsKey(name))
        return companies.get(name);

    try {
        lock.lock();

        /* check again whether other guy already parsed it for me */
        if (companies.containsKey(name))
            return companies.get(name);

        CompanySchema companySchema = parseCompanySchema(name);
        if (companySchema != null) {
            companies.put(name, companySchema);
        }

        return companySchema;
    } finally {
        lock.unlock();
    }
  }

  private CompanySchema parseCompanySchema(String name) {
      FileReader fr = null;
      try {
          fr = new FileReader(schemaDir + "/" + name + ".json");
      } catch (Exception e ) {
          LOG.warn("File" + schemaDir + name + ".json not found");
          return null;
      };

      parser.reset();
      try {
          JSONObject json = (JSONObject) parser.parse(fr);
          fr.close();

          if(!json.containsKey("company") || !name.equals((String)json.get("company")) ) {
              LOG.warn("JSON file corruption or malformed: " + name);
              return null;
          }

          CompanySchema companySchema = new CompanySchema(name);
          companySchema.setCookie((String) json.get("cookie"));
          companySchema.setL1_url((String) json.get("l1_url"));
          companySchema.setL1_method((String) json.get("l1_method"));
          companySchema.setL1_postdata((String) json.get("l1_postdata"));

          JSONObject page_list = (JSONObject)json.get("page_list");
          if ( page_list != null ) {
              companySchema.setL2_content_type((String) page_list.get("l2_content-type"));
              companySchema.setL2_template_for_nextpage_url((String) page_list.get("l2_template_for_nextpage_url"));
              companySchema.setL2_schema_for_nextpage_url((String) page_list.get("l2_schema_for_nextpage_url"));
              companySchema.setL2_nextpage_method((String) page_list.get("l2_nextpage_method"));
              companySchema.setL2_template_for_nextpage_postdata((String) page_list.get("l2_template_for_nextpage_postdata"));
              companySchema.setL2_nextpage_regex((String) page_list.get("l2_nextpage_regex"));
              companySchema.setL2_nextpage_increment((String) page_list.get("l2_nextpage_increment"));
              companySchema.setL2_last_page((String) page_list.get("l2_last_page"));

              companySchema.setL2_nextpage_postdata_inherit_regex((String) page_list.get("l2_nextpage_postdata_inherit_regex"));
              companySchema.setL2_nextpage_endflag((String) page_list.get("l2_nextpage_endflag"));
          }

          JSONObject job_list = (JSONObject)json.get("job_list");
          if ( job_list != null ) {
              companySchema.setL2_schema_for_jobs((String) job_list.get("l2_schema_for_jobs"));
              companySchema.setL2_template_for_joburl((String) job_list.get("l2_template_for_joburl"));
              companySchema.setL2_joburl_regex((String) job_list.get("l2_joburl_regex"));
              companySchema.setL2_schema_for_joburl((String) job_list.get("l2_schema_for_joburl"));
              companySchema.setL2_schema_for_joburl_repr((String) job_list.get("l2_schema_for_joburl_repr"));
              companySchema.setL2_job_title((String) job_list.get("l2_job_title"));
              companySchema.setL2_job_location((String) job_list.get("l2_job_location"));
              companySchema.setL2_job_date((String) job_list.get("l2_job_date"));
              companySchema.setL2_job_description((String) job_list.get("l2_job_description"));
          }

          JSONObject job = (JSONObject)json.get("job");
          if ( job != null ) {
              companySchema.setL3_job_title((String) job.get("l3_job_title"));
              companySchema.setL3_job_date((String) job.get("l3_job_date"));
              companySchema.setL3_job_date_format((String) job.get("l3_job_date_format"));
              companySchema.setL3_job_description((String) job.get("l3_job_description"));
          }

          return companySchema;
      } catch (Exception e) {
          LOG.warn("JSON file parse failed: " + name);
          e.printStackTrace();
          //fr.close(); if parsing failed, fr will be collected by GC
          return null;
      }
  }

  private void displayStatus() {
    LOG.info("Company Schema parsing mode: [" + this.auto + "]");
    LOG.info("Parsed Company Schema:");

    if ((companies == null) || (companies.size() == 0)) {
      LOG.info("\tNONE");
    } else {
      for (CompanySchema company : companies.values()) {
        LOG.info("\t" + company.getName());
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: CompanySchemaRepository name");
      return;
    }
    CompanySchemaRepository repo = CompanySchemaRepository.getInstance("/sdk/tools/apache-nutch-2.3/localrepo/schemas");
    CompanySchema d = repo.getCompanySchema(args[0]);
    if (d == null) {
      System.err.println("Company Schema '" + args[0] + "' not present");
      return;
    }
  }
}
