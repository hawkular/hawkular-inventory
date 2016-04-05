/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.paths;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * Used for serializing the paths. Paths segments are URI-encoded.
 *
 * <p>This does the URI encoding and decoding roughly twice as fast as {@link java.net.URLEncoder} and
 * {@link java.net.URLDecoder}.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
public final class PathSegmentCodec {
    private static final ThreadLocal<CharsetDecoder> DECODER = new ThreadLocal<>();
    private static final ThreadLocal<CharsetEncoder> ENCODER = new ThreadLocal<>();
    private static final ThreadLocal<ByteBuffer> BYTE_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<CharBuffer> CHAR_BUFFER = new ThreadLocal<>();

    private PathSegmentCodec() {

    }

// in case of fear of unknown or, gasp, bugs found in the custom impl, we can switch back to these less performant but
// safer variants.

//    public static String decode(String str) {
//        try {
//            return URLDecoder.decode(str, "UTF-8");
//        } catch (UnsupportedEncodingException e) {
//            throw new IllegalArgumentException("Failed to decode: " + str, e);
//        }
//    }
//
//    public static String encode(String str) {
//        try {
//            return URLEncoder.encode(str, "UTF-8").replace("+", "%20");
//        } catch (UnsupportedEncodingException e) {
//            throw new IllegalArgumentException("Failed to encode: " + str, e);
//        }
//    }

    public static String decode(String str) {
        char[] ret = new char[str.length()];

        int len = str.length();

        int state = 0; //0 - normal, 1 - reading escape
        int octet = -1;

        CharsetDecoder dec = getDecoder();
        ByteBuffer bytes = getByteBuffer();
        bytes.clear();
        CharBuffer chars = getCharBuffer();
        chars.clear();
        boolean decoded = true;

        int oi = 0;
        for (int i = 0; i < len; ++i) {
            char c = str.charAt(i);

            switch (state) {
                case 0:
                    if (c == '%') {
                        decoded = false;
                        state = 1;
                    } else {
                        if (!decoded) {
                            throw new IllegalArgumentException("Could not decode URL-encoded octets ending" +
                                    " at position: " + i + " of input string: " + str);
                        }
                        ret[oi++] = c;
                    }
                    break;
                case 1:
                    if (octet == -1) {
                        octet = asHexDigitValue(c) * 16;
                    } else {
                        octet += asHexDigitValue(c);

                        bytes.put((byte) octet);
                        bytes.flip();

                        CoderResult res = dec.decode(bytes, chars, i == len - 1);
                        if (res.isOverflow()) {
                            throw new AssertionError("Decoder failed with insufficient room for new character." +
                                    "This should never happen.");
                        } else if (res.isUnderflow()) {
                            bytes.position(bytes.limit());
                            bytes.limit(bytes.capacity());
                        } else if (res.isError()) {
                            try {
                                res.throwException();
                            } catch (CharacterCodingException e) {
                                throw new IllegalArgumentException("Failed to decode: " + str, e);
                            }
                        }

                        if (chars.remaining() == 0) {
                            chars.flip();
                            ret[oi++] = chars.get();
                            chars.clear();
                            bytes.clear();
                            dec.reset();
                            decoded = true;
                        }

                        state = 0;
                        octet = -1;
                    }
                    break;
            }
        }

        if (state == 1) {
            throw new IllegalArgumentException("Incomplete trailing escape pattern in input string: " + str);
        }

        if (bytes.position() != 0) {
            throw new IllegalArgumentException("Could not decode URL-encoded octets ending" +
                    " at position: " + len + " of input string: " + str);
        }

        return new String(ret, 0, oi);
    }

    public static String encode(String str) {
        StringBuilder bld = new StringBuilder(str.length());
        ByteBuffer bytes = getByteBuffer();
        CharBuffer chars = getCharBuffer();
        CharsetEncoder enc = getEncoder();

        int len = str.length();
        for (int i = 0; i < len; ++i) {
            char c = str.charAt(i);

            if (isURISafe(c)) {
                bld.append(c);
            } else {
                chars.clear();
                bytes.clear();

                chars.append(c);
                chars.flip();

                CoderResult res = enc.encode(chars, bytes, i == len - 1);
                if (res.isOverflow()) {
                    throw new AssertionError("Encoder failed with insufficient room for new character." +
                            "This should never happen.");
                } else if (res.isUnderflow()) {
                    if (bytes.remaining() == bytes.capacity()) {
                        //nothing got encoded
                        throw new AssertionError("Underflow while converting character at position " + i + " of input: "
                                + str);
                    }
                } else if (res.isError()) {
                    try {
                        res.throwException();
                    } catch (CharacterCodingException e) {
                        throw new IllegalArgumentException("Failed to encode: " + str, e);
                    }
                }

                bytes.flip();
                while (bytes.hasRemaining()) {
                    byte b = bytes.get();
                    bld.append('%');
                    bld.append(asHexDigit((b & 0xF0) >> 4));
                    bld.append(asHexDigit(b & 0x0F));
                }

                enc.reset();
            }
        }

        return bld.toString();
    }

    private static CharsetDecoder getDecoder() {
        CharsetDecoder ret = DECODER.get();
        if (ret == null) {
            ret = Charset.forName("UTF-8").newDecoder();
            DECODER.set(ret);
        }

        return ret;
    }

    private static CharsetEncoder getEncoder() {
        CharsetEncoder ret = ENCODER.get();
        if (ret == null) {
            ret = Charset.forName("UTF-8").newEncoder();
            ENCODER.set(ret);
        }

        return ret;
    }

    private static ByteBuffer getByteBuffer() {
        ByteBuffer ret = BYTE_BUFFER.get();
        if (ret == null) {
            ret = ByteBuffer.allocateDirect(6);
            BYTE_BUFFER.set(ret);
        }

        return ret;
    }

    private static CharBuffer getCharBuffer() {
        CharBuffer ret = CHAR_BUFFER.get();
        if (ret == null) {
            ret = CharBuffer.allocate(1);
            CHAR_BUFFER.set(ret);
        }

        return ret;
    }

    private static int asHexDigitValue(char c) {
        if ('0' <= c && c <= '9') {
            return c - '0';
        } else if ('a' <= c && c <= 'f') {
            return 10 + (c - 'a');
        } else if ('A' <= c && c <= 'F') {
            return 10 + (c - 'A');
        } else {
            throw new IllegalArgumentException("Invalid hex digit: " + c);
        }
    }

    private static char asHexDigit(int b) {
        if (b < 10) {
            return (char) ('0' + b);
        } else {
            return (char) ('A' + (b - 10));
        }
    }

    private static boolean isURISafe(char c) {
        //made compatible with RestEasy path encoding
      /*
       * <a href="http://ietf.org/rfc/rfc3986.txt">RFC 3986</a>
       *
       * unreserved  = ALPHA / DIGIT / "-" / "." / "_" / "~"
       * sub-delims  = "!" / "$" / "&" / "'" / "(" / ")"
                     / "*" / "+" / "," / ";" / "="
       * pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
       *
       */

        //we encode the sub-delims so that the URI processors don't try to assign them a meaning they don't have
        return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9') ||
                "-._~:@".indexOf(c) >= 0;
    }
}
