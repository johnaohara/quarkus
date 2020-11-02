package io.quarkus.it.envers;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.query.AuditEntity;

import io.quarkus.rest.Blocking;
import io.quarkus.runtime.StartupEvent;

@Path("/project")
@Blocking
public class ProjectResource {

    @Inject
    EntityManager em;

    @Inject
    EntityManagerFactory entityManagerFactory;

    @Transactional
    public void startup(@Observes final StartupEvent startupEvent) {
        final Project project = new Project();
        project.setName("Quarkus");
        em.persist(project);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional
    @Path("/{id}")
    public String getProjectAtLastRevision(@PathParam("id") final Long id) {
        return executeQuery(em, id);
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/emf/{id}")
    public String getProjectAtLastRevisionEmf(@PathParam("id") final Long id) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        return executeQuery(entityManager, id);
    }

    private String executeQuery(EntityManager em, Long id) {
        final Project projectAudited = (Project) AuditReaderFactory.get(em)
                .createQuery()
                .forRevisionsOfEntity(
                        Project.class, true, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().desc())
                .getSingleResult();
        return projectAudited.getName();
    }
}
