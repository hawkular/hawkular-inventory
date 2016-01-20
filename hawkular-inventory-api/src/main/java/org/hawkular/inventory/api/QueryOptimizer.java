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
package org.hawkular.inventory.api;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import static org.hawkular.inventory.api.Relationships.WellKnown.contains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.filters.Related;
import org.hawkular.inventory.api.filters.With;
import org.hawkular.inventory.api.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.Path;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 0.6.0
 */
final class QueryOptimizer {
    private QueryOptimizer() {

    }

    public static void appendOptimized(List<QueryFragment> fragments, QueryFragment... filters) {
        //remove duplicates
        List<QueryFragment> applicableNewFilters;

        if (fragments.isEmpty()) {
            applicableNewFilters = new ArrayList<>(Arrays.asList(filters));
        } else {
            applicableNewFilters = removeDuplicates(fragments.get(fragments.size() - 1).getFilter(),
                    new ArrayList<>(Arrays.asList(filters)));
        }

        cleanUnnecessaryNoops(applicableNewFilters);

        QueryFragment cpFilter = findLastCanonicalFilterAndPrepareNewFilters(fragments, applicableNewFilters);

        List<CanonicalPath.Extender> checkers;
        if (cpFilter == null) {
            checkers = Collections.singletonList(CanonicalPath.empty());
        } else {
            With.CanonicalPaths cps = (With.CanonicalPaths) cpFilter.getFilter();
            checkers = Arrays.asList(cps.getPaths()).stream().map(CanonicalPath::modified).collect(toList());
        }

        while (!applicableNewFilters.isEmpty()) {
            boolean isPath = applicableNewFilters.get(0) instanceof PathFragment;

            QueryFragment first, second, third;

            if ((first = removeOrReadd(applicableNewFilters, null, null)) == null) {
                break;
            }
            if ((second = removeOrReadd(applicableNewFilters, first, null)) == null) {
                break;
            }
            third = applicableNewFilters.isEmpty() ? null : applicableNewFilters.remove(0);

            //check that the query is not transitioning from a path to a filter or vice versa
            //we do this check only on the positions of the contains filter, because only that can actually influence
            //the results
            if (cpFilter != null && first.getClass() != cpFilter.getClass()) {
                //we were trying to continue a classpath filter. move the contains to the processed fragments
                //and try with the type/id as if this was a new start of the cp filter.
                reAddNonNull(false, fragments, first);
                reAddNonNull(true, applicableNewFilters, second, third);
                checkers = Collections.singletonList(CanonicalPath.empty());
                cpFilter = null;
                continue;
            }

            With.Types typeFilter;
            With.Ids idFilter;

            if (cpFilter == null) {
                //there is no prior canonical path filter, so we're starting a new one potentially
                //this means that first and second are type+id
                //additionally, the contains query fragment, if it is present, must be of the same type as the type/id
                //filters.
                //this is so that they all are either path segs or filter segs - we must not mix them together.
                typeFilter = choose(With.Types.class, first, second);
                idFilter = choose(With.Ids.class, first, second);
                if (third != null && (!isOutgoingContains(third.getFilter()) || second.getClass() != third
                        .getClass())) {
                    reAddNonNull(true, applicableNewFilters, third);
                    third = null;
                }
            } else {
                //there is a prior canonical path filter, so we're trying to continue it.
                //this means that first must be a contains filter, and second and third are type+id
                //again we must check for the change in the query fragment type
                if (!isOutgoingContains(first.getFilter())) {
                    reAddNonNull(false, fragments, first);
                    reAddNonNull(true, applicableNewFilters, second, third);
                    checkers = Collections.singletonList(CanonicalPath.empty());
                    cpFilter = null;
                    continue;
                }

                typeFilter = choose(With.Types.class, second, third);
                idFilter = choose(With.Ids.class, second, third);
            }

            if (typeFilter == null || idFilter == null) {
                //hmm... not sure what to do with filters invalid for cp extension.. so just add them back to
                //fragments and try to continue
                reAddNonNull(false, fragments, first, second, third);
                checkers = Collections.singletonList(CanonicalPath.empty());
                cpFilter = null;
                continue;
            }

            if (typeFilter.getTypes().length != idFilter.getIds().length) {
                //the canonical path filter wouldn't be able to completely match the type+id filters, so
                //bail
                reAddNonNull(false, fragments, first, second, third);
                checkers = Collections.singletonList(CanonicalPath.empty());
                cpFilter = null;
                continue;
            }

            // if we should be able to use the canonical path filter, everything must match - all the
            // checkers must be able to extend to all types
            boolean checkFailed = false;
            CHECK:
            for (CanonicalPath.Extender checker : checkers) {
                org.hawkular.inventory.paths.SegmentType[] types = typeFilter.getSegmentTypes();
                for (int n = 0; n < typeFilter.getTypes().length; ++n) {
                    Path.Segment seg = new Path.Segment(types[n], idFilter.getIds()[n]);
                    if (!checker.canExtendTo(seg)) {
                        if (cpFilter == null) {
                            //k we tried to start a new cp filter, but failed
                            reAddNonNull(false, fragments, first, second, third);
                        } else {
                            //move the contains to "processed" fragments and try again as if this was a new path
                            reAddNonNull(false, fragments, first);
                            reAddNonNull(true, applicableNewFilters, second, third);
                            checkers = Collections.singletonList(CanonicalPath.empty());
                            cpFilter = null;
                        }
                        checkFailed = true;
                        break CHECK;
                    } else {
                        checker.extend(seg);
                    }
                }
            }

            if (!checkFailed) {
                //k, so now we can transform the checkers into a new canonical path filter, because they contain
                //our new canonical positions
                if (!fragments.isEmpty()
                        && fragments.get(fragments.size() - 1).getFilter() instanceof With.CanonicalPaths) {
                    //remove the last filter from our fragments
                    fragments.remove(fragments.size() - 1);
                }

                CanonicalPath[] newPaths = checkers.stream().map(CanonicalPath.Extender::get)
                        .toArray(CanonicalPath[]::new);

                QueryFragment related = cpFilter == null ? third : null;

                cpFilter = isPath ? new PathFragment(With.paths(newPaths))
                        : new FilterFragment(With.paths(newPaths));

                fragments.add(cpFilter);

                if (related != null) {
                    reAddNonNull(true, applicableNewFilters, related);
                }
            }
        }

        //add the rest of the filters from the input
        fragments.addAll(applicableNewFilters);
    }

    private static void cleanUnnecessaryNoops(List<QueryFragment> filters) {

    }

    private static <T> void reAddNonNull(boolean atBeginning, List<T> list, T... objs) {
        int pos = atBeginning ? 0 : list.size();
        for (int i = objs.length - 1; i >= 0; --i) {
            T o = objs[i];
            if (o != null) {
                list.add(pos, o);
            }
        }
    }

    private static QueryFragment removeOrReadd(List<QueryFragment> fragments, QueryFragment first,
                                               QueryFragment second) {

        if (fragments.isEmpty()) {
            addIfNotNull(fragments, first);
            addIfNotNull(fragments, second);
            return null;
        } else {
            return fragments.remove(0);
        }
    }

    private static <T> void addIfNotNull(Collection<T> col, T el) {
        if (el != null) {
            col.add(el);
        }
    }

    private static <T extends Filter> T choose(Class<T> type, QueryFragment... objects) {
        for (QueryFragment o : objects) {
            if (o != null && type.equals(o.getFilter().getClass())) {
                return type.cast(o.getFilter());
            }
        }

        return null;
    }

    private static boolean isOutgoingContains(Filter f) {
        if (!(f instanceof Related)) {
            return false;
        }

        Related rel = (Related) f;

        return rel.getEntityRole() == Related.EntityRole.SOURCE &&
                contains.name().equals(rel.getRelationshipName());
    }

    private static QueryFragment findLastCanonicalFilterAndPrepareNewFilters(List<QueryFragment> fragments,
                                                                             List<QueryFragment> newFilters) {

        if (fragments.isEmpty()) {
            return null;
        }

        Filter last = fragments.get(fragments.size() - 1).getFilter();

        QueryFragment ret = null;

        if (last instanceof With.CanonicalPaths) {
            ret = fragments.get(fragments.size() - 1);
            CanonicalPath[] sources = ((With.CanonicalPaths) last).getPaths();

            //remove anything from newFilters that is also matched by the canonical path filter
            Set<Class<?>> expectedClasses = Arrays.asList(sources).stream()
                    .map((s) -> Entity.typeFromSegmentType(s.getSegment().getElementType())).collect(toSet());
            Set<String> expectedIds = Arrays.asList(sources).stream().map((s) -> s.getSegment().getElementId())
                    .collect(toSet());

            for (Iterator<QueryFragment> it = newFilters.iterator(); it.hasNext(); ) {
                QueryFragment f = it.next();
                if (f.getFilter() instanceof With.Types) {
                    Set<SegmentType> filterTypes =
                            new HashSet<>(Arrays.asList(((With.Types) f.getFilter()).getSegmentTypes()));
                    if (!expectedClasses.equals(filterTypes)) {
                        break;
                    }
                } else if (f.getFilter() instanceof With.Ids) {
                    Set<String> filterIds = new HashSet<>(Arrays.asList(((With.Ids) f.getFilter()).getIds()));
                    if (!expectedIds.equals(filterIds)) {
                        break;
                    }
                } else {
                    break;
                }

                it.remove();
            }
        } else if (fragments.size() > 1) {
            //check if the second last isn't a canonical filter and the last + the current filter cannot be
            //made into continuation of that canonical path filter
            Filter secondLast = fragments.get(fragments.size() - 2).getFilter();
            Filter thirdLast = fragments.size() > 2 ? fragments.get(fragments.size() - 3).getFilter() : null;

            if (secondLast instanceof With.CanonicalPaths && last instanceof Related) {
                Related rel = (Related) last;
                if (contains.name().equals(rel.getRelationshipName())) {
                    ret = fragments.get(fragments.size() - 2);
                    //prepend the filters with the last
                    newFilters.add(0, fragments.remove(fragments.size() - 1));
                }
            } else if (thirdLast instanceof With.CanonicalPaths && secondLast instanceof Related
                    && (last instanceof With.Types || last instanceof With.Ids)) {
                Related rel = (Related) secondLast;
                if (contains.name().equals(rel.getRelationshipName())) {
                    ret = fragments.get(fragments.size() - 3);
                    //prepend the filters with the last and second last
                    newFilters.add(0, fragments.remove(fragments.size() - 1));
                    newFilters.add(0, fragments.remove(fragments.size() - 1));
                } else {
                    //k, so the Related filter is not what we want... but the last element still might offer something
                    //to base the cp filter on, so let's provide it
                    newFilters.add(0, fragments.remove(fragments.size() - 1));
                }
            } else if (!newFilters.isEmpty() && ((last instanceof With.Types
                    && newFilters.get(0).getFilter() instanceof With.Ids)
                    || (last instanceof With.Ids
                    && newFilters.get(0).getFilter() instanceof With.Types))) {

                //we've not seen any canonical path filter, but we might be starting a new one because the end
                //of the fragments list lends itself to it
                newFilters.add(0, fragments.remove(fragments.size() - 1));
            }
        } else if (!newFilters.isEmpty() && ((last instanceof With.Types
                && newFilters.get(0).getFilter() instanceof With.Ids)
                || (last instanceof With.Ids
                && newFilters.get(0).getFilter() instanceof With.Types))) {

            //we've not seen any canonical path filter, but we might be starting a new one because the end
            //of the fragments list lends itself to it
            newFilters.add(0, fragments.remove(fragments.size() - 1));
        }

        return ret;
    }

    private static List<QueryFragment> removeDuplicates(Filter original, List<QueryFragment> filters) {
        List<QueryFragment> ret = filters;
        int i = 0;
        while (i < filters.size() && original.equals(filters.get(i).getFilter())) {
            i++;
        }
        if (i > 0) {
            ret = ret.subList(i, ret.size());
        }

        return ret;
    }
}
