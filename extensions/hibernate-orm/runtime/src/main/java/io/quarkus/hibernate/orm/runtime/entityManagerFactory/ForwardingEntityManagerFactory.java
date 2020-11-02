package io.quarkus.hibernate.orm.runtime.entityManagerFactory;

import java.util.Map;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.metamodel.Metamodel;

public abstract class ForwardingEntityManagerFactory implements EntityManagerFactory {

    protected abstract EntityManagerFactory delegate();

    @Override
    public EntityManager createEntityManager() {
        return delegate().createEntityManager();
    }

    @Override
    public EntityManager createEntityManager(Map map) {
        return delegate().createEntityManager(map);
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType) {
        return delegate().createEntityManager(synchronizationType);
    }

    @Override
    public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
        return delegate().createEntityManager(synchronizationType, map);
    }

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return delegate().getCriteriaBuilder();
    }

    @Override
    public Metamodel getMetamodel() {
        return delegate().getMetamodel();
    }

    @Override
    public boolean isOpen() {
        return delegate().isOpen();
    }

    @Override
    public void close() {
        delegate().close();
    }

    @Override
    public Map<String, Object> getProperties() {
        return delegate().getProperties();
    }

    @Override
    public Cache getCache() {
        return delegate().getCache();
    }

    @Override
    public PersistenceUnitUtil getPersistenceUnitUtil() {
        return delegate().getPersistenceUnitUtil();
    }

    @Override
    public void addNamedQuery(String s, Query query) {
        delegate().addNamedQuery(s, query);
    }

    @Override
    public <T> T unwrap(Class<T> aClass) {
        return delegate().unwrap(aClass);
    }

    @Override
    public <T> void addNamedEntityGraph(String s, EntityGraph<T> entityGraph) {
        delegate().addNamedEntityGraph(s, entityGraph);
    }
}
