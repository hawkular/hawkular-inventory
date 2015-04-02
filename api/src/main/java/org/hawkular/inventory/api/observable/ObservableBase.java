/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.inventory.api.observable;

import org.hawkular.inventory.api.ReadInterface;
import org.hawkular.inventory.api.Relatable;
import org.hawkular.inventory.api.Relationships;
import org.hawkular.inventory.api.ResolvableToMany;
import org.hawkular.inventory.api.ResolvableToSingle;
import org.hawkular.inventory.api.WriteInterface;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.Relationship;
import rx.subjects.Subject;

import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class ObservableBase<T> {

    protected final ObservableContext context;
    protected final T wrapped;

    ObservableBase(T wrapped, ObservableContext context) {
        this.context = context;
        this.wrapped = wrapped;
    }

    protected <V, I> I wrap(BiFunction<V, ObservableContext, I> constructor, V value) {
        return constructor.apply(value, context);
    }

    protected <E, V extends ResolvableToSingle<E>, I> I wrapAndNotify(BiFunction<V, ObservableContext, I> constructor,
        V value, Action<E> action) {

        E e = value.entity();

        notify(e, action);

        return constructor.apply(value, context);
    }

    protected <E> void notify(E entity, Action<E> action) {
        Iterator<Subject<E, E>> subjects = context.matchingSubjects(action, entity);
        while (subjects.hasNext()) {
            Subject<E, E> s = subjects.next();
            s.onNext(entity);
        }
    }

    public static abstract class Read<Single extends ResolvableToSingle<?>,
            Multiple extends ResolvableToMany<?>, Iface extends ReadInterface<Single, Multiple>>
            extends ObservableBase<Iface> {

        Read(Iface wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        protected abstract BiFunction<Single, ObservableContext, ? extends Single> singleCtor();

        protected abstract BiFunction<Multiple, ObservableContext, ? extends Multiple> multipleCtor();

        public Single get(String id) {
            return wrap(singleCtor(), wrapped.get(id));
        }

        public Multiple getAll(Filter... filters) {
            return wrap(multipleCtor(), wrapped.getAll(filters));
        }
    }

    public static abstract class ReadWrite<Entity, Blueprint,
            Single extends ResolvableToSingle<Entity> & Relatable<Relationships.ReadWrite>,
            Multiple extends ResolvableToMany<Entity>,
            Iface extends ReadInterface<Single, Multiple> & WriteInterface<Entity, Blueprint, Single>>
            extends ObservableBase<Iface> {

        ReadWrite(Iface wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        protected abstract BiFunction<Single, ObservableContext, ? extends Single> singleCtor();

        protected abstract BiFunction<Multiple, ObservableContext, ? extends Multiple> multipleCtor();

        public Single get(String id) {
            return wrap(singleCtor(), wrapped.get(id));
        }

        public Multiple getAll(Filter... filters) {
            return wrap(multipleCtor(), wrapped.getAll(filters));
        }

        public Single create(Blueprint b) {
            Single s = wrapped.create(b);

            notify(s.entity(), Action.<Entity>create());

            //there is a possible race here if someone creates a relationship on the entity between the time it
            //is created above and here. Such relationships would be observed twice...
            s.relationships(Relationships.Direction.both).getAll().entities()
                    .forEach((r) -> notify(r, Action.<Relationship>create()));

            return wrap(singleCtor(), s);
        }

        public void update(Entity e) {
            wrapped.update(e);
            notify(e, Action.update());
        }

        public void delete(String id) {
            Entity e = get(id).entity();
            wrapped.delete(id);
            notify(e, Action.delete());
        }
    }

    public static abstract class Single<E, T extends ResolvableToSingle<E>> extends ObservableBase<T> {

        Single(T wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        public E entity() {
            return wrapped.entity();
        }
    }

    public static abstract class Multiple<E, T extends ResolvableToMany<E>> extends ObservableBase<T> {

        Multiple(T wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        public Set<E> entities() {
            return wrapped.entities();
        }
    }

    public static abstract class RelatableSingle<E,
            T extends Relatable<Relationships.ReadWrite> & ResolvableToSingle<E>> extends Single<E, T> {

        RelatableSingle(T wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        public ObservableRelationships.ReadWrite relationships() {
            return wrap(ObservableRelationships.ReadWrite::new, wrapped.relationships());
        }

        public ObservableRelationships.ReadWrite relationships(Relationships.Direction direction) {
            return wrap(ObservableRelationships.ReadWrite::new, wrapped.relationships(direction));
        }
    }

    public static abstract class RelatableMultiple<E,
            T extends Relatable<Relationships.Read> & ResolvableToMany<E>> extends Multiple<E, T> {

        RelatableMultiple(T wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        public ObservableRelationships.Read relationships() {
            return wrap(ObservableRelationships.Read::new, wrapped.relationships());
        }

        public ObservableRelationships.Read relationships(Relationships.Direction direction) {
            return wrap(ObservableRelationships.Read::new, wrapped.relationships(direction));
        }
    }
}
