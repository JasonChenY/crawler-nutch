package org.apache.nutch.companyschema;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.nio.charset.Charset;
import java.io.InputStream;
import java.lang.CharSequence;

public class EncodeUtils {
    private static final String PARAMETER_SEPARATOR = "&";
    private static final String NAME_VALUE_SEPARATOR = "=";
    private static final char[] DELIM = new char[]{'&'};
    private static final BitSet UNRESERVED = new BitSet(256);
    private static final BitSet PUNCT = new BitSet(256);
    private static final BitSet USERINFO = new BitSet(256);
    private static final BitSet PATHSAFE = new BitSet(256);
    private static final BitSet FRAGMENT = new BitSet(256);
    private static final BitSet RESERVED = new BitSet(256);
    private static final BitSet URLENCODER = new BitSet(256);
    private static final int RADIX = 16;

    public static String rawdecode(String content, String cs) {
        Charset charset = Charset.forName(cs);
        if(content == null) {
            return null;
        } else {
            byte[] bytes = content.getBytes();

            ByteBuffer bb = ByteBuffer.allocate(content.length()*2);;
            for ( int i = 0; i < bytes.length; i++ ) {
                byte c = bytes[i];

                if(c == 37 && ((i+2)<bytes.length) ) {
                    byte uc = bytes[i+1];
                    byte lc = bytes[i+2];
                    if ( uc == '2' && (   lc == '0' || lc == '2' || lc == '3' || lc == '4'
                                       || lc == '6' || lc == '7' || lc == 'B'|| lc == 'C' )
                      || uc == '3' && (   lc == 'A' || lc == 'B' || lc == 'C' || lc == 'D'
                            || lc == 'E' || lc == 'F'  ) ) {
                        byte high = (byte)(uc - '0');
                        byte low = (byte)(lc > 'A' ? lc - 'A' + 10 : lc - '0');
                        byte ascii = (byte)(high * 16 + low);
                        bb.put(ascii);
                        i += 2;
                    } else if ( uc == '0' && lc == 'A'
                            ||  uc == '5' && lc == 'C') {
                        // eat it
                        i += 2;
                    } else {
                        bb.put((byte)37);
                    }
                } else {
                    bb.put((byte)c);
                }
            }

            bb.flip();
            return charset.decode(bb).toString();
        }
    }

    public static String urldecode(String content, String cs, boolean plusAsBlank) {
        Charset charset = Charset.forName(cs);
        if(content == null) {
            return null;
        } else {
            ByteBuffer bb = ByteBuffer.allocate(content.length());
            CharBuffer cb = CharBuffer.wrap(content);

            while(cb.hasRemaining()) {
                char c = cb.get();
                if(c == 37 && cb.remaining() >= 2) {
                    char uc = cb.get();
                    char lc = cb.get();
                    int u = Character.digit(uc, 16);
                    int l = Character.digit(lc, 16);
                    if(u != -1 && l != -1) {
                        bb.put((byte)((u << 4) + l));
                    } else {
                        bb.put((byte)37);
                        bb.put((byte)uc);
                        bb.put((byte)lc);
                    }
                } else if(plusAsBlank && c == 43) {
                    bb.put((byte)32);
                } else {
                    bb.put((byte)c);
                }
            }

            bb.flip();
            return charset.decode(bb).toString();
        }
    }

    static {
        int i;
        for(i = 97; i <= 122; ++i) {
            UNRESERVED.set(i);
        }

        for(i = 65; i <= 90; ++i) {
            UNRESERVED.set(i);
        }

        for(i = 48; i <= 57; ++i) {
            UNRESERVED.set(i);
        }

        UNRESERVED.set(95);
        UNRESERVED.set(45);
        UNRESERVED.set(46);
        UNRESERVED.set(42);
        URLENCODER.or(UNRESERVED);
        UNRESERVED.set(33);
        UNRESERVED.set(126);
        UNRESERVED.set(39);
        UNRESERVED.set(40);
        UNRESERVED.set(41);
        PUNCT.set(44);
        PUNCT.set(59);
        PUNCT.set(58);
        PUNCT.set(36);
        PUNCT.set(38);
        PUNCT.set(43);
        PUNCT.set(61);
        USERINFO.or(UNRESERVED);
        USERINFO.or(PUNCT);
        PATHSAFE.or(UNRESERVED);
        PATHSAFE.set(47);
        PATHSAFE.set(59);
        PATHSAFE.set(58);
        PATHSAFE.set(64);
        PATHSAFE.set(38);
        PATHSAFE.set(61);
        PATHSAFE.set(43);
        PATHSAFE.set(36);
        PATHSAFE.set(44);
        RESERVED.set(59);
        RESERVED.set(47);
        RESERVED.set(63);
        RESERVED.set(58);
        RESERVED.set(64);
        RESERVED.set(38);
        RESERVED.set(61);
        RESERVED.set(43);
        RESERVED.set(36);
        RESERVED.set(44);
        RESERVED.set(91);
        RESERVED.set(93);
        FRAGMENT.or(RESERVED);
        FRAGMENT.or(UNRESERVED);
    }
}

