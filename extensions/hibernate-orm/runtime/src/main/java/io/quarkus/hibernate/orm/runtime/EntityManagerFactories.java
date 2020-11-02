package io.quarkus.hibernate.orm.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

@ApplicationScoped
public class EntityManagerFactories {

    @Inject
    JPAConfig jpaConfig;

    private final ConcurrentMap<String, EntityManagerFactory> factories;

    public EntityManagerFactories() {
        this.factories = new ConcurrentHashMap<>();
    }

    public EntityManagerFactory getEntityManager(String unitName) {
        EntityManagerFactory entityManagerFactory = factories.get(unitName);
        if (entityManagerFactory != null) {
            return entityManagerFactory;
        }
        return factories.computeIfAbsent(unitName, (un) -> jpaConfig
                .getEntityManagerFactory(unitName));
    }
}
