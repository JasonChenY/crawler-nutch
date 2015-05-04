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
//import org.apache.hadoop.conf.Configuration;
//import org.apache.nutch.util.NutchConfiguration;

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

public class CompanySchemaRepository {

  private boolean auto;

  private HashMap<String, CompanySchema> companies;

  public static final Logger LOG = LoggerFactory.getLogger(CompanySchemaRepository.class);

  private JSONParser parser;

  private String schemaDir;

  private static CompanySchemaRepository _instance;
  public static CompanySchemaRepository getInstance(String dir) throws RuntimeException {
      if ( _instance == null ) {
          _instance = new CompanySchemaRepository(dir);
      }
      return _instance;
  }
  private CompanySchemaRepository(String dir) throws RuntimeException {
    companies = new HashMap<String, CompanySchema>();
    //this.auto = conf.getBoolean("company.schema.auto-load", false);
    schemaDir = dir;
    parser = new JSONParser();
  }

  public CompanySchema getCompanySchema(String name) {
    if (companies.containsKey(name))
        return companies.get(name);

    CompanySchema companySchema = parseCompanySchema(name);
    if ( companySchema != null ) {
        companies.put(name, companySchema);
    }
    return companySchema;
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
          companySchema.url((String) json.get("url"));
          companySchema.method((String)json.get("method"));
          companySchema.data((String)json.get("data"));

          JSONObject page_list = (JSONObject)json.get("page_list");
          companySchema.page_list_schema((String)page_list.get("schema"));
          companySchema.page_list_pattern((String) page_list.get("pattern"));
          companySchema.page_list_increment((String)page_list.get("increment"));
          companySchema.page_list_last((String)page_list.get("last"));

          JSONObject job_list = (JSONObject)json.get("job_list");
          companySchema.job_list_schema((String) job_list.get("schema"));
          companySchema.job_link((String)job_list.get("job_link"));
          companySchema.job_title((String)job_list.get("job_title"));
          companySchema.job_location((String) job_list.get("job_location"));
          companySchema.job_date((String)job_list.get("job_date"));

          JSONObject job = (JSONObject)json.get("job");
          companySchema.job_abstract((String)job.get("job_abstract"));
          companySchema.job_description((String) job.get("job_description"));

          return companySchema;
      } catch (Exception e) {
          LOG.warn("JSON file parse failed: " + name);
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
        LOG.info("\t" + company.name());
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
