package org.drools.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Properties;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RulesRepositoryConfigurator {

    private static final Logger log = LoggerFactory.getLogger(RulesRepositoryConfigurator.class);
    /**
     * The classpath resource from which the RepositoryFactory properties are loaded. This is currently {@value}.
     */
    public static final String PROPERTIES_FILE = "/drools_repository.properties";
    public static final String CONFIGURATOR_CLASS = "org.drools.repository.configurator";

    private static JCRRepositoryConfigurator jcrRepositoryConfigurator = null;
    private static Repository jcrRepository = null;
    private static RulesRepositoryConfigurator rulesRepositoryConfigurator = null;

    private RulesRepositoryConfigurator() {
    }

    public Repository getJCRRepository() {
        return jcrRepository;
    }

    /**
     * Creates an instance of the RulesRepositoryConfigurator, which stores a reference to the under laying JCRRepository.
     *
     * @param properties - if null, they will be read from the /drools_repository.properties file.
     * @return RulesRepositoryConfigurator
     * @throws RepositoryException
     */
    public synchronized static RulesRepositoryConfigurator getInstance(Properties properties) {
        if (rulesRepositoryConfigurator == null) {
            log.info("Creating an instance of the RulesRepositoryConfigurator.");
            rulesRepositoryConfigurator = new RulesRepositoryConfigurator();

            if (properties == null) { //load from file only when the properties passed in are null
                properties = new Properties();
                // Load the properties file ...
                InputStream propStream = ClassUtil.getResourceAsStream(PROPERTIES_FILE, rulesRepositoryConfigurator.getClass());
                if (propStream != null) {
                    try {
                        properties.load(propStream);
                    } catch (IOException ioe) {
                        throw new RulesRepositoryException(ioe);
                    } finally {
                        try {
                            propStream.close();
                        } catch (IOException ioe) {
                            throw new RulesRepositoryException(ioe);
                        }
                    }
                } else {
                    throw new RulesRepositoryException("Cannot load properties from " + PROPERTIES_FILE);
                }
            }

            String configuratorClazz = properties.getProperty(CONFIGURATOR_CLASS);
            if (configuratorClazz == null) {
                throw new RulesRepositoryException("User must define a '" +
                        CONFIGURATOR_CLASS + "' property.");
            }
            try {
                Class<?> clazz = ClassUtil.forName(configuratorClazz, rulesRepositoryConfigurator.getClass());
                jcrRepositoryConfigurator = (JCRRepositoryConfigurator) clazz.newInstance();
            } catch (ClassNotFoundException e) {
                throw new RulesRepositoryException(e);
            } catch (InstantiationException e) {
                throw new RulesRepositoryException(e);
            } catch (IllegalAccessException e) {
                throw new RulesRepositoryException(e);
            }
            try {
                jcrRepository = jcrRepositoryConfigurator.getJCRRepository(properties);
            } catch (RepositoryException e) {
                throw new RulesRepositoryException(e);
            }
        }
        return rulesRepositoryConfigurator;
    }

    public Session login(String userName, String password) throws RepositoryException {
        return jcrRepositoryConfigurator.login(userName, password);
    }

    public Session login(String userName) throws RepositoryException {
        return jcrRepositoryConfigurator.login(userName);
    }

    /**
     * Attempts to setup the repository. If the work that it tries to do has already been done, it will return without modifying
     * the repository. This will register any node types, and setup bootstrap nodes as needed. This will not erase any data.
     *
     * @throws RepositoryException
     */
    public void setupRepository(Session session) throws RepositoryException {
        log.info("Setting up the repository, registering node types etc.");
        try {
            Node root = session.getRootNode();
            Workspace ws = session.getWorkspace();

            //no need to set it up again, skip it if it has.
            boolean registered = RulesRepositoryAdministrator.isNamespaceRegistered(session);

            if (!registered) {
                ws.getNamespaceRegistry().registerNamespace("drools", RulesRepository.DROOLS_URI);

                //Note, the order in which they are registered actually does matter !

                jcrRepositoryConfigurator.registerNodeTypesFromCndFile("/node_type_definitions/configuration_node_type.cnd", session, ws);
                jcrRepositoryConfigurator.registerNodeTypesFromCndFile("/node_type_definitions/tag_node_type.cnd", session, ws);
                jcrRepositoryConfigurator.registerNodeTypesFromCndFile("/node_type_definitions/state_node_type.cnd", session, ws);
                jcrRepositoryConfigurator.registerNodeTypesFromCndFile("/node_type_definitions/versionable_node_type.cnd", session, ws);
                jcrRepositoryConfigurator.registerNodeTypesFromCndFile("/node_type_definitions/versionable_asset_folder_node_type.cnd", session, ws);

                jcrRepositoryConfigurator.registerNodeTypesFromCndFile("/node_type_definitions/rule_node_type.cnd", session, ws);
                jcrRepositoryConfigurator.registerNodeTypesFromCndFile("/node_type_definitions/rulepackage_node_type.cnd", session, ws);

            }

            // Setup the rule repository node
            Node repositoryNode = RulesRepository.addNodeIfNew(root, RulesRepository.RULES_REPOSITORY_NAME, "nt:folder");

            // Setup the RulePackageItem area
            Node packageAreaNode = RulesRepository.addNodeIfNew(repositoryNode, RulesRepository.MODULE_AREA, "nt:folder");

            // Setup the global area
            if (!packageAreaNode.hasNode(RulesRepository.GLOBAL_AREA)) {
                Node globalAreaNode = RulesRepository.addNodeIfNew(packageAreaNode, RulesRepository.GLOBAL_AREA, ModuleItem.MODULE_TYPE_NAME);
                globalAreaNode.addNode(ModuleItem.ASSET_FOLDER_NAME, "drools:versionableAssetFolder");
                globalAreaNode.setProperty(ModuleItem.TITLE_PROPERTY_NAME, RulesRepository.GLOBAL_AREA);
                globalAreaNode.setProperty(AssetItem.DESCRIPTION_PROPERTY_NAME, "the global area that holds sharable assets");
                globalAreaNode.setProperty(AssetItem.FORMAT_PROPERTY_NAME, ModuleItem.MODULE_FORMAT);
                globalAreaNode.setProperty(ModuleItem.CREATOR_PROPERTY_NAME, session.getUserID());
                Calendar lastModified = Calendar.getInstance();
                globalAreaNode.setProperty(ModuleItem.LAST_MODIFIED_PROPERTY_NAME, lastModified);
            }

            // Setup the Snapshot area
            RulesRepository.addNodeIfNew(repositoryNode, RulesRepository.MODULE_SNAPSHOT_AREA, "nt:folder");

            //Setup the Category area
            RulesRepository.addNodeIfNew(repositoryNode, RulesRepository.TAG_AREA, "nt:folder");

            //Setup the State area
            RulesRepository.addNodeIfNew(repositoryNode, RulesRepository.STATE_AREA, "nt:folder");

//            Node configurationArea = RulesRepository.addNodeIfNew(repositoryNode, RulesRepository.CONFIGURATION_AREA, "nt:folder");
//            RulesRepository.addNodeIfNew(configurationArea, RulesRepository.PERSPECTIVES_CONFIGURATION_AREA, "nt:folder");

            //and we need the "Draft" state
            RulesRepository.addNodeIfNew(repositoryNode.getNode(RulesRepository.STATE_AREA), StateItem.DRAFT_STATE_NAME, StateItem.STATE_NODE_TYPE_NAME);

            //Setup the schema area
            RulesRepository.addNodeIfNew(repositoryNode, RulesRepository.SCHEMA_AREA, "nt:folder");

            //Setup the workspace area
            RulesRepository.addNodeIfNew(repositoryNode.getNode(RulesRepository.SCHEMA_AREA), RulesRepository.WORKSPACE_AREA, "nt:folder");

            session.save();
        } catch (RuntimeException e) {
            throw new RepositoryException(e);
        }
    }

    public void shutdown() {
        jcrRepositoryConfigurator.shutdown();
        log.info("SHUTDOWN RULES CONFIG");
        rulesRepositoryConfigurator = null;
    }
}