#!/bin/bash
#
# Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# inherit the exit status from the mvn command rather than set
set -o pipefail

# here we go... our mighty sed filter explained:
# -n flag: print nothing unless specifically instructed to by the sed script
#
# s/^\\[INFO\\] org\\.apache\\.maven\\.cli\\.event\\.ExecutionEventLogger - \\( *[^ ].*\\)$/[INFO] \\1/p;
# print all [INFO] lines of the main Maven CLI logger (if there is any non-space after [INFO]). This is used to make sure that we see what is currently being built.
#
# /WARN/p;
# print any line that has "WARN" in it.
#
# /ERROR/p
# print any line that has "ERROR" in it.
#
# /^Tests run/p
# print the summary of the test results
#
# /^.* <<< FAILURE! *$/p
# This how surefire reports errors, so we output that to have some visibility of the failing tests in the travis log.
#
# /.* <<< ERROR! */,/^\s*$/p
# If there is a test error print it and everything under it until the next empty line - this way we see the failed tests.
#
# With this, we have the overview of what stage the build is currently in, yet we don't flood the output with stuff from tests and merely informative messages from the build.
#
mvn -fae -s .travis.maven.settings.xml clean install -Pitest 2>&1 | sed -n 's/^\[INFO\]  *org\.apache\.maven\.cli\.event\.ExecutionEventLogger - \( *[^ ].*\)$/[INFO] \1/p; /WARN/p; /ERROR/p; /^Tests run/p; /.* <<< FAILURE! *$/p; /.* <<< ERROR! *$/,/^ *$/p'
