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
import org.hawkular.inventory.api.ResolvingToMultiple;
import org.hawkular.inventory.api.WriteInterface;
import org.hawkular.inventory.api.filters.Filter;
import org.hawkular.inventory.api.model.AbstractElement;
import org.hawkular.inventory.api.model.Entity;
import rx.subjects.Subject;

import java.util.Iterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

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

    protected <C, E, V extends ResolvableToSingle<E>, I> I wrapAndNotify(
            BiFunction<V, ObservableContext, I> constructor, V value, Function<V, C> contextProducer,
            Action<C, E> action) {

        E e = value.entity();
        C c = contextProducer.apply(value);

        notify(e, c, action);

        return constructor.apply(value, context);
    }

    protected <E> void notify(E entity, Action<E, E> action) {
        notify(entity, entity, action);
    }

    protected <C, E> void notify(E entity, C actionContext, Action<C, E> action) {
        Iterator<Subject<C, C>> subjects = context.matchingSubjects(action, entity);
        while (subjects.hasNext()) {
            Subject<C, C> s = subjects.next();
            s.onNext(actionContext);
        }
    }

    public abstract static class ReadMultiple<Multiple extends ResolvableToMany<?>,
            Iface extends ResolvingToMultiple<Multiple>> extends ObservableBase<Iface> {

        ReadMultiple(Iface wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        protected abstract BiFunction<Multiple, ObservableContext, ? extends Multiple> multipleCtor();

        public Multiple getAll(Filter... filters) {
            return wrap(multipleCtor(), wrapped.getAll(filters));
        }
    }

    public abstract static class Read<Single extends ResolvableToSingle<?>,
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

    public abstract static class ReadWrite<E extends AbstractElement<B, U>, B extends Entity.Blueprint,
            U extends AbstractElement.Update, Single extends ResolvableToSingle<E> & Relatable<Relationships.ReadWrite>,
            Multiple extends ResolvableToMany<E>,
            Iface extends ReadInterface<Single, Multiple> & WriteInterface<U, B, Single>>
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

        public Single create(B b) {
            Single s = wrapped.create(b);

            E e = s.entity();

            notify(e, e, Action.created());

            //there is a possible race here if someone creates a relationship on the entity between the time it
            //is created above and here. Such relationships would be observed twice...
            s.relationships(Relationships.Direction.both).getAll().entities()
                    .forEach((r) -> notify(r, r, Action.created()));

            return wrap(singleCtor(), s);
        }

        public void update(String id, U u) {
            E e = wrapped.get(id).entity();
            wrapped.update(id, u);
            notify(e, new Action.Update<>(e, u), Action.updated());
        }

        public void delete(String id) {
            E e = get(id).entity();
            wrapped.delete(id);
            notify(e, e, Action.deleted());
        }
    }

    public abstract static class SingleBase<E, T extends ResolvableToSingle<E>> extends ObservableBase<T> {

        SingleBase(T wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        public E entity() {
            return wrapped.entity();
        }
    }

    public abstract static class MultipleBase<E, T extends ResolvableToMany<E>> extends ObservableBase<T> {

        MultipleBase(T wrapped, ObservableContext context) {
            super(wrapped, context);
        }

        public Set<E> entities() {
            return wrapped.entities();
        }
    }

    public abstract static class RelatableSingle<E,
            T extends Relatable<Relationships.ReadWrite> & ResolvableToSingle<E>> extends SingleBase<E, T> {

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

    public abstract static class RelatableMultiple<E,
            T extends Relatable<Relationships.Read> & ResolvableToMany<E>> extends MultipleBase<E, T> {

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
