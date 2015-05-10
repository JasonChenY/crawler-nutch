package org.apache.nutch.companyschema;

import org.apache.avro.util.Utf8;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.util.Bytes;

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

    private static List<NameValuePair> months = initialize_months();
    private static List<NameValuePair> initialize_months() {
        List<NameValuePair> months = new ArrayList<NameValuePair>();

        try {
            months.add(new BasicNameValuePair(new String("一月".getBytes(), "UTF-8"), "Jan"));
            months.add(new BasicNameValuePair(new String("一月".getBytes(), "UTF-8"), "Jan"));
            months.add(new BasicNameValuePair(new String("二月".getBytes(), "UTF-8"), "Feb"));
            months.add(new BasicNameValuePair(new String("三月".getBytes(), "UTF-8"), "Mar"));
            months.add(new BasicNameValuePair(new String("四月".getBytes(), "UTF-8"), "Apr"));
            months.add(new BasicNameValuePair(new String("五月".getBytes(), "UTF-8"), "May"));
            months.add(new BasicNameValuePair(new String("六月".getBytes(), "UTF-8"), "Jun"));
            months.add(new BasicNameValuePair(new String("七月".getBytes(), "UTF-8"), "Jul"));
            months.add(new BasicNameValuePair(new String("八月".getBytes(), "UTF-8"), "Aug"));
            months.add(new BasicNameValuePair(new String("九月".getBytes(), "UTF-8"), "Sep"));
            months.add(new BasicNameValuePair(new String("十月".getBytes(), "UTF-8"), "Oct"));
            months.add(new BasicNameValuePair(new String("十一月".getBytes(), "UTF-8"), "Nov"));
            months.add(new BasicNameValuePair(new String("十二月".getBytes(), "UTF-8"), "Dec"));
        } catch (Exception e) {
        }
        return months;
    }

    public static String convert_month(String str) {
        for ( int i = 0; i < months.size(); i++ ) {
            str = str.replaceAll(months.get(i).getName(), months.get(i).getValue());
        }
        return str;
    }
}
