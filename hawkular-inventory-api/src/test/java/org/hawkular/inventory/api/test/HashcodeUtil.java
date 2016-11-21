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
package org.hawkular.inventory.api.test;

import java.util.stream.Stream;

/**
 * @author Lukas Krejci
 * @since 1.1.2
 */
public class HashcodeUtil {

    private static final String[] SAME_HASH_SET = new String[]{
            "0nn",
            "0oO",
            "0p0",
            "1On",
            "1PO",
            "1Q0",
            "20n",
            "21O",
            "220"
    };

//this is to generate the full set of length 3 string sets with the same hashcode
//    private static final int SAFE_CHAR_COUNT = 26 + 26 + 10; //lower and upper case letters and digits
//    private static final String[][] SAME_HASH_SETS;
//    static {
//        Map<Integer, Set<String>> sameHash = new HashMap<>();
//        for (int i = 0; i < SAFE_CHAR_COUNT; ++i) {
//            for (int j = 0; j < SAFE_CHAR_COUNT; ++j) {
//                for (int k = 0; k < SAFE_CHAR_COUNT; ++k) {
//                    String str = "" + getSafeAsciiChar(i) + getSafeAsciiChar(j) + getSafeAsciiChar(k);
//                    int hash = str.hashCode();
//                    Set<String> ss = sameHash.get(hash);
//                    if (ss == null) {
//                        ss = new HashSet<>(9);
//                        sameHash.put(hash, ss);
//                    }
//                    ss.add(str);
//                }
//            }
//        }
//
//        Iterator<Map.Entry<Integer, Set<String>>> it = sameHash.entrySet().iterator();
//        while (it.hasNext()) {
//            if (it.next().getValue().size() == 1) {
//                it.remove();
//            }
//        }
//
//        SAME_HASH_SETS = sameHash.values().stream()
//                .sorted((a, b) -> b.size() - a.size())
//                .map(ss -> ss.toArray(new String[ss.size()]))
//                .toArray(String[][]::new);
//    }
//    private static char getSafeAsciiChar(int index) {
//        //self proclaimed "safe" indices in ASCII table are 48-57, 65-90, 97-122 which are [0-9a-zA-Z] as regex
//        //below "10" is the number of digits and 26 is the number of letters in alphabet
//        if ('0' + index <= '9') {
//            return (char) ('0' + index);
//        } else if ('A' + index - 10 <= 'Z') {
//            return (char) ('A' + index - 10);
//        } else if ('a' + index - 10 - 26 <= 'z') {
//            return (char) ('a' + index - 10 - 26);
//        } else {
//            throw new IndexOutOfBoundsException();
//        }
//    }


    /**
     * Returns the {@code count} number of strings with the same hashcode.
     * <p>
     * <p>Note that this relies heavily on the implementation of {@link String#hashCode()} so it might stop working if
     * that implementation changes.
     *
     * @param count the number of strings to generate
     * @return the array with size of {@code count} contains mutually different strings with the same hashcode.
     */
    public static String[] getStringsWithSameHashcode(int count) {
        StringBuilder[] bld = new StringBuilder[count];

        for (int i = 0; i < count; ++i) {
            bld[i] = new StringBuilder();
        }

        //log of count in base length
        int length = (int) Math.ceil(Math.log(count) / Math.log(SAME_HASH_SET.length));

        for (int l = 0; l < length; ++l) {
            int scale = (int) (Math.pow(SAME_HASH_SET.length, l));
            for (int i = 0; i < count; ++i) {
                int index = i / scale;
                bld[i].append(SAME_HASH_SET[index % SAME_HASH_SET.length]);
            }
        }

        return Stream.of(bld).map(StringBuilder::toString).toArray(String[]::new);
    }
}
