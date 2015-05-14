package org.apache.nutch.companyschema;

import org.apache.avro.util.Utf8;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.util.Bytes;
import java.nio.ByteBuffer;

public class CompanyUtils {

    public static Utf8 company_key = new Utf8("__company__");
    public static Utf8 company_dyn_data = new Utf8("__company_dyn_data__");
    public static Utf8 company_link_type = new Utf8("__company_link_type__");

    /* following 3 items plus dyn_data are used for passing info between parser plugin and parserJob,
     *  ParserJob will generate new WebPage basing on these information.
     *  When that's done, ParserJob need to remove these metadata from old WebPage.
     */
    public static Utf8 company_page_list_url = new Utf8("__company_page_list_url__");
    public static Utf8 company_page_list_incr = new Utf8("__company_page_list_incr__");
    public static Utf8 company_page_list_last = new Utf8("__company_page_list_last__");
    public static Utf8 company_page_list_pattern = new Utf8("__company_page_list_pattern__");

    /* urlsuffix will be added to the generated urls,
    * and FetcherJob need to strip it off before send the HTTP request
    * new way: setup the whole requestURI as webpage url
    public static String company_page_list_urlsuffix = "/__suffix__";
    * */

    /* Following 3 items plus the company_key will be used for finally indexing */
    public static Utf8 company_job_title = new Utf8("__company_job_title__");
    public static Utf8 company_job_location = new Utf8("__company_job_location__");
    public static Utf8 company_job_date = new Utf8("__company_job_date__");

    public final static String LINK_TYPE_ENTRY = "entry";
    public final static String LINK_TYPE_LIST = "list";
    public final static String LINK_TYPE_SUMMARY = "summary";

    /* some helper function */
    public static String getCompanyName(WebPage page) {
        if (page.getMetadata().containsKey(company_key)) {
            return Bytes.toString(page.getMetadata().get(company_key));
        } else {
            return "";
        }
    }
    public static void setCompanyName(WebPage page, String name) {
        page.getMetadata().put(company_key, ByteBuffer.wrap(name.getBytes()));
    }
    public static String getLinkType(WebPage page) {
        if (page.getMetadata().containsKey(company_link_type))
            return Bytes.toString(page.getMetadata().get(company_link_type));
        else
            return "";
    }
    public static void setEntryLink(WebPage page) {
        page.getMetadata().put(company_link_type, ByteBuffer.wrap(LINK_TYPE_ENTRY.getBytes()));
    }
    public static void setListLink(WebPage page) {
        page.getMetadata().put(company_link_type, ByteBuffer.wrap(LINK_TYPE_LIST.getBytes()));
    }
    public static void setSummaryLink(WebPage page) {
        page.getMetadata().put(company_link_type, ByteBuffer.wrap(LINK_TYPE_SUMMARY.getBytes()));
    }
    public static boolean isEntryLink(WebPage page) {
        if (page.getMetadata().containsKey(company_link_type))
            return LINK_TYPE_ENTRY.equals(Bytes.toString(page.getMetadata().get(company_link_type)));
        else
            return false;
    }
    public static boolean isListLink(WebPage page) {
        if (page.getMetadata().containsKey(company_link_type))
            return LINK_TYPE_LIST.equals(Bytes.toString(page.getMetadata().get(company_link_type)));
        else
            return false;
    }
    public static boolean isSummaryLink(WebPage page) {
        if (page.getMetadata().containsKey(company_link_type))
            return LINK_TYPE_SUMMARY.equals(Bytes.toString(page.getMetadata().get(company_link_type)));
        else
            return false;
    }
}
