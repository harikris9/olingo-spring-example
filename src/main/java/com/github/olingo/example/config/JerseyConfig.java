package com.github.olingo.example.config;

import com.github.olingo.example.service.OdataJpaServiceFactory;
import org.apache.olingo.odata2.api.ODataServiceFactory;
import org.apache.olingo.odata2.core.rest.ODataRootLocator;
import org.apache.olingo.odata2.core.rest.app.ODataApplication;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.List;

@Component
@ApplicationPath("/odata")
public class JerseyConfig extends ResourceConfig {



    public JerseyConfig(OdataJpaServiceFactory serviceFactory, EntityManagerFactory entityManagerFactory,
                        @Value("${app.odata.enabled-entities}") List<String> enabledEntities,
                        @Value("${app.odata.enabled-methods}") List<String> enabledMethods) {
        ODataApplication oDataApplication = new ODataApplication();
        oDataApplication
                .getClasses()
                .forEach( c -> {
                    if ( !ODataRootLocator.class.isAssignableFrom(c)) {
                        register(c);
                    }
                });

        serviceFactory.setEnabledEntities(enabledEntities);
        EntityManagerFilter entityManagerFilter = new EntityManagerFilter(entityManagerFactory);
        entityManagerFilter.setEnabledMethods(enabledMethods);

        register(new ODataServiceRootLocator(serviceFactory));
        register(entityManagerFilter);
    }

    @Path("/")
    public static class ODataServiceRootLocator extends ODataRootLocator {

        private OdataJpaServiceFactory serviceFactory;

        @Inject
        public ODataServiceRootLocator (OdataJpaServiceFactory serviceFactory) {
            this.serviceFactory = serviceFactory;
        }

        @Override
        public ODataServiceFactory getServiceFactory() {
            return this.serviceFactory;
        }
    }

    @Provider
    public static class EntityManagerFilter implements ContainerRequestFilter,
            ContainerResponseFilter {
        public static final String EM_REQUEST_ATTRIBUTE =
                EntityManagerFilter.class.getName() + "_ENTITY_MANAGER";
        private final EntityManagerFactory entityManagerFactory;

        private List<String> enabledMethods;

        @Context
        private HttpServletRequest httpRequest;
        public EntityManagerFilter(EntityManagerFactory entityManagerFactory) {
            this.entityManagerFactory = entityManagerFactory;
        }

        @Override
        public void filter(ContainerRequestContext containerRequestContext) throws IOException {
            EntityManager entityManager = this.entityManagerFactory.createEntityManager();
            httpRequest.setAttribute(EM_REQUEST_ATTRIBUTE, entityManager);

            if(enabledMethods != null && !enabledMethods.contains(containerRequestContext.getMethod())){
                // Update the error handling framework to handle the below exception
                throw new RuntimeException("Odata is enabled only for the methods: "+enabledMethods);
            }

            if (!"GET".equalsIgnoreCase(containerRequestContext.getMethod())) {
                entityManager.getTransaction().begin();
            }
        }
        @Override
        public void filter(ContainerRequestContext requestContext,
                           ContainerResponseContext responseContext) throws IOException {
            EntityManager entityManager = (EntityManager) httpRequest.getAttribute(EM_REQUEST_ATTRIBUTE);
            if (!"GET".equalsIgnoreCase(requestContext.getMethod())) {
                EntityTransaction entityTransaction = entityManager.getTransaction(); //we do not commit because it's just a READ
                if (entityTransaction.isActive() && !entityTransaction.getRollbackOnly()) {
                    entityTransaction.commit();
                }
            }
            entityManager.close();
        }

        public void setEnabledMethods(List<String> enabledMethods) {
            this.enabledMethods = enabledMethods;
        }
    }

}