#!/bin/bash
#
# Copyright 2015 Red Hat, Inc. and/or its affiliates
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


#create tenant
curl -ivX POST -H "Content-Type: application/json" -d '{"id": "acme"}' http://127.0.0.1:8080/hawkular/inventory/tenants

#create environment
curl -ivX POST -H "Content-Type: application/json" -d '{"id": "prod"}' http://127.0.0.1:8080/hawkular/inventory/acme/environments

#create resource type
curl -ivX POST -H "Content-Type: application/json" -d '{"id": "URL", "version": "1.0"}' http://127.0.0.1:8080/hawkular/inventory/acme/resourceTypes

#create resource
curl -ivX POST -H "Content-Type: application/json" -d '{"id": "URL-res", "type": {"id": "URL", "version": "1.0"}}' http://127.0.0.1:8080/hawkular/inventory/acme/prod/resources

#create metric type
curl -ivX POST -H "Content-Type: application/json" -d '{"id":"MetricType", "unit":"b"}' http://127.0.0.1:8080/hawkular/inventory/acme/metricTypes

#create metric
curl -ivX POST -H "Content-Type: application/json" -d '{"id": "fooMetric", "metricTypeId": "MetricType"}' http://127.0.0.1:8080/hawkular/inventory/acme/prod/metrics

#assign metric to a resource
curl -iX POST -H "Content-Type: application/json" -d '["fooMetric"]' http://127.0.0.1:8080/hawkular/inventory/acme/prod/resources/URL-res/metrics
