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
package org.hawkular.inventory.impl.cassandra;

/**
 * Configures the RxJava plugins. Needs to be called prior to instantiating an inventory in ANY test class.
 *
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class RxSetup {
    private static boolean INITIALIZED = false;

    private RxSetup() {

    }

    synchronized static void setup() {
        if (!INITIALIZED) {
//            RxJavaPlugins.getInstance().registerObservableExecutionHook(new DebugHook<>(
//                    new DebugNotificationListener<Object>() {
//                        @Override public <T> T onNext(DebugNotification<T> n) {
//                            Log.LOG.debugf("onNext on %s", n);
//                            return super.onNext(n);
//                        }
//
//                        @Override public <T> Object start(DebugNotification<T> n) {
//                            Log.LOG.debugf("start on %s", n);
//                            return n.getSource();
//                        }
//
//                        @Override public void complete(Object context) {
//                            Log.LOG.debugf("complete on %s", context);
//                            super.complete(context);
//                        }
//
//                        @Override public void error(Object context, Throwable e) {
//                            Log.LOG.debugf(e, "error on %s", context);
//                            super.error(context, e);
//                        }
//                    }));

//            RxJavaHooks.setOnIOScheduler(sched -> ImmediateScheduler.INSTANCE);
//            RxJavaHooks.setOnComputationScheduler(sched -> ImmediateScheduler.INSTANCE);
//            RxJavaHooks.setOnGenericScheduledExecutorService(Executors::newSingleThreadScheduledExecutor);
//            RxJavaHooks.setOnNewThreadScheduler(scheduler -> ImmediateScheduler.INSTANCE);

            INITIALIZED = true;
        }
    }
}
